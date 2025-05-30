/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.HighlightColorAction
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.RECOMPOSITION_COLOR_RED_ARGB
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ApplicationRule
import java.awt.event.MouseEvent
import java.util.EnumSet
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RenderSettingsActionTest {
  private lateinit var event: AnActionEvent

  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  private val treeSettings = FakeTreeSettings().apply { showRecompositions = true }
  private val fakeRenderSettings = FakeRenderSettings()
  private val capabilities =
    EnumSet.noneOf(Capability::class.java).apply {
      add(Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS)
    }
  private var isConnected = true

  @Before
  fun setUp() {
    event = createEvent()
  }

  @Test
  fun testActionVisibility() {
    val highlightColorAction = HighlightColorAction { fakeRenderSettings }

    isConnected = false
    treeSettings.showRecompositions = false
    capabilities.clear()

    highlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()

    treeSettings.showRecompositions = true
    highlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()

    isConnected = true
    highlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()

    capabilities.add(Capability.SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS)
    highlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()

    treeSettings.showRecompositions = false
    highlightColorAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun testSelectedColor() {
    val colors =
      mapOf(
        0xFFFF0000.toInt() to "Red",
        0xFF4F9EE3.toInt() to "Blue",
        0xFF479345.toInt() to "Green",
        0xFFFFC66D.toInt() to "Yellow",
        0xFF871094.toInt() to "Purple",
        0xFFE1A336.toInt() to "Orange",
      )
    val highlightColorAction = HighlightColorAction { fakeRenderSettings }

    for ((color, text) in colors) {
      fakeRenderSettings.recompositionColor = color
      for (action in highlightColorAction.getChildren(event)) {
        action.update(event)
        assertThat(Toggleable.isSelected(event.presentation)).isEqualTo(action.templateText == text)
      }
    }

    for (action in highlightColorAction.getChildren(event)) {
      fakeRenderSettings.recompositionColor = 0
      action.update(event)
      (action as CheckboxAction).setSelected(event, true)
      val expected = colors.filter { it.value == action.templateText }.map { it.key }.single()
      assertThat(fakeRenderSettings.recompositionColor).isEqualTo(expected)
    }
  }

  private fun createEvent(): AnActionEvent {
    val inspector: LayoutInspector = mock()
    val client: AppInspectionInspectorClient = mock()
    whenever(inspector.treeSettings).thenReturn(treeSettings)
    whenever(inspector.currentClient).thenReturn(client)
    doAnswer { capabilities }.whenever(client).capabilities
    doAnswer { isConnected }.whenever(client).isConnected

    val dataContext = SimpleDataContext.getSimpleContext(LAYOUT_INSPECTOR_DATA_KEY, inspector)
    val inputEvent = mock<MouseEvent>()
    return AnActionEvent.createEvent(
      dataContext,
      Presentation(),
      ActionPlaces.UNKNOWN,
      ActionUiKind.NONE,
      inputEvent,
    )
  }
}

class FakeRenderSettings : RenderSettings {
  override val modificationListeners = mutableListOf<RenderSettings.Listener>()

  override var scalePercent = 100
    set(value) {
      field = value
      invokeListeners()
    }

  override var drawBorders = true
    set(value) {
      field = value
      invokeListeners()
    }

  override var drawUntransformedBounds = false
    set(value) {
      field = value
      invokeListeners()
    }

  override var drawLabel = true
    set(value) {
      field = value
      invokeListeners()
    }

  override var drawFold = false
    set(value) {
      field = value
      invokeListeners()
    }

  override val hoverColor = HOVER_COLOR_ARGB
  override val selectionColor = SELECTION_COLOR_ARGB
  override val baseColor = BASE_COLOR_ARGB
  override val outlineColor = OUTLINE_COLOR_ARGB
  override var recompositionColor = RECOMPOSITION_COLOR_RED_ARGB
    set(value) {
      field = value
      invokeListeners()
    }
}
