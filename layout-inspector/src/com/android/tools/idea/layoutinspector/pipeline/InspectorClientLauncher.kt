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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.ddmlib.AndroidDebugBridge
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyClient
import com.android.tools.idea.layoutinspector.pipeline.transport.TransportInspectorClient
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Future

/**
 * Class responsible for listening to active process connections and launching the correct
 * [InspectorClient] to handle it.
 *
 * @param clientCreators Client factory callbacks that will be triggered in order, and the first
 * callback to return a non-null value will be used.
 */
class InspectorClientLauncher(adb: AndroidDebugBridge,
                              processes: ProcessesModel,
                              clientCreators: List<(Params) -> InspectorClient?>,
                              parentDisposable: Disposable) {
  companion object {

    /**
     * Convenience method for creating a launcher with useful client creation rules used in production
     */
    fun createDefaultLauncher(adb: AndroidDebugBridge,
                              processes: ProcessesModel,
                              model: InspectorModel,
                              parentDisposable: Disposable): InspectorClientLauncher {
      val transportComponents = TransportInspectorClient.TransportComponents()
      Disposer.register(parentDisposable, transportComponents)

      return InspectorClientLauncher(
        adb,
        processes,
        listOf(
          { params ->
            if (params.process.device.apiLevel >= AndroidVersion.VersionCodes.Q) {
              TransportInspectorClient(params.adb, params.process, model, transportComponents)
            }
            else {
              null
            }
          },
          { params -> LegacyClient(params.adb, params.process, model) }
        ),
        parentDisposable)
    }
  }

  interface Params {
    val adb: AndroidDebugBridge
    val process: ProcessDescriptor
    val disposable: Disposable
  }

  init {
    processes.addSelectedProcessListeners {
      val process = processes.selectedProcess
      activeClient = if (process != null && process.isRunning) {
        val params = object : Params {
          override val adb: AndroidDebugBridge = adb
          override val process: ProcessDescriptor = process
          override val disposable: Disposable = parentDisposable
        }

        clientCreators.asSequence()
          .mapNotNull { createClient -> createClient(params) }
          .firstOrNull() ?: DisconnectedClient
      }
      else {
        DisconnectedClient
      }
    }

    Disposer.register(parentDisposable) {
      activeClient = DisconnectedClient
    }
  }

  var activeClient: InspectorClient = DisconnectedClient
    private set(value) {
      if (field != value) {
        if (field.isConnected) {
          val future = field.disconnect()
          disconnectCallbacks.forEach { it(future) }
          disconnectCallbacks.clear()
        }
        field = value
        clientCallbacks.forEach { callback -> callback(activeClient) }
        value.connect()
      }
    }

  private val clientCallbacks = mutableListOf<(InspectorClient) -> Unit>()
  private val disconnectCallbacks = mutableListOf<(Future<*>) -> Unit>()

  /**
   * Register a callback that is triggered whenever the active client changes.
   */
  fun addClientChangedListener(callback: (InspectorClient) -> Unit) {
    clientCallbacks.add(callback)
  }

  /**
   * Register callbacks that get triggered whenever an [InspectorClient] was disconnected by this
   * launcher. They are only meant for single-use; they will be triggered once and then cleared.
   *
   * The callback takes a future which will be completed when the disconnect is finished.
   */
  @TestOnly
  fun addDisconnectionListener(callback: (Future<*>) -> Unit) {
    disconnectCallbacks.add(callback)
  }
}