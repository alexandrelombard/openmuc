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
package org.openmuc.framework.driver.dlms.settings

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.UnknownHostException
import java.text.MessageFormat

class DeviceAddress(deviceAddress: String?) : GenericSetting() {
    @Option(value = "t", mandatory = true, range = "serial|tcp")
    val connectionType: String? = null

    @Option(value = "h", range = "inet_address")
    var hostAddress: InetAddress? = null

    @Option(value = "p", range = "int")
    val port = 4059

    @Option(value = "hdlc", range = "boolean")
    private val useHdlc = false

    @Option(value = "sp")
    val serialPort = ""

    @Option(value = "bd", range = "int")
    val baudrate = 9600

    @Option("d")
    val baudRateChangeDelay: Long = 0

    @Option("eh")
    private val enableBaudRateHandshake = false

    @Option("iec")
    val iec21Address = ""

    @Option("pd")
    val physicalDeviceAddress = 0

    init {
        val addressLength = parseFields(deviceAddress)
        if (connectionType.equals("tcp", ignoreCase = true)) {
            if (addressLength == 1) {
                logger.info(
                    MessageFormat.format(
                        "No device address set in configuration, default values will be used: host address = localhost; port = {0}",
                        port
                    )
                )
            }
            if (hostAddress == null) {
                hostAddress = try {
                    InetAddress.getLocalHost()
                } catch (e: UnknownHostException) {
                    throw ArgumentSyntaxException("Could not set default host address: localhost")
                }
            }
        } else if (connectionType.equals("serial", ignoreCase = true)) {
            if (serialPort.isEmpty()) {
                throw ArgumentSyntaxException("No serial port given. e.g. Linux: /dev/ttyUSB0 or Windows: COM12 ")
            }
        } else {
            throw ArgumentSyntaxException(
                "Only 'tcp' and 'serial' are supported connection types, given is: $connectionType"
            )
        }
    }

    fun useHdlc(): Boolean {
        return useHdlc
    }

    fun enableBaudRateHandshake(): Boolean {
        return enableBaudRateHandshake
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeviceAddress::class.java)
    }
}
