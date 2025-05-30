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
package com.android.tools.idea.ui.save

import com.android.SdkConstants.EXT_PNG
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.idea.ui.extractTextFromHtml
import com.android.tools.idea.ui.save.SaveConfigurationResolver.Companion.DEFAULT_SAVE_LOCATION
import com.android.tools.idea.ui.save.SaveConfigurationResolver.Companion.PROJECT_DIR_MACRO
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBTextField
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent

/** Tests for [SaveConfigurationDialog]. */
@RunsInEdt
class SaveConfigurationDialogTest {

  private val projectRule = ProjectRule()
  @get:Rule
  val ruleChain = RuleChain(projectRule, EdtRule(), HeadlessDialogRule())

  private val project: Project
    get() = projectRule.project
  private val projectDir
    get() = project.guessProjectDir()?.toNioPath().toString()
  private val homeDir = System.getProperty("user.home")
  private val timestamp = LocalDateTime.of(2025, 1, 21, 10, 22, 14).atZone(ZoneId.systemDefault()).toInstant()

  @Test
  fun testBasic() {
    // Create an instance of SaveConfigurationDialog (you might need to modify constructor arguments)
    val dialog = SaveConfigurationDialog(
      project,
      DEFAULT_SAVE_LOCATION,
      "Screenshot_%Y%M%D_%H%m%S",
      PostSaveAction.OPEN,
      EXT_PNG, timestamp,
      5)

    val dialogWrapper = dialog.createWrapper()
    createModalDialogAndInteractWithIt(dialogWrapper::show) { dlg ->
      val ui = FakeUi(dlg.rootPane)
      val saveLocationField = ui.getComponent<TextFieldWithBrowseButton>()
      assertThat(saveLocationField.text).isEqualTo("$homeDir/Desktop".toPlatformPath())
      val filenameTemplateField = ui.getComponent<JBTextField>()
      assertThat(filenameTemplateField.text).isEqualTo("Screenshot_%Y%M%D_%H%m%S")
      val previewField = ui.getComponent<JEditorPane>()
      assertThat(extractTextFromHtml(previewField.text)).isEqualTo("$homeDir/Desktop/Screenshot_20250121_102214.png".toPlatformPath())
      saveLocationField.text = "$projectDir/foo/bar"
      filenameTemplateField.text = "screenshots/%4d"
      assertThat(extractTextFromHtml(previewField.text)).isEqualTo("$projectDir/foo/bar/screenshots/0005.png".toPlatformPath())
      val postSavingActionSelector = ui.getComponent<ComboBox<PostSaveAction>>()
      assertThat(postSavingActionSelector.itemCount).isEqualTo(PostSaveAction.entries.filter(PostSaveAction::isSupported).size)
      assertThat(postSavingActionSelector.selectedItem).isEqualTo(PostSaveAction.OPEN)
      postSavingActionSelector.selectedItem = PostSaveAction.NONE
      dlg.clickDefaultButton()
    }
    assertThat(dialogWrapper.isDisposed).isTrue()
    assertThat(dialogWrapper.isOK).isTrue()
    assertThat(dialog.saveLocation).isEqualTo("$PROJECT_DIR_MACRO/foo/bar")
    assertThat(dialog.filenameTemplate).isEqualTo("screenshots/%4d")
    assertThat(dialog.postSaveAction).isEqualTo(PostSaveAction.NONE)
  }

  @Test
  fun testPatternInsertion() {
    // Create an instance of SaveConfigurationDialog (you might need to modify constructor arguments)
    val dialog = SaveConfigurationDialog(
      project,
      DEFAULT_SAVE_LOCATION,
      "Screenshot_%Y%M%D_%H%m%S",
      PostSaveAction.NONE,
      EXT_PNG, timestamp,
      5)

    val dialogWrapper = dialog.createWrapper()
    createModalDialogAndInteractWithIt(dialogWrapper::show) { dlg ->
      val ui = FakeUi(dlg.rootPane)
      val patternInserter = ui.getComponent<JEditorPane> { it.text.contains("year (4 digits)") }
      patternInserter.clickOnHyperlink("%Nd")
      val filenameTemplateField = ui.getComponent<JBTextField>()
      assertThat(filenameTemplateField.text).isEqualTo("Screenshot_%Y%M%D_%H%m%S%3d")
      filenameTemplateField.selectionStart = filenameTemplateField.text.indexOf("%Y")
      filenameTemplateField.selectionEnd = filenameTemplateField.text.indexOf("%3d")
      patternInserter.clickOnHyperlink("%p")
      assertThat(filenameTemplateField.text).isEqualTo("Screenshot_%p%3d")
      assertThat(filenameTemplateField.selectionStart).isEqualTo(filenameTemplateField.text.indexOf("%3d"))
      assertThat(filenameTemplateField.selectionEnd).isEqualTo(filenameTemplateField.text.indexOf("%3d"))
      assertThat(dialogWrapper.doCancelAction())
    }
    assertThat(dialogWrapper.isDisposed).isTrue()
    assertThat(dialogWrapper.isOK).isFalse()
  }

  private fun JEditorPane.clickOnHyperlink(hyperlink: String) {
    assertThat(text.contains("<a href=\"$hyperlink\">")).isTrue()
    fireHyperlinkUpdate(HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, null, hyperlink))
  }

  private fun String.toPlatformPath(): String =
      replace('/', File.separatorChar)
}