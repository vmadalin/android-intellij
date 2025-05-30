/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.editor

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.adtui.util.ActionToolbarUtil.makeToolbarNavigable
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

/**
 * The actions toolbar updates dynamically based on the component selection, their parents (and if
 * no selection, the root layout)
 */
class ActionsToolbar(private val parent: Disposable, private val surface: DesignSurface<*>) :
  DesignSurfaceListener, Disposable, ConfigurationListener, ModelListener {
  val toolbarComponent: JComponent

  val scope = AndroidCoroutineScope(this)

  @get:TestOnly
  var northToolbar: ActionToolbar? = null
    private set

  @get:TestOnly
  var northEastToolbar: ActionToolbar? = null
    private set

  @get:TestOnly
  var centerToolbar: ActionToolbarImpl? = null
    private set

  private var eastToolbar: ActionToolbar? = null
  private val dynamicGroup = DefaultActionGroup()
  private val configuration: Configuration?
  private var layoutType: DesignerEditorFileType? = null
  private var toolbarActionGroups: ToolbarActionGroups? = null
  private var model: NlModel? = null
    set(value) {
      field?.removeListener(this)
      field = value
      field?.addListener(this)
    }

  init {
    Disposer.register(parent, this)
    scope.launch {
      merge(surface.panningChanged, surface.zoomChanged).collect {
        withContext(uiThread) { northEastToolbar?.updateActionsAsync() }
      }
    }

    scope.launch {
      surface.modelChanged.collect { models -> withContext(uiThread) { modelsChanged(models) } }
    }

    surface.addListener(this)
    // TODO: Update to support multiple configurations
    configuration = surface.configurations.firstOrNull()
    configuration?.addListener(this)
    toolbarComponent = createToolbarComponent()
    updateActionGroups(surface.layoutType)
    updateActions()
  }

  override fun dispose() {
    surface.removeListener(this)
    configuration?.removeListener(this)
    model?.removeListener(this)
    model = null
  }

  private fun updateActionGroups(layoutType: DesignerEditorFileType) {
    toolbarComponent.removeAll()
    toolbarActionGroups?.let { Disposer.dispose(it) }

    toolbarActionGroups =
      layoutType.getToolbarActionGroups(surface).apply {
        if (!Disposer.tryRegister(parent, this)) {
          // The parent object has been disposed so no more updates are needed.
          // Dispose the newly created ToolbarActionGroups and return.
          Disposer.dispose(this)
          return@updateActionGroups
        }
      }

    northToolbar =
      createActionToolbar("NlConfigToolbar", surface, toolbarActionGroups!!.northGroup).apply {
        this.component.name = "NlConfigToolbar"
        this.layoutStrategy = ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY
        this.setLayoutSecondaryActions(true)
      }

    northEastToolbar =
      createActionToolbar("NlRhsConfigToolbar", surface, toolbarActionGroups!!.northEastGroup)
        .apply {
          this.isReservePlaceAutoPopupIcon = false
          this.component.name = "NlRhsConfigToolbar"
          this.layoutStrategy = ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY
          this.setLayoutSecondaryActions(true)
        }

    centerToolbar =
      createActionToolbar("NlLayoutToolbar", surface, dynamicGroup).apply {
        this.component.name = "NlLayoutToolbar"

        // Wrap the component inside a fixed height component so it doesn't disappear
        val wrapper: JPanel = AdtPrimaryPanel(BorderLayout())
        wrapper.add(this.component)

        val model = surface.models.firstOrNull()
        // Only add center toolbar for XML files.
        if (
          model != null &&
            BackedVirtualFile.getOriginFileIfBacked(model.virtualFile).fileType is XmlFileType
        ) {
          toolbarComponent.add(wrapper, BorderLayout.CENTER)
        }
      }

    eastToolbar =
      createActionToolbar("NlRhsToolbar", surface, toolbarActionGroups!!.eastGroup).apply {
        this.component.name = "NlRhsToolbar"
        toolbarComponent.add(this.component, BorderLayout.EAST)
      }

    val northToolbarComponent = northToolbar!!.component
    val northEastToolbarComponent = northEastToolbar!!.component

    val toolbarPanel =
      object : AdtPrimaryPanel(BorderLayout()) {
          override fun getBackground() = surface.background

          override fun isVisible(): Boolean =
            northToolbarComponent.isVisible || northEastToolbarComponent.isVisible
        }
        .apply {
          // set background to null to use the parent's background
          northToolbarComponent.background = null
          northEastToolbarComponent.background = null
          this.add(northToolbarComponent, BorderLayout.CENTER)
          this.add(northEastToolbarComponent, BorderLayout.EAST)
          this.border = BORDER
        }
    toolbarComponent.add(toolbarPanel, BorderLayout.NORTH)
  }

  /**
   * Call to update the state of all the toolbar icons. This can be called when we do not want to
   * wait the default 500ms automatic delay where toolbars are updated automatically.
   */
  private fun refreshToolbarState() {
    UIUtil.invokeAndWaitIfNeeded {
      northToolbar?.updateActionsAsync()
      northEastToolbar?.updateActionsAsync()
      eastToolbar?.updateActionsAsync()
      centerToolbar?.updateActionsAsync()
    }
  }

  fun updateActions() {
    val view = surface.focusedSceneView
    if (view != null) {
      var selection = view.selectionModel.selection
      if (selection.isEmpty()) {
        val roots = view.sceneManager.model.treeReader.components
        roots.singleOrNull()?.let { selection = listOf(it) }
      }
      updateActions(selection)
    } else {
      refreshToolbarState()
    }
  }

  private fun updateActions(newSelection: List<NlComponent>) {
    if (surface.focusedSceneView != null) {
      // TODO: Perform caching
      val selectionToolbar = surface.layoutType.getSelectionContextToolbar(surface, newSelection)
      if (selectionToolbar.childrenCount > 0) {
        dynamicGroup.copyFromGroup(selectionToolbar)
      }
      updateBottomActionBarBorder()
      centerToolbar?.reset()
    }
    refreshToolbarState()
  }

  // ---- Implements DesignSurfaceListener ----
  override fun componentSelectionChanged(
    surface: DesignSurface<*>,
    newSelection: List<NlComponent>,
  ) {
    assert(surface === this.surface)
    if (newSelection.isNotEmpty()) {
      updateActions(newSelection)
    } else {
      updateActions()
    }
  }

  @UiThread
  private fun modelsChanged(models: List<NlModel?>) {
    // Here it is only necessary to keep the reference to one of the models in order to set the
    // ModelListener to one of them
    this.model = models.firstOrNull()
    northToolbar?.updateActionsAsync()
    northEastToolbar?.updateActionsAsync()
    val surfaceLayoutType = surface.layoutType
    if (surfaceLayoutType !== layoutType) {
      layoutType = surfaceLayoutType
      updateActionGroups(layoutType!!)
    }
    updateActions()
  }

  // Hide the bottom border on the main toolbar when the toolbar is empty.
  // This eliminates the double border from the toolbar when the north toolbar is visible.
  private fun updateBottomActionBarBorder() {
    val hasBottomActionBar = eastToolbar!!.component.isVisible || dynamicGroup.childrenCount > 0
    val bottom = if (hasBottomActionBar) 1 else 0
    toolbarComponent.border =
      BorderFactory.createMatteBorder(0, 0, bottom, 0, JBUI.CurrentTheme.Editor.BORDER_COLOR)
  }

  // ---- Implements ModelListener ----
  override fun modelDerivedDataChanged(model: NlModel) {
    ApplicationManager.getApplication().invokeLater {
      if (surface.project.isDisposed) {
        return@invokeLater
      }
      if (model.treeReader.components.size == 1) {
        updateActions()
      }
    }
  }

  override fun changed(flags: Int): Boolean {
    if ((flags and CONFIGURATION_UPDATE_FLAGS) > 0) {
      northToolbar?.let {
        // The North toolbar is the one holding the Configuration Actions
        UIUtil.invokeLaterIfNeeded { it.updateActionsAsync() }
      }
    }
    return true
  }

  companion object {

    val BORDER = BorderFactory.createMatteBorder(0, 0, 1, 0, JBUI.CurrentTheme.Editor.BORDER_COLOR)

    private const val CONFIGURATION_UPDATE_FLAGS =
      ConfigurationListener.CFG_TARGET or ConfigurationListener.CFG_DEVICE

    private fun createToolbarComponent(): JComponent {
      val panel: JComponent = AdtPrimaryPanel(BorderLayout())
      panel.border = BORDER
      return panel
    }

    private fun createActionToolbar(
      place: String,
      targetComponent: JComponent,
      group: ActionGroup,
    ): ActionToolbarImpl {
      val toolbar = ActionManager.getInstance().createActionToolbar(place, group, true)
      if (group === ActionGroup.EMPTY_GROUP) {
        toolbar.component.isVisible = false
      }
      makeToolbarNavigable(toolbar)
      toolbar.targetComponent = targetComponent
      return toolbar as ActionToolbarImpl
    }
  }
}
