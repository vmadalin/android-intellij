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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.actions.SCENE_VIEW
import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.common.layout.positionable.PositionablePanel
import com.android.tools.idea.common.layout.positionable.getScaledContentSize
import com.android.tools.idea.common.layout.positionable.margin
import com.android.tools.idea.common.layout.positionable.scaledContentSize
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import com.android.tools.idea.common.surface.sceneview.SceneViewTopPanel
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.android.tools.idea.uibuilder.surface.layout.horizontal
import com.android.tools.idea.uibuilder.surface.layout.vertical
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Distance between bottom bound of SceneView and bottom of [SceneViewPeerPanel]. */
@SwingCoordinate private const val BOTTOM_BORDER_HEIGHT = 3

/** Minimum allowed width for the SceneViewPeerPanel. */
@SwingCoordinate private const val SCENE_VIEW_PEER_PANEL_MIN_WIDTH = 100

/**
 * A Swing component associated to the given [SceneView]. There will be one of this components in
 * the [DesignSurface] per every [SceneView] available. This panel will be positioned on the
 * coordinates of the [SceneView] and can be used to paint Swing elements on top of the [SceneView].
 */
class SceneViewPeerPanel(
  val scope: CoroutineScope,
  val sceneView: SceneView,
  labelPanel: JComponent,
  statusIconAction: AnAction?,
  toolbarActions: List<AnAction>,
  leftPanel: JComponent?,
  rightPanel: JComponent?,
  private val errorsPanel: JComponent?,
  private val isOrganizationEnabled: StateFlow<Boolean>,
) : JPanel(), PositionablePanel, UiDataProvider {

  init {
    scope.launch {
      sceneView.sceneManager.model.organizationGroup?.isOpened?.collect {
        withContext(uiThread) { invalidate() }
      }
    }
  }

  /**
   * Contains cached layout data that can be used by this panel to verify when it's been invalidated
   * without having to explicitly call [revalidate]
   */
  private var layoutData = LayoutData.fromSceneView(sceneView)

  private val cachedContentSize = Dimension()
  private val cachedScaledContentSize = Dimension()
  private val cachedPreferredSize = Dimension()

  override val positionableAdapter =
    object : PositionableContent {
      override val organizationGroup: OrganizationGroup?
        get() = sceneView.sceneManager.model.organizationGroup

      override val scale: Double
        get() = sceneView.scale

      override val x: Int
        get() = sceneView.x

      override val y: Int
        get() = sceneView.y

      override val isFocusedContent: Boolean
        get() = sceneView.isFocusedScene

      override fun getMargin(scale: Double): Insets {
        val margin =
          JBInsets(
            /* top */ sceneViewTopPanel.preferredSize.height,
            /* left */ sceneViewLeftPanel.preferredSize.width,
            /* bottom */ BOTTOM_BORDER_HEIGHT,
            /* right */ sceneViewRightPanel.preferredSize.width,
          )

        if (errorsPanel?.isVisible == true) {
          margin.bottom += sceneViewCenterPanel.preferredSize.height
        }

        val scaledContentWidth = getContentSize(null).width * scale

        if (scaledContentWidth < minimumSize.width) {
          // If there is no content, or the content is smaller than the minimum size,
          // pad the margins horizontally to occupy the empty space.
          // The content is aligned on the left
          margin.right += (minimumSize.width - scaledContentWidth.toInt()).coerceAtLeast(0)
        }
        return margin
      }

      override fun getContentSize(dimension: Dimension?): Dimension =
        if (sceneView.hasContentSize())
          sceneView.getContentSize(dimension).also { cachedContentSize.size = it }
        else if (!sceneView.isVisible || sceneView.hasRenderErrors()) {
          dimension?.apply { setSize(0, 0) } ?: Dimension(0, 0)
        } else {
          dimension?.apply { size = cachedContentSize } ?: Dimension(cachedContentSize)
        }

      /** Applies the calculated coordinates from this adapter to the backing SceneView. */
      private fun applyLayout() {
        getScaledContentSize(cachedScaledContentSize)
        val margin = this.margin // To avoid recalculating the size
        setBounds(
          x - margin.left,
          y - margin.top,
          cachedScaledContentSize.width + margin.left + margin.right,
          cachedScaledContentSize.height + margin.top + margin.bottom,
        )
        sceneView.scene.needsRebuildList()
      }

      /**
       * Calculates total size for [SceneViewPeerPanel] including margins and content for target
       * [scale]
       *
       * Total size for [SceneViewPeerPanel] is contentSize * scale + margin(scale)
       * * contentSize is the size of the content without any scaling. contentSize is a "raw" size
       *   of the preview and is not changing unless preview is updated. It should be scaled
       *   proportionally to get size to be used in surface.
       * * margin is mostly fixed size, they are not scaled proportionally with scale. margin covers
       *   the size of labels, toolbars or extra panels surrounding the preview.
       */
      override fun sizeForScale(scale: Double): Dimension {
        val size = getContentSize(null)
        val margin = getMargin(scale)
        // To be more precise - round to nearest Int
        size.width = round(size.width * scale + margin.horizontal).toInt()
        size.height = round(size.height * scale + margin.vertical).toInt()
        return size
      }

      override fun setLocation(x: Int, y: Int) {
        // The SceneView is painted right below the top toolbar panel.
        // This set the top-left corner of preview.
        sceneView.setLocation(x, y)

        // After positioning the view, we re-apply the bounds to the SceneViewPanel.
        // We do this even if x & y did not change since the size might have.
        applyLayout()
      }
    }

  fun PositionableContent.isEmptyContent() =
    scaledContentSize.let { it.height == 0 && it.width == 0 }

  /**
   * This panel wraps both the label and the toolbar and puts them left aligned (label) and right
   * aligned (the toolbar).
   */
  @VisibleForTesting
  val sceneViewTopPanel = SceneViewTopPanel(this, statusIconAction, toolbarActions, labelPanel)

  val sceneViewLeftPanel = wrapPanel(leftPanel)
  val sceneViewRightPanel = wrapPanel(rightPanel)
  val sceneViewCenterPanel = wrapPanel(errorsPanel)

  private fun wrapPanel(panel: JComponent?) =
    JPanel(BorderLayout()).apply {
      isOpaque = false
      isVisible = true
      if (panel != null) {
        add(panel, BorderLayout.CENTER)
      }
    }

  init {
    isOpaque = false
    layout = null

    add(sceneViewTopPanel)
    add(sceneViewCenterPanel)
    add(sceneViewLeftPanel)
    add(sceneViewRightPanel)
    // This setup the initial positions of sceneViewTopPanel, sceneViewCenterPanel,
    // sceneViewBottomPanel, and sceneViewLeftPanel.
    // Otherwise they are all placed at top-left corner before first time layout.
    doLayout()
  }

  override fun isValid(): Boolean {
    return super.isValid() && layoutData.isValidFor(sceneView)
  }

  override fun doLayout() {
    layoutData = LayoutData.fromSceneView(sceneView)

    //      SceneViewPeerPanel layout:
    //
    //      |--------------------------------------------|
    //      |             sceneViewTopPanel              |    ↕ preferredHeight
    //      |---------------------------------------------
    //      |         |                       |          |    ↑
    //      |  scene  |                       |  scene   |    |
    //      |  View   |  sceneViewCenterPanel |  View    |    | centerPanelHeight
    //      |  Left   |                       |  Right   |    |
    //      |  Panel  |                       |  Panel   |    ↓
    //      |---------------------------------------------
    //      |                                            |    ↕ BOTTOM_BORDER_HEIGHT
    //      |---------------------------------------------
    //
    //       ←-------→                         ←--------→
    //       preferredWidth                    preferredWidth

    if (sceneViewTopPanel.isVisible) {
      sceneViewTopPanel.setBounds(
        0,
        0,
        width + insets.horizontal,
        sceneViewTopPanel.preferredSize.height,
      )
    }
    val leftSectionWidth = sceneViewLeftPanel.preferredSize.width
    val centerPanelHeight =
      if (positionableAdapter.isEmptyContent()) {
        sceneViewCenterPanel.preferredSize.height
      } else {
        positionableAdapter.scaledContentSize.height
      }
    sceneViewCenterPanel.setBounds(
      leftSectionWidth,
      sceneViewTopPanel.preferredSize.height,
      width + insets.horizontal - leftSectionWidth,
      centerPanelHeight,
    )
    sceneViewLeftPanel.setBounds(
      0,
      sceneViewTopPanel.preferredSize.height,
      sceneViewLeftPanel.preferredSize.width,
      centerPanelHeight,
    )
    sceneViewRightPanel.setBounds(
      sceneViewLeftPanel.preferredSize.width + positionableAdapter.scaledContentSize.width,
      sceneViewTopPanel.preferredSize.height,
      sceneViewRightPanel.preferredSize.width,
      centerPanelHeight,
    )
    super.doLayout()
  }

  /** [Dimension] used to avoid extra allocations calculating [getPreferredSize] */
  override fun getPreferredSize(): Dimension =
    positionableAdapter.getScaledContentSize(cachedPreferredSize).also {
      val shouldShowCenterPanel = it.width == 0 && it.height == 0
      val width = if (shouldShowCenterPanel) sceneViewCenterPanel.preferredSize.width else it.width
      val height =
        if (shouldShowCenterPanel) sceneViewCenterPanel.preferredSize.height else it.height

      it.width = width + positionableAdapter.margin.horizontal
      it.height = height + positionableAdapter.margin.vertical
    }

  override fun getMinimumSize(): Dimension {
    val shouldShowCenterPanel =
      positionableAdapter.scaledContentSize.let { it.height == 0 && it.width == 0 }
    val centerPanelWidth = if (shouldShowCenterPanel) sceneViewCenterPanel.minimumSize.width else 0
    val centerPanelHeight =
      if (shouldShowCenterPanel) sceneViewCenterPanel.minimumSize.height else 0

    return Dimension(
      maxOf(sceneViewTopPanel.minimumSize.width, SCENE_VIEW_PEER_PANEL_MIN_WIDTH, centerPanelWidth),
      BOTTOM_BORDER_HEIGHT +
      centerPanelHeight +
      sceneViewTopPanel.minimumSize.height +
      JBUI.scale(20),
    )
  }

  override fun isVisible(): Boolean {
    return sceneView.isVisible && !isHiddenInOrganizationGroup()
  }

  private fun isHiddenInOrganizationGroup() =
    isOrganizationEnabled.value &&
    sceneView.sceneManager.model.organizationGroup?.isOpened?.value == false

  override fun uiDataSnapshot(sink: DataSink) {
    sink[SCENE_VIEW] = sceneView
    sceneView.sceneManager.model.dataProvider?.uiDataSnapshot(sink)
  }
}