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
package org.openmuc.framework.driver.modbus

import org.openmuc.framework.config.*
import org.openmuc.framework.driver.modbus.rtu.ModbusConfigurationException
import org.openmuc.framework.driver.modbus.rtu.ModbusRTUConnection
import org.openmuc.framework.driver.modbus.rtutcp.ModbusRTUTCPConnection
import org.openmuc.framework.driver.modbus.tcp.ModbusTCPConnection
import org.openmuc.framework.driver.spi.*
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

/**
 * Main class of the modbus driver.
 */
@Component
class ModbusDriver : DriverService {
    // FIXME auto generate settings string from class to avoid inconsistency

    // FIXME OpenMUC passes only the connection settings to the driver. Driver is unable to access the
    // samplingTimeout specified in channels.xml. As workaround the timeout is added to the device settings for the
    // modbus driver. timeoutInMs used for:
    // TCP: m_Socket.setSoTimeout(m_Timeout);
    // RTU: m_SerialPort.enableReceiveTimeout(ms);
    override val info: DriverInfo
        get() {
            val ID = "modbus"
            val DESCRIPTION =
                "Driver to communicate with devices via Modbus protocol. The driver supports TCP, RTU and RTU over TCP."
            val TCP_ADDRESS = "  TCP: <ip>[:<port>] (e.g. 192.168.30.103:502)"
            val RTUTCP_ADDRESS = "  RTUTCP: <ip>[:<port>] (e.g. 192.168.30.103:502)"
            val RTU_ADDRESS = "  RTU: <serial port> (e.g. /dev/ttyS0)"
            val DEVICE_ADDRESS = """
                 The device address dependes on the selected type: 
                 $TCP_ADDRESS
                 $RTUTCP_ADDRESS
                 $RTU_ADDRESS
                 """.trimIndent()

            // FIXME auto generate settings string from class to avoid inconsistency

            // FIXME OpenMUC passes only the connection settings to the driver. Driver is unable to access the
            // samplingTimeout specified in channels.xml. As workaround the timeout is added to the device settings for the
            // modbus driver. timeoutInMs used for:
            // TCP: m_Socket.setSoTimeout(m_Timeout);
            // RTU: m_SerialPort.enableReceiveTimeout(ms);
            val TCP_SETTINGS = "  TCP[:timeout=<timoutInMs>] (e.g. TCP or TCP:timeout=3000)"
            val RTUTCP_SETTINGS = "  RTUTCP[:timeout=<timoutInMs>] "
            val RTU_SETTINGS =
                "  RTU:<ENCODING>:<BAUDRATE>:<DATABITS>:<PARITY>:<STOPBITS>:<ECHO>:<FLOWCONTROL_IN>:<FLOWCONTEOL_OUT>[:timeout=<timoutInMs>]"
            val DEVICE_SETTINGS = """
                 Device settings depend on selected type: 
                 $TCP_SETTINGS
                 $RTUTCP_SETTINGS
                 $RTU_SETTINGS
                 """.trimIndent()
            val CHANNEL_ADDRESS = "<UnitId>:<PrimaryTable>:<Address>:<Datatyp>"
            val DEVICE_SCAN_SETTINGS = "Device scan is not supported."
            return DriverInfo(ID, DESCRIPTION, DEVICE_ADDRESS, DEVICE_SETTINGS, CHANNEL_ADDRESS, DEVICE_SCAN_SETTINGS)
        }

    @Throws(ConnectionException::class)
    override fun connect(deviceAddress: String?, settings: String?): Connection? {
        val connection: ModbusConnection

        // TODO consider retries in sampling timeout (e.g. one time 12000 ms or three times 4000 ms)
        // FIXME quite inconvenient/complex to get the timeout from config, since the driver doesn't know the device id!
        connection = if (settings == "") {
            throw ConnectionException("no device settings found in config. Please specify settings.")
        } else {
            val settingsArray = settings!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val mode = settingsArray[0]
            val timeoutMs = getTimeoutFromSettings(settingsArray)
            if (mode.equals("RTU", ignoreCase = true)) {
                try {
                    ModbusRTUConnection(deviceAddress, settingsArray, timeoutMs)
                } catch (e: ModbusConfigurationException) {
                    logger.error("Unable to create ModbusRTUConnection", e)
                    throw ConnectionException()
                }
            } else if (mode.equals("TCP", ignoreCase = true)) {
                ModbusTCPConnection(deviceAddress, timeoutMs)
            } else if (mode.equals("RTUTCP", ignoreCase = true)) {
                ModbusRTUTCPConnection(deviceAddress, timeoutMs)
            } else {
                throw ConnectionException("Unknown Mode. Use RTU, TCP or RTUTCP.")
            }
        }
        return connection
    }

    // FIXME 1: better timeout handling and general settings parsing for drivers
    // FIXME 2: this should be the max timeout. the duration of each read channel should be subtracted from the max
    // timeout
    private fun getTimeoutFromSettings(settingsArray: Array<String>): Int {
        var timeoutMs = DEFAULT_TIMEOUT_MS
        try {
            for (setting in settingsArray) {
                if (setting.startsWith("timeout")) {
                    val timeoutParam = setting.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    timeoutMs = validateTimeout(timeoutParam)
                }
            }
        } catch (e: Exception) {
            logger.warn(
                "Unable to parse timeout from settings. Using default timeout of " + DEFAULT_TIMEOUT_MS + " ms."
            )
        }
        logger.info("Set sampling timeout to $timeoutMs ms.")
        return timeoutMs
    }

    private fun validateTimeout(timeoutParam: Array<String>): Int {
        val timeoutMs = Integer.valueOf(timeoutParam[1]).toInt()
        require(timeoutMs > 0) { "Invalid SamplingTimeout is smaller or equal 0." }
        return timeoutMs
    }

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

    companion object {
        private val logger = LoggerFactory.getLogger(ModbusDriver::class.java)
        private const val DEFAULT_TIMEOUT_MS = 3000
    }
}
