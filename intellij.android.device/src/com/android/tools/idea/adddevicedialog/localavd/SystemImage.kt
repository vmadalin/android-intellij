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
package com.android.tools.idea.adddevicedialog.localavd

import androidx.compose.runtime.Immutable
import com.android.repository.api.RepoPackage
import com.android.sdklib.SystemImageTags
import com.android.sdklib.repository.meta.DetailsTypes.ApiDetailsType
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.google.common.annotations.VisibleForTesting

@Immutable
internal class SystemImage @VisibleForTesting internal constructor(repoPackage: RepoPackage) {
  internal val androidVersion = (repoPackage.typeDetails as ApiDetailsType).androidVersion
  internal val services = getServices(repoPackage)

  internal companion object {
    internal fun getSystemImages(): Collection<SystemImage> {
      val indicator = StudioLoggerProgressIndicator(SystemImage::class.java)
      val manager = AndroidSdks.getInstance().tryToChooseSdkHandler().getSdkManager(indicator)

      manager.loadSynchronously(
        0,
        indicator,
        StudioDownloader(),
        StudioSettingsController.getInstance(),
      )

      return manager.packages.remotePackages.values
        .filter(RepoPackage::hasSystemImage)
        .map(::SystemImage)
        .toList()
    }
  }

  private fun getServices(repoPackage: RepoPackage): Services {
    val tags = SystemImageTags.getTags(repoPackage)

    if (SystemImageTags.hasGooglePlay(tags, androidVersion, repoPackage)) {
      return Services.GOOGLE_PLAY_STORE
    }

    if (SystemImageTags.hasGoogleApi(tags)) {
      return Services.GOOGLE_APIS
    }

    return Services.ANDROID_OPEN_SOURCE
  }
}
