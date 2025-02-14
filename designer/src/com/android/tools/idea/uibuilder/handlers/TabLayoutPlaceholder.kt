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
package com.android.tools.idea.uibuilder.handlers

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.Region
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SnappingInfo
import java.awt.Point

@SwingCoordinate private const val SIZE = 8

class TabLayoutPlaceholder(
  tabLayoutComponent: SceneComponent,
  private val tabItemAnchor: SceneComponent?,
) : Placeholder(tabLayoutComponent) {

  private val snappedX =
    tabItemAnchor?.drawX ?: (tabLayoutComponent.drawX + tabLayoutComponent.drawWidth)

  override val region: Region
    get() {
      val top = host.drawY
      val bottom = top + host.drawHeight
      return Region(snappedX - SIZE, top, snappedX + SIZE, bottom, host.depth)
    }

  override fun findNextSibling(appliedComponent: SceneComponent, newParent: SceneComponent) =
    tabItemAnchor

  // Dragging in TabLayout just change the order. No attribute is changed.
  override fun updateAttribute(sceneComponent: SceneComponent, attributes: NlAttributesHolder) =
    Unit

  override fun snap(info: SnappingInfo, retPoint: Point): Boolean {
    val r = region
    if (r.contains(info.centerX, info.centerY)) {
      retPoint.x = snappedX - (info.centerX - info.left)
      retPoint.y = info.top
      return true
    }
    return false
  }
}
