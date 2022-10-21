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
package org.openmuc.framework.driver.dlms

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.data.*
import org.openmuc.framework.driver.dlms.settings.ChannelAddress
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.framework.driver.spi.ChannelValueContainer
import org.openmuc.framework.driver.spi.ConnectionException
import org.openmuc.jdlms.AccessResultCode
import org.openmuc.jdlms.AttributeAddress
import org.openmuc.jdlms.DlmsConnection
import org.openmuc.jdlms.GetResult
import org.openmuc.jdlms.datatypes.DataObject
import org.slf4j.LoggerFactory
import java.io.IOException

internal class ReadHandle(private val dlmsConnection: DlmsConnection) {
    @Throws(ConnectionException::class)
    fun read(containers: List<ChannelRecordContainer>) {
        val readList: List<ChannelRecordContainer> = ArrayList(containers)
        try {
            callGet(dlmsConnection, readList)
        } catch (ex: IOException) {
            handleIoException(containers, ex)
        }
    }

    @Throws(ConnectionException::class)
    private fun createAttributeAddresFor(readList: List<ChannelRecordContainer>): List<AttributeAddress> {
        val getParams = ArrayList<AttributeAddress>(readList.size)
        for (recordContainer in readList) {
            try {
                val channelAddress = ChannelAddress(recordContainer.channelAddress)
                getParams.add(channelAddress.attributeAddress)
            } catch (e: ArgumentSyntaxException) {
                throw ConnectionException(e)
            }
        }
        return getParams
    }

    @Throws(IOException::class, ConnectionException::class)
    private fun callGet(dlmsConnection: DlmsConnection, readList: List<ChannelRecordContainer>) {
        val timestamp = System.currentTimeMillis()
        val getParams = createAttributeAddresFor(readList)
        val writeListIter = readList.iterator()
        val resIter: Iterator<GetResult> = this.dlmsConnection[getParams].iterator()
        while (writeListIter.hasNext() && resIter.hasNext()) {
            val channelContainer = writeListIter.next()
            val record = createRecordFor(timestamp, resIter.next())
            channelContainer.record = record
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReadHandle::class.java)
        @Throws(ConnectionException::class)
        private fun handleIoException(containers: List<ChannelRecordContainer>, ex: IOException) {
            logger.error("Failed to read from device.", ex)
            val timestamp = System.currentTimeMillis()
            for (c in containers) {
                c.record = Record(null, timestamp, Flag.COMM_DEVICE_NOT_CONNECTED)
            }
            throw ConnectionException(ex.message)
        }

        private fun createRecordFor(timestamp: Long, result: GetResult): Record {
            return if (result.resultCode == AccessResultCode.SUCCESS) {
                val resultFlag = Flag.VALID
                val resultValue = convertToValue(result.resultData)
                Record(resultValue, timestamp, resultFlag)
            } else {
                val resultFlag = convertStatusFlag(result.resultCode)
                Record(null, timestamp, resultFlag)
            }
        }

        private fun getType(dataObject: DataObject): ValueType? {
            val valueType: ValueType?
            val type = dataObject.type
            valueType = when (type) {
                DataObject.Type.BOOLEAN -> ValueType.BOOLEAN
                DataObject.Type.FLOAT32 -> ValueType.FLOAT
                DataObject.Type.FLOAT64 -> ValueType.DOUBLE
                DataObject.Type.BCD, DataObject.Type.INTEGER -> ValueType.BYTE
                DataObject.Type.LONG_INTEGER, DataObject.Type.UNSIGNED -> ValueType.SHORT
                DataObject.Type.ENUMERATE, DataObject.Type.DOUBLE_LONG, DataObject.Type.LONG_UNSIGNED -> ValueType.INTEGER
                DataObject.Type.DOUBLE_LONG_UNSIGNED, DataObject.Type.LONG64, DataObject.Type.LONG64_UNSIGNED -> ValueType.LONG
                DataObject.Type.OCTET_STRING -> ValueType.BYTE_ARRAY
                DataObject.Type.VISIBLE_STRING -> ValueType.STRING
                DataObject.Type.NULL_DATA -> null // TODO
                DataObject.Type.ARRAY, DataObject.Type.BIT_STRING, DataObject.Type.COMPACT_ARRAY, DataObject.Type.DONT_CARE, DataObject.Type.DATE, DataObject.Type.DATE_TIME, DataObject.Type.STRUCTURE, DataObject.Type.TIME -> null
                else -> null
            }
            return valueType
        }

        private fun convertStatusFlag(accessResultCode: AccessResultCode): Flag {
            return when (accessResultCode) {
                AccessResultCode.HARDWARE_FAULT -> Flag.DRIVER_ERROR_CHANNEL_NOT_ACCESSIBLE
                AccessResultCode.TEMPORARY_FAILURE -> Flag.DRIVER_ERROR_CHANNEL_TEMPORARILY_NOT_ACCESSIBLE
                AccessResultCode.READ_WRITE_DENIED -> Flag.DRIVER_ERROR_CHANNEL_NOT_ACCESSIBLE
                AccessResultCode.OBJECT_UNDEFINED -> Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND
                AccessResultCode.OBJECT_UNAVAILABLE -> Flag.DRIVER_ERROR_CHANNEL_NOT_ACCESSIBLE
                else -> Flag.UNKNOWN_ERROR
            }
        }

        private fun convertToValue(data: DataObject): Value {
            return if (data.isBoolean) {
                BooleanValue(data.getValue<Any>() as Boolean)
            } else if (data.isNumber) {
                val numberVal = data.getValue<Number>()
                DoubleValue(numberVal.toDouble())
            } else if (data.isByteArray) {
                ByteArrayValue((data.getValue<Any>() as ByteArray))
            } else if (data.isBitString) {
                StringValue(data.getValue<Any>().toString())
            } else if (data.isComplex) {
                // better solution?
                StringValue(data.toString())
            } else {
                StringValue(data.toString())
            }
        }
    }
}
