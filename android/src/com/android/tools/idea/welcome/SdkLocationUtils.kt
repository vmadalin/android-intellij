/*
 * Copyright (C) 2016 The Android Open Source Project
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

@file:JvmName("SdkLocationUtils")

package com.android.tools.idea.welcome

import com.android.io.CancellableFileIo
import java.nio.file.Path

/**
 * Returns true if the SDK wizards will write into the SDK location. If the location exists and is a
 * directory, returns true if the directory is writable. If the location doesn't exist, returns true
 * if one of the parent directories is writable.
 */
fun isWritable(sdkLocation: Path?): Boolean =
  when {
    sdkLocation == null -> false
    CancellableFileIo.exists(sdkLocation) ->
      CancellableFileIo.isDirectory(sdkLocation) && CancellableFileIo.isWritable(sdkLocation)
    else -> {
      val parent = getFirstExistentParent(sdkLocation)
      parent != null && CancellableFileIo.isWritable(parent)
    }
  }

private fun getFirstExistentParent(file: Path): Path? =
  generateSequence(file.parent) { it.parent }.firstOrNull { CancellableFileIo.exists(it) }
