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
package com.android.tools.idea.adb.wireless

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.catching
import com.android.tools.idea.concurrency.finallySync
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformAsync
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import java.util.concurrent.Executor

/**
 * Handles the QR Code pairing aspect of the "Pair device over Wi-FI" dialog
 */
@UiThread
class QrCodeScanningController(private val service: AdbDevicePairingService,
                               private val view: AdbDevicePairingView,
                               edtExecutor: Executor,
                               parentDisposable: Disposable) : Disposable {
  private val LOG = logger<QrCodeScanningController>()
  private val edtExecutor = FutureCallbackExecutor.wrap(edtExecutor)
  private val pollingAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  private val listener = MyModelListener()
  private var state = State.Init

  init {
    Disposer.register(parentDisposable, this)
    view.model.addListener(listener)
  }

  override fun dispose() {
    pollingAlarm.cancelAllRequests()
    view.model.removeListener(listener)
    state = State.Disposed
  }

  fun startPairingProcess() {
    view.showQrCodePairingStarted()
    startAnotherPairingProcess()
  }

  private fun startAnotherPairingProcess() {
    generateQrCode(view.model)
    state = State.Polling
    pollMdnsServices()
  }

  private fun generateQrCode(model: AdbDevicePairingModel) {
    val futureQrCode = service.generateQrCode(UIColors.QR_CODE_BACKGROUND, UIColors.QR_CODE_FOREGROUND)
    edtExecutor.transform(futureQrCode) {
      model.qrCodeImage = it
    }
  }

  private fun startParingDevice(mdnsService: MdnsService, password: String) {
    state = State.Pairing
    view.showQrCodePairingInProgress(mdnsService)
    val futurePairing = service.pairMdnsService(mdnsService, password)
    futurePairing.transform(edtExecutor) { pairingResult ->
      //TODO: Ensure not disposed and state still the same
      view.showQrCodeMdnsPairingSuccess(pairingResult)
      pairingResult
    }.transformAsync(edtExecutor) { pairingResult ->
      //TODO: Ensure not disposed and state still the same
      service.waitForDevice(pairingResult)
    }.transform(edtExecutor) { device ->
      //TODO: Ensure not disposed and state still the same
      state = State.PairingSuccess
      view.showQrCodePairingSuccess(mdnsService, device)
      startAnotherPairingProcess()
    }.catching(edtExecutor, Throwable::class.java) { error ->
      //TODO: Ensure not disposed and state still the same
      state = State.PairingError
      view.showQrCodePairingError(mdnsService, error)
      startAnotherPairingProcess()
    }
  }

  private fun pollMdnsServices() {
    // Don't start a new polling request if we are not in "polling" mode
    if (state != State.Polling) {
      return
    }

    val futureServices = service.scanMdnsServices()
    edtExecutor.transform(futureServices) { services ->
      view.model.mdnsServices = services
    }.catching(edtExecutor, Throwable::class.java) {
      //TODO: Display/log error
    }.finallySync(edtExecutor) {
      // Run again in 1 second, unless we are disposed
      if (!Disposer.isDisposed(this)) {
        pollingAlarm.addRequest({ pollMdnsServices() }, 1_000)
      }
    }
  }

  enum class State {
    Init,
    Polling,
    Pairing,
    PairingError,
    PairingSuccess,
    Disposed
  }

  @UiThread
  inner class MyModelListener : AdbDevicePairingModelListener {
    override fun qrCodeGenerated(newImage: QrCodeImage) {
    }

    override fun mdnsServicesDiscovered(services: List<MdnsService>) {
      LOG.info("${services.size} mDNS services discovered")
      services.forEachIndexed { index, it ->
        LOG.info("  mDNS service #${index + 1}: name=${it.serviceName} - ip=${it.ipAddress} - port=${it.port}")
      }

      // If there is a QR Code displayed, look for a mDNS service with the same service name
      view.model.qrCodeImage?.let { qrCodeImage ->
        services.firstOrNull { it.serviceName == qrCodeImage.serviceName }
          ?.let {
            // We found the service we created, meaning the phone is in "paring" mode
            startParingDevice(it, qrCodeImage.password)
          }
      }
    }
  }
}