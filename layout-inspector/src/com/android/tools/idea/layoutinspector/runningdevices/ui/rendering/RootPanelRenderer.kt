/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.ui.rendering

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.components.BorderLayoutPanel

/** A generic renderer for embedded Layout Inspector */
abstract class LayoutInspectorRenderer : BorderLayoutPanel(), Disposable {
  abstract var interceptClicks: Boolean

  open fun refresh() {}
}

/**
 * A panel containing the actual renderer. This panel does not do the rendering itself, instead it
 * delegates the rendering to a [LayoutInspectorRenderer] that it contains. The actual renderer
 * panel is provided by [onDeviceRendererProvider] and [studioRendererProvider].
 */
class RootPanelRenderer(
  disposable: Disposable,
  private val inspectorModel: InspectorModel,
  private val onDeviceRendererProvider: (Disposable) -> OnDeviceRendererPanel,
  private val studioRendererProvider: (Disposable) -> StudioRendererPanel,
) : LayoutInspectorRenderer() {
  companion object {
    private val logger = Logger.getInstance(RootPanelRenderer::class.java)
  }

  override var interceptClicks: Boolean
    set(value) {
      currentRenderer?.interceptClicks = value

      if (!value) {
        // Clear selection to avoid keeping a selected rectangle in the ui, that would be
        // un-selectable since clicks are not being intercepted.
        inspectorModel.setSelection(null, SelectionOrigin.INTERNAL)
        inspectorModel.hoveredNode = null
      }
    }
    get() = currentRenderer?.interceptClicks == true

  private var currentRenderer: LayoutInspectorRenderer? = null
    set(value) {
      if (field == value) {
        // No need to replace if it's the same instance.
        return
      }
      logger.info("Setting up renderer: ${value?.javaClass?.name}")

      field?.let {
        // Remove and dispose the old renderer
        remove(it)
        Disposer.dispose(it)
      }

      field = value

      // Add the new renderer
      value?.let { addToCenter(it) }
    }

  private val connectionListener =
    object : InspectorModel.ConnectionListener {
      // Each time the connection changes the OnDeviceRendererPanel holds a stale instance of
      // OnDeviceRenderingClient. Resetting currentRenderer is a workaround to force the
      // OnDeviceRendererPanel to be recreated for each new connection.
      // TODO("b/403288855"): RootPanelRenderer shouldn't know about connections, refactor the code
      // to remove this workaround.
      override fun onConnectionChanged(newClient: InspectorClient) {
        if (!newClient.isConnected) {
          currentRenderer = null
        }
      }
    }

  /**
   * [InspectorModel.ModificationListener] responsible for setting the correct renderer, if the app
   * is XR or not.
   */
  private val modificationListener =
    object : InspectorModel.ModificationListener {
      override fun onModification(
        oldWindow: AndroidWindow?,
        newWindow: AndroidWindow?,
        isStructuralChange: Boolean,
      ) {
        // TODO(b/398195142) it would be good to refactor this class to have a ViewModel, and move
        // this logic inside the view model.
        val isXr =
          inspectorModel.isXr || StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ON_DEVICE_RENDERING.get()
        if (isXr) {
          when (currentRenderer) {
            is StudioRendererPanel,
            null -> {
              currentRenderer = onDeviceRendererProvider(this@RootPanelRenderer)
            }
            is OnDeviceRendererPanel -> {}
            else -> throw IllegalArgumentException("Unknown renderer: $currentRenderer")
          }
        } else {
          when (currentRenderer) {
            is StudioRendererPanel -> {}
            is OnDeviceRendererPanel,
            null -> {
              currentRenderer = studioRendererProvider(this@RootPanelRenderer)
            }
            else -> throw IllegalArgumentException("Unknown renderer: $currentRenderer")
          }
        }
      }
    }

  init {
    Disposer.register(disposable, this)
    isOpaque = false

    // Events are not dispatched to the parent if the child has a mouse listener. So we need to
    // manually forward them.
    ForwardingMouseListener(componentProvider = { parent }, shouldForward = { true }).also {
      addMouseListener(it)
      addMouseMotionListener(it)
      addMouseWheelListener(it)
    }

    inspectorModel.addModificationListener(modificationListener)
    inspectorModel.addConnectionListener(connectionListener)
  }

  override fun dispose() {
    inspectorModel.removeModificationListener(modificationListener)
    inspectorModel.removeConnectionListener(connectionListener)
  }

  override fun refresh() {
    currentRenderer?.refresh()
  }
}
