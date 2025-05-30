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
package com.android.tools.idea.apk.viewer

import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.apk.analyzer.AndroidApplicationInfo
import com.android.tools.apk.analyzer.ArchiveEntry
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.sdk.AndroidSdks
import com.google.common.util.concurrent.ListenableFuture
import java.nio.file.Path

/**
 * Extracts an [AndroidApplicationInfo] from an archive entry using AAPT
 */
class AndroidApplicationInfoProviderImpl() : AndroidApplicationInfoProvider {
  override fun getApplicationInfo(apkParser: ApkParser, entry: ArchiveEntry): ListenableFuture<AndroidApplicationInfo> {
    val handler: AndroidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val pathToAapt: Path = AaptInvoker.getPathToAapt(handler, LogWrapper(AndroidApplicationInfoProvider::class.java))
    return apkParser.getApplicationInfo(pathToAapt, entry)
  }
}