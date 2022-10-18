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
package org.openmuc.framework.driver.mbus

import org.openmuc.framework.config.*
import org.openmuc.framework.driver.spi.*
import org.openmuc.jmbus.*
import org.openmuc.jrxtx.SerialPortTimeoutException
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InterruptedIOException
import java.util.*

@Component
class Driver : DriverService {
    private val interfaces: MutableMap<String, ConnectionInterface> = HashMap()
    private var strictConnectionTest = false
    var interruptScan = false
    override val info: DriverInfo
        get() = Companion.info

    @Throws(ArgumentSyntaxException::class, ScanException::class, ScanInterruptedException::class)
    override fun scanForDevices(settingsString: String?, listener: DriverDeviceScanListener?) {
        interruptScan = false
        val settings: Settings = Settings(settingsString, true)
        val mBusConnection: MBusConnection?
        if (!interfaces.containsKey(settings.scanConnectionAddress)) {
            mBusConnection = try {
                if (settings.host.isEmpty()) {
                    MBusConnection.newSerialBuilder(settings.scanConnectionAddress)
                        .setBaudrate(settings.baudRate)
                        .setTimeout(settings.timeout)
                        .build()
                } else {
                    MBusConnection.newTcpBuilder(settings.host, settings.port)
                        .setTimeout(settings.timeout)
                        .setConnectionTimeout(settings.connectionTimeout)
                        .build()
                }
            } catch (e: IOException) {
                throw ScanException(e)
            }
            if (logger.isTraceEnabled) {
                mBusConnection.setVerboseMessageListener(VerboseMessageListenerImpl())
            }
        } else {
            mBusConnection = interfaces[settings.scanConnectionAddress].getMBusConnection()
        }
        val connectionInterface = interfaces[settings.scanConnectionAddress]
        if (connectionInterface != null && connectionInterface.isOpen) {
            throw ScanException(
                "Device is already connected. Disable device which uses port "
                        + settings.scanConnectionAddress + ", before scan."
            )
        }
        try {
            if (settings.scanSecondary) {
                val secondaryAddressListenerImplementation = SecondaryAddressListenerImplementation(
                    listener, settings.scanConnectionAddress
                )
                mBusConnection!!.scan("ffffffff", secondaryAddressListenerImplementation, settings.delay.toLong())
            } else {
                scanPrimaryAddress(listener, settings, mBusConnection)
            }
        } catch (e: IOException) {
            logger.error("Failed to scan for devices.", e)
        } finally {
            mBusConnection!!.close()
        }
    }

    @Throws(ScanInterruptedException::class, ScanException::class)
    private fun scanPrimaryAddress(
        listener: DriverDeviceScanListener?,
        settings: Settings,
        mBusConnection: MBusConnection?
    ) {
        var dataStructure: VariableDataStructure? = null
        for (i in 0..250) {
            if (interruptScan) {
                throw ScanInterruptedException()
            }
            if (i % 5 == 0) {
                listener!!.scanProgressUpdate(i * 100 / 250)
            }
            logger.debug("scanning for meter with primary address {}", i)
            try {
                dataStructure = mBusConnection!!.read(i)
                sleep(settings.delay.toLong())
            } catch (e: InterruptedIOException) {
                logger.debug("No meter found on address {}", i)
                continue
            } catch (e: IOException) {
                throw ScanException(e)
            } catch (e: ConnectionException) {
                throw ScanException(e)
            }
            var description = ""
            if (dataStructure != null) {
                val secondaryAddress = dataStructure.secondaryAddress
                description = getScanDescription(secondaryAddress)
            }
            listener!!.deviceFound(DeviceScanInfo(settings.scanConnectionAddress + ':' + i, "", description))
            logger.debug("Meter found on address {}", i)
        }
    }

    override fun interruptDeviceScan() {
        interruptScan = true
    }

    @Throws(ArgumentSyntaxException::class, ConnectionException::class)
    override fun connect(deviceAddress: String?, settingsString: String?): Connection? {
        val deviceAddressTokens =
            deviceAddress!!.trim { it <= ' ' }.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var serialPortName = ""
        var isTCP = false
        var host = ""
        var port = 0
        val offset: Int
        if (deviceAddressTokens[0].equals(TCP, ignoreCase = true)) {
            host = deviceAddressTokens[1]
            port = try {
                deviceAddressTokens[2].toInt()
            } catch (e: NumberFormatException) {
                throw ArgumentSyntaxException("Could not parse port.")
            }
            isTCP = true
            offset = 2
        } else {
            if (deviceAddressTokens.size != 2) {
                throw ArgumentSyntaxException("The device address does not consist of two parameters.")
            }
            offset = 0
            serialPortName = deviceAddressTokens[0 + offset]
        }
        val mBusAddress: Int
        var secondaryAddress: SecondaryAddress? = null
        try {
            if (deviceAddressTokens[1 + offset].length == 16) {
                mBusAddress = 0xfd
                val saData = Helper.hexToBytes(deviceAddressTokens[1 + offset])
                secondaryAddress = SecondaryAddress.newFromLongHeader(saData, 0)
            } else {
                mBusAddress = Integer.decode(deviceAddressTokens[1 + offset])
            }
        } catch (e: Exception) {
            throw ArgumentSyntaxException(
                "Settings: mBusAddress (" + deviceAddressTokens[1 + offset]
                        + ") is not a number between 0 and 255 nor a 16 sign long hexadecimal secondary address"
            )
        }
        var connectionInterface: ConnectionInterface
        val settings: Settings = Settings(settingsString, false)
        synchronized(this) {
            synchronized(interfaces) {
                connectionInterface = setConnectionInterface(
                    deviceAddressTokens, serialPortName, isTCP, host, port,
                    offset, settings
                )
            }
            synchronized(connectionInterface) {
                if (strictConnectionTest) {
                    try {
                        testConnection(mBusAddress, secondaryAddress, connectionInterface, settings)
                    } catch (e: IOException) {
                        connectionInterface.close()
                        throw ConnectionException(e)
                    }
                }
                connectionInterface.increaseConnectionCounter()
            }
        }
        val driverCon = DriverConnection(
            connectionInterface, mBusAddress, secondaryAddress,
            settings.delay
        )
        driverCon.setResetLink(settings.resetLink)
        driverCon.setResetApplication(settings.resetApplication)
        return driverCon
    }

    @Throws(IOException::class, ConnectionException::class)
    private fun testConnection(
        mBusAddress: Int, secondaryAddress: SecondaryAddress?,
        connectionInterface: ConnectionInterface, settings: Settings
    ) {
        val mBusConnection = connectionInterface.mBusConnection
        val delay = 100 + settings.delay
        val usesSecondaryAddress = secondaryAddress != null
        if (usesSecondaryAddress || settings.resetLink) {
            linkReset(mBusAddress, secondaryAddress, connectionInterface, mBusConnection, delay)
        }
        if (usesSecondaryAddress) {
            resetReadout(mBusAddress, settings.resetApplication, mBusConnection, delay)
            mBusConnection!!.selectComponent(secondaryAddress)
            sleep(delay.toLong())
            mBusConnection.resetReadout(mBusAddress)
            sleep(delay.toLong())
        }
        mBusConnection!!.linkReset(mBusAddress)
        sleep(delay.toLong())
    }

    @Throws(ConnectionException::class)
    private fun setConnectionInterface(
        deviceAddressTokens: Array<String>, serialPortName: String,
        isTCP: Boolean, host: String, port: Int, offset: Int, settings: Settings
    ): ConnectionInterface {
        var connectionInterface: ConnectionInterface?
        connectionInterface = if (isTCP) {
            interfaces[host + port]
        } else {
            interfaces[serialPortName]
        }
        if (connectionInterface == null) {
            val mBusConnection = getMBusConnection(
                deviceAddressTokens, serialPortName, isTCP, host, port,
                offset, settings
            )
            if (logger.isTraceEnabled) {
                mBusConnection.setVerboseMessageListener(VerboseMessageListenerImpl())
            }
            connectionInterface = if (isTCP) {
                ConnectionInterface(mBusConnection, host, port, settings.delay, interfaces)
            } else {
                ConnectionInterface(
                    mBusConnection, serialPortName, settings.delay,
                    interfaces
                )
            }
        }
        return connectionInterface
    }

    @Throws(ConnectionException::class)
    private fun getMBusConnection(
        deviceAddressTokens: Array<String>, serialPortName: String, isTCP: Boolean,
        host: String, port: Int, offset: Int, settings: Settings
    ): MBusConnection {
        val connection: MBusConnection
        connection = try {
            if (isTCP) {
                MBusConnection.newTcpBuilder(host, port)
                    .setConnectionTimeout(settings.connectionTimeout)
                    .setTimeout(settings.timeout)
                    .build()
            } else {
                MBusConnection.newSerialBuilder(serialPortName)
                    .setBaudrate(settings.baudRate)
                    .setTimeout(settings.timeout)
                    .build()
            }
        } catch (e: IOException) {
            throw ConnectionException("Unable to bind local interface: " + deviceAddressTokens[0 + offset], e)
        }
        return connection
    }

    @Throws(IOException::class, ConnectionException::class)
    private fun resetReadout(mBusAddress: Int, resetApplication: Boolean, mBusConnection: MBusConnection?, delay: Int) {
        if (resetApplication) {
            mBusConnection!!.resetReadout(mBusAddress)
            sleep(delay.toLong())
        }
    }

    @Throws(IOException::class, ConnectionException::class)
    private fun linkReset(
        mBusAddress: Int, secondaryAddress: SecondaryAddress?,
        connectionInterface: ConnectionInterface, mBusConnection: MBusConnection?, delay: Int
    ) {
        try {
            mBusConnection!!.linkReset(mBusAddress)
            sleep(delay.toLong()) // for slow slaves
        } catch (e: SerialPortTimeoutException) {
            if (secondaryAddress == null) {
                serialPortTimeoutExceptionHandler(connectionInterface, e)
            }
        }
    }

    @Throws(ConnectionException::class)
    private fun serialPortTimeoutExceptionHandler(
        connectionInterface: ConnectionInterface,
        e: SerialPortTimeoutException
    ) {
        if (connectionInterface.deviceCounter == 0) {
            connectionInterface.close()
        }
        throw ConnectionException(e)
    }

    @Throws(ConnectionException::class)
    private fun sleep(millisec: Long) {
        if (millisec > 0) {
            try {
                Thread.sleep(millisec)
            } catch (e: InterruptedException) {
                throw ConnectionException(e)
            }
        }
    }

    internal inner class SecondaryAddressListenerImplementation(
        private val driverDeviceScanListener: DriverDeviceScanListener?,
        private val connectionAddress: String
    ) : SecondaryAddressListener {
        override fun newScanMessage(message: String) {
            // Do nothing
        }

        override fun newDeviceFound(secondaryAddress: SecondaryAddress) {
            driverDeviceScanListener!!.deviceFound(
                DeviceScanInfo(
                    connectionAddress + SEPERATOR + Helper.bytesToHex(secondaryAddress.asByteArray()), "",
                    getScanDescription(secondaryAddress)
                )
            )
        }
    }

    private fun getScanDescription(secondaryAddress: SecondaryAddress): String {
        return ("ManufactureId:" + secondaryAddress.manufacturerId + ";DeviceType:"
                + secondaryAddress.deviceType + ";DeviceID:" + secondaryAddress.deviceId + ";Version:"
                + secondaryAddress.version)
    }

    private inner class Settings private constructor(settings: String, scan: Boolean) {
        var scanConnectionAddress = ""
        var scanSecondary = false
        var resetLink = false
        var resetApplication = false
        var timeout = 2500
        var baudRate = 2400
        var host = ""
        var port = 0
        var connectionTimeout = 10000
        var delay = 0

        init {
            if (scan) {
                setScanOptions(settings)
            } else {
                parseDeviceSettings(settings)
            }
        }

        @Throws(ArgumentSyntaxException::class)
        private fun setScanOptions(settings: String?) {
            val message = "Less than one or more than five arguments in the settings are not allowed."
            if (settings == null || settings.isEmpty()) {
                throw ArgumentSyntaxException("Settings field is empty. $message")
            }
            val args = settings.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (settings.isEmpty() || args.size > 5) {
                throw ArgumentSyntaxException(message)
            }
            var i: Int
            if (args[0].equals(TCP, ignoreCase = true)) {
                host = args[1]
                parsePort(args)
                i = 3
            } else {
                scanConnectionAddress = args[0]
                i = 1
            }
            while (i < args.size) {
                if (args[i].equals(SECONDARY_ADDRESS_SCAN, ignoreCase = true)) {
                    scanSecondary = true
                } else if (args[i].matches("^[t,T][0-9]*")) {
                    val setting = args[i].substring(1)
                    timeout = parseInt(setting, "Timeout is not a parsable number.")
                } else if (args[i].matches("^[d,D][0-9]*")) {
                    val setting = args[i].substring(1)
                    delay = parseInt(setting, "Settings: Delay is not a parsable number.")
                } else {
                    baudRate = try {
                        args[i].toInt()
                    } catch (e: NumberFormatException) {
                        throw ArgumentSyntaxException("Argument " + (i + 1) + " is not an integer.")
                    }
                }
                ++i
            }
        }

        @Throws(ArgumentSyntaxException::class)
        private fun parsePort(args: Array<String>) {
            port = try {
                args[2].toInt()
            } catch (e: NumberFormatException) {
                throw ArgumentSyntaxException("Error parsing TCP port")
            }
        }

        @Throws(ArgumentSyntaxException::class)
        private fun parseDeviceSettings(settings: String) {
            if (!settings.isEmpty()) {
                val settingArray = settings.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (setting in settingArray) {
                    if (setting.matches("^[t,T][0-9]*")) {
                        setting = setting.substring(1)
                        timeout = parseInt(setting, "Settings: Timeout is not a parsable number.")
                    } else if (setting.matches("^[tc,TC][0-9]*")) {
                        setting = setting.substring(1)
                        connectionTimeout = parseInt(setting, "Settings: Connection timeout is not a parsable number.")
                    } else if (setting.matches("^[d,D][0-9]*")) {
                        setting = setting.substring(1)
                        delay = parseInt(setting, "Settings: Delay is not a parsable number.")
                    } else if (setting == LINK_RESET) {
                        resetLink = true
                    } else if (setting == APPLICATION_RESET) {
                        resetApplication = true
                    } else if (setting == STRICT_CONNECTION) {
                        strictConnectionTest = true
                    } else if (setting.matches("^[0-9]*")) {
                        baudRate = parseInt(setting, "Settings: Baudrate is not a parseable number.")
                    } else {
                        throw ArgumentSyntaxException("Settings: Unknown settings parameter. [$setting]")
                    }
                }
            }
        }

        @Throws(ArgumentSyntaxException::class)
        private fun parseInt(setting: String, errorMsg: String): Int {
            var ret = 0
            ret = try {
                setting.toInt()
            } catch (e: NumberFormatException) {
                throw ArgumentSyntaxException("$errorMsg [$setting]")
            }
            return ret
        }
    }

    private inner class VerboseMessageListenerImpl : VerboseMessageListener {
        override fun newVerboseMessage(debugMessage: VerboseMessage) {
            val msgDir = debugMessage.messageDirection.toString().lowercase(Locale.getDefault())
            val msgHex = Helper.bytesToHex(debugMessage.message)
            logger.trace("{} message: {}", msgDir, msgHex)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Driver::class.java)
        private const val ID = "mbus"
        private const val DESCRIPTION = "M-Bus (wired) is a protocol to read out meters."
        private const val DEVICE_ADDRESS = ("Synopsis: <serial_port>:<mbus_address> or tcp:<host_address>:<port>"
                + "Example for <serial_port>: /dev/ttyS0 (Unix), COM1 (Windows) The mbus_address can either be the primary"
                + " address or the secondary address")
        private const val SETTINGS =
            ("Synopsis: [<baud_rate>][:t<timeout>][:lr][:ar][:d<delay>][:tc<tcp_connection_timeout>][sc]"
                    + "The default baud rate is 2400. Default read timeout is 2500 ms. Delay is for slow devices who needs more time between every message, default is 0 ms."
                    + "Example: 9600:t5000. 'ar' means application reset and 'lr' link reset before readout. activate sc if strict device connection is needed.")
        private const val CHANNEL_ADDRESS =
            ("Synopsis: [X]<dib>:<vib> The DIB and VIB fields in hexadecimal form separated by a colon. "
                    + "If the channel address starts with an X then the specific data record will be selected for readout before reading it.")
        private const val DEVICE_SCAN_SETTINGS = ("Synopsis: <serial_port>[:<baud_rate>][:s][:t<scan_timeout>]"
                + "Examples for <serial_port>: /dev/ttyS0 (Unix), COM1 (Windows); 's' for secondary address scan.>")
        private val info = DriverInfo(
            ID, DESCRIPTION, DEVICE_ADDRESS, SETTINGS, CHANNEL_ADDRESS,
            DEVICE_SCAN_SETTINGS
        )
        private const val SECONDARY_ADDRESS_SCAN = "s"
        private const val APPLICATION_RESET = "ar"
        private const val LINK_RESET = "lr"
        private const val STRICT_CONNECTION = "sc"
        private const val SEPERATOR = ":"
        private const val TCP = "tcp"
    }
}
