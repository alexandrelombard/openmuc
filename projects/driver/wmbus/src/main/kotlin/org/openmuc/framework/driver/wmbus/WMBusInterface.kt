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
import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.data.*
import org.openmuc.framework.driver.spi.*
import org.openmuc.framework.driver.wmbus.WMBusInterface
import org.openmuc.jmbus.*
import org.openmuc.jmbus.DataRecord.DataValueType
import org.openmuc.jmbus.wireless.WMBusConnection
import org.openmuc.jmbus.wireless.WMBusConnection.*
import org.openmuc.jmbus.wireless.WMBusListener
import org.openmuc.jmbus.wireless.WMBusMessage
import org.openmuc.jmbus.wireless.WMBusMode
import org.slf4j.LoggerFactory
import java.io.IOException
import java.text.MessageFormat
import java.util.*

//import javax.xml.bind.DatatypeConverter;
/**
 * Class representing an MBus Connection.<br></br>
 * This class will bind to the local com-interface.<br></br>
 *
 */
class WMBusInterface {
    private val connectionsBySecondaryAddress = HashMap<SecondaryAddress?, DriverConnection>()
    var listener: RecordsReceivedListener? = null
    private var con: WMBusConnection? = null
    private val connectionName: String
    private val transceiverString: String
    private val modeString: String

    inner class Receiver : WMBusListener {
        override fun discardedBytes(bytes: ByteArray) {
            if (logger.isDebugEnabled) {
                val bytesAsHexStr = Hex.encodeHexString(bytes)
                logger.debug("received bytes that will be discarded: {}", bytesAsHexStr)
            }
        }

        override fun newMessage(message: WMBusMessage) {
            try {
                message.variableDataResponse.decode()
            } catch (e: DecodingException) {
                if (logger.isDebugEnabled) {
                    logger.debug("Unable to decode header of received message: $message", e)
                }
                return
            }
            synchronized(this) {
                val connection = connectionsBySecondaryAddress[message.secondaryAddress]
                if (connection == null) {
                    if (logger.isTraceEnabled) {
                        logger.trace(
                            "WMBus: connection is null, from device: {} with HashCode: {}",
                            message.secondaryAddress.deviceId.toString(), message.secondaryAddress
                        )
                    }
                    return
                }
                val channelContainers = connection.containersToListenFor
                if (channelContainers!!.isEmpty()) {
                    if (logger.isTraceEnabled) {
                        logger.trace(
                            "WMBus: channelContainers.size == 0, from device: "
                                    + message.secondaryAddress.deviceId.toString()
                        )
                    }
                    return
                }
                val variableDataStructure = message.variableDataResponse
                try {
                    variableDataStructure.decode()
                } catch (e: DecodingException) {
                    if (logger.isWarnEnabled) {
                        logger.warn(
                            "Unable to decode header of variable data response or received message: {}",
                            message, e
                        )
                    }
                    return
                }
                val dataRecords = message.variableDataResponse.dataRecords
                val dibvibs = arrayOfNulls<String>(dataRecords.size)
                var i = 0
                for (dataRecord in dataRecords) {
                    val dibHexStr = Hex.encodeHexString(dataRecord.dib)
                    val vibHexStr = Hex.encodeHexString(dataRecord.vib)
                    dibvibs[i++] = MessageFormat.format("{0}:{1}", dibHexStr, vibHexStr)
                }
                val containersReceived: MutableList<ChannelRecordContainer?> = LinkedList()
                val timestamp = System.currentTimeMillis()
                for (container in channelContainers) {
                    i = 0
                    setRecords(dataRecords, dibvibs, i, containersReceived, timestamp, container)
                }
                listener!!.newRecords(containersReceived)
            }
        }

        private fun setRecords(
            dataRecords: List<DataRecord>, dibvibs: Array<String?>, i: Int,
            containersReceived: MutableList<ChannelRecordContainer?>, timestamp: Long,
            container: ChannelRecordContainer?
        ) {
            var i = i
            for (dataRecord in dataRecords) {
                if (dibvibs[i++].equals(container!!.channelAddress, ignoreCase = true)) {
                    var value: Value? = null
                    when (dataRecord.dataValueType) {
                        DataValueType.DATE -> {
                            value = DoubleValue((dataRecord.dataValue as Date).time.toDouble())
                            container.setRecord(Record(value, timestamp))
                        }

                        DataValueType.STRING -> {
                            value = StringValue((dataRecord.dataValue as String))
                            container.setRecord(Record(value, timestamp))
                        }

                        DataValueType.DOUBLE -> {
                            value = DoubleValue(dataRecord.scaledDataValue)
                            container.setRecord(Record(value, timestamp))
                        }

                        DataValueType.LONG -> if (dataRecord.multiplierExponent == 0) {
                            value = LongValue((dataRecord.dataValue as Long))
                            container.setRecord(Record(value, timestamp))
                        } else {
                            value = DoubleValue(dataRecord.scaledDataValue)
                            container.setRecord(Record(value, timestamp))
                        }

                        DataValueType.BCD -> if (dataRecord.multiplierExponent == 0) {
                            value = LongValue((dataRecord.dataValue as Bcd).toLong())
                            container.setRecord(Record(value, timestamp))
                        } else {
                            value = DoubleValue(
                                (dataRecord.dataValue as Bcd).toLong()
                                        * Math.pow(10.0, dataRecord.multiplierExponent.toDouble())
                            )
                            container.setRecord(Record(value, timestamp))
                        }

                        DataValueType.NONE -> {
                            logger.warn("Received data record with <dib>:<vib> = {} has value type NONE.", dibvibs[i])
                            continue
                        }
                    }
                    if (logger.isTraceEnabled) {
                        val channelId = container.channel!!.id
                        logger.trace("WMBus: Value from channel {} is: {}.", channelId, value)
                    }
                    containersReceived.add(container)
                    break
                }
            }
        }

        override fun stoppedListening(e: IOException) {
            this@WMBusInterface.stoppedListening()
        }
    }

    private constructor(serialPortName: String, transceiverString: String, modeString: String) {
        connectionName = serialPortName
        this.transceiverString = transceiverString
        this.modeString = modeString
        val mode = getWMBusModeFromString(modeString)
        val wmBusManufacturer = getWMBusManufactureFromString(transceiverString)
        con = try {
            WMBusSerialBuilder(wmBusManufacturer, Receiver(), serialPortName)
                .setMode(mode)
                .build()
        } catch (e: IOException) {
            throw ConnectionException("Failed to open serial interface", e)
        }
    }

    constructor(host: String, port: Int, transceiverString: String, modeString: String) {
        connectionName = "$host:$port"
        this.transceiverString = transceiverString
        this.modeString = modeString
        val mode = getWMBusModeFromString(modeString)
        val wmBusManufacturer = getWMBusManufactureFromString(transceiverString)
        con = try {
            WMBusTcpBuilder(wmBusManufacturer, Receiver(), host, port).setMode(mode)
                .build()
        } catch (e: IOException) {
            throw ConnectionException("Failed to open TCP interface", e)
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun getWMBusModeFromString(modeString: String): WMBusMode {
        val mode: WMBusMode
        mode = try {
            WMBusMode.valueOf(modeString.uppercase(Locale.getDefault()))
        } catch (e: IllegalArgumentException) {
            throw ArgumentSyntaxException(
                "The wireless M-Bus mode is not correctly specified in the device's parameters string. Should be S, T or C but is: "
                        + modeString
            )
        }
        return mode
    }

    fun connectionClosedIndication(secondaryAddress: SecondaryAddress?) {
        connectionsBySecondaryAddress.remove(secondaryAddress)
        if (connectionsBySecondaryAddress.size == 0) {
            close()
        }
    }

    fun close() {
        synchronized(interfaces) {
            try {
                con!!.close()
            } catch (e: IOException) {
                logger.warn("Failed to close connection properly", e)
            }
            interfaces.remove(connectionName)
        }
    }

    @Throws(ArgumentSyntaxException::class, DecoderException::class)
    fun connect(secondaryAddress: SecondaryAddress?, keyString: String?): Connection {
        val connection = DriverConnection(con, secondaryAddress, keyString, this)
        if (logger.isTraceEnabled) {
            logger.trace(
                "WMBus: connect device with ID {} and HashCode {}", secondaryAddress!!.deviceId,
                secondaryAddress
            )
        }
        connectionsBySecondaryAddress[secondaryAddress] = connection
        return connection
    }

    fun stoppedListening() {
        synchronized(interfaces) { interfaces.remove(connectionName) }
        synchronized(this) {
            for (connection in connectionsBySecondaryAddress.values) {
                listener!!.connectionInterrupted("wmbus", connection)
            }
            connectionsBySecondaryAddress.clear()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WMBusInterface::class.java)
        private val interfaces: MutableMap<String, WMBusInterface> = HashMap()
        @Throws(ConnectionException::class, ArgumentSyntaxException::class)
        fun getSerialInstance(serialPortName: String, transceiverString: String, modeString: String): WMBusInterface? {
            var wmBusInterface: WMBusInterface?
            synchronized(interfaces) {
                wmBusInterface = interfaces[serialPortName]
                if (wmBusInterface == null) {
                    wmBusInterface = WMBusInterface(serialPortName, transceiverString, modeString)
                    interfaces.put(serialPortName, wmBusInterface!!)
                } else {
                    if (wmBusInterface!!.modeString != modeString
                        || wmBusInterface!!.transceiverString != transceiverString
                    ) {
                        throw ConnectionException(
                            "Connections serial interface is already in use with a different transceiver or mode"
                        )
                    }
                }
            }
            return wmBusInterface
        }

        @Throws(ConnectionException::class, ArgumentSyntaxException::class)
        fun getTCPInstance(host: String, port: Int, transceiverString: String, modeString: String): WMBusInterface? {
            var wmBusInterface: WMBusInterface?
            val hostAndPort = "$host:$port"
            synchronized(interfaces) {
                wmBusInterface = interfaces[hostAndPort]
                if (wmBusInterface == null) {
                    wmBusInterface = WMBusInterface(host, port, transceiverString, modeString)
                    interfaces.put(hostAndPort, wmBusInterface!!)
                } else {
                    if (wmBusInterface!!.modeString != modeString
                        || wmBusInterface!!.transceiverString != transceiverString
                    ) {
                        throw ConnectionException(
                            "Connections TCP interface is already in use with a different transceiver or mode"
                        )
                    }
                }
            }
            return wmBusInterface
        }

        @Throws(ArgumentSyntaxException::class)
        private fun getWMBusManufactureFromString(transceiverString: String): WMBusManufacturer {
            return try {
                WMBusManufacturer.valueOf(transceiverString.uppercase(Locale.getDefault()))
            } catch (e: IllegalArgumentException) {
                throw ArgumentSyntaxException(
                    "The type of transceiver is not correctly specified in the device's parameters string. Should be amber, imst or rc but is: "
                            + transceiverString
                )
            }
        }
    }
}
