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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert
import org.junit.Test

@RunsInEdt
class FabricCrashlyticsRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @Test
  fun testNecessities() {
    val expectedNecessitiesMap = mapOf(
      ("3.6.0" to "4.0.0") to AgpUpgradeComponentNecessity.IRRELEVANT_FUTURE,
      ("4.0.0" to "4.2.0") to AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT,
      ("4.1.0" to "4.2.0") to AgpUpgradeComponentNecessity.IRRELEVANT_PAST
    )
    expectedNecessitiesMap.forEach { (t, u) ->
      val processor = FabricCrashlyticsRefactoringProcessor(project, GradleVersion.parse(t.first), GradleVersion.parse(t.second))
      Assert.assertEquals(processor.necessity(), u)
    }
  }

  @Test
  fun testClasspathDependencies() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricClasspathDependencies"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, GradleVersion.parse("4.0.0"), GradleVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/FabricClasspathDependenciesExpected"))
  }

  @Test
  fun testIsAlwaysNoOpOnFabricClasspathDependenciesExpected() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricClasspathDependenciesExpected"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, GradleVersion.parse("4.0.0"), GradleVersion.parse("4.2.0"))
    Assert.assertTrue(processor.isAlwaysNoOpForProject)
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/FabricClasspathDependenciesExpected"))
  }

  @Test
  fun testFabricSdk() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricSdk"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, GradleVersion.parse("4.0.0"), GradleVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/FabricSdkExpected"))
  }

  @Test
  fun testIsAlwaysNoOpOnFabricSdkExpected() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricSdkExpected"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, GradleVersion.parse("4.0.0"), GradleVersion.parse("4.2.0"))
    Assert.assertTrue(processor.isAlwaysNoOpForProject)
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/FabricSdkExpected"))
  }

  @Test
  fun testFabricSdkWithNdk() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricSdkWithNdk"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, GradleVersion.parse("4.0.0"), GradleVersion.parse("4.2.0"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/FabricSdkWithNdkExpected"))
  }

  @Test
  fun testIsAlwaysNoOpOnFabricSdkWithNdkExpected() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricSdkWithNdkExpected"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, GradleVersion.parse("4.0.0"), GradleVersion.parse("4.2.0"))
    Assert.assertTrue(processor.isAlwaysNoOpForProject)
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/FabricSdkWithNdkExpected"))
  }

  @Test
  fun testIsAlwaysNoOpOnNonFabric() {
    writeToBuildFile(TestFileName("FabricCrashlytics/NonFabric"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, GradleVersion.parse("4.0.0"), GradleVersion.parse("4.2.0"))
    Assert.assertTrue(processor.isAlwaysNoOpForProject)
    processor.run()
    verifyFileContents(buildFile, TestFileName("FabricCrashlytics/NonFabric"))
  }
}