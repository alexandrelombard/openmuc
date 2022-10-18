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
package org.openmuc.framework.driver.iec60870

import org.openmuc.framework.config.*
import org.openmuc.framework.data.Record.value
import org.openmuc.framework.driver.iec60870.settings.*
import org.openmuc.framework.driver.spi.*
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import org.osgi.service.component.annotations.Component

@Component
class Iec60870Driver : DriverService {
    override val info: DriverInfo
        get() = Companion.info

    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ScanInterruptedException::class
    )
    override fun scanForDevices(settings: String?, listener: DriverDeviceScanListener?) {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class)
    override fun interruptDeviceScan() {
        throw UnsupportedOperationException()
    }

    @Throws(ArgumentSyntaxException::class, ConnectionException::class)
    override fun connect(deviceAddress: String?, settings: String?): Connection? {
        return Iec60870Connection(DeviceAddress(deviceAddress), DeviceSettings(settings), DRIVER_ID)
    }

    companion object {
        private const val DRIVER_ID = "iec60870"
        private const val DESCRIPTION = "This driver can be used to access IEC 60870-104 devices"
        private val info = DriverInfo(
            DRIVER_ID, DESCRIPTION,
            GenericSetting.Companion.syntax(DeviceAddress::class.java), GenericSetting.Companion.syntax(
                DeviceSettings::class.java
            ),
            GenericSetting.Companion.syntax(ChannelAddress::class.java), GenericSetting.Companion.syntax(
                DeviceScanSettings::class.java
            )
        )
    }
}
