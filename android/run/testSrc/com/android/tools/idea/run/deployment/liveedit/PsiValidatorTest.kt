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
package com.android.tools.idea.run.deployment.liveedit

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.editors.liveedit.LiveEditService
import com.android.tools.idea.projectsystem.TestApplicationProjectContext
import com.android.tools.idea.run.deployment.liveedit.analysis.createKtFile
import com.android.tools.idea.run.deployment.liveedit.analysis.directApiCompileByteArray
import com.android.tools.idea.run.deployment.liveedit.analysis.modifyKtFile
import com.android.tools.idea.run.deployment.liveedit.tokens.FakeBuildSystemLiveEditServices
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.wireless.android.sdk.stats.LiveEditEvent
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PsiValidatorTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory().withKotlin()

  private val fakeBuildSystemLiveEditServices = FakeBuildSystemLiveEditServices()

  @Before
  fun setUp() {
    fakeBuildSystemLiveEditServices.register(projectRule.testRootDisposable)
    setUpComposeInProjectFixture(projectRule)
  }

  @Test
  fun propertyInitializer() {
    val file = projectRule.createKtFile("A.kt", """
      val x = 100
      val y = 100
    """.trimIndent())

    val apk = projectRule.directApiCompileByteArray(file)
    val monitor = LiveEditProjectMonitor(LiveEditService.getInstance(projectRule.project), projectRule.project)

    val device: IDevice = mock()
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    whenever(device.isEmulator).thenReturn(false)

    monitor.notifyAppDeploy(TestApplicationProjectContext("app"), device, LiveEditApp(emptySet(), 32), listOf(file.virtualFile)) { true }
    projectRule.modifyKtFile(file, """
      val x = 999
      val y = 100
    """.trimIndent())

    monitor.processChangesForTest(projectRule.project, listOf(file), LiveEditEvent.Mode.MANUAL)
    val status = monitor.status(device)
    assertEquals("Out Of Date", status.title)
    assertTrue(status.description.contains("modified property"))
  }

  @Test
  fun propertyDelegate() {
    val file = projectRule.createKtFile("A.kt", """
      val x: Int by lazy {
        100
      }
    """.trimIndent())

    val apk = projectRule.directApiCompileByteArray(file)
    fakeBuildSystemLiveEditServices.withClasses(apk)
    val monitor = LiveEditProjectMonitor(LiveEditService.getInstance(projectRule.project), projectRule.project)

    val device: IDevice = mock()
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    whenever(device.isEmulator).thenReturn(false)

    monitor.notifyAppDeploy(TestApplicationProjectContext("app"), device, LiveEditApp(emptySet(), 32), listOf(file.virtualFile)) { true }
    projectRule.modifyKtFile(file, """
      val x: Int by lazy {
        999
      }
    """.trimIndent())

    monitor.processChangesForTest(projectRule.project, listOf(file), LiveEditEvent.Mode.MANUAL)
    val status = monitor.status(device)
    assertEquals("Out Of Date", status.title)
    assertTrue(status.description.contains("modified property"))
  }

  @Test
  fun constructor() {
    val file = projectRule.createKtFile("Foo.kt", """
      class Foo(val a: Int, val b: Int) {
        constructor(a: String, b: String): this(0, 0) {}
        constructor(a: Double, b: Double): this(0, 0) {}
      }
    """.trimIndent())

    val apk = projectRule.directApiCompileByteArray(file)
    fakeBuildSystemLiveEditServices.withClasses(apk)
    val monitor = LiveEditProjectMonitor(LiveEditService.getInstance(projectRule.project), projectRule.project)

    val device: IDevice = mock()
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    whenever(device.isEmulator).thenReturn(false)

    monitor.notifyAppDeploy(TestApplicationProjectContext("app"), device, LiveEditApp(emptySet(), 32), listOf(file.virtualFile)) { true }
    projectRule.modifyKtFile(file, """
      class Foo(val a: Int, val b: Int) {
        constructor(a: String, b: String): this(0, 0) {}
        constructor(a: Double, b: Double): this(1, 1) {}
      }

    """.trimIndent())

    monitor.processChangesForTest(projectRule.project, listOf(file), LiveEditEvent.Mode.MANUAL)
    val status = monitor.status(device)
    assertEquals("Out Of Date", status.title)
    assertTrue(status.description.contains("modified constructor"))
  }

  @Test
  fun ignoresNewlinesAndComments() {
    val file = projectRule.createKtFile("Foo.kt", """
      class Foo(val a: Int, val b: Int) {
        constructor(a: String, b: String): this(0, 0) {}
        val foo: Int by lazy {
          100
        }
      }
    """.trimIndent())

    val apk = projectRule.directApiCompileByteArray(file)
    fakeBuildSystemLiveEditServices.withClasses(apk)
    val monitor = LiveEditProjectMonitor(LiveEditService.getInstance(projectRule.project), projectRule.project)

    val device: IDevice = mock()
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    whenever(device.isEmulator).thenReturn(false)

    monitor.notifyAppDeploy(TestApplicationProjectContext("app"), device, LiveEditApp(emptySet(), 32), listOf(file.virtualFile)) { true }
    projectRule.modifyKtFile(file, """
      class Foo(val a: Int, val b: Int) {
        constructor(a: String, b: String): this(0, 0) {
        
        
        }
        val foo: Int by lazy {
          // Comment
          100
        }
      }

    """.trimIndent())

    monitor.processChangesForTest(projectRule.project, listOf(file), LiveEditEvent.Mode.MANUAL)
    val status = monitor.status(device)
    assertEquals("Loading", status.title)
  }
}