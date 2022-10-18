/*
 * Copyright 2011-2022 Fraunhofer ISE
 *
 * This file is part of OpenMUC.
 * For more information visit http://www.openmuc.org
 *
 * OpenMUC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenMUC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenMUC.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.openmuc.framework.server.restws.servlets

import org.openmuc.framework.config.DeviceScanInfo
import org.openmuc.framework.config.DeviceScanListener
import org.openmuc.framework.lib.rest1.rest.objects.RestScanProgressInfo

internal class DeviceScanListenerImplementation : DeviceScanListener {
    val restScanProgressInfo = RestScanProgressInfo()
    private val scannedDevicesList: MutableList<DeviceScanInfo?>?

    constructor() {
        scannedDevicesList = ArrayList()
    }

    constructor(scannedDevicesList: MutableList<DeviceScanInfo?>?) {
        restScanProgressInfo.isScanFinished = false
        this.scannedDevicesList = scannedDevicesList
    }

    override fun deviceFound(scanInfo: DeviceScanInfo?) {
        scannedDevicesList!!.add(scanInfo)
    }

    override fun scanProgress(scanProgress: Int) {
        restScanProgressInfo.scanProgress = scanProgress
    }

    @Synchronized
    override fun scanFinished() {
        notifyAll()
        restScanProgressInfo.isScanFinished = true
    }

    @Synchronized
    override fun scanInterrupted() {
        notifyAll()
        restScanProgressInfo.isScanInterrupted = true
    }

    @Synchronized
    override fun scanError(message: String?) {
        notifyAll()
        restScanProgressInfo.scanError = message
    }

    @Synchronized
    fun getScannedDevicesList(): MutableList<DeviceScanInfo?>? {
        while (!restScanProgressInfo.isScanFinished && !restScanProgressInfo.isScanInterrupted && restScanProgressInfo.scanError == null) {
            try {
                wait()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return scannedDevicesList
    }
}
