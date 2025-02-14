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
package com.android.tools.idea.gradle.structure.configurables.ui

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.project.Project

fun Project.logUsageTopNavigateTo(toSelect: ModelPanel<*>) {
  val psdEvent = PSDEvent
    .newBuilder()
    .setGeneration(PSDEvent.PSDGeneration.PROJECT_STRUCTURE_DIALOG_GENERATION_002)
  toSelect.copyIdFieldsTo(psdEvent)
  UsageTracker.log(
    AndroidStudioEvent
      .newBuilder()
      .setCategory(AndroidStudioEvent.EventCategory.PROJECT_STRUCTURE_DIALOG)
      .setKind(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_TOP_TAB_CLICK)
      .setPsdEvent(psdEvent)
      .withProjectId(this))
}
