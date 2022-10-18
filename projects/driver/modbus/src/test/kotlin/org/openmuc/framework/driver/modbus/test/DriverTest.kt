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
package org.openmuc.framework.driver.modbus.test

import org.junit.jupiter.api.Test
import org.openmuc.framework.driver.modbus.ModbusDriver
import org.slf4j.LoggerFactory

class DriverTest {
    @Test
    fun printDriverInfo() {
        val driver = ModbusDriver()
        val info = driver.info
        val sb = StringBuilder()
        sb.append("\n")
        sb.append(
            """
    Driver Id = ${info.id}
    
    """.trimIndent()
        )
        sb.append(
            """
    Description = ${info.description}
    
    """.trimIndent()
        )
        sb.append(
            """
    DeviceAddressSyntax = ${info.deviceAddressSyntax}
    
    """.trimIndent()
        )
        sb.append(
            """
    SettingsSyntax = ${info.settingsSyntax}
    
    """.trimIndent()
        )
        sb.append(
            """
    ChannelAddressSyntax = ${info.channelAddressSyntax}
    
    """.trimIndent()
        )
        sb.append(
            """
    DeviceScanSettingsSyntax = ${info.deviceScanSettingsSyntax}
    
    """.trimIndent()
        )
        logger.info(sb.toString())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriverTest::class.java)
    }
}
