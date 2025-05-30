/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.AndroidXConstants.CONSTRAINT_LAYOUT
import com.android.SdkConstants.RELATIVE_LAYOUT
import com.android.tools.adtui.ZoomController
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.layout.LayoutManagerSwitcher
import com.android.tools.idea.common.layout.manager.PositionableContentLayoutManager
import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.common.model.DnDTransferItem
import com.android.tools.idea.common.model.ItemTransferable
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.scene.TestSceneManager
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import java.awt.Dimension
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.event.ComponentEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.uipreview.AndroidEditorSettings

class DesignSurfaceTest : LayoutTestCase() {

  private var originalSensitivity: Double = 0.0

  override fun setUp() {
    super.setUp()
    originalSensitivity = AndroidEditorSettings.getInstance().globalState.magnifySensitivity
  }

  override fun tearDown() {
    // Reset sensitivity
    AndroidEditorSettings.getInstance().globalState.magnifySensitivity = originalSensitivity
    super.tearDown()
  }

  fun testAddAndRemoveModel() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val model2 = model("model2.xml", component(CONSTRAINT_LAYOUT.oldName())).buildWithoutSurface()
    val surface = TestDesignSurface(myModule.project, myModule.project)

    assertEquals(0, surface.models.size)

    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model1))
    assertEquals(1, surface.models.size)

    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model2))
    assertEquals(2, surface.models.size)

    surface.removeModel(model2)
    surface.zoomController.zoomToFit()
    assertEquals(1, surface.models.size)

    surface.removeModel(model1)
    surface.zoomController.zoomToFit()
    assertEquals(0, surface.models.size)
  }

  fun testAddDuplicatedModel() {
    val model = model("model.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val surface = TestDesignSurface(myModule.project, myModule.project)

    assertEquals(0, surface.models.size)

    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model))
    assertEquals(1, surface.models.size)

    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model))
    // should not add model again and the callback should not be triggered.
    assertEquals(1, surface.models.size)
  }

  fun testAddDuplicatedModelConcurrently() {
    val sceneCreationLatch = CountDownLatch(2)
    val surface =
      TestDesignSurface(
        myModule.project,
        myModule.project,
        createSceneManager = { model, surface ->
          sceneCreationLatch.await()
          TestSceneManager(model, surface)
        },
      )
    val model = model("model.xml", component(RELATIVE_LAYOUT)).build()

    val model1Future = surface.addModelWithoutRender(model)
    val model2Future = surface.addModelWithoutRender(model)

    sceneCreationLatch.countDown()
    sceneCreationLatch.countDown()

    PlatformTestUtil.waitForFuture(CompletableFuture.allOf(model1Future, model2Future))
    assertEquals(
      "the same manager should be used for both models",
      model1Future.get(),
      model2Future.get(),
    )
  }

  fun testRemoveIllegalModel() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val model2 = model("model2.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val surface = TestDesignSurface(myModule.project, myModule.project)

    assertEquals(0, surface.models.size)

    surface.removeModel(model1)
    surface.zoomController.zoomToFit()
    // do nothing and the callback should not be triggered.
    assertEquals(0, surface.models.size)

    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model1))
    assertEquals(1, surface.models.size)

    surface.removeModel(model2)
    surface.zoomController.zoomToFit()
    assertEquals(1, surface.models.size)
  }

  fun testScale() {
    val surface = TestDesignSurface(myModule.project, myModule.project)
    surface.zoomController.setScale(0.66, -1, -1)
    assertFalse(surface.zoomController.setScale(0.663, -1, -1))
    assertFalse(surface.zoomController.setScale(0.664, -1, -1))
    assertTrue(surface.zoomController.setScale(0.665, -1, -1))

    surface.zoomController.setScale(0.33, -1, -1)
    assertFalse(surface.zoomController.setScale(0.332, -1, -1))
    assertTrue(surface.zoomController.setScale(0.335, -1, -1))
  }

  fun testResizeSurfaceRebuildScene() {
    val builder =
      model(
        "relative.xml",
        component(RELATIVE_LAYOUT)
          .withBounds(0, 0, 1000, 1000)
          .matchParentWidth()
          .matchParentHeight(),
      )
    val model1 = builder.buildWithoutSurface()
    val model2 = builder.buildWithoutSurface()

    val surface = TestDesignSurface(project, testRootDisposable)
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model1))
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model2))

    val scene1 = surface.getSceneManager(model1)!!.scene
    val scene2 = surface.getSceneManager(model2)!!.scene
    val oldVersion1 = scene1.displayListVersion
    val oldVersion2 = scene2.displayListVersion

    surface.dispatchEvent(ComponentEvent(surface, ComponentEvent.COMPONENT_RESIZED))

    assertFalse(scene1.displayListVersion == oldVersion1)
    assertFalse(scene2.displayListVersion == oldVersion2)
  }

  fun testResizeSurfaceDoesNotChangeScale() {
    // This also checks if the zoom level is same after resizing, because the screen factor of
    // TestDesignSurface is always 1.
    val builder =
      model(
        "relative.xml",
        component(RELATIVE_LAYOUT)
          .withBounds(0, 0, 1000, 1000)
          .matchParentWidth()
          .matchParentHeight(),
      )
    val model1 = builder.buildWithoutSurface()
    val model2 = builder.buildWithoutSurface()

    val surface = TestDesignSurface(project, testRootDisposable)
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model1))
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model2))

    surface.setSize(1000, 1000)
    surface.dispatchEvent(ComponentEvent(surface, ComponentEvent.COMPONENT_RESIZED))
    val oldScale = surface.zoomController.scale

    surface.setSize(500, 500)
    surface.dispatchEvent(ComponentEvent(surface, ComponentEvent.COMPONENT_RESIZED))

    assertTrue(oldScale == surface.zoomController.scale)
  }

  fun testDesignSurfaceModelOrdering() {
    val builder =
      model(
        "relative.xml",
        component(RELATIVE_LAYOUT)
          .withBounds(0, 0, 1000, 1000)
          .matchParentWidth()
          .matchParentHeight(),
      )
    val model1 = builder.buildWithoutSurface()
    val model2 = builder.buildWithoutSurface()
    val model3 = builder.buildWithoutSurface()

    val surface = TestDesignSurface(project, testRootDisposable)
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model1))
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model2))
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model3))

    assertThat(surface.models).containsExactly(model1, model2, model3).inOrder()
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model3))
    assertThat(surface.models).containsExactly(model1, model2, model3).inOrder()
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model1))
    assertThat(surface.models).containsExactly(model2, model3, model1).inOrder()
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model3))
    assertThat(surface.models).containsExactly(model2, model1, model3).inOrder()
  }

  fun testCanZoom() {
    val surface = TestDesignSurface(project, testRootDisposable)

    // Test min
    surface.zoomController.setScale(0.0104)
    assertFalse(surface.zoomController.canZoomOut())
    surface.zoomController.setScale(0.11)
    assertTrue(surface.zoomController.canZoomOut())

    // Test max
    surface.zoomController.setScale(9.996)
    assertFalse(surface.zoomController.canZoomIn())
    surface.zoomController.setScale(9.99)
    assertTrue(surface.zoomController.canZoomIn())

    // Test some normal cases.
    surface.zoomController.setScale(0.25)
    surface.zoomController.canZoomIn()
    surface.zoomController.canZoomOut()
    surface.zoomController.setScale(0.5)
    surface.zoomController.canZoomIn()
    surface.zoomController.canZoomOut()
    surface.zoomController.setScale(1.0)
    surface.zoomController.canZoomIn()
    surface.zoomController.canZoomOut()
    surface.zoomController.setScale(2.0)
    surface.zoomController.canZoomIn()
    surface.zoomController.canZoomOut()
  }

  fun testSetScale() {
    val surface = TestDesignSurface(project, testRootDisposable)

    surface.zoomController.setScale(1.0)

    // Setting scale is restricted between min and max
    surface.zoomController.setScale(0.001)
    assertEquals(0.01, surface.zoomController.scale)
    surface.zoomController.setScale(20.0)
    assertEquals(10.0, surface.zoomController.scale)
  }

  fun testWaitDesignSurfaceResizeBeforeZoomToFit() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val fitScaleValue = 1.78

    val surface =
      TestDesignSurface(
        project = project,
        disposable = testRootDisposable,
        fitScaleProvider = { fitScaleValue },
        waitForRenderBeforeZoomToFit = true,
      )
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model1))

    // We try to notify that we are ready to apply zoom-to-fit with a bitwiseNumber of "1"
    // (NOTIFY_ZOOM_TO_FIT_INT_MASK).
    surface.notifyZoomToFit()
    surface.notifyZoomToFit()
    surface.notifyZoomToFit()

    // Zoom-to-fit shouldn't be applied if notifyZoomToFit doesn't have also a
    // bitwiseNumber of "2" (NOTIFY_COMPONENT_RESIZED_INT_MASK).
    assertEquals(1.0, surface.zoomController.scale)
    surface.notifyComponentResizedForTest()

    // Zoom-to-fit shouldn't be applied if notifyZoomToFit doesn't have also a
    // bitwiseNumber of "4" (NOTIFY_LAYOUT_CREATED).
    assertEquals(1.0, surface.zoomController.scale)
    surface.notifyLayoutCreatedForTest()

    // We check that zoom-to-fit has been applied.
    assertFalse(surface.zoomController.zoomToFit())
  }

  fun testWaitRenderBeforeZoomToFit() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val fitScaleValue = 1.78

    val surface =
      TestDesignSurface(
        project = project,
        disposable = testRootDisposable,
        fitScaleProvider = { fitScaleValue },
        waitForRenderBeforeZoomToFit = true,
      )
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model1))

    // We try to notify that we are ready to apply zoom-to-fit with a bitwiseNumber of
    // "2"(NOTIFY_COMPONENT_RESIZED_INT_MASK).
    // We try to call notifyComponentResizedForTest multiple times to make sure the mask doesn't
    // change its value if multiple resize callbacks happens.
    surface.notifyComponentResizedForTest()
    surface.notifyComponentResizedForTest()
    surface.notifyComponentResizedForTest()

    // Zoom-to-fit shouldn't be applied if notifyZoomToFit doesn't have also a
    // bitwiseNumber of "1" (NOTIFY_ZOOM_TO_FIT_INT_MASK).
    assertEquals(1.0, surface.zoomController.scale)
    surface.notifyZoomToFit()

    // Zoom-to-fit shouldn't be applied if notifyZoomToFit doesn't have also a
    // bitwiseNumber of "4" (NOTIFY_LAYOUT_CREATED).
    assertEquals(1.0, surface.zoomController.scale)
    surface.notifyLayoutCreatedForTest()

    // We check that zoom-to-fit has been applied.
    assertFalse(surface.zoomController.zoomToFit())
  }

  fun testDoNotWaitToZoomToFit() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val fitScaleValue = 1.78

    val surface =
      TestDesignSurface(
        project = project,
        disposable = testRootDisposable,
        fitScaleProvider = { fitScaleValue },
        waitForRenderBeforeZoomToFit = false,
      )
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model1))

    surface.notifyLayoutCreatedForTest()

    // Zoom-to-fit shouldn't be applied if notifyZoomToFit doesn't have also a
    // bitwiseNumber of "2" (NOTIFY_COMPONENT_RESIZED_INT_MASK).
    assertEquals(1.0, surface.zoomController.scale)

    // We notify that we are ready to apply zoom-to-fit with a bitwiseNumber of "2"
    // (NOTIFY_COMPONENT_RESIZED_INT_MASK).
    // We try to call notifyComponentResizedForTest multiple times to make sure the mask doesn't
    // change its value if multiple resize callbacks happens.
    surface.notifyComponentResizedForTest()
    surface.notifyComponentResizedForTest()
    surface.notifyComponentResizedForTest()

    // We check that zoom-to-fit has been applied.
    assertFalse(surface.zoomController.zoomToFit())
  }

  fun testResetZoomToFitNotifier() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val fitScaleValue = 1.78

    val surface =
      TestDesignSurface(
          project = project,
          disposable = testRootDisposable,
          fitScaleProvider = { fitScaleValue },
          waitForRenderBeforeZoomToFit = true,
        )
        .apply { this.setSize(200, 400) }
    PlatformTestUtil.waitForFuture(surface.addModelWithoutRender(model1))

    // Notify layout creation and DesignSurface resize
    surface.notifyLayoutCreatedForTest()
    surface.notifyComponentResizedForTest()

    // Notify zoom-to-fit a first time:
    // * Current mask takes the expected mask value
    // * Zoom-to-fit is applied
    surface.notifyZoomToFit()
    assertFalse(surface.zoomController.canZoomToFit())
    assertEquals(fitScaleValue, surface.zoomController.scale)

    // Notify zoom-to-fit a second time current mask take ZOOM_TO_FIT_DONE_INT_MASK
    surface.notifyZoomToFit()
    // zoomToFit returns false because zoom-to-fit is already applied.
    assertFalse(surface.zoomController.zoomToFit())

    // Reset the zoom mask and change the zoom to a non zoom-to-fit value, we also wait for
    // DesignSurface resize.
    surface.resetZoomToFitNotifier(shouldWaitForResize = true)
    assertTrue(surface.zoomController.setScale(0.45))

    // Simulate layout creations.
    surface.notifyLayoutCreatedForTest()

    // Scale is still not zoom-to-fit.
    assertEquals(0.45, surface.zoomController.scale)

    // Simulate layout resize.
    surface.notifyComponentResizedForTest()

    // Scale is still not zoom-to-fit.
    assertEquals(0.45, surface.zoomController.scale)

    // Notify zoom-to-fit a first time:
    // * Current mask takes the expected mask value
    // * Zoom-to-fit is applied
    surface.notifyZoomToFit()
    assertFalse(surface.zoomController.canZoomToFit())
    assertEquals(fitScaleValue, surface.zoomController.scale)

    // Notify zoom-to-fit a second time current mask take ZOOM_TO_FIT_DONE_INT_MASK
    surface.notifyZoomToFit()
    // zoomToFit returns false because zoom-to-fit is already applied.
    assertFalse(surface.zoomController.zoomToFit())

    // Reset the zoom mask and change the zoom to a non zoom-to-fit value, we don't wait for
    // DesignSurface.resize
    surface.resetZoomToFitNotifier(shouldWaitForResize = false)
    assertTrue(surface.zoomController.setScale(0.45))

    // Simulate layout creations.
    surface.notifyLayoutCreatedForTest()

    // Scale is still not zoom-to-fit.
    assertEquals(0.45, surface.zoomController.scale)

    // Notify zoom-to-fit:
    // * Current mask takes the expected mask value
    // * Zoom-to-fit is applied
    surface.notifyZoomToFit()
    assertFalse(surface.zoomController.canZoomToFit())

    // Scale is now zoom-to-fit.
    assertEquals(fitScaleValue, surface.zoomController.scale)
  }
}

class TestInteractionHandler(surface: DesignSurface<*>) : InteractionHandlerBase(surface) {
  override fun createInteractionOnPressed(
    mouseX: Int,
    mouseY: Int,
    modifiersEx: Int,
  ): Interaction? = null

  override fun createInteractionOnDrag(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? =
    null
}

class TestLayoutManager : PositionableContentLayoutManager() {
  override fun layoutContainer(
    content: Collection<PositionableContent>,
    availableSize: Dimension,
  ) {}

  lateinit var surface: DesignSurface<*>

  override fun preferredLayoutSize(
    content: Collection<PositionableContent>,
    availableSize: Dimension,
  ): Dimension {
    return surface.sceneViews.map { it.getContentSize(null) }.firstOrNull() ?: Dimension(0, 0)
  }

  override fun getMeasuredPositionableContentPosition(
    content: Collection<PositionableContent>,
    availableWidth: Int,
    availableHeight: Int,
  ): Map<PositionableContent, Point> {
    return content.firstOrNull()?.let { mapOf(it to Point(0, 0)) } ?: emptyMap()
  }
}

class TestActionHandler<T : SceneManager>(surface: DesignSurface<T>) :
  DesignSurfaceActionHandler<DesignSurface<T>>(surface) {
  override val pasteTarget: NlComponent? = null

  override fun canHandleChildren(component: NlComponent, pasted: List<NlComponent>): Boolean = false

  override val flavor: DataFlavor = ItemTransferable.DESIGNER_FLAVOR

  override fun canDeleteElement(dataContext: DataContext): Boolean = false

  override fun isPasteEnabled(dataContext: DataContext): Boolean = false

  override fun isCopyEnabled(dataContext: DataContext): Boolean = false

  override fun isCopyVisible(dataContext: DataContext): Boolean = false

  override fun isCutVisible(dataContext: DataContext): Boolean = false

  override fun isPastePossible(dataContext: DataContext): Boolean = false
}

class TestDesignSurface(
  project: Project,
  disposable: Disposable,
  val createSceneManager: suspend (model: NlModel, surface: DesignSurface<*>) -> SceneManager =
    { model, surface ->
      TestSceneManager(model, surface)
    },
  testLayoutManager: TestLayoutManager = TestLayoutManager(),
  fitScaleProvider: () -> Double = { 1.0 },
  waitForRenderBeforeZoomToFit: Boolean = false,
) :
  DesignSurface<SceneManager>(
    project = project,
    actionManagerProvider = { ModelBuilder.TestActionManager(it) },
    interactionProviderCreator = { TestInteractionHandler(it) },
    positionableLayoutManager = testLayoutManager,
    actionHandlerProvider = { TestActionHandler(it) },
    zoomControlsPolicy = ZoomControlsPolicy.VISIBLE,
    waitForRenderBeforeZoomToFit = waitForRenderBeforeZoomToFit,
  ) {

  init {
    testLayoutManager.surface = this
    Disposer.register(disposable, this)
  }

  override val layoutManagerSwitcher: LayoutManagerSwitcher?
    get() = null

  override val selectionAsTransferable: ItemTransferable
    get() = ItemTransferable(DnDTransferItem(0, ImmutableList.of()))

  override fun createSceneManager(model: NlModel) = runBlocking {
    createSceneManager(model, this@TestDesignSurface).apply { updateSceneViews() }
  }

  override fun scrollToCenter(list: List<NlComponent>) {}

  override val scrollToVisibleOffset = Dimension()

  override fun forceUserRequestedRefresh() {}

  override fun forceRefresh() {}

  override val selectableComponents: List<NlComponent>
    get() = emptyList()

  private val zoomControllerFake =
    createDesignSurfaceZoomControllerFake(
      project = project,
      disposable = disposable,
      trackZoom = null,
      fitScaleProvider = fitScaleProvider,
    )
  override val zoomController: ZoomController
    get() = zoomControllerFake
}
