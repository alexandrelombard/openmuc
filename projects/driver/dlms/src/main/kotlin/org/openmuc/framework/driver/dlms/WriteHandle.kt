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
import org.openmuc.framework.data.ByteArrayValue
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.StringValue
import org.openmuc.framework.data.Value
import org.openmuc.framework.driver.dlms.settings.ChannelAddress
import org.openmuc.framework.driver.spi.ChannelValueContainer
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import org.openmuc.framework.driver.spi.ConnectionException
import org.openmuc.jdlms.AccessResultCode
import org.openmuc.jdlms.DlmsConnection
import org.openmuc.jdlms.SetParameter
import org.openmuc.jdlms.datatypes.CosemDate
import org.openmuc.jdlms.datatypes.CosemDateTime
import org.openmuc.jdlms.datatypes.CosemDateTime.ClockStatus
import org.openmuc.jdlms.datatypes.CosemTime
import org.openmuc.jdlms.datatypes.DataObject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.MessageFormat
import java.util.*

internal class WriteHandle(private val dlmsConnection: DlmsConnection?) {
    @Throws(ConnectionException::class, UnsupportedOperationException::class)
    fun write(containers: List<ChannelValueContainer?>?) {
        multiSet(ArrayList(containers))
    }

    @Throws(ConnectionException::class)
    private fun multiSet(writeList: List<ChannelValueContainer?>) {
        val resultCodes = callSet(writeList)
        val iterResult = resultCodes.iterator()
        val iterWriteList = writeList.iterator()
        while (iterResult.hasNext() && iterWriteList.hasNext()) {
            val valueContainer = iterWriteList.next()
            val resCode = iterResult.next()
            val flag = convertToFlag(resCode)
            valueContainer!!.flag = flag
        }
    }

    @Throws(ConnectionException::class)
    private fun callSet(writeList: List<ChannelValueContainer?>): List<AccessResultCode> {
        val setParams = createSetParamsFor(writeList)
        var resultCodes: List<AccessResultCode>? = null
        try {
            resultCodes = dlmsConnection!!.set(setParams)
        } catch (ex: IOException) {
            handleIoException(writeList, ex)
        }
        if (resultCodes == null) {
            throw ConnectionException("Did not get any result after xDLMS SET was called.")
        }
        return resultCodes
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WriteHandle::class.java)
        @Throws(ConnectionException::class)
        private fun createSetParamsFor(writeList: List<ChannelValueContainer?>): List<SetParameter> {
            val setParams: MutableList<SetParameter> = ArrayList(writeList.size)
            for (channelContainer in writeList) {
                try {
                    val channelAddress = ChannelAddress(
                        channelContainer!!.channelAddress
                    )
                    val type = channelAddress.type
                    if (type == null) {
                        val msg = MessageFormat.format(
                            "Can not set attribute with address {0} where the type is unknown.", channelAddress
                        )
                        throw ConnectionException(msg)
                    }
                    val newValue = createDoFor(channelContainer, type)
                    val address = channelAddress.attributeAddress
                    val setParameter = SetParameter(address, newValue)
                    setParams.add(setParameter)
                } catch (e: ArgumentSyntaxException) {
                    throw ConnectionException(e)
                }
            }
            return setParams
        }

        private fun convertToFlag(setResult: AccessResultCode?): Flag {

            // should not occur
            return if (setResult == null) {
                Flag.UNKNOWN_ERROR
            } else when (setResult) {
                AccessResultCode.HARDWARE_FAULT -> Flag.UNKNOWN_ERROR
                AccessResultCode.OBJECT_UNDEFINED -> Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND
                AccessResultCode.OBJECT_UNAVAILABLE, AccessResultCode.READ_WRITE_DENIED, AccessResultCode.SCOPE_OF_ACCESS_VIOLATED -> Flag.DRIVER_ERROR_CHANNEL_NOT_ACCESSIBLE
                AccessResultCode.SUCCESS -> Flag.VALID
                AccessResultCode.TEMPORARY_FAILURE -> Flag.DRIVER_ERROR_CHANNEL_TEMPORARILY_NOT_ACCESSIBLE
                AccessResultCode.OBJECT_CLASS_INCONSISTENT, AccessResultCode.TYPE_UNMATCHED -> Flag.DRIVER_ERROR_CHANNEL_VALUE_TYPE_CONVERSION_EXCEPTION
                AccessResultCode.DATA_BLOCK_NUMBER_INVALID, AccessResultCode.DATA_BLOCK_UNAVAILABLE, AccessResultCode.LONG_GET_ABORTED, AccessResultCode.LONG_SET_ABORTED, AccessResultCode.NO_LONG_GET_IN_PROGRESS, AccessResultCode.NO_LONG_SET_IN_PROGRESS, AccessResultCode.OTHER_REASON -> Flag.DRIVER_THREW_UNKNOWN_EXCEPTION
                else -> Flag.DRIVER_THREW_UNKNOWN_EXCEPTION
            }
        }

        @Throws(ConnectionException::class)
        private fun handleIoException(containers: List<ChannelValueContainer?>, ex: IOException) {
            logger.error("Faild to write to device.", ex)
            for (c in containers) {
                c!!.flag = Flag.COMM_DEVICE_NOT_CONNECTED
            }
            throw ConnectionException(ex.message)
        }

        @Throws(UnsupportedOperationException::class)
        private fun createDoFor(channelValueContainer: ChannelValueContainer?, type: DataObject.Type): DataObject? {
            val flag = channelValueContainer!!.flag
            if (flag !== Flag.VALID) {
                return null
            }
            val value = channelValueContainer.value
            return when (type) {
                DataObject.Type.BCD -> DataObject.newBcdData(value!!.asByte())
                DataObject.Type.BOOLEAN -> DataObject.newBoolData(value!!.asBoolean())
                DataObject.Type.DOUBLE_LONG -> DataObject.newInteger32Data(value!!.asInt())
                DataObject.Type.DOUBLE_LONG_UNSIGNED -> DataObject.newUInteger32Data(value!!.asLong()) // TODO: not safe!
                DataObject.Type.ENUMERATE -> DataObject.newEnumerateData(value!!.asInt())
                DataObject.Type.FLOAT32 -> DataObject.newFloat32Data(value!!.asFloat())
                DataObject.Type.FLOAT64 -> DataObject.newFloat64Data(value!!.asDouble())
                DataObject.Type.INTEGER -> DataObject.newInteger8Data(value!!.asByte())
                DataObject.Type.LONG64 -> DataObject.newInteger64Data(value!!.asLong())
                DataObject.Type.LONG64_UNSIGNED -> DataObject.newUInteger64Data(value!!.asLong()) // TODO: is not unsigned
                DataObject.Type.LONG_INTEGER -> DataObject.newInteger16Data(value!!.asShort())
                DataObject.Type.LONG_UNSIGNED -> DataObject.newUInteger16Data(value!!.asInt()) // TODO: not safe!
                DataObject.Type.NULL_DATA -> DataObject.newNullData()
                DataObject.Type.OCTET_STRING -> DataObject.newOctetStringData(value!!.asByteArray())
                DataObject.Type.UNSIGNED -> DataObject.newUInteger8Data(value!!.asShort()) // TODO: not safe!
                DataObject.Type.UTF8_STRING -> {
                    val byteArrayValue = byteArrayValueOf(value)
                    DataObject.newUtf8StringData(byteArrayValue)
                }

                DataObject.Type.VISIBLE_STRING -> {
                    byteArrayValue = byteArrayValueOf(value)
                    DataObject.newVisibleStringData(byteArrayValue)
                }

                DataObject.Type.DATE -> {
                    val calendar = getCalendar(value!!.asLong())
                    DataObject.newDateData(
                        CosemDate(
                            calendar[Calendar.YEAR], calendar[Calendar.MONTH],
                            calendar[Calendar.DAY_OF_MONTH]
                        )
                    )
                }

                DataObject.Type.DATE_TIME -> {
                    calendar = getCalendar(value!!.asLong())
                    DataObject.newDateTimeData(
                        CosemDateTime(
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH),
                            calendar.get(Calendar.DAY_OF_WEEK), calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND),
                            calendar.get(Calendar.MILLISECOND) / 10, 0x8000, ClockStatus.INVALID_CLOCK_STATUS
                        )
                    )
                }

                DataObject.Type.TIME -> {
                    calendar = getCalendar(value!!.asLong())
                    DataObject.newTimeData(
                        CosemTime(
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND)
                        )
                    )
                }

                DataObject.Type.ARRAY, DataObject.Type.BIT_STRING, DataObject.Type.COMPACT_ARRAY, DataObject.Type.DONT_CARE, DataObject.Type.STRUCTURE -> {
                    val message = MessageFormat.format("DateType {0} not supported, yet.", type.toString())
                    throw UnsupportedOperationException(message)
                }

                else -> {
                    val message = MessageFormat.format("DateType {0} not supported, yet.", type.toString())
                    throw UnsupportedOperationException(message)
                }
            }
        }

        private fun byteArrayValueOf(value: Value?): ByteArray? {
            return (value as? StringValue)?.asString()?.toByteArray(StandardCharsets.UTF_8)
                ?: if (value is ByteArrayValue) {
                    value.asByteArray()
                } else {
                    ByteArray(0)
                }
        }

        private fun getCalendar(timestampInMilisec: Long): Calendar {
            val calendar = GregorianCalendar()
            calendar.timeInMillis = timestampInMilisec
            return calendar
        }
    }
}
