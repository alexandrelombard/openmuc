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

import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.data.*
import org.openmuc.framework.driver.spi.*
import org.openmuc.jmbus.*
import org.openmuc.jmbus.DataRecord.DataValueType
import org.openmuc.jrxtx.SerialPortTimeoutException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.text.MessageFormat
import java.util.*

class DriverConnection(
    private val connectionInterface: ConnectionInterface,
    private val mBusAddress: Int,
    private val secondaryAddress: SecondaryAddress?,
    private val delay: Int
) : Connection {
    private var resetApplication = false
    private var resetLink = false
    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun scanForChannels(settings: String): List<ChannelScanInfo> {
        val scanDelay = 50 + delay
        synchronized(connectionInterface) {
            val channelScanInfo: MutableList<ChannelScanInfo> = ArrayList()
            try {
                val mBusConnection = connectionInterface.mBusConnection
                if (secondaryAddress != null) {
                    mBusConnection.selectComponent(secondaryAddress)
                } else {
                    mBusConnection.linkReset(mBusAddress)
                    sleep(delay.toLong())
                    mBusConnection.resetReadout(mBusAddress)
                }
                var variableDataStructure: VariableDataStructure
                do {
                    sleep(scanDelay.toLong())
                    variableDataStructure = mBusConnection.read(mBusAddress)
                    val dataRecords = variableDataStructure.dataRecords
                    for (dataRecord in dataRecords) {
                        fillDataRecordInChannelScanInfo(channelScanInfo, dataRecord)
                    }
                } while (variableDataStructure.moreRecordsFollow())
            } catch (e: IOException) {
                throw ConnectionException(e)
            }
            return channelScanInfo
        }
    }

    private fun fillDataRecordInChannelScanInfo(
        channelScanInfo: MutableList<ChannelScanInfo>,
        dataRecord: DataRecord
    ) {
        val vib = Helper.bytesToHex(dataRecord.vib)
        val dib = Helper.bytesToHex(dataRecord.dib)
        val valueType: ValueType
        val valueLength: Int?
        when (dataRecord.dataValueType) {
            DataValueType.STRING -> {
                valueType = ValueType.STRING
                valueLength = 25
            }

            DataValueType.LONG -> {
                valueType = if (dataRecord.multiplierExponent == 0) {
                    ValueType.LONG
                } else {
                    ValueType.DOUBLE
                }
                valueLength = null
            }

            DataValueType.DOUBLE, DataValueType.DATE -> {
                valueType = ValueType.DOUBLE
                valueLength = null
            }

            DataValueType.BCD -> {
                valueType = if (dataRecord.multiplierExponent == 0) {
                    ValueType.DOUBLE
                } else {
                    ValueType.LONG
                }
                valueLength = null
            }

            DataValueType.NONE -> {
                valueType = ValueType.BYTE_ARRAY
                valueLength = 100
            }

            else -> {
                valueType = ValueType.BYTE_ARRAY
                valueLength = 100
            }
        }
        var unit: String? = ""
        if (dataRecord.unit != null) {
            unit = dataRecord.unit.unit
        }
        channelScanInfo.add(
            ChannelScanInfo(
                "$dib:$vib", getDescription(dataRecord), valueType, valueLength!!,
                true, true, "", unit!!
            )
        )
    }

    private fun getDescription(dataRecord: DataRecord): String {
        val dataValueType = dataRecord.dataValueType
        val scaledDataValue = dataRecord.scaledDataValue
        val description = dataRecord.description
        val functionField = dataRecord.functionField
        val tariff = dataRecord.tariff
        val subunit = dataRecord.subunit
        val userDefinedDescription = dataRecord.userDefinedDescription
        val storageNumber = dataRecord.storageNumber
        val multiplierExponent = dataRecord.multiplierExponent
        val dataValue = dataRecord.dataValue
        val builder = StringBuilder().append("Descr:").append(description)
        if (description == DataRecord.Description.USER_DEFINED) {
            builder.append(':').append(userDefinedDescription)
        }
        builder.append(";Function:").append(functionField)
        if (storageNumber > 0) {
            builder.append(";Storage:").append(storageNumber)
        }
        if (tariff > 0) {
            builder.append(";Tariff:").append(tariff)
        }
        if (subunit > 0) {
            builder.append(";Subunit:").append(subunit.toInt())
        }
        val valuePlacHolder = ";Value:"
        val scaledValueString = ";ScaledValue:"
        when (dataValueType) {
            DataValueType.DATE, DataValueType.STRING -> builder.append(valuePlacHolder).append(dataValue.toString())
            DataValueType.DOUBLE -> builder.append(scaledValueString).append(scaledDataValue)
            DataValueType.LONG -> if (multiplierExponent == 0) {
                builder.append(valuePlacHolder).append(dataValue)
            } else {
                builder.append(scaledValueString).append(scaledDataValue)
            }

            DataValueType.BCD -> if (multiplierExponent == 0) {
                builder.append(valuePlacHolder).append(dataValue.toString())
            } else {
                builder.append(scaledValueString).append(scaledDataValue)
            }

            DataValueType.NONE -> builder.append(";value:NONE")
        }
        return builder.toString()
    }

    override fun disconnect() {
        synchronized(connectionInterface) {
            if (!connectionInterface.isOpen) {
                return
            }
            connectionInterface.decreaseConnectionCounter()
        }
    }

    @Throws(ConnectionException::class)
    override fun read(
        containers: List<ChannelRecordContainer>,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        synchronized(connectionInterface) {
            val dataRecords: MutableList<DataRecord> = ArrayList()
            if (!connectionInterface.isOpen) {
                throw ConnectionException(
                    "Connection " + connectionInterface.interfaceAddress + " is closed."
                )
            }
            val mBusConnection = connectionInterface.mBusConnection
            if (secondaryAddress != null) {
                try {
                    mBusConnection.selectComponent(secondaryAddress)
                    sleep(delay.toLong())
                } catch (e: IOException) {
                    for (container in containers) {
                        container.record = Record(Flag.DRIVER_ERROR_UNSPECIFIED)
                    }
                    connectionInterface.close()
                    logger.error(e.message)
                    throw ConnectionException(e)
                }
            }
            try {
                if (secondaryAddress == null) {
                    if (resetLink) {
                        mBusConnection.linkReset(mBusAddress)
                        sleep(delay.toLong())
                    }
                    if (resetApplication) {
                        mBusConnection.resetReadout(mBusAddress)
                        sleep(delay.toLong())
                    }
                }
                var variableDataStructure: VariableDataStructure? = null
                do {
                    variableDataStructure = mBusConnection.read(mBusAddress)
                    sleep(delay.toLong())
                    dataRecords.addAll(variableDataStructure.dataRecords)
                } while (variableDataStructure!!.moreRecordsFollow())
            } catch (e: IOException) {
                for (container in containers) {
                    container.record = Record(Flag.DRIVER_ERROR_UNSPECIFIED)
                }
                connectionInterface.close()
                logger.error(e.message)
                throw ConnectionException(e)
            }
            val timestamp = System.currentTimeMillis()
            val dibvibs = arrayOfNulls<String>(dataRecords.size)
            setDibVibs(dataRecords, dibvibs)
            val selectForReadoutSet = setRecords(containers, mBusConnection, timestamp, dataRecords, dibvibs)
            if (selectForReadoutSet) {
                try {
                    mBusConnection.resetReadout(mBusAddress)
                    sleep(delay.toLong())
                } catch (e: IOException) {
                    try {
                        mBusConnection.linkReset(mBusAddress)
                        sleep(delay.toLong())
                    } catch (e1: IOException) {
                        for (container in containers) {
                            container.record = Record(Flag.CONNECTION_EXCEPTION)
                        }
                        connectionInterface.close()
                        logger.error("{}\n{}", e.message, e1.message)
                        throw ConnectionException(e)
                    }
                }
            }
            return null
        }
    }

    private fun setDibVibs(dataRecords: List<DataRecord>, dibvibs: Array<String?>) {
        var i = 0
        for (dataRecord in dataRecords) {
            val dibHex = Helper.bytesToHex(dataRecord.dib)
            val vibHex = Helper.bytesToHex(dataRecord.vib)
            dibvibs[i++] = MessageFormat.format("{0}:{1}", dibHex, vibHex)
        }
    }

    @Throws(ConnectionException::class)
    private fun setRecords(
        containers: List<ChannelRecordContainer>, mBusConnection: MBusConnection, timestamp: Long,
        dataRecords: List<DataRecord>, dibvibs: Array<String?>
    ): Boolean {
        var selectForReadoutSet = false
        for (container in containers) {
            val channelAddress = container.channelAddress
            if (channelAddress.startsWith("X")) {
                val dibAndVib = channelAddress.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (dibAndVib.size != 2) {
                    container.record = Record(Flag.DRIVER_ERROR_CHANNEL_ADDRESS_SYNTAX_INVALID)
                }
                val dataRecordsToSelectForReadout: List<DataRecord> = ArrayList(1)
                selectForReadoutSet = true
                try {
                    mBusConnection.selectForReadout(mBusAddress, dataRecordsToSelectForReadout)
                    sleep(delay.toLong())
                } catch (e: SerialPortTimeoutException) {
                    container.record = Record(Flag.DRIVER_ERROR_TIMEOUT)
                    continue
                } catch (e: IOException) {
                    connectionInterface.close()
                    throw ConnectionException(e)
                }
                val variableDataStructure2 = try {
                    mBusConnection.read(mBusAddress)
                } catch (e1: SerialPortTimeoutException) {
                    container.record = Record(Flag.DRIVER_ERROR_TIMEOUT)
                    continue
                } catch (e1: IOException) {
                    connectionInterface.close()
                    throw ConnectionException(e1)
                }
                val dataRecord = variableDataStructure2.dataRecords[0]
                setContainersRecord(timestamp, container, dataRecord)
                continue
            }
            var j = 0
            for (dataRecord in dataRecords) {
                if (dibvibs[j++].equals(channelAddress, ignoreCase = true)) {
                    setContainersRecord(timestamp, container, dataRecord)
                    break
                }
            }
            if (container.record == null) {
                container.record = Record(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND)
            }
        }
        return selectForReadoutSet
    }

    private fun setContainersRecord(timestamp: Long, container: ChannelRecordContainer, dataRecord: DataRecord) {
        try {
            when (dataRecord.dataValueType) {
                DataValueType.DATE -> container.record =
                    Record(DoubleValue((dataRecord.dataValue as Date).time.toDouble()), timestamp)
                DataValueType.STRING -> container.record =
                    Record(StringValue((dataRecord.dataValue as String)), timestamp)
                DataValueType.DOUBLE -> container.record =
                    Record(DoubleValue(dataRecord.scaledDataValue), timestamp)
                DataValueType.LONG -> if (dataRecord.multiplierExponent == 0) {
                    container.record = Record(LongValue((dataRecord.dataValue as Long)), timestamp)
                } else {
                    container.record = Record(DoubleValue(dataRecord.scaledDataValue), timestamp)
                }
                DataValueType.BCD -> if (dataRecord.multiplierExponent == 0) {
                    container.record = Record(LongValue((dataRecord.dataValue as Bcd).toLong()), timestamp)
                } else {
                    container.record =
                        Record(
                            DoubleValue(
                                (dataRecord.dataValue as Bcd).toLong()
                                        * Math.pow(10.0, dataRecord.multiplierExponent.toDouble())), timestamp)
                }
                DataValueType.NONE -> {
                    container.record = Record(Flag.DRIVER_ERROR_CHANNEL_VALUE_TYPE_CONVERSION_EXCEPTION)
                    if (logger.isWarnEnabled) {
                        logger.warn(
                            "Received data record with <dib>:<vib> = {}  has value type NONE.",
                            container.channelAddress
                        )
                    }
                }
            }
        } catch (e: IllegalStateException) {
            container.record = Record(Flag.DRIVER_ERROR_CHANNEL_VALUE_TYPE_CONVERSION_EXCEPTION)
            logger.error(
                "Received data record with <dib>:<vib> = {} has wrong value type. ErrorMsg: {}",
                container.channelAddress, e.message
            )
        }
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer>, listener: RecordsReceivedListener?) {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer>, containerListHandle: Any?): Any? {
        throw UnsupportedOperationException()
    }

    fun setResetApplication(resetApplication: Boolean) {
        this.resetApplication = resetApplication
    }

    fun setResetLink(resetLink: Boolean) {
        this.resetLink = resetLink
    }

    @Throws(ConnectionException::class)
    private fun sleep(millisecond: Long) {
        if (millisecond > 0) {
            try {
                Thread.sleep(millisecond)
            } catch (e: InterruptedException) {
                throw ConnectionException(e)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriverConnection::class.java)
    }
}
