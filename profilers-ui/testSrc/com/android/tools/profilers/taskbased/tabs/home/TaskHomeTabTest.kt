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
package com.android.tools.profilers.taskbased.tabs.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.adtui.compose.StudioTestTheme
import com.android.tools.adtui.compose.standaloneSingleWindowApplication
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel
import com.android.tools.profilers.taskbased.home.selections.deviceprocesses.ProcessListModel
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandlerFactory
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule

class TaskHomeTabTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  val composeTestRule = createStudioComposeTestRule()

  @get:Rule
  val ignoreTestRule = IgnoreTestRule()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("TaskGridViewTestChannel", myTransportService, FakeEventService())

  private val myComponents = FakeIdeProfilerComponents()

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var taskHomeTabModel: TaskHomeTabModel

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    taskHomeTabModel = myProfilers.taskHomeTabModel
    ideProfilerServices.enableTaskBasedUx(true)
    val taskHandlers = ProfilerTaskHandlerFactory.createTaskHandlers(myManager)
    taskHandlers.forEach { myProfilers.addTaskHandler(it.key, it.value) }
    assertThat(taskHomeTabModel.processListModel.deviceToProcesses.value).isEmpty()
  }

  @After
  fun cleanup() {
    taskHomeTabModel.setProfilingProcessStartingPoint(TaskHomeTabModel.ProfilingProcessStartingPoint.UNSPECIFIED)
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, light theme`() {
    standaloneSingleWindowApplication(
      title = "Testing TaskHomeTab",
    ) {
      StudioTestTheme(darkMode = false) {
        TaskHomeTab(taskHomeTabModel, myComponents)
      }
    }
  }

  @Ignore("b/309566948")
  @Test
  fun `visual test, dark theme`() {
    standaloneSingleWindowApplication(
      title = "Testing TaskHomeTab",
    ) {
      StudioTestTheme(darkMode = true) {
        TaskHomeTab(taskHomeTabModel, myComponents)
      }
    }
  }

  @Test
  fun `selecting device, process, and task enable start profiler task button`() {
    composeTestRule.setContent {
      TaskHomeTab(taskHomeTabModel, myComponents)
    }

    // Populate the device.
    val device = TaskModelTestUtils.createDevice("FakeDevice", Common.Device.State.ONLINE, "12", 28)
    // Populate the processes for the selected device.
    TaskModelTestUtils.addDeviceWithProcess(device, TaskModelTestUtils.createProcess(20, "FakeProcess1", Common.Process.State.ALIVE,
                                                                                     device.deviceId), myTransportService, myTimer)
    TaskModelTestUtils.addDeviceWithProcess(device, TaskModelTestUtils.createProcess(40, "FakeProcess2", Common.Process.State.ALIVE,
                                                                                     device.deviceId), myTransportService, myTimer)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Select the device
    taskHomeTabModel.processListModel.onDeviceSelection(device)

    // Selection of the FakeDevice should populate the process list with 2 processes.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(2)
    assertThat(taskHomeTabModel.processListModel.getSelectedDeviceProcesses()).hasSize(2)

    assertThat(taskHomeTabModel.selectedDevice).isNotNull()
    // Make sure device selection is also registered in data model.
    assertThat(taskHomeTabModel.selectedDevice!!.name).isEqualTo("FakeDevice")

    // Select a process.
    composeTestRule.onAllNodesWithTag("ProcessListRow").onFirst().assertExists()
    composeTestRule.onAllNodesWithTag("ProcessListRow").onFirst().performClick()

    // Make sure process selection was registered in data model.
    assertThat(taskHomeTabModel.selectedProcess.name).isEqualTo("FakeProcess1")

    // The profiler pre-selects the first task in the task grid already, so no explicit selection is needed.
    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)

    // Set starting point to 'NOW' to enable the start task button.
    verifyAndSelectNow()

    // Make sure at this point, the start profiler task button is now enabled as device, process, and task selections were all valid.
    composeTestRule.onNodeWithTag("EnterTaskButton").assertIsEnabled()
  }

  @Test
  fun `test selecting dead preferred process and startup-capable task`() {
    composeTestRule.setContent {
      TaskHomeTab(taskHomeTabModel, myComponents)
    }

    // Select the offline device
    taskHomeTabModel.processListModel.onDeviceSelection(ProcessListModel.ToolbarDeviceSelection("FakeDevice", 30, false, false, ""))
    assertThat(taskHomeTabModel.selectedDevice).isNotNull()
    // Make sure device selection is also registered in data model
    assertThat(taskHomeTabModel.selectedDevice!!.name).isEqualTo("FakeDevice")

    // Set the preferred process to a non-empty/null name so the dead/static entry is allowed to be added
    myProfilers.preferredProcessName = "com.foo.bar"

    // Selection of the OFFLINE FakeDevice should populate the process list with 1, dead/static process representing the preferred process
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(1)
    composeTestRule.onNodeWithTag("ProcessListRow").onChildAt(1).assertTextContains("com.foo.bar")
    composeTestRule.onNodeWithTag("ProcessListRow").onChildAt(3).assertTextContains("Not running")

    // Make sure preferred process selection was registered in data model (preferred process should have been auto-selected)
    assertThat(taskHomeTabModel.selectedProcess.name).isEqualTo("com.foo.bar")

    // Select a startup-capable task (e.g. System Trace)
    verifyTaskExistsAndSelect(ProfilerTaskType.SYSTEM_TRACE)

    // Set starting point to 'PROCESS_START' to enable the start task button.
    verifyAndSelectProcessStart(isSelectedProcessAlive = false)

    // Make sure at this point, the start profiler task button is enabled as the PROCESS_START task starting point option is selected
    // and all other device, process, and task criteria is met for starting a task from process start
    composeTestRule.onNodeWithTag("EnterTaskButton").assertExists().assertIsEnabled()
  }

  @Test
  fun `test selecting an alive, non preferred process and startup capable task`() {
    // should not allow for startup dropdown option to be present
    composeTestRule.setContent {
      TaskHomeTab(taskHomeTabModel, myComponents)
    }

    // Set the preferred process to a non-empty/null name so the dead/static entry is allowed to be added
    myProfilers.preferredProcessName = "com.foo.bar"

    // Add online device with alive process (that is NOT the preferred process)
    TaskModelTestUtils.addDeviceWithProcess(TaskModelTestUtils.createDevice("FakeDevice", "123", 456, Common.Device.State.ONLINE, "12", 30),
                                            TaskModelTestUtils.createProcess(20, "not.preferred.process", Common.Process.State.ALIVE, 456,
                                                                             Common.Process.ExposureLevel.PROFILEABLE), myTransportService,
                                            myTimer)
    taskHomeTabModel.processListModel.onDeviceSelection(ProcessListModel.ToolbarDeviceSelection("FakeDevice", 30, true, false, "123"))
    // Make sure device selection is also registered in data model.
    assertThat(taskHomeTabModel.selectedDevice!!.name).isEqualTo("FakeDevice")

    // Selection of the online FakeDevice, with no preferred process alive on device should populate the process list with 1 dead/static
    // process representing the preferred process, and another process representing the alive process populated above.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(2)
    composeTestRule.onNodeWithText("not.preferred.process").assertExists().assertIsDisplayed()
    // Select the non preferred process.
    composeTestRule.onNodeWithText("not.preferred.process").performClick()
    // Make sure process selection was registered in data model.
    assertThat(taskHomeTabModel.selectedProcess.name).isEqualTo("not.preferred.process")

    // Select a startup-capable task (e.g. System Trace)
    verifyTaskExistsAndSelect(ProfilerTaskType.SYSTEM_TRACE)

    // Set starting point to 'NOW' to enable the start task button.
    verifyAndSelectNow()

    // Make sure at this point, the start profiler task button is enabled as the NOW task starting point option is selected
    // and all other device, process, and task criteria is met for starting a task from now
    composeTestRule.onNodeWithTag("EnterTaskButton").assertExists().assertIsEnabled()
  }

  @Test
  fun `test selecting dead preferred process and non startup capable task`() {
    composeTestRule.setContent {
      TaskHomeTab(taskHomeTabModel, myComponents)
    }

    // Select the offline device
    taskHomeTabModel.processListModel.onDeviceSelection(ProcessListModel.ToolbarDeviceSelection("FakeDevice", 30, false, false, ""))
    assertThat(taskHomeTabModel.selectedDevice).isNotNull()
    // Make sure device selection is also registered in data model.
    assertThat(taskHomeTabModel.selectedDevice!!.name).isEqualTo("FakeDevice")

    // Set the preferred process to a non-empty/null name so the dead/static entry is allowed to be added
    myProfilers.preferredProcessName = "com.foo.bar"

    // Selection of the OFFLINE FakeDevice should populate the process list with 1, dead/static process representing the preferred process.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(1)
    composeTestRule.onNodeWithTag("ProcessListRow").onChildAt(1).assertTextContains("com.foo.bar")
    composeTestRule.onNodeWithTag("ProcessListRow").onChildAt(3).assertTextContains("Not running")

    // Make sure preferred process selection was registered in data model (preferred process should have been auto-selected)
    assertThat(taskHomeTabModel.selectedProcess.name).isEqualTo("com.foo.bar")

    // Select a task that is NOT startup-capable (e.g. Heap Dump)
    verifyTaskExistsAndSelect(ProfilerTaskType.HEAP_DUMP)

    // Set starting point to 'PROCESS_START'.
    verifyAndSelectProcessStart(isSelectedProcessAlive = false)

    // Make sure at this point, the start profiler task button is disabled as the task is not startup capable.
    composeTestRule.onNodeWithTag("EnterTaskButton").assertExists().assertIsNotEnabled()
  }

  @Test
  fun `test selecting alive, profileable preferred process and non startup capable task`() {
    composeTestRule.setContent {
      TaskHomeTab(taskHomeTabModel, myComponents)
    }

    // Set the preferred process to a non-empty/null name so the dead/static entry is allowed to be added
    myProfilers.preferredProcessName = "com.foo.bar"

    // Add online device with alive process (that is also the preferred process)
    TaskModelTestUtils.addDeviceWithProcess(
      TaskModelTestUtils.createDevice("FakeDevice", "123", 456, Common.Device.State.ONLINE, "12", 30),
      TaskModelTestUtils.createProcess(20, "com.foo.bar", Common.Process.State.ALIVE, 456, Common.Process.ExposureLevel.PROFILEABLE),
      myTransportService, myTimer)
    taskHomeTabModel.processListModel.onDeviceSelection(ProcessListModel.ToolbarDeviceSelection("FakeDevice", 30, true, false, "123"))
    // Make sure device selection is also registered in data model.
    assertThat(taskHomeTabModel.selectedDevice!!.name).isEqualTo("FakeDevice")

    // Selection of the OFFLINE FakeDevice should populate the process list with 1, dead/static process representing the preferred process.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(1)
    composeTestRule.onNodeWithTag("ProcessListRow").onChildAt(1).assertTextContains("com.foo.bar")
    composeTestRule.onNodeWithTag("ProcessListRow").onChildAt(3).assertTextContains("Profileable")

    // Make sure preferred process selection was registered in data model (preferred process should have been auto-selected)
    assertThat(taskHomeTabModel.selectedProcess.name).isEqualTo("com.foo.bar")

    // Select a task that is NOT startup-capable, like heap dump
    verifyTaskExistsAndSelect(ProfilerTaskType.HEAP_DUMP)

    // Set starting point to 'NOW' to enable the start task button.
    verifyAndSelectNow()

    // Make sure at this point, the start profiler task button is disabled as the task selected is debuggable-only and the selected process
    // is profileable, despite the task starting point selection of 'NOW'.
    composeTestRule.onNodeWithTag("EnterTaskButton").assertExists().assertIsNotEnabled()
  }

  @Test
  fun `test selecting alive, debuggable preferred process and non startup capable task`() {
    composeTestRule.setContent {
      TaskHomeTab(taskHomeTabModel, myComponents)
    }

    // Populate the device.
    val device = TaskModelTestUtils.createDevice("FakeDevice", Common.Device.State.ONLINE, "12", 30)
    // Populate the processes for the selected device.
    TaskModelTestUtils.addDeviceWithProcess(device, TaskModelTestUtils.createProcess(20, "FakeProcess", Common.Process.State.ALIVE,
                                                                                     device.deviceId,
                                                                                     Common.Process.ExposureLevel.DEBUGGABLE),
                                              myTransportService, myTimer)
    // Select the device
    taskHomeTabModel.processListModel.onDeviceSelection(device)

    // Selection of the FakeDevice should populate the process list with 2 processes.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(1)
    assertThat(taskHomeTabModel.processListModel.getSelectedDeviceProcesses()).hasSize(1)

    assertThat(taskHomeTabModel.selectedDevice).isNotNull()
    // Make sure device selection is also registered in data model.
    assertThat(taskHomeTabModel.selectedDevice!!.name).isEqualTo("FakeDevice")

    // Select the only process (the profileable process).
    composeTestRule.onAllNodesWithTag("ProcessListRow").onFirst().assertExists()
    composeTestRule.onAllNodesWithTag("ProcessListRow").onFirst().performClick()

    // Make sure process selection was registered in data model.
    assertThat(taskHomeTabModel.selectedProcess.name).isEqualTo("FakeProcess")

    // Make heap dump task selection (a debuggable-only task).
    verifyTaskExistsAndSelect(ProfilerTaskType.HEAP_DUMP)

    // Make sure task selection was registered in data model.
    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.HEAP_DUMP)

    // Set starting point to 'NOW' to enable the start task button.
    verifyAndSelectNow()

    // Make sure at this point, the start profiler task button is enabled as the task selected is debuggable-only, the selected process is
    // debuggable, and the task starting point is NOW, which any task supports when a running process is selected.
    composeTestRule.onNodeWithTag("EnterTaskButton").assertExists().assertIsEnabled()
  }

  @Test
  fun `test selecting profileable process and debuggable only task`() {
    composeTestRule.setContent {
      TaskHomeTab(taskHomeTabModel, myComponents)
    }

    // Populate the device.
    val device = TaskModelTestUtils.createDevice("FakeDevice", Common.Device.State.ONLINE, "12", 30)
    // Populate the processes for the selected device.
    TaskModelTestUtils.addDeviceWithProcess(device,
                                            TaskModelTestUtils.createProcess(20, "FakeProcess", Common.Process.State.ALIVE, device.deviceId,
                                                                             Common.Process.ExposureLevel.PROFILEABLE), myTransportService,
                                            myTimer)
    // Select the device
    taskHomeTabModel.processListModel.onDeviceSelection(device)

    // Selection of the FakeDevice should populate the process list with 2 processes.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(1)
    assertThat(taskHomeTabModel.processListModel.getSelectedDeviceProcesses()).hasSize(1)

    assertThat(taskHomeTabModel.selectedDevice).isNotNull()
    // Make sure device selection is also registered in data model.
    assertThat(taskHomeTabModel.selectedDevice!!.name).isEqualTo("FakeDevice")

    // Select the only process (the profileable process).
    composeTestRule.onAllNodesWithTag("ProcessListRow").onFirst().assertExists()
    composeTestRule.onAllNodesWithTag("ProcessListRow").onFirst().performClick()

    // Make sure process selection was registered in data model.
    assertThat(taskHomeTabModel.selectedProcess.name).isEqualTo("FakeProcess")

    // Make heap dump task selection (a debuggable-only task).
    verifyTaskExistsAndSelect(ProfilerTaskType.HEAP_DUMP)

    // Make sure task selection was registered in data model.
    assertThat(taskHomeTabModel.selectedTaskType).isEqualTo(ProfilerTaskType.HEAP_DUMP)

    // Set starting point to 'NOW' to enable the start task button.
    verifyAndSelectNow()

    // Make sure at this point, the start profiler task button is disabled as the task selected is debuggable-only, and a profileable
    // process was selected.
    composeTestRule.onNodeWithTag("EnterTaskButton").assertExists().assertIsNotEnabled()
  }

  @Test
  fun `test selecting dead preferred process and startup-capable task enables profiler task start button`() {
    composeTestRule.setContent {
      TaskHomeTab(taskHomeTabModel, myComponents)
    }

    // Select the offline device
    taskHomeTabModel.processListModel.onDeviceSelection(ProcessListModel.ToolbarDeviceSelection("FakeDevice", 30, false, false, ""))
    assertThat(taskHomeTabModel.selectedDevice).isNotNull()
    // Make sure device selection is also registered in data model.
    assertThat(taskHomeTabModel.selectedDevice!!.name).isEqualTo("FakeDevice")

    // Set the preferred process to a non-empty/null name so the dead/static entry is allowed to be added
    myProfilers.preferredProcessName = "com.foo.bar"

    // Selection of the OFFLINE FakeDevice should populate the process list with 1, dead/static process representing the preferred process.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(1)
    composeTestRule.onNodeWithTag("ProcessListRow").onChildAt(1).assertTextContains("com.foo.bar")
    composeTestRule.onNodeWithTag("ProcessListRow").onChildAt(3).assertTextContains("Not running")

    // Make sure preferred process selection was registered in data model (preferred process should have been auto-selected)
    assertThat(taskHomeTabModel.selectedProcess.name).isEqualTo("com.foo.bar")

    // Select a startup-capable task (e.g. System Trace)
    verifyTaskExistsAndSelect(ProfilerTaskType.SYSTEM_TRACE)

    // Set starting point to 'PROCESS_START' to enable the start task button.
    verifyAndSelectProcessStart(isSelectedProcessAlive = false)

    // Now that startup-tasks are enabled via the task starting point dropdown, and preferred process + startup-capable task selections have
    // been made, the start profiler task button should be enabled.
    composeTestRule.onNodeWithTag("EnterTaskButton").assertExists().assertIsEnabled()
  }

  @Test
  fun `test selecting alive preferred process and startup capable task`() {
    composeTestRule.setContent {
      TaskHomeTab(taskHomeTabModel, myComponents)
    }

    // Set the preferred process to a non-empty/null name so the dead/static entry is allowed to be added
    myProfilers.preferredProcessName = "com.foo.bar"

    // Add online device with alive process (that is also the preferred process)
    TaskModelTestUtils.addDeviceWithProcess(
      TaskModelTestUtils.createDevice("FakeDevice", "123", 456, Common.Device.State.ONLINE, "12", 30),
      TaskModelTestUtils.createProcess(20, "com.foo.bar", Common.Process.State.ALIVE, 456, Common.Process.ExposureLevel.PROFILEABLE),
      myTransportService, myTimer)
    taskHomeTabModel.processListModel.onDeviceSelection(ProcessListModel.ToolbarDeviceSelection("FakeDevice", 30, true, false, "123"))
    // Make sure device selection is also registered in data model.
    assertThat(taskHomeTabModel.selectedDevice!!.name).isEqualTo("FakeDevice")

    // Selection of the OFFLINE FakeDevice should populate the process list with 1, dead/static process representing the preferred process.
    composeTestRule.onAllNodesWithTag("ProcessListRow").assertCountEquals(1)
    composeTestRule.onNodeWithTag("ProcessListRow").onChildAt(1).assertTextContains("com.foo.bar")
    composeTestRule.onNodeWithTag("ProcessListRow").onChildAt(3).assertTextContains("Profileable")

    // Make sure preferred process selection was registered in data model (preferred process should have been auto-selected)
    assertThat(taskHomeTabModel.selectedProcess.name).isEqualTo("com.foo.bar")

    // Have a non-startup capable task selected, such as Heap Dump.
    verifyTaskExistsAndSelect(ProfilerTaskType.HEAP_DUMP)

    // Select a startup-capable task (e.g. System Trace)
    verifyTaskExistsAndSelect(ProfilerTaskType.SYSTEM_TRACE)

    // Set starting point to 'NOW' to enable the start task button.
    verifyAndSelectNow()
    // The selected process is alive, so the enter task button should be enabled, even with the task starting point not being startup
    composeTestRule.onNodeWithTag("EnterTaskButton").assertExists().assertIsEnabled()

    // Select startup from task starting point dropdown
    verifyAndSelectProcessStart(isSelectedProcessAlive = true)
    // Under startup, the start profiler task button should still be enabled
    composeTestRule.onNodeWithTag("EnterTaskButton").assertExists().assertIsEnabled()
  }

  private fun verifyTaskExistsAndSelect(taskType: ProfilerTaskType) {
    composeTestRule.onNodeWithText(taskType.description).assertIsDisplayed().assertIsEnabled().assertHasClickAction()
    composeTestRule.onNodeWithText(taskType.description).performClick()
  }

  private fun verifyAndSelectNow() = withVerifyingStartingPointDropdown{
    val nowDropdownOption = composeTestRule.onAllNodesWithTag("TaskStartingPointOption", useUnmergedTree = true).onFirst()
    nowDropdownOption.onChildAt(0).assertTextContains("Now").onParent()
    nowDropdownOption.onChildAt(1).assertTextContains("(attaches to selected process)").onParent()
    nowDropdownOption.onParent().assertIsEnabled()
    nowDropdownOption.onParent().performClick()
  }

  private fun verifyAndSelectProcessStart(isSelectedProcessAlive: Boolean) = withVerifyingStartingPointDropdown {
    val processStartDropdownOption = composeTestRule.onAllNodesWithTag("TaskStartingPointOption", useUnmergedTree = true).onLast()
    processStartDropdownOption.onChildAt(0).assertTextContains("Process start").onParent()
    processStartDropdownOption.onChildAt(1).assertTextContains(
      "(${if (isSelectedProcessAlive) "restarts" else "starts"} process)").onParent()
    processStartDropdownOption.onParent().assertIsEnabled()
    processStartDropdownOption.onParent().performClick()
  }

  private fun withVerifyingStartingPointDropdown(verifyDropdownOption: () -> Unit) {
    composeTestRule.onNodeWithTag("TaskStartingPointDropdown").assertHasClickAction()
    composeTestRule.onNodeWithTag("TaskStartingPointDropdown").performClick()
    composeTestRule.onAllNodesWithTag("TaskStartingPointOption", useUnmergedTree = true).assertCountEquals(2)

    verifyDropdownOption()
  }

  @Test
  fun `test recording type dropdown appears for applicable tasks only`() {
    composeTestRule.setContent {
      TaskHomeTab(taskHomeTabModel, myComponents)
    }
    composeTestRule.onNodeWithTag("TaskRecordingTypeDropdown").assertDoesNotExist()
    // Selecting a task that has recording types should now show the recording type dropdown
    verifyTaskExistsAndSelect(ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING)
    composeTestRule.onNodeWithTag("TaskRecordingTypeDropdown").assertExists().assertIsDisplayed()
    // Selecting a task that does NOT have recording types should NOT show the recording type dropdown
    verifyTaskExistsAndSelect(ProfilerTaskType.SYSTEM_TRACE)
    composeTestRule.onNodeWithTag("TaskRecordingTypeDropdown").assertDoesNotExist()
  }
}