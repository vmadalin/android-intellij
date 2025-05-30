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
package com.android.tools.idea.avd

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.android.tools.adtui.compose.LocalProject
import com.android.tools.idea.avdmanager.AccelerationErrorCode
import com.android.tools.idea.avdmanager.AccelerationErrorSolution
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AccelerationErrorBanner(
  accelerationError: AccelerationErrorCode,
  refresh: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ErrorBanner(
    accelerationError.problem,
    modifier,
    rightContent = {
      if (accelerationError.solution == AccelerationErrorSolution.SolutionCode.NONE) {
        AccelerationErrorLink(accelerationError, refresh)
      } else {
        Tooltip(tooltip = { Text(accelerationError.solutionMessage) }) {
          AccelerationErrorLink(accelerationError, refresh)
        }
      }
    },
  )
}

@Composable
private fun AccelerationErrorLink(accelerationError: AccelerationErrorCode, refresh: () -> Unit) {
  val project = LocalProject.current
  Link(
    accelerationError.solution.description,
    onClick = {
      AccelerationErrorSolution.getActionForFix(accelerationError, project, refresh, null).run()
    },
    overflow = TextOverflow.Ellipsis,
  )
}
