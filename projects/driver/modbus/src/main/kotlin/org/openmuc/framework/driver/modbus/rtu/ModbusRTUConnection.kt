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
package org.openmuc.framework.driver.modbus.rtu

import com.ghgande.j2mod.modbus.Modbus
import com.ghgande.j2mod.modbus.ModbusException
import com.ghgande.j2mod.modbus.ModbusIOException
import com.ghgande.j2mod.modbus.io.ModbusSerialTransaction
import com.ghgande.j2mod.modbus.net.SerialConnection
import com.ghgande.j2mod.modbus.util.SerialParameters
import gnu.io.SerialPort
import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.config.ScanException
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.Value
import org.openmuc.framework.driver.modbus.EDatatype
import org.openmuc.framework.driver.modbus.ModbusChannel
import org.openmuc.framework.driver.modbus.ModbusChannel.EAccess
import org.openmuc.framework.driver.modbus.ModbusConnection
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.framework.driver.spi.ChannelValueContainer
import org.openmuc.framework.driver.spi.ConnectionException
import org.openmuc.framework.driver.spi.RecordsReceivedListener
import org.slf4j.LoggerFactory

/**
 *
 * TODO
 *
 */
class ModbusRTUConnection(deviceAddress: String, settings: Array<String>, timoutMs: Int) : ModbusConnection() {
    private val connection: SerialConnection
    private var transaction: ModbusSerialTransaction? = null

    init {
        val params = setParameters(deviceAddress, settings)
        connection = SerialConnection(params)
        try {
            connect()
            // connection.setReceiveTimeout(timoutMs);
            ModbusSerialTransaction(connection).let {
                this.transaction = it
                it.setSerialConnection(connection)
            }
            setTransaction(transaction)
        } catch (e: Exception) {
            logger.error("Unable to connect to device $deviceAddress", e)
            throw ModbusConfigurationException("Wrong Modbus RTU configuration. Check configuration file")
        }
        logger.info("Modbus Device: $deviceAddress connected")
    }

    @Throws(ConnectionException::class)
    override fun connect() {
        if (!connection.isOpen) {
            try {
                connection.open()
            } catch (e: Exception) {
                throw ConnectionException(e)
            }
        }
    }

    override fun disconnect() {
        if (connection.isOpen) {
            connection.close()
        }
    }

    @Throws(ModbusConfigurationException::class)
    private fun setParameters(address: String?, settings: Array<String>): SerialParameters {
        val params = SerialParameters()
        params.portName = address
        try {
            setEncoding(params, settings[ENCODING])
            setBaudrate(params, settings[BAUDRATE])
            setDatabits(params, settings[DATABITS])
            setParity(params, settings[PARITY])
            setStopbits(params, settings[STOPBITS])
            setEcho(params, settings[ECHO])
            setFlowControlIn(params, settings[FLOWCONTROL_IN])
            setFlowControlOut(params, settings[FLOWCONTEOL_OUT])
        } catch (e: Exception) {
            logger.error("Unable to set all parameters for RTU connection", e)
            throw ModbusConfigurationException("Specify all settings parameter")
        }
        return params
    }

    @Throws(ModbusConfigurationException::class)
    private fun setFlowControlIn(params: SerialParameters, flowControlIn: String) {
        if (flowControlIn.equals("FLOWCONTROL_NONE", ignoreCase = true)) {
            params.flowControlIn = SerialPort.FLOWCONTROL_NONE
        } else if (flowControlIn.equals("FLOWCONTROL_RTSCTS_IN", ignoreCase = true)) {
            params.flowControlIn = SerialPort.FLOWCONTROL_RTSCTS_IN
        } else if (flowControlIn.equals("FLOWCONTROL_XONXOFF_IN", ignoreCase = true)) {
            params.flowControlIn = SerialPort.FLOWCONTROL_XONXOFF_IN
        } else {
            throw ModbusConfigurationException("Unknown flow control in setting. Check configuration file")
        }
    }

    @Throws(ModbusConfigurationException::class)
    private fun setFlowControlOut(params: SerialParameters, flowControlOut: String) {
        if (flowControlOut.equals("FLOWCONTROL_NONE", ignoreCase = true)) {
            params.flowControlOut = SerialPort.FLOWCONTROL_NONE
        } else if (flowControlOut.equals("FLOWCONTROL_RTSCTS_OUT", ignoreCase = true)) {
            params.flowControlOut = SerialPort.FLOWCONTROL_RTSCTS_OUT
        } else if (flowControlOut.equals("FLOWCONTROL_XONXOFF_OUT", ignoreCase = true)) {
            params.flowControlOut = SerialPort.FLOWCONTROL_XONXOFF_OUT
        } else {
            throw ModbusConfigurationException("Unknown flow control out setting. Check configuration file")
        }
    }

    @Throws(ModbusConfigurationException::class)
    private fun setEcho(params: SerialParameters, echo: String) {
        if (echo.equals(ECHO_TRUE, ignoreCase = true)) {
            params.isEcho = true
        } else if (echo.equals(ECHO_FALSE, ignoreCase = true)) {
            params.isEcho = false
        } else {
            throw ModbusConfigurationException("Unknown echo setting. Check configuration file")
        }
    }

    @Throws(ModbusConfigurationException::class)
    private fun setStopbits(params: SerialParameters, stopbits: String) {
        if (stopbits.equals("STOPBITS_1", ignoreCase = true)) {
            params.stopbits = SerialPort.STOPBITS_1
        } else if (stopbits.equals("STOPBITS_1_5", ignoreCase = true)) {
            params.stopbits = SerialPort.STOPBITS_1_5
        } else if (stopbits.equals("STOPBITS_2", ignoreCase = true)) {
            params.stopbits = SerialPort.STOPBITS_2
        } else {
            throw ModbusConfigurationException("Unknown stobit setting. Check configuration file")
        }
    }

    @Throws(ModbusConfigurationException::class)
    private fun setParity(params: SerialParameters, parity: String) {
        if (parity.equals("PARITY_EVEN", ignoreCase = true)) {
            params.parity = SerialPort.PARITY_EVEN
        } else if (parity.equals("PARITY_MARK", ignoreCase = true)) {
            params.parity = SerialPort.PARITY_MARK
        } else if (parity.equals("PARITY_NONE", ignoreCase = true)) {
            params.parity = SerialPort.PARITY_NONE
        } else if (parity.equals("PARITY_ODD", ignoreCase = true)) {
            params.parity = SerialPort.PARITY_ODD
        } else if (parity.equals("PARITY_SPACE", ignoreCase = true)) {
            params.parity = SerialPort.PARITY_SPACE
        } else {
            throw ModbusConfigurationException("Unknown parity setting. Check configuration file")
        }
    }

    @Throws(ModbusConfigurationException::class)
    private fun setDatabits(params: SerialParameters, databits: String) {
        if (databits.equals("DATABITS_5", ignoreCase = true)) {
            params.databits = SerialPort.DATABITS_5
        } else if (databits.equals("DATABITS_6", ignoreCase = true)) {
            params.databits = SerialPort.DATABITS_6
        } else if (databits.equals("DATABITS_7", ignoreCase = true)) {
            params.databits = SerialPort.DATABITS_7
        } else if (databits.equals("DATABITS_8", ignoreCase = true)) {
            params.databits = SerialPort.DATABITS_8
        } else {
            throw ModbusConfigurationException("Unknown databit setting. Check configuration file")
        }
    }

    private fun setBaudrate(params: SerialParameters, baudrate: String) {
        params.setBaudRate(baudrate)
    }

    @Throws(ModbusConfigurationException::class)
    private fun setEncoding(params: SerialParameters, encoding: String) {
        if (encoding.equals(SERIAL_ENCODING_RTU, ignoreCase = true)) {
            params.encoding = Modbus.SERIAL_ENCODING_RTU
        } else {
            throw ModbusConfigurationException("Unknown encoding setting. Check configuration file")
        }
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun read(
        containers: List<ChannelRecordContainer>,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        if (samplingGroup!!.isEmpty()) {
            for (container in containers) {
                val receiveTime = System.currentTimeMillis()
                val channel = getModbusChannel(container.channelAddress, EAccess.READ)
                var value: Value?
                try {
                    value = readChannel(channel)
                    if (logger.isTraceEnabled) {
                        printResponseValue(channel, value)
                    }
                    container.record = Record(value, receiveTime)
                } catch (e: ModbusIOException) {
                    logger.error("ModbusIOException while reading channel:" + channel.channelAddress, e)
                    disconnect()
                    throw ConnectionException("ModbusIOException")
                } catch (e: ModbusException) {
                    logger.error("ModbusException while reading channel: " + channel.channelAddress, e)
                    container.record = Record(Flag.DRIVER_ERROR_CHANNEL_NOT_ACCESSIBLE)
                } catch (e: Exception) {
                    // catch all possible exceptions and provide info about the channel
                    logger.error("Exception while reading channel: " + channel.channelAddress, e)
                    container.record = Record(Flag.UNKNOWN_ERROR)
                }
            }
        } else {
            readChannelGroupHighLevel(containers, containerListHandle, samplingGroup)
        }
        return null
    }

    private fun printResponseValue(channel: ModbusChannel, value: Value) {
        if (channel.datatype == EDatatype.BYTEARRAY) {
            val sb = StringBuilder()
            for (b in value.asByteArray()) {
                sb.append(String.format("%02x ", b))
            }
            logger.trace("Value of response: $sb")
        } else {
            logger.trace("Value of response: $value")
        }
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer>, containerListHandle: Any?): Any? {
        for (container in containers) {
            val channel = getModbusChannel(container.channelAddress, EAccess.WRITE)
            try {
                container.value.let {
                    if(it != null) {
                        writeChannel(channel, it)
                        container.flag = Flag.VALID
                    }
                }
            } catch (e: ModbusIOException) {
                logger.error("ModbusIOException while writing channel:" + channel.channelAddress, e)
                disconnect()
                throw ConnectionException("Try to solve issue with reconnect.")
            } catch (e: ModbusException) {
                logger.error("ModbusException while writing channel: " + channel.channelAddress, e)
                container.flag = Flag.DRIVER_ERROR_CHANNEL_NOT_ACCESSIBLE
            } catch (e: Exception) {
                logger.error("Exception while writing channel: " + channel.channelAddress, e)
                container.flag = Flag.UNKNOWN_ERROR
            }
        }
        return null
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer>, listener: RecordsReceivedListener?) {
        throw UnsupportedOperationException()
    }

    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ConnectionException::class
    )
    override fun scanForChannels(settings: String): List<ChannelScanInfo> {
        throw UnsupportedOperationException()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ModbusRTUConnection::class.java)
        private const val ENCODING = 1
        private const val BAUDRATE = 2
        private const val DATABITS = 3
        private const val PARITY = 4
        private const val STOPBITS = 5
        private const val ECHO = 6
        private const val FLOWCONTROL_IN = 7
        private const val FLOWCONTEOL_OUT = 8
        private const val SERIAL_ENCODING_RTU = "SERIAL_ENCODING_RTU"
        private const val ECHO_TRUE = "ECHO_TRUE"
        private const val ECHO_FALSE = "ECHO_FALSE"
    }
}
