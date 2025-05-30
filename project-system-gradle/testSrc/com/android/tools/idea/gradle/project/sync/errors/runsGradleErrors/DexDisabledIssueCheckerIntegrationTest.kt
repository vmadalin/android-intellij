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
package com.android.tools.idea.gradle.project.sync.errors.runsGradleErrors

import com.android.testutils.TestUtils
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.project.sync.errors.DexDisabledIssue
import com.android.tools.idea.gradle.task.AndroidGradleTaskManager
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test

class DexDisabledIssueCheckerIntegrationTest: AndroidGradleTestCase() {
  @Test
  fun testDependencyLibraryLambdaCausesBuildIssue() {
    checkLevel8Issue("library_lambda.jar", "Invoke-customs are only supported starting with Android O")
  }

  @Test
  fun testDependencyDefaultInterfaceCausesBuildIssue() {
    checkLevel8Issue("default_interface.jar", "Default interface methods are only supported starting with Android N")
  }

  @Test
  fun testDependencyStaticInterfaceCausesBuildIssue() {
    checkLevel8Issue("static_interface.jar", "Static interface methods are only supported starting with Android N")
  }

  private fun checkLevel8Issue(dependency: String, expectedMessage: String) {
    loadSimpleApplication()
    val project = project
    addJarDependency(dependency)

    val generatedExceptions = mutableListOf<Exception>()
    val taskNotificationListener = object : ExternalSystemTaskNotificationListener {
      override fun onFailure(proojecPath: String, id: ExternalSystemTaskId, exception: Exception) {
        generatedExceptions.add(exception)
      }
    }
    val projectPath = project.basePath.orEmpty()
    val id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)
    val settings = GradleExecutionSettings().apply {
      tasks = listOf(":app:assembleDebug")
    }
    AndroidGradleTaskManager().executeTasks(projectPath, id, settings, taskNotificationListener)
    assertThat(generatedExceptions).hasSize(1)
    assertThat(generatedExceptions[0]).isInstanceOf(BuildIssueException::class.java)
    val buildIssue = (generatedExceptions[0] as BuildIssueException).buildIssue
    assertThat(buildIssue).isInstanceOf(DexDisabledIssue::class.java)
    assertThat((buildIssue as DexDisabledIssue).description).contains(expectedMessage)
  }

  private fun addJarDependency(dependency: String) {
    val gradlePropertiesFile = project.baseDir.findChild("gradle.properties")!!
    runWriteAction {
      gradlePropertiesFile.setBinaryContent("""
          org.gradle.java.installations.paths=${TestUtils.getJava17Jdk().toString().replace("\\", "/")}
      """.trimIndent().toByteArray(Charsets.UTF_8))
    }

    val appModule = project.findAppModule()
    val projectModel = ProjectBuildModel.get(project)
    val buildModel = projectModel.getModuleBuildModel(appModule)
    buildModel!!.android().compileOptions().sourceCompatibility().setLanguageLevel(LanguageLevel.JDK_1_7)
    buildModel.android().compileOptions().targetCompatibility().setLanguageLevel(LanguageLevel.JDK_1_7)
    buildModel.java().toolchain().languageVersion().setVersion(17)
    val dependencyFile = myFixture.copyFileToProject("desugaringErrors/$dependency", "jarDependencies/$dependency")
    assertThat(dependencyFile).isNotNull()
    buildModel.dependencies().addFile("implementation", dependencyFile.path)
    ApplicationManager.getApplication().invokeAndWait {
      WriteCommandAction.runWriteCommandAction(project) {
        projectModel.applyChanges()
      }
    }
  }
}