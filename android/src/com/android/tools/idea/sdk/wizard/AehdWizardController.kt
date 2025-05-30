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
package com.android.tools.idea.sdk.wizard

import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.welcome.install.AehdSdkComponentTreeNode
import com.android.tools.idea.welcome.install.AehdSdkComponentTreeNode.InstallationIntention
import com.android.tools.idea.welcome.install.InstallContext
import com.android.tools.idea.welcome.install.InstallOperation
import com.android.tools.idea.welcome.install.InstallOperation.Companion.wrap
import com.android.tools.idea.welcome.install.InstallSdkComponentsOperation
import com.android.tools.idea.welcome.install.InstallableSdkComponentTreeNode
import com.android.tools.idea.welcome.install.InstallationCancelledException
import com.android.tools.idea.welcome.install.SdkComponentInstaller
import com.android.tools.idea.welcome.install.WizardException
import com.android.tools.idea.welcome.wizard.ProgressStep
import com.android.tools.idea.welcome.wizard.StudioFirstRunWelcomeScreen
import com.google.common.collect.Lists
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PlatformUtils
import java.io.File

class AehdWizardController(val sdkComponentInstaller: SdkComponentInstaller = SdkComponentInstaller()) {

  fun getPackagesToInstall(sdkHandler: AndroidSdkHandler, aehdSdkComponentTreeNode: AehdSdkComponentTreeNode): Collection<RemotePackage> {
    try {
      return sdkComponentInstaller.getPackagesToInstall(sdkHandler, listOf(aehdSdkComponentTreeNode))
    }
    catch (e: SdkQuickfixUtils.PackageResolutionException) {
      logger<StudioFirstRunWelcomeScreen>().warn(e)
      return emptyList()
    }
  }

  fun setupAehd(aehdSdkComponentTreeNode: AehdSdkComponentTreeNode, progressStep: ProgressStep, progressIndicator: ProgressIndicator): Boolean {
    val tmpDir = FileUtil.createTempDirectory(PlatformUtils.getPlatformPrefix(), "AEHD", true)
    val installContext = InstallContext(tmpDir, progressStep)

    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    sdkHandler
      .getRepoManager(progressIndicator)
      .loadSynchronously(
        RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progressIndicator, StudioDownloader(), StudioSettingsController.getInstance())
    aehdSdkComponentTreeNode.updateState(sdkHandler)

    val selectedComponents: Collection<InstallableSdkComponentTreeNode> = Lists.newArrayList(aehdSdkComponentTreeNode)

    var configureAehdProgressRatio = 1.0
    if (aehdSdkComponentTreeNode.installationIntention.isInstall()) {
      configureAehdProgressRatio = 0.5 // leave the first half of the progress to the updates check & install operation
    }

    val configureAehdOperation = wrap(installContext, { input: File ->
      aehdSdkComponentTreeNode.configure(installContext, sdkHandler)
      input
    }, configureAehdProgressRatio)

    val opChain: InstallOperation<File, File>
    if (aehdSdkComponentTreeNode.installationIntention.isInstall()) {
      val install =
        InstallSdkComponentsOperation(installContext, sdkHandler, selectedComponents, sdkComponentInstaller, 0.5)
      opChain = install.then(configureAehdOperation)
    }
    else {
      opChain = configureAehdOperation
    }

    try {
      opChain.execute(sdkHandler.location!!.toFile())
    }
    catch (e: InstallationCancelledException) {
      installContext.print("Android Studio setup was canceled", ConsoleViewContentType.ERROR_OUTPUT)
    }
    catch (e: WizardException) {
      throw RuntimeException(e)
    }
    finally {
      if (!aehdSdkComponentTreeNode.isInstallerSuccessfullyCompleted && aehdSdkComponentTreeNode.installationIntention != InstallationIntention.UNINSTALL) {
        // The intention was to install AEHD, but the installation failed. Ensure we don't leave the SDK package behind
        sdkHandler.getRepoManager(progressIndicator).reloadLocalIfNeeded(progressIndicator)
        sdkComponentInstaller.ensureSdkPackagesUninstalled(sdkHandler, aehdSdkComponentTreeNode.requiredSdkPackages, progressIndicator)
      }
    }
    installContext.print("Done", ConsoleViewContentType.NORMAL_OUTPUT)

    return aehdSdkComponentTreeNode.isInstallerSuccessfullyCompleted
  }

  fun handleCancel(installationIntention: InstallationIntention, aehdSdkComponentTreeNode: AehdSdkComponentTreeNode, aClass: Class<*>, logger: Logger) {
    // The wizard was invoked to install, but installer invocation failed or was cancelled.
    // Have to ensure the SDK package is removed
    if (installationIntention != InstallationIntention.UNINSTALL) {
      try {
        val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
        val progress: ProgressIndicator = StudioLoggerProgressIndicator(aClass)
        sdkHandler.getRepoManager(progress).reloadLocalIfNeeded(progress)
        sdkComponentInstaller.ensureSdkPackagesUninstalled(sdkHandler, aehdSdkComponentTreeNode.requiredSdkPackages, progress)
      }
      catch (e: Exception) {
        Messages.showErrorDialog(sdkPackageCleanupFailedMessage(), "Cleanup Error")
        logger.warn("Failed to make sure AEHD SDK package is uninstalled after AEHD wizard was cancelled", e)
      }
    }
  }

  private fun sdkPackageCleanupFailedMessage(): String {
    return "AEHD installer cleanup failed. The status of the package in the SDK manager may " +
           "be reflected incorrectly. Reinstalling the package may solve the issue" +
           (if (SystemInfo.isWindows) " (is the SDK folder opened in another program?)" else ".")
  }

}