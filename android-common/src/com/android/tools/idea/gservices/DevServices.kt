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
package com.android.tools.idea.gservices

import com.android.tools.idea.gservices.DevServicesDeprecationStatus.SUPPORTED
import com.android.tools.idea.gservices.DevServicesDeprecationStatus.UNSUPPORTED
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.DevServicesDeprecationMetadata
import com.intellij.openapi.components.service

/**
 * Status of services that rely on backend APIs for the current build of the IDE. Our SLA is to
 * support features that rely on backend APIs for a period of about a year past the release of a
 * specific IDE version. Beyond that timeline, users must upgrade to a newer IDE version to continue
 * using those features.
 *
 * See go/android-studio-developer-services-compat-policy and go/as-kill-feature-past-deadline.
 */
enum class DevServicesDeprecationStatus {
  /** Developer services are within the support window. */
  SUPPORTED,

  /** Developer Services are no longer supported. */
  UNSUPPORTED,
}

interface DevServicesDeprecationDataProvider {
  /**
   * Returns the current deprecation policy data for a service of the given name.
   *
   * @param serviceName Name of the service
   */
  fun getCurrentDeprecationData(serviceName: String): DevServicesDeprecationData

  companion object {
    fun getInstance() = service<DevServicesDeprecationDataProvider>()
  }
}

data class DevServicesDeprecationData(
  // Header of the deprecation message
  val header: String,
  // Description of the deprecation message
  val description: String,
  // Link to provide more info
  val moreInfoUrl: String,
  // Show the update action button
  val showUpdateAction: Boolean,
  // Deprecation status of the service
  val status: DevServicesDeprecationStatus,
) {
  fun isDeprecated() = status == UNSUPPORTED
}

/** Provides [DevServicesDeprecationStatus] based on server flags. */
class ServerFlagBasedDevServicesDeprecationDataProvider : DevServicesDeprecationDataProvider {
  /**
   * Get the deprecation status of [serviceName] controlled by ServerFlags. Update the flags in
   * google3 to control the deprecation status.
   *
   * @param serviceName The service name as used in the server flags storage in the backend. This
   *   should be the same as server_flags/server_configurations/dev_services/$serviceName.textproto.
   */
  override fun getCurrentDeprecationData(serviceName: String): DevServicesDeprecationData {
    // Proto missing would imply the service is still supported.
    val proto =
      ServerFlagService.instance.getProtoOrNull(
        "dev_services/$serviceName",
        DevServicesDeprecationMetadata.getDefaultInstance(),
      ) ?: DevServicesDeprecationMetadata.getDefaultInstance()

    return proto.toDeprecationData()
  }

  private fun DevServicesDeprecationMetadata.toDeprecationData() =
    DevServicesDeprecationData(
      header,
      description,
      moreInfoUrl,
      showUpdateAction,
      if (isDeprecated()) UNSUPPORTED else SUPPORTED,
    )

  private fun DevServicesDeprecationMetadata.isDeprecated() =
    hasHeader() || hasDescription() || hasMoreInfoUrl() || hasShowUpdateAction()
}
