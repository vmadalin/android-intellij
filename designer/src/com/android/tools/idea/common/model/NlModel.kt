/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.common.model

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceNamespace.ANDROID
import com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO
import com.android.ide.common.rendering.api.ResourceReference.style
import com.android.ide.common.rendering.api.ViewInfo
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.configurations.Configuration
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.lint.LintAnnotationsModel
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.util.ListenerCollection.Companion.createWithDirectExecutor
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.BiFunction
import java.util.function.Consumer
import org.jetbrains.android.facet.AndroidFacet

/**
 * Model for an XML file
 *
 * @param componentRegistrar Returns the responsible for registering an [NlComponent] to enhance it
 *   with layout-specific properties and methods.
 * @param xmlFileProvider [LayoutlibSceneManager] requires the file from model to be an [XmlFile] to
 *   be able to render it. This is true in case of layout file and some others as well. However, we
 *   want to use model to render other file types (e.g. Java and Kotlin source files that contain
 *   custom Android [View]s)that do not have explicit conversion to [XmlFile] (but might have
 *   implicit). This provider should provide us with [XmlFile] representation of the VirtualFile fed
 *   to the model.
 * @param dataProvider Returns the [UiDataProvider] associated to this model. The [UiDataProvider]
 *   allows storing information that is specific to this model but is not part of it. For example,
 *   context information about how the model should be represented in a specific surface. The
 *   [UiDataProvider] might change at any point so make sure you always call this method to obtain
 *   the latest data.
 */
open class NlModel
@VisibleForTesting
protected constructor(
  parent: Disposable,
  val buildTarget: AndroidBuildTargetReference,
  val virtualFile: VirtualFile,
  open val configuration: Configuration,
  private val componentRegistrar: Consumer<NlComponent>,
  private val xmlFileProvider: BiFunction<Project, VirtualFile, XmlFile>,
  override var dataProvider: NlDataProvider?,
) : ModificationTracker, NlDataProviderHolder {

  val treeWriter =
    NlTreeWriter(buildTarget.facet, { file }, ::notifyModified, { createComponent(it) })
  val treeReader = NlTreeReader { file }

  /**
   * Adds information to the model from a render result. A given model can use different updaters
   * depending on what its usage requires. E.g. interactive preview may need less information from
   * an [NlModel] than a standard preview, so different updaters can be used in those cases.
   */
  private var modelUpdater: NlModelUpdaterInterface = DefaultModelUpdater()

  private val listeners = createWithDirectExecutor<ModelListener>()

  val displaySettings = DisplaySettings()

  // Deliberately not rev'ing the model version and firing changes here;
  // we know only the warnings layer cares about this change and can be
  // updated by a single repaint
  var lintAnnotationsModel: LintAnnotationsModel? = null
  val type: DesignerEditorFileType = file.typeOf()

  private val activations: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())
  private val modelVersion = ModelVersion()
  private var configurationModificationCount: Long = configuration.modificationCount

  /**
   * Field indicating whether a modification should be notified the next time this model is
   * activated, which should be true if a modification happened while this model was not active.
   */
  private val notifyModificationWhenActivated = AtomicBoolean(false)

  /** Variable to track what triggered the latest render (if known). */
  var lastChangeType: ChangeType? = null
    private set

  var isDisposed: Boolean = false
    private set

  /**
   * Indicate which group this NlModel belongs. This can be used to categorize the NlModel when
   * rendering or layouting.
   */
  var organizationGroup: OrganizationGroup? = null

  init {
    if (!Disposer.tryRegister(parent, this)) {
      Disposer.dispose(this)
    }
  }

  /** Returns if this model is currently active. */
  private val isActive: Boolean
    get() {
      synchronized(activations) {
        return activations.isNotEmpty()
      }
    }

  /**
   * Notify model that it's active. A model is active by default.
   *
   * @param source caller used to keep track of the references to this model. See [.deactivate]
   * @return true if the model was not active before and was activated.
   */
  fun activate(source: Any): Boolean {
    if (buildTarget.facet.isDisposed) {
      return false
    }

    // TODO: Tracking the source is just a workaround for the model being shared so the activations
    // and deactivations are
    // handled correctly. This should be solved by moving the removing this responsibility from the
    // model. The model shouldn't
    // need to keep track of activations/deactivation and they should be handled by the caller.
    var wasActive: Boolean
    synchronized(activations) {
      wasActive = activations.isNotEmpty()
      activations.add(source)
    }
    if (!wasActive) {
      // This was the first activation so enable listeners

      // If the resources have changed or the configuration has been modified, request a model
      // update

      if (configuration.modificationCount != configurationModificationCount) {
        updateTheme()
      }
      listeners.forEach { listener: ModelListener -> listener.modelActivated(this) }
      if (notifyModificationWhenActivated.getAndSet(false))
        notifyModified(ChangeType.MODEL_ACTIVATION)
      return true
    } else {
      return false
    }
  }

  private fun deactivate() {
    configurationModificationCount = configuration.modificationCount
  }

  /**
   * Notify model that it's not active. This means it can stop watching for events etc. It may be
   * activated again in the future.
   *
   * @param source the source is used to keep track of the references that are using this model.
   *   Only when all the sources have called deactivate(Object), the model will be really
   *   deactivated.
   * @return true if the model was active before and was deactivated.
   */
  fun deactivate(source: Any): Boolean {
    var shouldDeactivate: Boolean
    synchronized(activations) {
      val removed = activations.remove(source)
      // If there are no more activations, call the private #deactivate()
      shouldDeactivate = removed && activations.isEmpty()
    }
    if (shouldDeactivate) {
      deactivate()
      return true
    } else {
      return false
    }
  }

  val file: XmlFile
    get() = xmlFileProvider.apply(project, virtualFile)

  fun syncWithPsi(newRoot: XmlTag, roots: List<TagSnapshotTreeNode>) {
    modelUpdater.updateFromTagSnapshot(this, newRoot, roots)
  }

  fun updateAccessibility(viewInfos: List<ViewInfo>) {
    modelUpdater.updateFromViewInfo(this, viewInfos)
  }

  /**
   * Adds a new [ModelListener]. If the listener already exists, this method will make sure that the
   * listener is only added once.
   */
  fun addListener(listener: ModelListener) {
    listeners.add(listener)
  }

  fun removeListener(listener: ModelListener) {
    listeners.remove(listener)
  }

  /**
   * Calls all the listeners [ModelListener.modelDerivedDataChanged] method.
   *
   * TODO: move this mechanism to [LayoutlibSceneManager], or, ideally, remove the need for it
   *   entirely by moving all the derived data into the Scene.
   */
  fun notifyListenersModelDerivedDataChanged() {
    listeners.forEach { listener: ModelListener -> listener.modelDerivedDataChanged(this) }
  }

  /**
   * Calls all the listeners [ModelListener.modelChangedOnLayout] method.
   *
   * @param animate if true, warns the listeners to animate the layout update
   *
   * TODO: move these listeners out of [NlModel], since the model shouldn't care about being laid
   *   out.
   */
  fun notifyListenersModelChangedOnLayout(animate: Boolean) {
    listeners.forEach { listener: ModelListener -> listener.modelChangedOnLayout(this, animate) }
  }

  val facet: AndroidFacet
    get() = buildTarget.facet

  val module: Module
    get() = buildTarget.module

  val project: Project
    get() = buildTarget.project

  /**
   * This will warn model listeners that the model has been changed "live", without the attributes
   * of components being actually committed. Listeners such as Scene Managers will likely want for
   * example to schedule a layout pass in reaction to that callback.
   */
  fun notifyLiveUpdate() {
    listeners.forEach { listener -> listener.modelLiveUpdate(this) }
  }

  /** Simply create a component. In most cases you probably want [NlTreeWriter.createComponent]. */
  fun createComponent(tag: XmlTag): NlComponent {
    val component = NlComponent(this, tag)
    componentRegistrar.accept(component)
    return component
  }

  private fun updateTheme() {
    val themeUrl = ResourceUrl.parse(configuration.theme) ?: return
    if (themeUrl.type != ResourceType.STYLE) {
      return
    }
    val resolver = configuration.resourceItemResolver
    val themeReference = style(if (themeUrl.isFramework) ANDROID else RES_AUTO, themeUrl.name)
    if (resolver.getStyle(themeReference) == null) {
      val theme = configuration.preferredTheme
      configuration.setTheme(theme)
    }
  }

  override fun dispose() {
    isDisposed = true
    var shouldDeactivate: Boolean
    lintAnnotationsModel = null
    synchronized(activations) {
      // If there are no activations left, make sure we deactivate the model correctly
      shouldDeactivate = activations.isNotEmpty()
      activations.clear()
    }
    if (shouldDeactivate) {
      deactivate() // ensure listeners are unregistered if necessary
    }

    listeners.clear()
  }

  override fun toString(): String {
    return NlModel::class.java.simpleName + " for " + virtualFile
  }

  // ---- Implements ModificationTracker ----
  /** Maintains multiple counter depending on what did change in the model */
  internal class ModelVersion {
    private val _version = AtomicLong()

    @Suppress("unused") private var lastReason: ChangeType? = null

    fun increase(reason: ChangeType?) {
      _version.incrementAndGet()
      lastReason = reason
    }

    val version: Long
      get() = _version.get()
  }

  override fun getModificationCount(): Long {
    return modelVersion.version
  }

  private fun fireNotifyModified(reason: ChangeType) {
    modelVersion.increase(reason)
    updateTheme()
    lastChangeType = reason
    listeners.forEach { listener: ModelListener -> listener.modelChanged(this) }
  }

  fun notifyModified(reason: ChangeType) {
    // Notify modification now if the model is active, or in the next activation if it's currently
    // not active.
    if (isActive) {
      fireNotifyModified(reason)
    } else notifyModificationWhenActivated.set(true)
  }

  fun resetLastChange() {
    lastChangeType = null
  }

  fun setModelUpdater(modelUpdater: NlModelUpdaterInterface) {
    this.modelUpdater = modelUpdater
  }

  companion object {
    const val DELAY_AFTER_TYPING_MS: Int = 250

    fun getDefaultFile(project: Project, virtualFile: VirtualFile) =
      AndroidPsiUtils.getPsiFileSafely(project, virtualFile) as XmlFile
  }

  /** An [NlModel] builder */
  class Builder(
    val parentDisposable: Disposable,
    val buildTarget: AndroidBuildTargetReference,
    val file: VirtualFile,
    val configuration: Configuration,
  ) {
    private var componentRegistrar: Consumer<NlComponent> = Consumer {}
    private var xmlFileProvider: BiFunction<Project, VirtualFile, XmlFile> =
      BiFunction { project, virtualFile ->
        getDefaultFile(project, virtualFile)
      }
    private var dataProvider: NlDataProvider? = null

    fun withComponentRegistrar(componentRegistrar: Consumer<NlComponent>): Builder = also {
      this.componentRegistrar = componentRegistrar
    }

    fun withXmlProvider(xmlFileProvider: BiFunction<Project, VirtualFile, XmlFile>): Builder =
      also {
        this.xmlFileProvider = xmlFileProvider
      }

    fun withDataProvider(dataProvider: NlDataProvider): Builder = also {
      this.dataProvider = dataProvider
    }

    /** Instantiate a new [NlModel]. */
    @Slow
    fun build(): NlModel =
      NlModel(
        parentDisposable,
        buildTarget,
        file,
        configuration,
        componentRegistrar,
        xmlFileProvider,
        dataProvider,
      )
  }
}
