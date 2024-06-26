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
package com.android.build.attribution.proto.converters

import com.android.build.attribution.BuildAnalysisResultsMessage
import com.android.build.attribution.analyzers.ProjectConfigurationAnalyzer
import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.proto.PairEnumFinder
import com.android.build.attribution.proto.constructPluginType
import com.android.build.attribution.proto.transformPluginData

class ProjectConfigurationAnalyzerResultMessageConverter {
  companion object {
    fun transform(projectConfigurationAnalyzerResult: ProjectConfigurationAnalyzer.Result): BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult =
      BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult.newBuilder()
        .addAllPluginsConfigurationDataMap(
          projectConfigurationAnalyzerResult.pluginsConfigurationDataMap.map {
            transformPluginsDataLongMap(it.key, it.value)
          }
        )
        .addAllProjectConfigurationData(
          projectConfigurationAnalyzerResult.projectsConfigurationData.map(Companion::transformProjectConfigurationData))
        .addAllAllAppliedPlugins(
          projectConfigurationAnalyzerResult.allAppliedPlugins.map {
            transformStringPluginDataMap(it.key, it.value)
          }
        )
        .build()

    fun construct(projectConfigurationAnalyzerResult: BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult)
      : ProjectConfigurationAnalyzer.Result {
      val projectConfigurationData = mutableListOf<ProjectConfigurationData>()
      for (projectConfigurationDatum in projectConfigurationAnalyzerResult.projectConfigurationDataList) {
        val projectPath = projectConfigurationDatum.projectPath
        val totalConfigurationTime = projectConfigurationDatum.totalConfigurationTime
        val pluginsConfigurationData = mutableListOf<PluginConfigurationData>()
        for (pluginConfigurationDatum in projectConfigurationDatum.pluginsConfigurationDataList) {
          pluginsConfigurationData
            .add(
              PluginConfigurationData(
                PluginData(
                  constructPluginType(pluginConfigurationDatum.plugin.pluginType), pluginConfigurationDatum.plugin.idName),
                pluginConfigurationDatum.configurationTimeMS
              )
            )
        }
        val configurationSteps = mutableListOf<ProjectConfigurationData.ConfigurationStep>()
        for (configurationStep in projectConfigurationDatum.configurationStepsList) {
          val configurationStepType = constructConfigurationStepTypes(configurationStep.type)
          configurationSteps.add(ProjectConfigurationData.ConfigurationStep(configurationStepType, configurationStep.configurationTimeMs))
        }
        projectConfigurationData.add(
          ProjectConfigurationData(projectPath, totalConfigurationTime, pluginsConfigurationData, configurationSteps))
      }
      val pluginDataMap = mutableMapOf<PluginData, Long>()
      projectConfigurationAnalyzerResult.pluginsConfigurationDataMapList.forEach {
        val pluginData = PluginData(constructPluginType(it.pluginData.pluginType), it.pluginData.idName)
        pluginDataMap[pluginData] = it.long
      }
      val appliedPlugins = mutableMapOf<String, List<PluginData>>()
      projectConfigurationAnalyzerResult.allAppliedPluginsList.forEach {
        appliedPlugins[it.appliedPlugins] = it.pluginsList.map { plugin ->
          PluginData(constructPluginType(plugin.pluginType), plugin.idName)
        }
      }
      return ProjectConfigurationAnalyzer.Result(pluginDataMap, projectConfigurationData, appliedPlugins)
    }

    private fun transformPluginsDataLongMap(pluginData: PluginData,
                                            long: Long): BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult.PluginDataLongMap =
      BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult.PluginDataLongMap.newBuilder()
        .setPluginData(transformPluginData(pluginData))
        .setLong(long)
        .build()

    private fun transformProjectConfigurationData(projectConfigurationData: ProjectConfigurationData) =
      BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult.ProjectConfigurationData.newBuilder()
        .addAllPluginsConfigurationData((projectConfigurationData.pluginsConfigurationData).map(Companion::transformPluginConfigurationData))
        .addAllConfigurationSteps((projectConfigurationData.configurationSteps).map(Companion::transformConfigurationStep))
        .setProjectPath(projectConfigurationData.projectPath)
        .setTotalConfigurationTime(projectConfigurationData.totalConfigurationTimeMs)
        .build()

    private fun transformPluginConfigurationData(pluginConfigurationData: PluginConfigurationData) =
      BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult.ProjectConfigurationData.PluginConfigurationData.newBuilder()
        .setPlugin(transformPluginData(pluginConfigurationData.plugin))
        .setConfigurationTimeMS(pluginConfigurationData.configurationTimeMs)
        .build()

    private fun transformConfigurationStep(configurationStep: ProjectConfigurationData.ConfigurationStep) =
      BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.newBuilder()
        .setType(transformConfigurationStepTypes(configurationStep.type))
        .setConfigurationTimeMs(configurationStep.configurationTimeMs)
        .build()

    private fun transformConfigurationStepTypes(type: ProjectConfigurationData.ConfigurationStep.Type): BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type =
      PairEnumFinder.aToB(type)

    private fun transformStringPluginDataMap(appliedPlugins: String,
                                             plugins: List<PluginData>): BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult.StringPluginDataMap =
      BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult.StringPluginDataMap.newBuilder()
        .setAppliedPlugins(appliedPlugins)
        .addAllPlugins(plugins.map(::transformPluginData))
        .build()

    private fun constructConfigurationStepTypes(type: BuildAnalysisResultsMessage.ProjectConfigurationAnalyzerResult.ProjectConfigurationData.ConfigurationStep.Type): ProjectConfigurationData.ConfigurationStep.Type =
      PairEnumFinder.bToA(type)
  }
}