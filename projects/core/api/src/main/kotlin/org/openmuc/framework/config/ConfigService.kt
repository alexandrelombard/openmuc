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

import org.openmuc.framework.dataaccess.DeviceState
import java.io.FileNotFoundException

interface ConfigService {
    fun lock()
    fun tryLock(): Boolean
    fun unlock()

    /**
     * Returns a *clone* of the current configuration file.
     *
     * @return clone of the configuration file.
     *
     * @see .setConfig
     */
    var config: RootConfig?
    fun getConfig(listener: ConfigChangeListener?): RootConfig?
    fun stopListeningForConfigChange(listener: ConfigChangeListener?)

    @Throws(ConfigWriteException::class)
    fun writeConfigToFile()

    @Throws(FileNotFoundException::class, ParseException::class)
    fun reloadConfigFromFile()
    val emptyConfig: RootConfig?

    @Throws(
        DriverNotAvailableException::class,
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ScanInterruptedException::class
    )
    fun scanForDevices(driverId: String?, settings: String?): List<DeviceScanInfo?>?

    @Throws(DriverNotAvailableException::class)
    fun scanForDevices(driverId: String?, settings: String?, scanListener: DeviceScanListener?)

    @Throws(DriverNotAvailableException::class, UnsupportedOperationException::class)
    fun interruptDeviceScan(driverId: String)

    @Throws(
        DriverNotAvailableException::class,
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class
    )
    fun scanForChannels(deviceId: String, settings: String): List<ChannelScanInfo>

    @Throws(DriverNotAvailableException::class)
    fun getDriverInfo(driverId: String): DriverInfo
    val idsOfRunningDrivers: List<String>
    fun getDeviceState(deviceId: String): DeviceState?
}
