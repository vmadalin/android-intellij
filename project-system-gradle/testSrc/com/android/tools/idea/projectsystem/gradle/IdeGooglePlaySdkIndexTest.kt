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
package com.android.tools.idea.projectsystem.gradle

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker.setWriterForTest
import com.android.tools.lint.checks.GooglePlaySdkIndex
import com.android.tools.lint.detector.api.LintFix
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LIBRARY_IS_OUTDATED
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LIBRARY_UPDATED
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LIBRARY_IS_DEPRECATED
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.SDK_INDEX_LINK_FOLLOWED
import org.jetbrains.android.AndroidTestCase

internal class IdeGooglePlaySdkIndexTest: AndroidTestCase() {
  fun testGenerateSdkLinkHasOnUrlOpenCallBack() {
    val ideIndex = IdeGooglePlaySdkIndex
    ideIndex.initialize()
    val quickFix = ideIndex.generateSdkLinkLintFix("com.google.firebase", "firebase-auth", "9.0.0", null)
    assertThat(quickFix).isInstanceOf(LintFix.ShowUrl::class.java)
    assertWithMessage("onUrlOpen should be defined and ideally be used to report a $SDK_INDEX_LINK_FOLLOWED event")
      .that((quickFix as LintFix.ShowUrl).onUrlOpen).isNotNull()
  }

  fun testIssueIsBlockingReported() {
    val scheduler = VirtualTimeScheduler()
    val testUsageTracker = TestUsageTracker(scheduler)
    setWriterForTest(testUsageTracker)
    val ideIndex = IdeGooglePlaySdkIndex
    ideIndex.initialize()
    ideIndex.isLibraryOutdated("com.google.firebase", "firebase-auth", "9.0.0", null)
    val loggedEvents = testUsageTracker.usages
    val libraryEvents = loggedEvents.filter { it.studioEvent.kind == SDK_INDEX_LIBRARY_IS_OUTDATED }
    assertThat(libraryEvents).hasSize(1)
    assertThat(libraryEvents[0].studioEvent.hasSdkIndexLibraryDetails()).isTrue()
    val libraryDetails = libraryEvents[0].studioEvent.sdkIndexLibraryDetails
    assertThat(libraryDetails.hasIsBlocking()).isTrue()
  }

  fun testIdeFlagsMatchDefaultFlags() {
    val ideIndex = IdeGooglePlaySdkIndex
    ideIndex.initializeAndSetFlags()
    assertWithMessage("The value of GooglePlayIndex.DEFAULT_SHOW_NOTES_FROM_DEVELOPER and StudioFlags.SHOW_SDK_INDEX_NOTES_FROM_DEVELOPER should match.\n" +
                      "The difference may occur if there is a channel conditional default or one was changed but not the other").that(ideIndex.showNotesFromDeveloper).isEqualTo(GooglePlaySdkIndex.DEFAULT_SHOW_NOTES_FROM_DEVELOPER)
    assertWithMessage("The value of GooglePlayIndex.DEFAULT_SHOW_RECOMMENDED_VERSIONS and StudioFlags.SHOW_SDK_INDEX_RECOMMENDED_VERSIONS should match.\n" +
                      "The difference may occur if there is a channel conditional default or one was changed but not the other").that(ideIndex.showRecommendedVersions).isEqualTo(GooglePlaySdkIndex.DEFAULT_SHOW_RECOMMENDED_VERSIONS)
    assertWithMessage("The value of GooglePlayIndex.DEFAULT_SHOW_DEPRECATION_ISSUES and StudioFlags.SHOW_SDK_INDEX_DEPRECATION_ISSUES should match.\n" +
                      "The difference may occur if there is a channel conditional default or one was changed but not the other (see b/381276797)").that(ideIndex.showDeprecationIssues).isEqualTo(GooglePlaySdkIndex.DEFAULT_SHOW_DEPRECATION_ISSUES)
  }

  fun testLogUpdateLibraryGeneratesEvent() {
    val groupId = "com.google.firebase"
    val artifactId = "firebase-auth"
    val oldVersion = "9.0.0"
    val newVersion = "10.0.0"
    val scheduler = VirtualTimeScheduler()
    val testUsageTracker = TestUsageTracker(scheduler)
    setWriterForTest(testUsageTracker)
    val ideIndex = IdeGooglePlaySdkIndex
    ideIndex.initialize()
    ideIndex.logUpdateLibraryVersionFixApplied(groupId, artifactId, oldVersion, newVersion, file = null)
    val loggedEvents = testUsageTracker.usages
    val libraryEvents = loggedEvents.filter { it.studioEvent.kind == SDK_INDEX_LIBRARY_UPDATED }
    assertThat(libraryEvents).hasSize(1)
    assertThat(libraryEvents[0].studioEvent.hasSdkIndexLibraryDetails()).isTrue()
    val libraryDetails = libraryEvents[0].studioEvent.sdkIndexLibraryDetails
    assertThat(libraryDetails.groupId).isEqualTo(groupId)
    assertThat(libraryDetails.artifactId).isEqualTo(artifactId)
    assertThat(libraryDetails.isBlocking).isTrue()
    assertThat(libraryDetails.versionString).isEqualTo(oldVersion)
    assertThat(libraryDetails.updatedVersionString).isEqualTo(newVersion)
  }

  fun testLogDeprecatedLibraryEvent() {
    val groupId = "com.google.android.play"
    val artifactId = "core"
    val version = "1.10.3"
    val scheduler = VirtualTimeScheduler()
    val testUsageTracker = TestUsageTracker(scheduler)
    setWriterForTest(testUsageTracker)
    val ideIndex = IdeGooglePlaySdkIndex
    ideIndex.showDeprecationIssues = true
    ideIndex.initialize()
    ideIndex.isLibraryDeprecated(groupId, artifactId, version, null)
    val loggedEvents = testUsageTracker.usages
    val libraryEvents = loggedEvents.filter { it.studioEvent.kind == SDK_INDEX_LIBRARY_IS_DEPRECATED }
    assertThat(libraryEvents).hasSize(1)
    assertThat(libraryEvents[0].studioEvent.hasSdkIndexLibraryDetails()).isTrue()
    val libraryDetails = libraryEvents[0].studioEvent.sdkIndexLibraryDetails
    assertThat(libraryDetails.groupId).isEqualTo(groupId)
    assertThat(libraryDetails.artifactId).isEqualTo(artifactId)
    assertThat(libraryDetails.isBlocking).isTrue()
    assertThat(libraryDetails.versionString).isEqualTo(version)
  }
}
