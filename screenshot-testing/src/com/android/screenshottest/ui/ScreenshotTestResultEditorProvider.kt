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
package com.android.screenshottest.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.text.QuickDefinitionProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Custom FileEditorProvider for screenshot test result in memory virtual file.
 */
class ScreenshotTestResultEditorProvider : FileEditorProvider, QuickDefinitionProvider, DumbAware {
  lateinit var inMemoryTestResultVirtualFile: ScreenshotTestResultVirtualFile

  override fun accept(project: Project,
                      file: VirtualFile): Boolean = ::inMemoryTestResultVirtualFile.isInitialized &&
                                                    file is ScreenshotTestResultVirtualFile &&
                                                    file == inMemoryTestResultVirtualFile

  override fun createEditor(project: Project, file: VirtualFile): FileEditor =
    ScreenshotTestResultEditorSingleton.apply {
      inMemoryTestResultVirtualFile = file as ScreenshotTestResultVirtualFile
    }

  override fun getEditorTypeId(): String = SCREENSHOT_TEST_RESULT_EDITOR_ID

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  override fun acceptRequiresReadAction(): Boolean = false

  companion object {
    const val SCREENSHOT_TEST_RESULT_EDITOR_ID = "android-screenshot-test-result-editor"
  }
}