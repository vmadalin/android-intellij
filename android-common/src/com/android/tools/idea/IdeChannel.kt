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
package com.android.tools.idea

import com.android.tools.idea.IdeChannel.Channel.DEV
import com.android.tools.idea.IdeChannel.Channel.STABLE
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import java.util.regex.Pattern

object IdeChannel {
  /**
   * The IDE channel value. The channels are order based on stability, [DEV] being
   * the least stable and [STABLE] the most.
   */
  enum class Channel {
    // Channels are ORDERED by stability, do not change the order.
    DEV, // Less stable
    NIGHTLY,
    CANARY,
    BETA,
    RC,
    STABLE; // More stable

    /**
     * Returns true if this channel is more stable than or equal to [channel].
     */
    fun isAtLeast(channel: Channel): Boolean = compareTo(channel) >= 0

    /**
     * Returns true if this channel is less stable than or equal to [channel].
     */
    fun isAtMost(channel: Channel): Boolean = compareTo(channel) <= 0
  }

  private fun versionNameContainsChannel(versionName: String?, channel: String): Boolean {
    return Pattern.compile("\\b$channel\\b", Pattern.CASE_INSENSITIVE).matcher(versionName ?: return false).find()
  }

  private var _channel: Channel? = null

  val channel: Channel
    get() = _channel ?: computeChannel().also { _channel = it }

  @VisibleForTesting
  fun setChannel(versionName: String?) {
    _channel = versionName?.let { getChannelFromVersionName(it) }
  }

  /**
   * Returns the [Channel] based on the given [ApplicationInfo].
   */
  private fun computeChannel(): Channel {
    val versionName = when {
      ApplicationManager.getApplication() == null || ApplicationManager.getApplication().getServiceIfCreated(ApplicationInfo::class.java) == null -> "dev"
      else -> ApplicationInfo.getInstance().fullVersion
    }
    return getChannelFromVersionName(versionName)
  }

  private fun getChannelFromVersionName(versionName: String) : Channel {
    return when {
      versionNameContainsChannel(versionName, "dev") -> Channel.DEV
      versionNameContainsChannel(versionName, "nightly") -> Channel.NIGHTLY
      versionNameContainsChannel(versionName, "canary") -> Channel.CANARY
      versionNameContainsChannel(versionName, "beta") -> Channel.BETA
      versionNameContainsChannel(versionName, "rc") -> Channel.RC
      else -> Channel.STABLE
    }
  }
}