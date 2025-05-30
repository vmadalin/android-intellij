/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.integration.taskbased

import com.android.tools.asdriver.tests.AndroidStudio
import com.android.tools.profilers.integration.ProfilersTaskTestBase
import org.junit.Test

class HeapDumpTaskTest : ProfilersTaskTestBase() {

  override fun selectTask(studio: AndroidStudio) {
    selectHeapDumpTask(studio)
  }

  override fun verifyTaskStarted(studio: AndroidStudio) {
    verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+started.*support\\s+level\\s+\\=DEBUGGABLE\$", 120)
    verifyIdeaLog(".*PROFILER\\:\\s+Heap\\s+dump\\s+capture\\s+start\\s+succeeded", 120)
  }

  override fun verifyTaskStopped(studio: AndroidStudio) {
    verifyIdeaLog(".*PROFILER\\:\\s+Heap\\s+dump\\s+capture\\s+has\\s+finished", 450)
  }

  override fun verifyUIComponents(studio: AndroidStudio) {
    studio.waitForComponentByClass("CapturePanelUi")
  }

  override fun stopCurrentTask(studio: AndroidStudio) {
    studio.waitForComponentByClass("CapturePanelUi")
  }
  /**
   * Validate heap dump task workflow is working.
   *
   * Test Steps:
   *  1. Import "minApp" in the testData directory of this module.
   *  2. Deploy App and open profiler tool window, set to debuggable mode.
   *  3. Select device -> process -> task-> starting point.
   *  4. Start the task
   *
   * Test Verifications:
   *  1. Verify if the profiler tool window is opened.
   *  2. Verify if Transport proxy is created for the device.
   *  3. Verify task start succeeded.
   *  4. Verify if the capture is parsed successfully.
   *  5. Verify UI components after capture is parsed.
   */
  @Test
  fun test() = testTask()
}