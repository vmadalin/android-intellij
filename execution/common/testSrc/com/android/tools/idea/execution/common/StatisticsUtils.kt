/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.execution.common

import com.android.tools.analytics.LoggedUsage
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent

fun assertTaskPresentedInStats(usages: List<LoggedUsage>, taskName: String) {
  val runEvent = usages.find { it.studioEvent.kind == AndroidStudioEvent.EventKind.RUN_EVENT }!!.studioEvent.runEvent
  assertThat(runEvent.launchTaskDetailList.map { it.id }).contains(taskName)
}