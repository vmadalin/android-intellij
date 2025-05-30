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
package com.android.tools.idea.insights.client

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.InsightSource
import com.google.cloud.cloudaicompanion.v1main.ExperienceContext
import com.google.cloud.cloudaicompanion.v1main.TaskCompletionInput
import com.google.cloud.cloudaicompanion.v1main.TaskCompletionMessage
import com.google.cloud.cloudaicompanion.v1main.TaskCompletionRequest
import com.google.cloud.cloudaicompanion.v1main.TaskCompletionServiceGrpc
import com.google.protobuf.Any
import com.google.protobuf.Message
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.IncorrectOperationException
import io.grpc.ClientInterceptor
import io.grpc.ManagedChannel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.guava.await
import org.jetbrains.annotations.VisibleForTesting

private const val INSTANCE_FORMAT = "projects/%s/locations/global/instances/default"

@VisibleForTesting
internal val DEFAULT_TASK_COMPLETION_INPUT =
  TaskCompletionInput.newBuilder()
    .apply { addMessages(TaskCompletionMessage.getDefaultInstance()) }
    .build()

@VisibleForTesting
internal val CRASHLYTICS_EXPERIENCE_CONTEXT =
  ExperienceContext.newBuilder()
    .setExperience("/appeco/crashlytics/ai-insights/studio/free-tier")
    .build()

class TitanAiInsightClient
@VisibleForTesting
internal constructor(channel: ManagedChannel, interceptor: ClientInterceptor) : AiInsightClient {

  private val taskCompletionService =
    TaskCompletionServiceGrpc.newFutureStub(channel).withInterceptors(interceptor)

  override suspend fun fetchCrashInsight(
    projectId: String,
    additionalContextMsg: Message,
  ): AiInsight {
    val request =
      TaskCompletionRequest.newBuilder()
        .apply {
          input = DEFAULT_TASK_COMPLETION_INPUT
          experienceContext = CRASHLYTICS_EXPERIENCE_CONTEXT
          instance = String.format(INSTANCE_FORMAT, projectId)
          inputDataContext =
            inputDataContextBuilder
              .apply { additionalContext = Any.pack(additionalContextMsg) }
              .build()
        }
        .build()
    val response = taskCompletionService.completeTask(request).await()
    return AiInsight(
      response.output.messagesList.first().content,
      insightSource = InsightSource.CRASHLYTICS_TITAN,
    )
  }

  companion object {
    fun create(disposable: Disposable, interceptor: ClientInterceptor): TitanAiInsightClient {
      val address = StudioFlags.APP_INSIGHTS_AI_INSIGHT_ENDPOINT.get()
      val channel =
        channelBuilderForAddress(address)
          .apply {
            if (address.startsWith("localhost")) {
              usePlaintext()
            } else {
              useTransportSecurity()
            }
          }
          .build()
          .also {
            try {
              Disposer.register(disposable) {
                it.shutdown()
                it.awaitTermination(1, TimeUnit.SECONDS)
              }
            } catch (e: IncorrectOperationException) {
              it.shutdownNow()
            }
          }
      return TitanAiInsightClient(channel, interceptor)
    }
  }
}
