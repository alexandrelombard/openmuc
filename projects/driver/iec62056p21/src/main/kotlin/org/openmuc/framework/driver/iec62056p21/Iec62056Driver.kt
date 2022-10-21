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
package org.openmuc.framework.driver.iec62056p21

import org.openmuc.framework.config.*
import org.openmuc.framework.driver.spi.*
import org.openmuc.j62056.Iec21Port
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.io.IOException

@Component
class Iec62056Driver : DriverService {
    private var serialPortName: String = ""
    private var baudRateChangeDelay = 0
    private var timeout = 2000
    private var retries = 1
    private var initialBaudRate = -1
    private var fixedBaudRate = false
    private var deviceAddress = ""
    private var requestStartCharacter = ""
    private var readStandard = false

    override val info = DriverInfo(
        DRIVER_ID, DRIVER_DESCRIPTION, DEVICE_ADDRESS_SYNTAX,
        SETTINGS_SYNTAX, CHANNEL_ADDRESS_SYNTAX, DEVICE_SCAN_SETTINGS_SYNTAX)

    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ScanInterruptedException::class
    )
    override fun scanForDevices(settings: String, listener: DriverDeviceScanListener?) {
        handleScanParameter(settings)
        val iec21PortBuilder = configuredBuilder
        val iec21Port = try {
            iec21PortBuilder.buildAndOpen()
        } catch (e: IOException) {
            throw ScanException("Failed to open serial port: " + e.message)
        }
        try {
            val dataMessage = iec21Port.read()
            val dataSets = dataMessage.dataSets
            val deviceSettings = StringBuilder()
            if (baudRateChangeDelay > 0) {
                deviceSettings.append(' ').append(BAUD_RATE_CHANGE_DELAY).append(' ').append(baudRateChangeDelay)
            }
            val deviceSettingsString = deviceSettings.toString().trim { it <= ' ' }
            listener?.deviceFound(
                DeviceScanInfo(
                    serialPortName, deviceSettingsString,
                    dataSets[0].address.replace("\\p{Cntrl}".toRegex(), "")
                )
            )
        } catch (e: IOException) {
            throw ScanException(e)
        } finally {
            iec21Port.close()
        }
    }

    @Throws(UnsupportedOperationException::class)
    override fun interruptDeviceScan() {
        throw UnsupportedOperationException()
    }

    @Throws(ArgumentSyntaxException::class, ConnectionException::class)
    override fun connect(deviceAddress: String, settings: String): Connection {
        serialPortName = deviceAddress
        handleParameter(settings)
        val configuredBuilder = configuredBuilder
        return Iec62056Connection(configuredBuilder, retries, readStandard, requestStartCharacter)
    }

    private val configuredBuilder: Iec21Port.Builder
        get() = Iec21Port.Builder(serialPortName).setBaudRateChangeDelay(baudRateChangeDelay)
            .setTimeout(timeout)
            .enableFixedBaudrate(fixedBaudRate)
            .setInitialBaudrate(initialBaudRate)
            .setDeviceAddress(deviceAddress)
            .enableVerboseMode(VERBOSE)
            .setRequestStartCharacters(requestStartCharacter)

    @Throws(ArgumentSyntaxException::class)
    private fun handleScanParameter(settings: String?) {
        if (settings!!.isEmpty()) {
            throw ArgumentSyntaxException("No parameter given. At least serial port is needed")
        }
        val args = settings.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        serialPortName = args[0].trim { it <= ' ' }
        if (serialPortName.isEmpty()) {
            throw ArgumentSyntaxException(
                "The <serial_port> has to be specified in the settings, as first parameter"
            )
        }
        parseArguments(args, true)
        if (requestStartCharacter.isEmpty()) {
            readStandard = false
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun handleParameter(settings: String?) {
        val args = settings!!.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        parseArguments(args, false)
        if (requestStartCharacter.isEmpty()) {
            readStandard = false
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun parseArguments(args: Array<String>, isScan: Boolean) {
        var i = if (isScan) 1 else 0
        while (i < args.size) {
            if (args[i] == BAUD_RATE_CHANGE_DELAY) {
                ++i
                baudRateChangeDelay = getIntValue(args, i, BAUD_RATE_CHANGE_DELAY)
            } else if (args[i] == RETRIES_PARAM) {
                ++i
                retries = getIntValue(args, i, RETRIES_PARAM)
            } else if (args[i] == TIMEOUT_PARAM) {
                ++i
                timeout = getIntValue(args, i, TIMEOUT_PARAM)
            } else if (args[i] == INITIAL_BAUD_RATE) {
                ++i
                initialBaudRate = getIntValue(args, i, INITIAL_BAUD_RATE)
            } else if (args[i] == DEVICE_ADDRESS) {
                ++i
                deviceAddress = getStringValue(args, i, DEVICE_ADDRESS)
            } else if (args[i] == FIXED_BAUD_RATE) {
                fixedBaudRate = true
            } else if (args[i] == REQUEST_START_CHARACTER) {
                ++i
                requestStartCharacter = getStringValue(args, i, REQUEST_START_CHARACTER)
            } else if (args[i] == READ_STANDARD) {
                readStandard = true
            } else {
                throw ArgumentSyntaxException("Found unknown argument in settings: " + args[i])
            }
            i++
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun getIntValue(args: Array<String>, i: Int, parameter: String): Int {
        val ret: Int
        checkParameter(args, i, parameter)
        ret = try {
            args[i].toInt()
        } catch (e: NumberFormatException) {
            throw ArgumentSyntaxException("Specified value of parameter'$parameter' is not an integer")
        }
        return ret
    }

    @Throws(ArgumentSyntaxException::class)
    private fun getStringValue(args: Array<String>, i: Int, parameter: String): String {
        val ret: String
        checkParameter(args, i, parameter)
        ret = args[i]
        return ret
    }

    @Throws(ArgumentSyntaxException::class)
    private fun checkParameter(args: Array<String>, i: Int, parameter: String) {
        if (i == args.size) {
            throw ArgumentSyntaxException("No value was specified after the $parameter parameter")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Iec62056Driver::class.java)
        private const val BAUD_RATE_CHANGE_DELAY = "-d"
        private const val TIMEOUT_PARAM = "-t"
        private const val RETRIES_PARAM = "-r"
        private const val INITIAL_BAUD_RATE = "-bd"
        private const val DEVICE_ADDRESS = "-a"
        private const val FIXED_BAUD_RATE = "-fbd"
        private const val REQUEST_START_CHARACTER = "-rsc"
        private const val READ_STANDARD = "-rs"
        private const val VERBOSE = false
        private const val DRIVER_ID = "iec62056p21"
        private const val DRIVER_DESCRIPTION = "This driver can read meters using IEC 62056-21 Mode A, B and C."
        private const val DEVICE_ADDRESS_SYNTAX = "Synopsis: <serial_port>\nExamples: /dev/ttyS0 (Unix), COM1 (Windows)"
        private const val SETTINGS =
            """[$BAUD_RATE_CHANGE_DELAY <baud_rate_change_delay>] [$TIMEOUT_PARAM <timeout>] [$RETRIES_PARAM <number_of_read_retries>] [$INITIAL_BAUD_RATE <initial_baud_rate>] [$DEVICE_ADDRESS <device_address>] [$FIXED_BAUD_RATE] [$REQUEST_START_CHARACTER <request_message_start_character>]
$BAUD_RATE_CHANGE_DELAY sets the waiting time between a baud rate change, default: 0; 
$TIMEOUT_PARAM sets the response timeout, default: 2000
$INITIAL_BAUD_RATE sets a initial baud rate e.g. for devices with modem configuration, default: 300
$DEVICE_ADDRESS is mostly needed for devices with RS485, default: empty
$FIXED_BAUD_RATE activates fixed baud rate, default: deactivated
$REQUEST_START_CHARACTER Used for manufacture specific request messages
$READ_STANDARD Reads the standard message and the manufacture specific message. Only if $REQUEST_START_CHARACTER is set
"""
        private const val SETTINGS_SYNTAX = "Synopsis: " + SETTINGS
        private const val CHANNEL_ADDRESS_SYNTAX = "Synopsis: <data_set_id>"
        private const val DEVICE_SCAN_SETTINGS_SYNTAX = "Synopsis: <serial_port> " + SETTINGS
    }
}
