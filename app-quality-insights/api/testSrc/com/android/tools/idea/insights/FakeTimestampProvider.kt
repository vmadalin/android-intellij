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

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

object FakeTimeProvider {
  val now: Instant = Instant.now(Clock.fixed(Instant.parse("2022-06-08T10:00:00Z"), ZoneOffset.UTC))
}

val FAKE_10_HOURS_AGO: Instant = FakeTimeProvider.now.minus(Duration.ofHours(10))
val FAKE_6_DAYS_AGO: Instant = FakeTimeProvider.now.minus(Duration.ofDays(6))
val FAKE_25_DAYS_AGO: Instant = FakeTimeProvider.now.minus(Duration.ofDays(25))
val FAKE_50_DAYS_AGO: Instant = FakeTimeProvider.now.minus(Duration.ofDays(50))
val FAKE_80_DAYS_AGO: Instant = FakeTimeProvider.now.minus(Duration.ofDays(80))
