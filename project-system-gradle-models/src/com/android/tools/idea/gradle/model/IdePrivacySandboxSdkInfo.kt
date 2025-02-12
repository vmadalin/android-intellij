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
package com.android.tools.idea.gradle.model

import java.io.File
import java.io.Serializable

/** Information for privacy sandbox SDK APKs. */
interface IdePrivacySandboxSdkInfo : Serializable {
  /** The task to invoke to build the privacy sandbox SDK */
  val task: String

  /** The location that the privacy sandbox SDKs will be extracted */
  val outputListingFile: File

  val additionalApkSplitTask: String

  val additionalApkSplitFile: File

  /** The task to invoke to build the privacy sandbox SDK for devices that do not support Privacy Sandbox */
  val taskLegacy: String

  /** The location of the extracted SDK apk files */
  val outputListingLegacyFile: File
}
