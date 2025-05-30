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
package com.android.tools.idea.gradle.catalog

import com.android.tools.idea.gradle.dsl.model.VersionCatalogFilesModel
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData
import org.jetbrains.plugins.gradle.util.GradleConstants
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo
import com.intellij.openapi.module.Module

class VersionCatalogFilesGradleModel : VersionCatalogFilesModel {
  override fun getCatalogNameToFileMapping(project: Project): Map<String, String> {
    val externalProjectPath: String = project.basePath ?: return mapOf()
    val projectInfo = ProjectDataManager.getInstance()
      .getExternalProjectData(project, GradleConstants.SYSTEM_ID, externalProjectPath)
    return extractCatalogData(projectInfo)
  }

  override fun getCatalogNameToFileMapping(module: Module): Map<String, String> {
    val externalProjectPath: String = ExternalSystemModulePropertyManager.getInstance(module)
      .getRootProjectPath() ?: return mapOf()
    val projectInfo = ProjectDataManager.getInstance()
      .getExternalProjectData(module.project, GradleConstants.SYSTEM_ID, externalProjectPath)
    return extractCatalogData(projectInfo)
  }

  private fun extractCatalogData(projectInfo: ExternalProjectInfo?): Map<String, String> {
    val versionCatalogsModel = projectInfo?.externalProjectStructure ?: return mapOf()
    val model = ExternalSystemApiUtil.find(
      versionCatalogsModel, BuildScriptClasspathData.VERSION_CATALOGS)

    return model?.data?.catalogsLocations ?: mapOf()
  }
}