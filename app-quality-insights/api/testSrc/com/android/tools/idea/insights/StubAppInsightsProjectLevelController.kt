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
package com.android.tools.idea.insights

import com.android.tools.idea.insights.ai.AiInsightToolkit
import com.android.tools.idea.insights.ai.FakeAiInsightToolkit
import com.android.tools.idea.insights.analytics.IssueSelectionSource
import com.android.tools.idea.insights.events.actions.Action
import com.android.tools.idea.insights.experiments.InsightFeedback
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.mockito.Mockito.mock

open class StubAppInsightsProjectLevelController(
  override val provider: InsightsProvider = FakeInsightsProvider(),
  override val state: Flow<AppInsightsState> = emptyFlow(),
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext),
  private val retrieveInsights: (PsiFile) -> List<AppInsight> = { _ -> emptyList() },
  override val aiInsightToolkit: AiInsightToolkit = FakeAiInsightToolkit(),
) : AppInsightsProjectLevelController {
  override val project: Project = mock()

  override fun refresh() {}

  override fun selectIssue(value: AppInsightsIssue?, selectionSource: IssueSelectionSource) {}

  override fun selectVersions(values: Set<Version>) {}

  override fun selectDevices(values: Set<Device>) {}

  override fun selectOperatingSystems(values: Set<OperatingSystemInfo>) {}

  override fun selectTimeInterval(value: TimeIntervalFilter) {}

  override fun toggleFailureType(value: FailureType) {}

  override fun enterOfflineMode() {}

  override fun insightsInFile(file: PsiFile) = retrieveInsights(file)

  override fun revertToSnapshot(state: AppInsightsState) {}

  override fun selectSignal(value: SignalType) {}

  override fun selectConnection(value: Connection) {}

  override fun nextEvent() {}

  override fun previousEvent() {}

  override fun openIssue(issue: AppInsightsIssue) {}

  override fun closeIssue(issue: AppInsightsIssue) {}

  override fun addNote(issue: AppInsightsIssue, message: String) {}

  override fun deleteNote(note: Note) {}

  override fun selectVisibilityType(value: VisibilityType) {}

  override fun selectIssueVariant(variant: IssueVariant?) {}

  override fun refreshInsight(regenerateWithContext: Boolean) {}

  override fun submitInsightFeedback(insightFeedback: InsightFeedback) {}

  override fun disableAction(action: KClass<out Action>) {}

  override fun enableAction(action: KClass<out Action>) {}
}
