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
package com.android.tools.idea.compose.preview

import com.android.annotations.concurrency.Slow
import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_NAME
import com.android.tools.compose.COMPOSE_PREVIEW_PARAMETER_ANNOTATION_FQN
import com.android.tools.compose.MULTIPLATFORM_PREVIEW_ANNOTATION_FQN
import com.android.tools.idea.compose.preview.analytics.MultiPreviewNode
import com.android.tools.idea.compose.preview.analytics.MultiPreviewNodeImpl
import com.android.tools.idea.compose.preview.analytics.MultiPreviewNodeInfo
import com.android.tools.idea.preview.find.AnnotationPreviewNameHelper
import com.android.tools.idea.preview.find.NodeInfo
import com.android.tools.idea.preview.find.UAnnotationSubtreeInfo
import com.android.tools.idea.preview.find.UastAnnotatedMethod
import com.android.tools.idea.preview.find.UastAnnotationAttributesProvider
import com.android.tools.idea.preview.find.directPreviewChildrenCount
import com.android.tools.idea.preview.find.findAllAnnotationsInGraph
import com.android.tools.idea.preview.find.findPreviewDefaultValues
import com.android.tools.idea.preview.find.getContainingUMethodAnnotatedWith
import com.android.tools.idea.preview.find.getUAnnotations
import com.android.tools.idea.preview.find.isAnnotatedWith
import com.android.tools.idea.preview.find.qualifiedName
import com.android.tools.idea.preview.find.toSmartPsiPointer
import com.android.tools.preview.ComposePreviewElement
import com.android.tools.preview.PreviewNode
import com.android.tools.preview.previewAnnotationToPreviewElement
import com.google.wireless.android.sdk.stats.ComposeMultiPreviewEvent
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Computable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

/**
 * Returns true if the [UAnnotation] is a `@Preview` annotation.
 *
 * This method must be called under a read lock.
 */
@RequiresReadLock
fun UAnnotation.isPreviewAnnotation(includingMultiplatform: Boolean = true) =
  (COMPOSE_PREVIEW_ANNOTATION_NAME == qualifiedName?.substringAfterLast(".") &&
    COMPOSE_PREVIEW_ANNOTATION_FQN == qualifiedName) ||
    (includingMultiplatform && MULTIPLATFORM_PREVIEW_ANNOTATION_FQN == qualifiedName)

/**
 * Returns true if the [UElement] is a `@Preview` annotation.
 *
 * This method must be called under a read lock.
 */
@RequiresReadLock
private fun UElement?.isPreviewAnnotation() = (this as? UAnnotation)?.isPreviewAnnotation() == true

/**
 * Returns true if the [UMethod] is annotated with a @Preview annotation, taking in consideration
 * indirect annotations with MultiPreview.
 */
@RequiresBackgroundThread
internal fun UMethod?.hasPreviewElements(): Boolean =
  // TODO(b/381827960): avoid using runBlockingCancellable
  this?.let {
    ProgressManager.getInstance().runProcess(Computable {
      runBlockingCancellable { getPreviewElements(it).firstOrNull() }
    }, EmptyProgressIndicator())
  } != null

/**
 * Returns true if this is not a Preview annotation, but a MultiPreview annotation, i.e. an
 * annotation that is annotated with @Preview or with other MultiPreview.
 */
@RequiresReadLock
@RequiresBackgroundThread
fun UAnnotation?.isMultiPreviewAnnotation(): Boolean =
  this?.let {
    !it.isPreviewAnnotation() &&
    // TODO(b/381827960): avoid using runBlockingCancellable
    ProgressManager.getInstance().runProcess(Computable {
      runBlockingCancellable { it.getPreviewNodes(includeAllNodes = false).firstOrNull() != null }
    }, EmptyProgressIndicator())
  } == true

/**
 * Given a Composable method, return a sequence of [ComposePreviewElement] corresponding to its
 * Preview annotations
 */
private suspend fun getPreviewElements(uMethod: UMethod, overrideGroupName: String? = null) =
  getPreviewNodes(uMethod, overrideGroupName, false).mapNotNull { it as? ComposePreviewElement<*> }

/**
 * Given a Composable method, return a sequence of [PreviewNode] that are part of the method's
 * MultiPreview graph. Notes:
 * - The leaf nodes that correspond to Preview annotations will be not just a [PreviewNode], but
 *   specifically a [ComposePreviewElement].
 * - When [includeAllNodes] is true, the returned sequence will also include nodes corresponding to
 *   the MultiPreview annotations and the root composable [composableMethod]. These nodes, will be
 *   not just a [PreviewNode], but specifically a [MultiPreviewNode]
 */
@Slow
suspend fun getPreviewNodes(
  composableMethod: UMethod,
  overrideGroupName: String? = null,
  includeAllNodes: Boolean,
) =
  getPreviewNodes(
    composableMethod = composableMethod,
    overrideGroupName = overrideGroupName,
    includeAllNodes = includeAllNodes,
    rootSearchElement = composableMethod,
  )

/**
 * Given a root search [UElement], return a sequence of [PreviewNode] that are part of that
 * element's MultiPreview graph.
 *
 * @see getPreviewNodes
 */
@Slow
private suspend fun getPreviewNodes(
  composableMethod: UMethod,
  overrideGroupName: String? = null,
  includeAllNodes: Boolean,
  rootSearchElement: UElement,
): Flow<PreviewNode> {
  if (readAction { !composableMethod.isComposable() }) return emptyFlow()
  val composableFqn = readAction { composableMethod.qualifiedName }
  val multiPreviewNodesByFqn = mutableMapOf<String, MultiPreviewNode>()

  return flow {
    rootSearchElement
      .findAllAnnotationsInGraph(
        onTraversal =
          if (includeAllNodes)
            onTraversal@{ node ->
              val annotationFqn =
                readAction { (node.element as? UAnnotation)?.qualifiedName } ?: return@onTraversal
              val multiPreviewNode =
                node.toMultiPreviewNode(multiPreviewNodesByFqn, composableFqn) ?: return@onTraversal
              multiPreviewNodesByFqn[annotationFqn] = multiPreviewNode
            }
          else null
      ) {
        readAction { it.isPreviewAnnotation() }
      }
      .mapNotNull {
        it.toPreviewElement(
          composableMethod = composableMethod,
          overrideGroupName = overrideGroupName,
        )
      }
      .collect { emit(it) }

    if (includeAllNodes) {
      multiPreviewNodesByFqn.values.forEach { emit(it) }
      emit(composableMethod.toMultiPreviewNode(multiPreviewNodesByFqn))
    }
  }
}

/**
 * Convenience method for returning a sequence of [PreviewNode]s for a given [UAnnotation]. This
 * method calls [getPreviewNodes] with the composable [UMethod] attached to the [UAnnotation]. If
 * the given [UAnnotation] is not attached to a composable method then an empty sequence will be
 * returned instead.
 *
 * @see getPreviewNodes
 */
@RequiresReadLock
@Slow
private suspend fun UAnnotation.getPreviewNodes(
  overrideGroupName: String? = null,
  includeAllNodes: Boolean,
): Flow<PreviewNode> {
  val composableMethod = getContainingComposableUMethod() ?: return emptyFlow()
  return getPreviewNodes(
    composableMethod = composableMethod,
    overrideGroupName = overrideGroupName,
    includeAllNodes = includeAllNodes,
    rootSearchElement = this,
  )
}

/**
 * Returns the Composable [UMethod] annotated by this annotation, or null if it is not annotating a
 * method, or if the method is not also annotated with @Composable
 */
@RequiresReadLock
internal fun UAnnotation.getContainingComposableUMethod() =
  this.getContainingUMethodAnnotatedWith(COMPOSABLE_ANNOTATION_FQ_NAME)

/** Returns true when the UMethod is not null, and it is annotated with @Composable */
private fun UMethod?.isComposable() = this.isAnnotatedWith(COMPOSABLE_ANNOTATION_FQ_NAME)

/** Converts a given [NodeInfo] of type [UAnnotationSubtreeInfo] to a [ComposePreviewElement]. */
private suspend fun NodeInfo<UAnnotationSubtreeInfo>.toPreviewElement(
  composableMethod: UMethod,
  overrideGroupName: String?,
): ComposePreviewElement<*>? {
  val annotation = element as UAnnotation
  if (readAction { !annotation.isPreviewAnnotation() }) return null

  val rootAnnotation = subtreeInfo?.topLevelAnnotation ?: annotation
  val defaultValues = readAction { annotation.findPreviewDefaultValues() }
  val attributesProvider = UastAnnotationAttributesProvider(annotation, defaultValues)
  val previewElementDefinitionPsi = readAction { rootAnnotation.toSmartPsiPointer() }
  val annotatedMethod =
    UastAnnotatedMethod(composableMethod, COMPOSE_PREVIEW_PARAMETER_ANNOTATION_FQN)
  val nameHelper =
    AnnotationPreviewNameHelper.create(this, annotatedMethod.name) {
      readAction { isPreviewAnnotation() }
    }
  // TODO(b/339615825): avoid running the whole previewAnnotationToPreviewElement method under the
  // read lock
  return smartReadAction(project = composableMethod.project) {
    previewAnnotationToPreviewElement(
      attributesProvider,
      annotatedMethod,
      previewElementDefinitionPsi,
      ::StudioParametrizedComposePreviewElementTemplate,
      overrideGroupName,
      buildPreviewName = nameHelper::buildPreviewName,
      buildParameterName = nameHelper::buildParameterName,
    )
  }
}

private val areAssertionsEnabled = MultiPreviewNodeImpl::class.java.desiredAssertionStatus()

/**
 * Converts a composable [UMethod] to a [MultiPreviewNodeImpl].
 *
 * @param multiPreviewNodesByFqn a hashmap storing [MultiPreviewNode]s for this [UMethod]'s
 *   previews. The keys are the FQNs of their elements.
 */
private suspend fun UMethod.toMultiPreviewNode(
  multiPreviewNodesByFqn: MutableMap<String, MultiPreviewNode>
): MultiPreviewNodeImpl {
  if (areAssertionsEnabled) {
    // This assertion is expensive as it runs a read action. We don't want to compute the value if
    // the assertions are not enabled.
    assert(isComposable())
  }
  val nonPreviewChildNodes = getUAnnotations().nonPreviewNodes(multiPreviewNodesByFqn)

  return MultiPreviewNodeImpl(
    MultiPreviewNodeInfo(
        ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType.ROOT_COMPOSABLE_FUNCTION_NODE
      )
      .withChildNodes(
        nonPreviewChildNodes,
        directPreviewChildrenCount { readAction { isPreviewAnnotation() } },
      )
      .withDepthLevel(0)
      .withComposableFqn(readAction { qualifiedName })
  )
}

/**
 * Converts a [NodeInfo] to a [MultiPreviewNodeImpl] if [NodeInfo.element] is a MultiPreview
 * annotation. A MultiPreview annotation is an annotation that potentially has descendant preview
 * annotations and is **not** a preview annotation itself. This method returns null if
 * [NodeInfo.element] is a preview annotation. See [isPreviewAnnotation] for more information.
 *
 * @param multiPreviewNodesByFqn a hashmap storing [MultiPreviewNode]s for this [NodeInfo]'s
 *   children and siblings. The keys are the FQNs of their elements.
 * @param composableFqn the FQN of the top composable method these previews are attached to
 */
private suspend fun NodeInfo<UAnnotationSubtreeInfo>.toMultiPreviewNode(
  multiPreviewNodesByFqn: MutableMap<String, MultiPreviewNode>,
  composableFqn: String,
): MultiPreviewNodeImpl? {
  if (readAction { element.isPreviewAnnotation() }) return null
  val nonPreviewChildNodes =
    subtreeInfo
      ?.children
      ?.mapNotNull { it.element as? UAnnotation }
      ?.nonPreviewNodes(multiPreviewNodesByFqn) ?: emptyList()

  return MultiPreviewNodeImpl(
    MultiPreviewNodeInfo(
        ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType.MULTIPREVIEW_NODE
      )
      .withChildNodes(
        nonPreviewChildNodes,
        element.directPreviewChildrenCount { readAction { isPreviewAnnotation() } },
      )
      .withDepthLevel(subtreeInfo?.depth ?: -1)
      .withComposableFqn(composableFqn)
  )
}

/**
 * Convenience method that returns all [MultiPreviewNodeInfo]s for all [UAnnotation]s in the given
 * list that are not preview annotations. The [MultiPreviewNodeInfo]s are retrieved using the
 * [multiPreviewNodesByFqn] parameter, using the [UAnnotation]'s FQNs as keys.
 */
private suspend fun Collection<UAnnotation>.nonPreviewNodes(
  multiPreviewNodesByFqn: MutableMap<String, MultiPreviewNode>
) =
  filter { it.isPreviewAnnotation() == false }
    .mapNotNull { readAction { it.qualifiedName } }
    .map { multiPreviewNodesByFqn[it]?.nodeInfo }
