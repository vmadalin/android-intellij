/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.tools.deployer.DeploymentCacheDatabase;
import com.android.tools.deployer.SqlApkFileDatabase;
import com.android.tools.deployer.tasks.TaskRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import org.jetbrains.annotations.NotNull;

public class DeploymentService {

  private final TaskRunner runner;

  private final NotNullLazyValue<SqlApkFileDatabase> dexDatabase;
  private final NotNullLazyValue<DeploymentCacheDatabase> deploymentCacheDatabase;

  private DeploymentService() {
    ExecutorService service = AppExecutorUtil.createBoundedApplicationPoolExecutor("Deployment Service", 5);
    runner = new TaskRunner(service);

    Path dexDbPath = Paths.get(PathManager.getSystemPath(), ".dex_cache.db");
    Path deployDbPath = Paths.get(PathManager.getSystemPath(), ".deploy_cache.db");

    dexDatabase = NotNullLazyValue.createValue(() -> new SqlApkFileDatabase(dexDbPath.toFile(), PathManager.getTempPath()));
    deploymentCacheDatabase = NotNullLazyValue.createValue(() -> new DeploymentCacheDatabase(deployDbPath.toFile()));
  }

  @NotNull
  public static DeploymentService getInstance() {
    return ApplicationManager.getApplication().getService(DeploymentService.class);
  }

  public TaskRunner getTaskRunner() {
    return runner;
  }

  public SqlApkFileDatabase getDexDatabase() {
    return dexDatabase.getValue();
  }

  public DeploymentCacheDatabase getDeploymentCacheDatabase() {
    return deploymentCacheDatabase.getValue();
  }
}
