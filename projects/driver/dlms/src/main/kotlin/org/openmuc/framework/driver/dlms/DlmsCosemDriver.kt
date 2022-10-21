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
package org.openmuc.framework.driver.dlms

import org.openmuc.framework.config.*
import org.openmuc.framework.driver.dlms.settings.ChannelAddress
import org.openmuc.framework.driver.dlms.settings.DeviceAddress
import org.openmuc.framework.driver.dlms.settings.DeviceSettings
import org.openmuc.framework.driver.dlms.settings.GenericSetting
import org.openmuc.framework.driver.spi.*
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

@Component
class DlmsCosemDriver : DriverService {
    init {
        logger.debug(
            "DLMS Driver instantiated. Expecting rxtxserial.so in: " + System.getProperty("java.library.path")
                    + " for serial (HDLC) connections."
        )
    }

    override val info: DriverInfo
        get() = Companion.info

    @Throws(ArgumentSyntaxException::class, ScanException::class, ScanInterruptedException::class)
    override fun scanForDevices(settings: String, listener: DriverDeviceScanListener?) {
        throw UnsupportedOperationException()
    }

    override fun interruptDeviceScan() {
        throw UnsupportedOperationException()
    }

    @Throws(ConnectionException::class, ArgumentSyntaxException::class)
    override fun connect(deviceAddress: String, settings: String): Connection {
        return DlmsCosemConnection(deviceAddress, settings)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DlmsCosemDriver::class.java)
        private const val ID = "dlms"
        private const val DESCRIPTION =
            "This is a driver to communicate with smart meter over the IEC 62056 DLMS/COSEM protocol."
        private val info = DriverInfo(
            ID, DESCRIPTION,
            GenericSetting.Companion.strSyntaxFor<DeviceAddress>(
                DeviceAddress::class.java
            ), GenericSetting.Companion.strSyntaxFor<DeviceSettings>(
                DeviceSettings::class.java
            ),
            GenericSetting.Companion.strSyntaxFor<ChannelAddress>(
                ChannelAddress::class.java
            ), "No device scanning."
        )
    }
}
