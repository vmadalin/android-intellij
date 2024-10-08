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
package com.android.tools.profilers.tasks

import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfilerTaskLauncherTest {

  private val ideProfilerServices = FakeIdeProfilerServices()

  @Test
  fun `launch profiler task with undefined task type throws error`() {
    assertThrows(AssertionError::class.java) {
      ProfilerTaskLauncher.launchProfilerTask(ProfilerTaskType.UNSPECIFIED, false, emptyMap(), Common.Session.getDefaultInstance(),
                                              emptyMap(), { _, _ -> /* Do nothing */ }, ideProfilerServices)
    }
  }
}