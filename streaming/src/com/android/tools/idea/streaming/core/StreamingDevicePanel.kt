/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.adtui.ui.NotificationHolderPanel
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingConstants

private const val IS_TOOLBAR_HORIZONTAL = true

/**
 * Provides view of one Android device in the Running Devices tool window.
 */
abstract class StreamingDevicePanel(
  val id: DeviceId,
  mainToolbarId: String,
  secondaryToolbarId: String,
) : BorderLayoutPanel(), UiDataProvider, Disposable {

  /** Plain text name of the device. */
  internal abstract val title: String
  /** An HTML string containing detailed information about the device. */
  internal abstract val description: String
  internal abstract val icon: Icon
  internal abstract val isClosable: Boolean
  internal abstract val preferredFocusableComponent: JComponent

  internal abstract var zoomToolbarVisible: Boolean
  internal abstract val primaryDisplayView: AbstractDisplayView?
  internal val hasContent: Boolean
    get() = primaryDisplayView != null

  protected val mainToolbar: ActionToolbar
  protected val secondaryToolbar: ActionToolbar
  protected val centerPanel = BorderLayoutPanel()

  init {
    background = primaryPanelBackground

    mainToolbar = createToolbar(mainToolbarId, IS_TOOLBAR_HORIZONTAL)
    secondaryToolbar = createToolbar(secondaryToolbarId, IS_TOOLBAR_HORIZONTAL)
    secondaryToolbar.isReservePlaceAutoPopupIcon = false

    addToCenter(centerPanel)

    val toolbarPanel = BorderLayoutPanel()
    if (IS_TOOLBAR_HORIZONTAL) {
      mainToolbar.orientation = SwingConstants.HORIZONTAL
      secondaryToolbar.orientation = SwingConstants.HORIZONTAL
      toolbarPanel.add(mainToolbar.component, BorderLayout.CENTER)
      toolbarPanel.add(secondaryToolbar.component, BorderLayout.EAST)
      toolbarPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.BOTTOM)
      addToTop(toolbarPanel)
    }
    else {
      mainToolbar.orientation = SwingConstants.VERTICAL
      secondaryToolbar.orientation = SwingConstants.VERTICAL
      toolbarPanel.add(mainToolbar.component, BorderLayout.CENTER)
      toolbarPanel.add(secondaryToolbar.component, BorderLayout.SOUTH)
      toolbarPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.RIGHT)
      addToLeft(toolbarPanel)
    }
  }

  /**
   * Adds a notification panel. If the [notificationPanel] has a close action, that action has to make
   * sure that the notification is removed when the action is executed.
   */
  fun addNotification(notificationPanel: EditorNotificationPanel) {
    findNotificationHolderPanel()?.addNotification(notificationPanel)
  }

  /** Removes the given notification panel. */
  fun removeNotification(notificationPanel: EditorNotificationPanel) {
    findNotificationHolderPanel()?.removeNotification(notificationPanel)
  }

  internal abstract fun createContent(deviceFrameVisible: Boolean, savedUiState: UiState? = null)
  internal abstract fun destroyContent(): UiState
  internal abstract fun setDeviceFrameVisible(visible: Boolean)

  override fun uiDataSnapshot(sink: DataSink) {
    sink[DISPLAY_VIEW_KEY] = primaryDisplayView
    sink[ZOOMABLE_KEY] = primaryDisplayView
    sink[SERIAL_NUMBER_KEY] = id.serialNumber
    sink[STREAMING_CONTENT_PANEL_KEY] = centerPanel
    sink[DEVICE_ID_KEY] = id
  }

  override fun dispose() {
    destroyContent()
  }

  @Suppress("SameParameterValue")
  private fun createToolbar(toolbarId: String, horizontal: Boolean): ActionToolbar {
    val actions = listOf(CustomActionsSchema.getInstance().getCorrectedAction(toolbarId)!!)
    val toolbar = ActionManager.getInstance().createActionToolbar(toolbarId, DefaultActionGroup(actions), horizontal)
    toolbar.layoutStrategy = ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY
    toolbar.setLayoutSecondaryActions(true)
    toolbar.targetComponent = this
    ActionToolbarUtil.makeToolbarNavigable(toolbar)
    return toolbar
  }

  fun updateMainToolbar() {
    mainToolbar.updateActionsAsync()
  }

  private fun findNotificationHolderPanel() =
      primaryDisplayView?.findContainingComponent<NotificationHolderPanel>()

  internal interface UiState
}