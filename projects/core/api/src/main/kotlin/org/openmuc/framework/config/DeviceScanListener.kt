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
package org.openmuc.framework.config

/**
 * Interface to implement when you want to be informed about a device scan progress and results. Register your listener
 * with configService.scanForDevices(..., listener).
 *
 */
interface DeviceScanListener {
    /**
     * Called immediately when a new device has been found.
     *
     * @param scanInfo
     * the information of the device found
     */
    fun deviceFound(scanInfo: DeviceScanInfo)

    /**
     * Called when scan is progressing.
     *
     * @param progress
     * the scan progress in percentage
     */
    fun scanProgress(progress: Int)

    /**
     * Called when scan is finished.
     */
    fun scanFinished()

    /**
     * Called when scan was interrupted through `interruptScanDevice()`
     */
    fun scanInterrupted()

    /**
     * Called when there has been a scan error reported by the driver.
     *
     * @param message
     * the error message
     */
    fun scanError(message: String)
}
