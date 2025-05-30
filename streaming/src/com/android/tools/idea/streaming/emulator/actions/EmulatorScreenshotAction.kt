/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.streaming.emulator.actions

import com.android.SdkConstants
import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.emulator.control.Image
import com.android.emulator.control.ImageFormat
import com.android.io.writeImage
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.streaming.emulator.EmptyStreamObserver
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.DeferredResultStreamObserver
import com.android.tools.idea.ui.screenshot.FramingOption
import com.android.tools.idea.ui.screenshot.ScreenshotDecorator
import com.android.tools.idea.ui.screenshot.ScreenshotImage
import com.android.tools.idea.ui.screenshot.ScreenshotProvider
import com.android.tools.idea.ui.screenshot.ScreenshotViewer
import com.google.common.base.Throwables
import com.google.common.base.Throwables.throwIfUnchecked
import com.google.common.util.concurrent.UncheckedExecutionException
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.ExecutionException
import javax.imageio.IIOException
import javax.imageio.ImageIO

/**
 * Takes a screenshot of the Emulator display, saves it to a file, and opens it in editor.
 */
class EmulatorScreenshotAction : AbstractEmulatorAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val project: Project = event.getData(CommonDataKeys.PROJECT) ?: return
    val emulatorView = getEmulatorView(event) ?: return
    emulatorView.emulator.getScreenshot(getScreenshotRequest(emulatorView.displayId), ScreenshotReceiver(emulatorView, project))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private class ScreenshotReceiver(val emulatorView: EmulatorView, val project: Project) : EmptyStreamObserver<Image>() {

    override fun onNext(message: Image) {
      executeOnPooledThread {
        showScreenshotViewer(message)
      }
    }

    private fun showScreenshotViewer(screenshot: Image) {
      try {
        val imageBytes = screenshot.image
        val image = ImageIO.read(imageBytes.newInput()) ?: throw IIOException("Corrupted screenshot image")

        val screenshotDecorator = EmulatorScreenshotDecorator(emulatorView)
        val emulatorController = emulatorView.emulator
        val displayId = emulatorView.displayId
        val framingOptions = if (displayId == PRIMARY_DISPLAY_ID && emulatorController.getSkin() != null) listOf(avdFrame) else listOf()
        val screenshotImage = ScreenshotImage(image, screenshot.format.rotation.rotationValue,
                                              emulatorController.emulatorConfig.deviceType)
        val decoration = ScreenshotViewer.getDefaultDecoration(screenshotImage, screenshotDecorator, framingOptions.firstOrNull())
        val processedImage = screenshotDecorator.decorate(screenshotImage, decoration)
        val file = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG).toPath()
        processedImage.writeImage("PNG", file)
        val backingFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?: throw IOException("Unable to save screenshot")
        val screenshotProvider = EmulatorScreenshotProvider(emulatorController, displayId)

        ApplicationManager.getApplication().invokeLater {
          val viewer = ScreenshotViewer(project, screenshotImage, backingFile, screenshotProvider, screenshotDecorator, framingOptions, 0,
                                        EnumSet.noneOf(ScreenshotViewer.Option::class.java))
          viewer.show()
        }
      }
      catch (e: Exception) {
        thisLogger().error("Error while displaying screenshot viewer", e)
      }
    }
  }

  private class EmulatorScreenshotProvider(val emulatorController: EmulatorController, val displayId: Int) : ScreenshotProvider {

    init {
      Disposer.register(emulatorController, this)
    }

    @Throws(RuntimeException::class, CancellationException::class)
    override fun captureScreenshot(): ScreenshotImage {
      val receiver = DeferredResultStreamObserver<Image>()
      emulatorController.getScreenshot(getScreenshotRequest(displayId), receiver)

      try {
        val screenshot = runBlocking { receiver.deferredResult.await() }
        val image = ImageIO.read(screenshot.image.newInput()) ?: throw RuntimeException("Corrupted screenshot image")
        return ScreenshotImage(image, screenshot.format.rotation.rotationValue, emulatorController.emulatorConfig.deviceType)
      }
      catch (e: Throwable) {
        throwIfUnchecked(e)
        throw RuntimeException(e)
      }
    }

    override fun dispose() {
    }
  }

  private class EmulatorScreenshotDecorator(val emulatorView: EmulatorView) : ScreenshotDecorator {

    override fun decorate(screenshotImage: ScreenshotImage, framingOption: FramingOption?, backgroundColor: Color?): BufferedImage {
      if (emulatorView.displayId != PRIMARY_DISPLAY_ID) {
        return screenshotImage.image // Decorations are applied only to the primary display.
      }
      val image = screenshotImage.image
      val w = image.width
      val h = image.height
      val skinDefinition = emulatorView.emulator.getSkin(emulatorView.currentPosture?.posture)
      val skin = skinDefinition?.createScaledLayout(w, h, screenshotImage.screenshotRotationQuadrants)
      val arcWidth = skin?.displayCornerSize?.width ?: 0
      val arcHeight = skin?.displayCornerSize?.height ?: 0
      if (framingOption == null || skin == null) {
        @Suppress("UndesirableClassUsage")
        val result = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val graphics = result.createGraphics()
        val displayRectangle = Rectangle(0, 0, w, h)
        graphics.drawImageWithRoundedCorners(image, displayRectangle, arcWidth, arcHeight)
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.DST_OUT)
        skin?.drawFrameAndMask(graphics, displayRectangle)
        if (backgroundColor != null) {
          graphics.color = backgroundColor
          graphics.composite = AlphaComposite.getInstance(AlphaComposite.DST_OVER)
          graphics.fillRect(0, 0, image.width, image.height)
        }
        graphics.dispose()
        return result
      }

      val frameRectangle = skin.frameRectangle
      @Suppress("UndesirableClassUsage")
      val result = BufferedImage(frameRectangle.width, frameRectangle.height, BufferedImage.TYPE_INT_ARGB)
      val graphics = result.createGraphics()
      val displayRectangle = Rectangle(-frameRectangle.x, -frameRectangle.y, w, h)
      graphics.drawImageWithRoundedCorners(image, displayRectangle, arcWidth, arcHeight)

      skin.drawFrameAndMask(graphics, displayRectangle)
      graphics.dispose()
      return result
    }

    private fun Graphics2D.drawImageWithRoundedCorners(image: BufferedImage, displayRectangle: Rectangle, arcWidth: Int, arcHeight: Int) {
      if (arcWidth > 0 && arcHeight > 0) {
        clip = Area(RoundRectangle2D.Double(displayRectangle.x.toDouble(), displayRectangle.y.toDouble(),
                                            displayRectangle.width.toDouble(), displayRectangle.height.toDouble(),
                                            arcWidth.toDouble(), arcHeight.toDouble()))
      }
      drawImage(image, null, displayRectangle.x, displayRectangle.y)
      clip = null
    }

    override val canClipToDisplayShape: Boolean
      get() = true
  }
}

private val avdFrame = object : FramingOption {
  override val displayName = "Show Device Frame"
}

private fun getScreenshotRequest(displayId: Int) =
    ImageFormat.newBuilder().setFormat(ImageFormat.ImgFormat.PNG).setDisplay(displayId).build()
