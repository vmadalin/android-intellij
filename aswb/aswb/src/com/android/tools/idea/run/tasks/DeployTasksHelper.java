/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run.tasks;

import com.android.tools.deployer.DeployerException;
import com.android.tools.idea.execution.common.AndroidExecutionException;
import com.android.tools.idea.execution.common.DeployOptions;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.blaze.BlazeLaunchTask;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;

/** Helper class for {@link DeployTask} */
public class DeployTasksHelper {

  private DeployTasksHelper() {}

  public static BlazeLaunchTask createDeployTask(
      Project project, Collection<ApkInfo> packages, DeployOptions deployOptions) {
    return launchContext -> {
      try {
        List unused =
          new DeployTask(
            project,
            packages,
            deployOptions.getPmInstallFlags(),
            deployOptions.getInstallOnAllUsers(),
            deployOptions.getAlwaysInstallWithPm(),
            deployOptions.getAllowAssumeVerified(),
            true /* TODO: Assume blaze always build for now.  */)
            .run(launchContext.getDevice(), launchContext.getProgressIndicator());
      } catch (DeployerException de) {
        throw new AndroidExecutionException(de.getId(), de.getMessage() + "\n" + de.getDetails());
      }
    };
  }
}
