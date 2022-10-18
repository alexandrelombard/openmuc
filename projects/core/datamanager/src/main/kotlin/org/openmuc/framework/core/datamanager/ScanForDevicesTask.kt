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
package org.openmuc.framework.core.datamanager

import org.openmuc.framework.config.*
import org.openmuc.framework.driver.spi.DriverDeviceScanListener
import org.openmuc.framework.driver.spi.DriverService

class ScanForDevicesTask(
    private val driver: DriverService,
    private val settings: String?,
    private val listener: DeviceScanListener?
) : Runnable {
    override fun run() {
        try {
            driver.scanForDevices(settings, NonBlockingScanListener(listener))
        } catch (e: UnsupportedOperationException) {
            listener!!.scanError("Device scan not supported by driver")
            return
        } catch (e: ArgumentSyntaxException) {
            listener!!.scanError("Scan settings syntax invalid: " + e.message)
            return
        } catch (e: ScanException) {
            listener!!.scanError("IOException while scanning: " + e.message)
            return
        } catch (e: ScanInterruptedException) {
            listener!!.scanInterrupted()
            return
        }
        listener!!.scanFinished()
    }

    internal inner class NonBlockingScanListener(var listener: DeviceScanListener?) : DriverDeviceScanListener {
        var scanInfos: MutableList<DeviceScanInfo> = ArrayList()
        override fun scanProgressUpdate(progress: Int) {
            listener!!.scanProgress(progress)
        }

        override fun deviceFound(scanInfo: DeviceScanInfo) {
            if (!scanInfos.contains(scanInfo)) {
                scanInfos.add(scanInfo)
                listener!!.deviceFound(scanInfo)
            }
        }
    }
}
