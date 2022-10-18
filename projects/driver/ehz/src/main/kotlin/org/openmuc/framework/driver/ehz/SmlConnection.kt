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
package org.openmuc.framework.driver.ehz

import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.data.DoubleValue
import org.openmuc.framework.data.StringValue
import org.openmuc.framework.data.Value
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.framework.driver.spi.ConnectionException
import org.openmuc.framework.driver.spi.RecordsReceivedListener
import org.openmuc.jrxtx.*
import org.openmuc.jsml.structures.*
import org.openmuc.jsml.structures.responses.SmlGetListRes
import org.openmuc.jsml.transport.SerialReceiver
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InterruptedIOException
import java.text.MessageFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SmlConnection(serialPortName: String) : GeneralConnection() {
    private var receiver: SerialReceiver? = null
    private var serialPort: SerialPort? = null

    // TODO serverId is never used..
    private var serverId: String? = null
    private val threadExecutor: ExecutorService
    private var listenerTask: ListenerTask? = null
    override fun disconnect() {
        if (listenerTask != null) {
            listenerTask!!.stopListening()
        }
        threadExecutor.shutdown()
        try {
            receiver?.close()
            if (!serialPort!!.isClosed) {
                serialPort.close()
            }
        } catch (e: IOException) {
            logger.warn("Error, while closing serial port.", e)
        }
    }

    @Throws(ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer?>?, listener: RecordsReceivedListener?) {
        logger.trace("start listening")
        listenerTask = ListenerTask(containers, listener)
        threadExecutor.execute(listenerTask)
    }

    private inner class ListenerTask(
        private val containers: List<ChannelRecordContainer?>?,
        private val listener: RecordsReceivedListener?
    ) : Runnable {
        private var stopListening = false
        override fun run() {
            while (!stopListening) {
                try {
                    val timestamp = System.currentTimeMillis()
                    val smlListEntries = retrieveSmlListEntries()
                    addEntriesToContainers(containers, timestamp, smlListEntries)
                    listener!!.newRecords(containers)
                } catch (e: InterruptedIOException) {
                } catch (e: IOException) {
                    listener!!.connectionInterrupted("ehz", this@SmlConnection)
                }
            }
        }

        fun stopListening() {
            stopListening = true
        }
    }

    @Throws(ConnectionException::class)
    override fun read(containers: List<ChannelRecordContainer?>?, timeout: Int) {
        logger.trace("reading channels")
        val timestamp = System.currentTimeMillis()
        val list: Array<SmlListEntry>?
        list = try {
            retrieveSmlListEntries()
        } catch (e: IOException) {
            logger.error("read failed", e)
            disconnect()
            throw ConnectionException(e)
        }
        addEntriesToContainers(containers, timestamp, list)
    }

    override fun scanForChannels(timeout: Int): List<ChannelScanInfo?> {
        val channelInfos: MutableList<ChannelScanInfo?> = LinkedList()
        logger.debug("scanning channels")
        try {
            val list = retrieveSmlListEntries()
            for (entry in list!!) {
                val channelInfo = convertEntryToScanInfo(entry)
                channelInfos.add(channelInfo)
            }
        } catch (e: IOException) {
            logger.error("scan for channels failed", e)
        }
        return channelInfos
    }

    override fun works(): Boolean {
        return try {
            retrieveSmlListEntries()
            true
        } catch (e: IOException) {
            false
        }
    }

    @Synchronized
    @Throws(IOException::class)
    private fun retrieveSmlListEntries(): Array<SmlListEntry>? {
        val smlFile = receiver!!.smlFile
        val messages = smlFile.messages
        for (message in messages) {
            val tag = message.messageBody.tag
            if (tag != EMessageBody.GET_LIST_RESPONSE) {
                continue
            }
            val getListResult = message.messageBody.getChoice<ASNObject>() as SmlGetListRes
            if (serverId == null) {
                serverId = convertBytesToHexString(getListResult.serverId.value)
            }
            return getListResult.valList.valListEntry
        }
        return null
    }

    init {
        try {
            serialPort = setupSerialPort(serialPortName)
            receiver = SerialReceiver(serialPort)
        } catch (e: IOException) {
            throw ConnectionException(e)
        }
        threadExecutor = Executors.newSingleThreadExecutor()
    }

    private class ValueContainer(val value: Value, val valueType: ValueType)
    companion object {
        private val logger = LoggerFactory.getLogger(GeneralConnection::class.java)
        private fun addEntriesToContainers(
            containers: List<ChannelRecordContainer?>?, timestamp: Long,
            smlEntries: Array<SmlListEntry>?
        ) {
            val values: MutableMap<String?, Value?> = LinkedHashMap()
            for (entry in smlEntries!!) {
                val address = convertBytesToHexString(entry.objName.value)
                val valueContainer = extractValueOf(entry)
                values[address] = valueContainer.value
                logger.trace("{} = {}", address, valueContainer.value)
            }
            GeneralConnection.Companion.handleChannelRecordContainer(containers, values, timestamp)
        }

        private fun convertEntryToScanInfo(entry: SmlListEntry): ChannelScanInfo {
            val channelAddress = convertBytesToHexString(entry.objName.value)
            val valueContainer = extractValueOf(entry)
            val value = valueContainer.value
            val description = MessageFormat.format("Current value: {0} {1}", value, entry.unit)
            val valueType = valueContainer.valueType
            var valueTypeLength: Int? = null
            if (value != null) {
                if (valueType === ValueType.STRING) {
                    val stringValue = value.asString()
                    valueTypeLength = stringValue.length
                } else if (valueType === ValueType.BYTE_ARRAY) {
                    val byteValue = value.asByteArray()
                    valueTypeLength = byteValue!!.size
                }
            }
            val readable = true
            val writable = false
            return ChannelScanInfo(channelAddress, description, valueType, valueTypeLength!!, readable, writable)
        }

        private fun convertBytesToHexString(data: ByteArray): String? {
            return bytesToHex(data)
        }

        const val HEXES = "0123456789ABCDEF"
        private fun bytesToHex(raw: ByteArray?): String? {
            if (raw == null) {
                return null
            }
            val hex = StringBuilder(2 * raw.size)
            for (b in raw) {
                hex.append(HEXES[b.toInt() and 0xF0 shr 4]).append(HEXES[b.toInt() and 0x0F])
            }
            return hex.toString()
        }

        private fun extractValueOf(entry: SmlListEntry): ValueContainer {
            var value = 0.0
            val valueType = ValueType.DOUBLE
            val obj = entry.value.choice
            value = if (obj.javaClass == Integer64::class.java) {
                val `val` = obj as Integer64
                `val`.getVal().toDouble()
            } else if (obj.javaClass == Integer32::class.java) {
                val `val` = obj as Integer32
                `val`.getVal().toDouble()
            } else if (obj.javaClass == Integer16::class.java) {
                val `val` = obj as Integer16
                `val`.getVal().toDouble()
            } else if (obj.javaClass == Integer8::class.java) {
                val `val` = obj as Integer8
                `val`.getVal().toDouble()
            } else if (obj.javaClass == Unsigned64::class.java) {
                val `val` = obj as Unsigned64
                `val`.getVal().toDouble()
            } else if (obj.javaClass == Unsigned32::class.java) {
                val `val` = obj as Unsigned32
                `val`.getVal().toDouble()
            } else if (obj.javaClass == Unsigned16::class.java) {
                val `val` = obj as Unsigned16
                `val`.getVal().toDouble()
            } else if (obj.javaClass == Unsigned8::class.java) {
                val `val` = obj as Unsigned8
                `val`.getVal().toDouble()
            } else if (obj.javaClass == OctetString::class.java) {
                val `val` = obj as OctetString
                return ValueContainer(
                    StringValue(
                        String(`val`.value)
                    ), ValueType.STRING
                )
            } else {
                return ValueContainer(
                    DoubleValue(
                        Double.NaN
                    ), valueType
                )
            }
            val scaler = entry.scaler.getVal()
            val scaledValue = value * Math.pow(10.0, scaler.toDouble())
            return ValueContainer(DoubleValue(scaledValue), valueType)
        }

        @Throws(IOException::class)
        private fun setupSerialPort(serialPortName: String): SerialPort {
            val serialPortBuilder = SerialPortBuilder.newBuilder(serialPortName)
            serialPortBuilder.setBaudRate(9600)
                .setDataBits(DataBits.DATABITS_8)
                .setStopBits(StopBits.STOPBITS_1)
                .setParity(Parity.NONE)
                .setFlowControl(FlowControl.RTS_CTS)
            return serialPortBuilder.build()
        }
    }
}
