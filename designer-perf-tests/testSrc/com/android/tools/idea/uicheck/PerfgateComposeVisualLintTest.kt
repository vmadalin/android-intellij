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
package com.android.tools.idea.uicheck

import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.compose.gradle.renderer.renderPreviewElementForResult
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.rendering.ComposeRenderTestBase
import com.android.tools.idea.rendering.ElapsedTimeMeasurement
import com.android.tools.idea.rendering.HeapSnapshotMemoryUseMeasurement
import com.android.tools.idea.rendering.LayoutlibNativeMemoryMeasurement
import com.android.tools.idea.rendering.measureOperation
import com.android.tools.idea.testing.virtualFile
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.accessibilityBasedHierarchyParser
import com.android.tools.idea.uibuilder.visual.visuallint.BottomAppBarAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.BottomNavAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.BoundsAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.ButtonSizeAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.LongTextAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.OverlapAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.TextFieldSizeAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.ViewVisualLintIssueProvider
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.android.tools.idea.uibuilder.visual.visuallint.WearMarginAnalyzerInspection
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.android.tools.preview.config.REFERENCE_DESKTOP_SPEC
import com.android.tools.preview.config.REFERENCE_FOLDABLE_SPEC
import com.android.tools.preview.config.REFERENCE_PHONE_SPEC
import com.android.tools.preview.config.REFERENCE_TABLET_SPEC
import com.android.tools.rendering.RenderResult
import org.junit.Before
import org.junit.Test

internal const val NUMBER_OF_SAMPLES = 10

val uiCheckBenchmark =
  Benchmark.Builder("UI Check Benchmark")
    .setProject("Design tools")
    .setDescription(
      "Benchmark for measuring performance of UI Check, using $NUMBER_OF_SAMPLES samples."
    )
    .build()

data class ExtendedPreviewConfiguration(
  val configuration: PreviewConfiguration,
  val showDecorations: Boolean,
)

internal val UiCheckConfigurations =
  listOf(
    // Reference Phone
    ExtendedPreviewConfiguration(
      PreviewConfiguration.cleanAndGet(device = REFERENCE_PHONE_SPEC),
      true,
    ),
    // Reference Phone Landscape
    ExtendedPreviewConfiguration(
      PreviewConfiguration.cleanAndGet(
        device = "spec:width=411dp,height=891dp,orientation=landscape"
      ),
      true,
    ),
    // Reference Foldable
    ExtendedPreviewConfiguration(
      PreviewConfiguration.cleanAndGet(device = REFERENCE_FOLDABLE_SPEC),
      true,
    ),
    // Reference Tablet
    ExtendedPreviewConfiguration(
      PreviewConfiguration.cleanAndGet(device = REFERENCE_TABLET_SPEC),
      true,
    ),
    // Reference Desktop
    ExtendedPreviewConfiguration(
      PreviewConfiguration.cleanAndGet(device = REFERENCE_DESKTOP_SPEC),
      true,
    ),
    ExtendedPreviewConfiguration(PreviewConfiguration.cleanAndGet(fontScale = 0.85f), false),
    ExtendedPreviewConfiguration(PreviewConfiguration.cleanAndGet(fontScale = 1f), false),
    ExtendedPreviewConfiguration(PreviewConfiguration.cleanAndGet(fontScale = 1.15f), false),
    ExtendedPreviewConfiguration(PreviewConfiguration.cleanAndGet(fontScale = 1.3f), false),
    ExtendedPreviewConfiguration(PreviewConfiguration.cleanAndGet(fontScale = 1.8f), false),
    ExtendedPreviewConfiguration(PreviewConfiguration.cleanAndGet(fontScale = 2f), false),
    ExtendedPreviewConfiguration(
      PreviewConfiguration.cleanAndGet(uiMode = Configuration.UI_MODE_NIGHT_NO),
      false,
    ),
    ExtendedPreviewConfiguration(
      PreviewConfiguration.cleanAndGet(uiMode = Configuration.UI_MODE_NIGHT_YES),
      false,
    ),
  )

class PerfgateComposeVisualLintTest : ComposeRenderTestBase() {
  @Before
  override fun setUp() {
    super.setUp()
    val visualLintInspections =
      arrayOf(
        BoundsAnalyzerInspection(),
        BottomNavAnalyzerInspection(),
        BottomAppBarAnalyzerInspection(),
        TextFieldSizeAnalyzerInspection(),
        OverlapAnalyzerInspection(),
        LongTextAnalyzerInspection(),
        ButtonSizeAnalyzerInspection(),
        WearMarginAnalyzerInspection(),
      )
    projectRule.fixture.enableInspections(*visualLintInspections)
  }

  @Test
  fun testComposeVisualLintRun() {
    val facet = projectRule.mainAndroidFacet(":app")
    val uiCheckPreviewFile =
      facet.virtualFile("src/main/java/google/simpleapplication/UiCheckPreview.kt")
    val visualLintIssueProvider =
      ViewVisualLintIssueProvider(projectRule.fixture.testRootDisposable)
    val resultToModelMap = mutableMapOf<RenderResult, NlModel>()
    UiCheckConfigurations.forEach { config ->
      val renderResult =
        renderPreviewElementForResult(
            facet,
            uiCheckPreviewFile,
            SingleComposePreviewElementInstance.forTesting(
              "google.simpleapplication.UiCheckPreviewKt.VisualLintErrorPreview",
              configuration = config.configuration,
              showDecorations = config.showDecorations,
            ),
            customViewInfoParser = accessibilityBasedHierarchyParser,
          )
          .get()
      val file = renderResult.lightVirtualFile
      val nlModel =
        SyncNlModel.create(
          projectRule.fixture.testRootDisposable,
          NlComponentRegistrar,
          AndroidBuildTargetReference.gradleOnly(facet),
          file,
        )
      resultToModelMap[renderResult.result!!] = nlModel
    }

    uiCheckBenchmark.measureOperation(
      measures =
        listOf(
          ElapsedTimeMeasurement(Metric("compose_linting_time")),
          HeapSnapshotMemoryUseMeasurement(
            "android:designTools",
            null,
            Metric("compose_linting_memory_use"),
          ),
          LayoutlibNativeMemoryMeasurement(Metric("compose_linting_layoutlib_native_memory_use")),
        ),
      samplesCount = NUMBER_OF_SAMPLES,
    ) {
      VisualLintService.getInstance(projectRule.project)
        .runVisualLintAnalysis(
          projectRule.fixture.testRootDisposable,
          visualLintIssueProvider,
          emptyList(),
          resultToModelMap,
        )
    }
  }
}
