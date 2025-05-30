/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.tools.idea.imports.MavenClassRegistryBase.LibraryImportData
import com.google.common.truth.Truth.assertThat
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.mapToSetOrEmpty
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tests for [MavenClassRegistry]. */
class MavenClassRegistryTest {
  private var repositoryIndexContents = ""

  private fun getIndexByteStream(): InputStream = repositoryIndexContents.byteInputStream(UTF_8)

  private val testScheduler = TestCoroutineScheduler()
  private val testDispatcher = StandardTestDispatcher(testScheduler)
  private val testScope = TestScope(testDispatcher)

  @Test
  fun parseJsonFile() {
    repositoryIndexContents =
      """
        {
          "Index": [
            {
              "groupId": "androidx.activity",
              "artifactId": "activity",
              "version": "1.1.0",
              "ktxTargets": [],
              "fqcns": [
                "androidx.activity.ComponentActivity",
                "androidx.activity.Fake"
              ],
              "ktlfns": [
                {
                  "fqn": "androidx.activity.result.PickVisualMediaRequestKt.PickVisualMediaRequest"
                },
                {
                  "fqn": "androidx.activity.FakeFunctionKt.FakeFunction"
                },
                {
                  "xfqn": "androidx.activity.FakeFunctionKt.FakeFunction",
                  "rcvr": "with.a.Receiver"
                }
              ]
            },
            {
              "groupId": "androidx.activity",
              "artifactId": "activity-ktx",
              "version": "1.1.0",
              "ktxTargets": [
                "androidx.activity:activity"
              ],
              "fqcns": []
            },
            {
              "groupId": "androidx.annotation",
              "artifactId": "annotation",
              "version": "1.1.0",
              "ktxTargets": [],
              "fqcns": [
                "androidx.annotation.AnimRes",
                "androidx.annotation.Fake"
              ],
              "ktlfns": [
                {
                  "fqn": "androidx.annotation.FacadeFileKt.AnnotationFunction"
                },
                {
                  "fqn": "androidx.annotation.FakeFunctionKt.FakeFunction"
                }
              ]
            }
          ]
        }
      """
        .trimIndent()

    val mavenClassRegistry = MavenClassRegistry.createFrom(::getIndexByteStream)

    assertThat(mavenClassRegistry.lookup.classNameMap)
      .containsExactlyEntriesIn(
        mapOf(
          "ComponentActivity" to
            listOf(
              LibraryImportData(
                artifact = "androidx.activity:activity",
                importedItemFqName = "androidx.activity.ComponentActivity",
                importedItemPackageName = "androidx.activity",
                version = "1.1.0",
              )
            ),
          "Fake" to
            listOf(
              LibraryImportData(
                artifact = "androidx.activity:activity",
                importedItemFqName = "androidx.activity.Fake",
                importedItemPackageName = "androidx.activity",
                version = "1.1.0",
              ),
              LibraryImportData(
                artifact = "androidx.annotation:annotation",
                importedItemFqName = "androidx.annotation.Fake",
                importedItemPackageName = "androidx.annotation",
                version = "1.1.0",
              ),
            ),
          "AnimRes" to
            listOf(
              LibraryImportData(
                artifact = "androidx.annotation:annotation",
                importedItemFqName = "androidx.annotation.AnimRes",
                importedItemPackageName = "androidx.annotation",
                version = "1.1.0",
              )
            ),
        )
      )

    assertThat(mavenClassRegistry.lookup.topLevelFunctionsMap)
      .containsExactlyEntriesIn(
        mapOf(
          FunctionSpecifier("PickVisualMediaRequest", null) to
            listOf(
              LibraryImportData(
                artifact = "androidx.activity:activity",
                importedItemFqName = "androidx.activity.result.PickVisualMediaRequest",
                importedItemPackageName = "androidx.activity.result",
                version = "1.1.0",
              )
            ),
          FunctionSpecifier("FakeFunction", null) to
            listOf(
              LibraryImportData(
                artifact = "androidx.activity:activity",
                importedItemFqName = "androidx.activity.FakeFunction",
                importedItemPackageName = "androidx.activity",
                version = "1.1.0",
              ),
              LibraryImportData(
                artifact = "androidx.annotation:annotation",
                importedItemFqName = "androidx.annotation.FakeFunction",
                importedItemPackageName = "androidx.annotation",
                version = "1.1.0",
              ),
            ),
          FunctionSpecifier("FakeFunction", FqName("with.a.Receiver")) to
            listOf(
              LibraryImportData(
                artifact = "androidx.activity:activity",
                importedItemFqName = "androidx.activity.FakeFunction",
                importedItemPackageName = "androidx.activity",
                version = "1.1.0",
              )
            ),
          FunctionSpecifier("AnnotationFunction", null) to
            listOf(
              LibraryImportData(
                artifact = "androidx.annotation:annotation",
                importedItemFqName = "androidx.annotation.AnnotationFunction",
                importedItemPackageName = "androidx.annotation",
                version = "1.1.0",
              )
            ),
        )
      )

    assertThat(mavenClassRegistry.lookup.ktxMap)
      .containsExactlyEntriesIn(
        mapOf("androidx.activity:activity" to "androidx.activity:activity-ktx")
      )
  }

  @Test
  fun parseMalformedJsonFile_noIndexKeyDeclared() {
    repositoryIndexContents =
      """
        {
          "Indices": [
          ]
        }
      """
        .trimIndent()

    val mavenClassRegistry = MavenClassRegistry.createFrom(::getIndexByteStream)

    assertThat(mavenClassRegistry.lookup.classNameMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noGroupIdDeclared() {
    repositoryIndexContents =
      """
        {
          "Index": [
            {
              "artifactId": "activity",
              "version": "1.1.0",
              "ktxTargets": [],
              "fqcns": [
                "androidx.activity.ComponentActivity",
                "androidx.activity.Fake"
              ]
            },
          ]
        }
      """
        .trimIndent()

    val mavenClassRegistry = MavenClassRegistry.createFrom(::getIndexByteStream)

    assertThat(mavenClassRegistry.lookup.classNameMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noArtifactIdDeclared() {
    repositoryIndexContents =
      """
        {
          "Index": [
            {
              "groupId": "androidx.activity",
              "version": "1.1.0",
              "ktxTargets": [],
              "fqcns": [
                "androidx.activity.ComponentActivity",
                "androidx.activity.Fake"
              ]
            },
          ]
        }
      """
        .trimIndent()

    val mavenClassRegistry = MavenClassRegistry.createFrom(::getIndexByteStream)

    assertThat(mavenClassRegistry.lookup.classNameMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noVersionDeclared() {
    repositoryIndexContents =
      """
        {
          "Index": [
            {
              "groupId": "androidx.activity",
              "artifactId": "activity-ktx",
              "ktxTargets": [
                "androidx.activity:activity"
              ],
              "fqcns": [
                "androidx.activity.ComponentActivity",
                "androidx.activity.Fake"
              ]
            },
          ]
        }
      """
        .trimIndent()

    val mavenClassRegistry = MavenClassRegistry.createFrom(::getIndexByteStream)

    assertThat(mavenClassRegistry.lookup.classNameMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noFqcnsDeclared() {
    repositoryIndexContents =
      """
        {
          "Index": [
            {
              "groupId": "androidx.activity",
              "artifactId": "activity-ktx",
              "version": "1.1.0",
              "ktxTargets": [
                "androidx.activity:activity"
              ]
            },
          ]
        }
      """
        .trimIndent()

    val mavenClassRegistry = MavenClassRegistry.createFrom(::getIndexByteStream)

    assertThat(mavenClassRegistry.lookup.classNameMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseMalformedJsonFile_noKtxTargetsDeclared() {
    repositoryIndexContents =
      """
        {
          "Index": [
            {
              "groupId": "androidx.activity",
              "artifactId": "activity",
              "version": "1.1.0",
              "fqcns": [
                "androidx.activity.ComponentActivity",
                "androidx.activity.Fake"
              ]
            },
          ]
        }
      """
        .trimIndent()

    val mavenClassRegistry = MavenClassRegistry.createFrom(::getIndexByteStream)

    assertThat(mavenClassRegistry.lookup.classNameMap).isEmpty()
    assertThat(mavenClassRegistry.lookup.ktxMap).isEmpty()
  }

  @Test
  fun parseJsonFile_topLevelFunctionsPropertyIsOptional() {
    repositoryIndexContents =
      """
        {
          "Index": [
            {
              "groupId": "group1",
              "artifactId": "artifact1",
              "version": "1",
              "ktxTargets": [],
              "fqcns": [
                "class1"
              ]
            },
            {
              "groupId": "group2",
              "artifactId": "artifact2",
              "version": "1",
              "ktxTargets": [],
              "fqcns": [
                "class2"
              ],
              "ktlfns": []
            },
            {
              "groupId": "group3",
              "artifactId": "artifact3",
              "version": "1",
              "ktxTargets": [],
              "fqcns": [
                "class3"
              ],
              "ktlfns": [
                {
                  "fqn": "FacadeFileKt.someFqn",
                  "unrecognized": "should be ignored"
                },
                {
                  "has_no_fqn": "should be ignored"
                },
                {
                  "xfqn": "foo.bar.baz.FacadeFileKt.someExtensionFunction",
                  "rcvr": "amazingReceiver"
                }
              ]
            }
          ]
        }
      """
        .trimIndent()

    val mavenClassRegistry = MavenClassRegistry.createFrom(::getIndexByteStream)

    assertThat(mavenClassRegistry.lookup.topLevelFunctionsMap)
      .containsExactlyEntriesIn(
        mapOf(
          FunctionSpecifier("someFqn", null) to
            listOf(
              LibraryImportData(
                artifact = "group3:artifact3",
                importedItemFqName = "someFqn",
                importedItemPackageName = "",
                version = "1",
              )
            ),
          FunctionSpecifier("someExtensionFunction", FqName("amazingReceiver")) to
            listOf(
              LibraryImportData(
                artifact = "group3:artifact3",
                importedItemFqName = "foo.bar.baz.someExtensionFunction",
                importedItemPackageName = "foo.bar.baz",
                version = "1",
              )
            ),
        )
      )
  }

  @Test
  fun parseJsonFile_skipUnknownKey() {
    repositoryIndexContents =
      """
        {
          "UnKnown1": [],
          "UnKnown2": [
            {
              "a": "",
              "b": ""
            }
          ],
          "Index": [
            {
              "groupId": "androidx.activity",
              "artifactId": "activity",
              "version": "1.1.0",
              "ktxTargets": [],
              "unKnown3": "unknown content",
              "fqcns": [
                "androidx.activity.ComponentActivity",
                "androidx.activity.Fake"
              ]
            },
            {
              "groupId": "androidx.activity",
              "artifactId": "activity-ktx",
              "version": "1.1.0",
              "ktxTargets": [
                "androidx.activity:activity"
              ],
              "unKnown4": [
                "a",
                "b"
              ],
              "fqcns": []
            },
            {
              "groupId": "androidx.annotation",
              "artifactId": "annotation",
              "version": "1.1.0",
              "ktxTargets": [],
              "unKnown5": [],
              "fqcns": [
                "androidx.annotation.AnimRes",
                "androidx.annotation.Fake"
              ]
            }
          ],
          "UnKnown6": "unknown content"
        }
      """
        .trimIndent()

    val mavenClassRegistry = MavenClassRegistry.createFrom(::getIndexByteStream)

    assertThat(mavenClassRegistry.lookup.classNameMap)
      .containsExactlyEntriesIn(
        mapOf(
          "ComponentActivity" to
            listOf(
              LibraryImportData(
                artifact = "androidx.activity:activity",
                importedItemFqName = "androidx.activity.ComponentActivity",
                importedItemPackageName = "androidx.activity",
                version = "1.1.0",
              )
            ),
          "Fake" to
            listOf(
              LibraryImportData(
                artifact = "androidx.activity:activity",
                importedItemFqName = "androidx.activity.Fake",
                importedItemPackageName = "androidx.activity",
                version = "1.1.0",
              ),
              LibraryImportData(
                artifact = "androidx.annotation:annotation",
                importedItemFqName = "androidx.annotation.Fake",
                importedItemPackageName = "androidx.annotation",
                version = "1.1.0",
              ),
            ),
          "AnimRes" to
            listOf(
              LibraryImportData(
                artifact = "androidx.annotation:annotation",
                importedItemFqName = "androidx.annotation.AnimRes",
                importedItemPackageName = "androidx.annotation",
                version = "1.1.0",
              )
            ),
        )
      )
    assertThat(mavenClassRegistry.lookup.ktxMap)
      .containsExactlyEntriesIn(
        mapOf("androidx.activity:activity" to "androidx.activity:activity-ktx")
      )
  }

  @Test
  fun readOfflineIndexFile() {
    val tempDir = createInMemoryFileSystemAndFolder("tempCacheDir")
    val repository =
      GMavenIndexRepository("https://example.com", tempDir, testScope, testDispatcher)

    val mavenClassRegistry = MavenClassRegistry.createFrom(repository::loadIndexFromDisk)
    val data = repository.loadIndexFromDisk().bufferedReader(UTF_8).use { it.readText() }

    // Check if we have a valid built-in index file.
    Json.parseToJsonElement(data)

    // Check if this offline index file has some of the expected data.
    mavenClassRegistry.lookup.classNameMap.let {
      assertThat(it.size).isAtLeast(6000)
      assertThat(it)
        .containsEntry(
          "HintConstants",
          listOf(
            LibraryImportData(
              artifact = "androidx.autofill:autofill",
              importedItemFqName = "androidx.autofill.HintConstants",
              importedItemPackageName = "androidx.autofill",
              version = "1.1.0",
            )
          ),
        )
      assertThat(it)
        .containsEntry(
          "ImageViewStyle",
          listOf(
            LibraryImportData(
              artifact = "androidx.autofill:autofill",
              importedItemFqName = "androidx.autofill.inline.common.ImageViewStyle",
              importedItemPackageName = "androidx.autofill.inline.common",
              version = "1.1.0",
            )
          ),
        )
    }

    mavenClassRegistry.lookup.ktxMap.let {
      assertThat(it.size).isAtLeast(40)
      assertThat(it)
        .containsEntry(
          "androidx.navigation:navigation-fragment",
          "androidx.navigation:navigation-fragment-ktx",
        )
      assertThat(it).containsEntry("androidx.activity:activity", "androidx.activity:activity-ktx")
    }
  }

  @Test
  fun googleMavenArtifactIdConsistencyWithOfflineFile() {
    val tempDir = createInMemoryFileSystemAndFolder("tempCacheDir")
    val repository =
      GMavenIndexRepository("https://example.com", tempDir, testScope, testDispatcher)

    val mavenClassRegistry = MavenClassRegistry.createFrom(repository::loadIndexFromDisk)
    val data = repository.loadIndexFromDisk().bufferedReader(UTF_8).use { it.readText() }

    // Check if we have a valid built-in index file.
    Json.parseToJsonElement(data)

    val ids =
      mavenClassRegistry.lookup.coordinateList.mapToSetOrEmpty { "${it.groupId}:${it.artifactId}" }
    assertThat(ids).containsAllIn(GoogleMavenArtifactId.entries.mapToSetOrEmpty { it.toString() })
  }

  @Test
  fun kotlinTopLevelFunction_fromJvmQualifiedName() {
    with(
      KotlinTopLevelFunction.fromJvmQualifiedName(
        "com.example.FileFacadeKt.foo",
        "com.example.Receiver",
      )
    ) {
      assertThat(simpleName).isEqualTo("foo")
      assertThat(packageName).isEqualTo("com.example")
      assertThat(kotlinFqName.asString()).isEqualTo("com.example.foo")
      assertThat(receiverFqName?.asString()).isEqualTo("com.example.Receiver")
    }
  }

  @Test
  fun kotlinTopLevelFunction_fromJvmQualifiedName_withNullReceiver() {
    with(KotlinTopLevelFunction.fromJvmQualifiedName("com.example.FileFacadeKt.foo")) {
      assertThat(simpleName).isEqualTo("foo")
      assertThat(packageName).isEqualTo("com.example")
      assertThat(kotlinFqName.asString()).isEqualTo("com.example.foo")
      assertThat(receiverFqName).isNull()
    }
  }

  @Test
  fun kotlinTopLevelFunction_fromJvmQualifiedName_noPackageName() {
    with(KotlinTopLevelFunction.fromJvmQualifiedName("FileFacadeKt.foo", "com.example.Receiver")) {
      assertThat(simpleName).isEqualTo("foo")
      assertThat(packageName).isEqualTo("")
      assertThat(kotlinFqName.asString()).isEqualTo("foo")
      assertThat(receiverFqName?.asString()).isEqualTo("com.example.Receiver")
    }
  }

  @Test
  fun kotlinTopLevelFunction_fromJvmQualifiedName_noFacadeFile() {
    assertThrows(IllegalArgumentException::class.java) {
      KotlinTopLevelFunction.fromJvmQualifiedName("foo", "com.example.Receiver")
    }
  }
}
