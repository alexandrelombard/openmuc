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
package org.openmuc.framework.driver.wmbus

import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.openmuc.framework.config.*
import org.openmuc.framework.driver.spi.*
import org.openmuc.jmbus.SecondaryAddress
import org.osgi.service.component.annotations.Component
import java.util.*

//import javax.xml.bind.DatatypeConverter;
@Component
class Driver : DriverService {
    override val info: DriverInfo
        get() = Companion.info

    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ScanInterruptedException::class
    )
    override fun scanForDevices(settings: String, listener: DriverDeviceScanListener?) {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class)
    override fun interruptDeviceScan() {
        throw UnsupportedOperationException()
    }

    @Throws(ArgumentSyntaxException::class, ConnectionException::class)
    override fun connect(deviceAddress: String, settings: String): Connection {
        val deviceAddressTokens =
            deviceAddress.trim { it <= ' ' }.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (deviceAddressTokens.size != 2) {
            throw ArgumentSyntaxException("The device address does not consist of two parameters.")
        }
        val connectionPort = deviceAddressTokens[0]
        var secondaryAddressAsString = ""
        var host = ""
        var port = 0
        var isTCP = false
        if (connectionPort.equals("TCP", ignoreCase = true)) {
            isTCP = true
            host = deviceAddressTokens[0]
            port = try {
                Integer.decode(deviceAddressTokens[1])
            } catch (e: NumberFormatException) {
                throw ArgumentSyntaxException("TCP port is not a number.")
            }
            secondaryAddressAsString = deviceAddressTokens[2].lowercase(Locale.getDefault())
        } else {
            secondaryAddressAsString = deviceAddressTokens[1].lowercase(Locale.getDefault())
        }
        var secondaryAddress: SecondaryAddress? = null
        try {
            secondaryAddress = parseSecondaryAddress(secondaryAddressAsString)
        } catch (e: DecoderException) {
            e.printStackTrace()
        }
        val settingsTokens = splitSettingsToken(settings)
        val transceiverString = settingsTokens[0]
        val modeString = settingsTokens[1]
        var keyString: String? = null
        if (settingsTokens.size == 3) {
            keyString = settingsTokens[2]
        }
        var wmBusInterface: WMBusInterface
        if (isTCP) {
            synchronized(this) {
                wmBusInterface = WMBusInterface.getTCPInstance(host, port, transceiverString, modeString)
                try {
                    return wmBusInterface.connect(secondaryAddress, keyString)
                } catch (e: DecoderException) {
                    e.printStackTrace()
                }
            }
        } else {
            synchronized(this) {
                wmBusInterface =
                    WMBusInterface.getSerialInstance(connectionPort, transceiverString, modeString)
                try {
                    return wmBusInterface.connect(secondaryAddress, keyString)
                } catch (e: DecoderException) {
                    e.printStackTrace()
                }
            }
        }
        throw ConnectionException()
    }

    @Throws(ArgumentSyntaxException::class)
    private fun splitSettingsToken(settings: String?): Array<String> {
        val settingsTokens = settings!!.trim { it <= ' ' }
            .lowercase(Locale.getDefault()).split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (settingsTokens.size < 2 || settingsTokens.size > 3) {
            throw ArgumentSyntaxException("The device's settings parameters does not contain 2 or 3 parameters.")
        }
        return settingsTokens
    }

    @Throws(ArgumentSyntaxException::class, DecoderException::class)
    private fun parseSecondaryAddress(secondaryAddressAsString: String): SecondaryAddress {
        val secondaryAddress: SecondaryAddress
        secondaryAddress = try {
            val bytes = Hex.decodeHex(secondaryAddressAsString)
            SecondaryAddress.newFromWMBusHeader(bytes, 0)
        } catch (e: NumberFormatException) {
            throw ArgumentSyntaxException(
                "The SecondaryAddress: $secondaryAddressAsString could not be converted to a byte array."
            )
        }
        return secondaryAddress
    }

    companion object {
        private val info = DriverInfo(
            "wmbus",  // id
            // description
            "Wireless M-Bus is a protocol to read out meters and sensors.",  // device address
            "Synopsis: <serial_port>:<secondary_address> / TCP:<host_address>:<port>:<secondary_address>"
                    + "Example for <serial_port>: /dev/ttyS0 (Unix), COM1 (Windows)  <secondary_address> as a hex string.  "
                    + "Example for <host_address>:<port> 192.168.8.15:2018",  // settings
            "Synopsis: <transceiver> <mode> [<key>]\n Transceiver could be 'amber', 'imst' and 'rc'for RadioCraft. Possible modes are T or S. ",  // channel address
            "Synopsis: <dib>:<vib>",  // device scan settings
            "N.A."
        )
    }
}
