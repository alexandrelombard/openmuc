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
package org.openmuc.framework.datalogger.ascii

import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.Record.value
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.datalogger.ascii.exceptions.WrongCharacterException
import org.openmuc.framework.datalogger.ascii.exceptions.WrongScalingException
import org.openmuc.framework.datalogger.ascii.utils.Const
import org.openmuc.framework.datalogger.ascii.utils.IESDataFormatUtils
import org.openmuc.framework.datalogger.ascii.utils.LoggerUtils
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.slf4j.LoggerFactory
import java.io.*
import java.util.*

class LogFileWriter(private val directoryPath: String?, private val isFillUpFiles: Boolean) {
    private val sb = StringBuilder()
    private val sbValue = StringBuilder()
    private var actualFile: File? = null

    /**
     * Main logger writing controller.
     *
     * @param group
     * log interval container group
     * @param loggingInterval
     * logging interval
     * @param logTimeOffset
     * logging time offset
     * @param calendar
     * calendar of current time
     * @param logChannelList
     * logging channel list
     */
    fun log(
        group: LogIntervalContainerGroup?, loggingInterval: Int, logTimeOffset: Int, calendar: Calendar,
        logChannelList: Map<String?, LogChannel?>
    ) {
        val out = getStream(group, loggingInterval, logTimeOffset, calendar, logChannelList) ?: return
        val logRecordContainer = group.getList()

        // TODO match column with container id, so that they don't get mixed up
        if (isFillUpFiles) {
            fillUpFile(loggingInterval, logTimeOffset, calendar, logChannelList, logRecordContainer, out)
        }
        val logLine = getLoggingLine(logRecordContainer, logChannelList, calendar, false)
        out.print(logLine) // print because of println makes different newline char on different systems
        out.flush()
        out.close()
    }

    private fun fillUpFile(
        loggingInterval: Int, logTimeOffset: Int, calendar: Calendar,
        logChannelList: Map<String?, LogChannel?>, loggingRecords: MutableList<LoggingRecord?>?, out: PrintStream
    ) {
        val lastLoglineTimestamp: Long =
            AsciiLogger.Companion.getLastLoggedLineTimeStamp(loggingInterval, logTimeOffset)
        if (lastLoglineTimestamp != null && lastLoglineTimestamp > 0) {
            val diff = calendar.timeInMillis - lastLoglineTimestamp
            if (diff >= loggingInterval) {
                val errCalendar: Calendar = GregorianCalendar(Locale.getDefault())
                errCalendar.timeInMillis = lastLoglineTimestamp
                if (errCalendar[Calendar.DAY_OF_YEAR] == calendar[Calendar.DAY_OF_YEAR]
                    && errCalendar[Calendar.YEAR] == calendar[Calendar.YEAR]
                ) {
                    val numOfErrorLines = diff / loggingInterval
                    for (i in 1 until numOfErrorLines) {
                        errCalendar.timeInMillis = lastLoglineTimestamp + loggingInterval.toLong() * i
                        out.print(getLoggingLine(loggingRecords, logChannelList, errCalendar, true))
                    }
                }
            }
        }
    }

    private fun getLoggingLine(
        logRecordContainer: MutableList<LoggingRecord?>?, logChannelList: Map<String?, LogChannel?>,
        calendar: Calendar, isError32: Boolean
    ): String {
        sb.setLength(0)
        LoggerUtils.setLoggerTimestamps(sb, calendar)
        for (i in logRecordContainer!!.indices) {
            var size = Const.VALUE_SIZE_MINIMAL
            var left = true
            var record = logRecordContainer[i]!!.record
            val channelId = logRecordContainer[i]!!.channelId
            val logChannel = logChannelList[channelId]
            sbValue.setLength(0)
            if (record != null) {
                val recordValue = record.value
                var recordBackup: Record? = null
                if (isError32) {
                    recordBackup = logRecordContainer[i]!!.record
                    logRecordContainer[i] = LoggingRecord(channelId, Record(Flag.DATA_LOGGING_NOT_ACTIVE))
                }
                record = logRecordContainer[i]!!.record
                if (record.flag === Flag.VALID) {
                    if (recordValue == null) {
                        // write error flag
                        LoggerUtils.buildError(sbValue, Flag.CANNOT_WRITE_NULL_VALUE)
                        size = getDataTypeSize(logChannel, i)
                    } else {
                        val valueType = logChannel!!.valueType
                        when (valueType) {
                            ValueType.BOOLEAN -> sbValue.append(
                                recordValue.asShort().toInt()
                            ).toString()

                            ValueType.LONG -> {
                                sbValue.append(recordValue.asLong()).toString()
                                size = Const.VALUE_SIZE_LONG
                            }

                            ValueType.INTEGER -> {
                                sbValue.append(recordValue.asInt()).toString()
                                size = Const.VALUE_SIZE_INTEGER
                            }

                            ValueType.SHORT -> {
                                sbValue.append(recordValue.asShort().toInt()).toString()
                                size = Const.VALUE_SIZE_SHORT
                            }

                            ValueType.DOUBLE, ValueType.FLOAT -> {
                                size = Const.VALUE_SIZE_DOUBLE
                                try {
                                    IESDataFormatUtils.convertDoubleToStringWithMaxLength(
                                        sbValue, recordValue.asDouble(),
                                        size
                                    )
                                } catch (e: WrongScalingException) {
                                    LoggerUtils.buildError(sbValue, Flag.UNKNOWN_ERROR)
                                    logger.error(e.message + " ChannelId: " + channelId)
                                }
                            }

                            ValueType.BYTE_ARRAY -> {
                                left = false
                                size = checkMinimalValueSize(getDataTypeSize(logChannel, i))
                                val byteArray = recordValue.asByteArray()
                                if (byteArray!!.size > size) {
                                    LoggerUtils.buildError(sbValue, Flag.UNKNOWN_ERROR)
                                    logger.error(
                                        "The byte array is too big, length is ", byteArray.size,
                                        " but max. length allowed is ", size, ", ChannelId: ", channelId
                                    )
                                } else {
                                    sbValue.append(Const.HEXADECIMAL)
                                    LoggerUtils.byteArrayToHexString(sbValue, byteArray)
                                }
                            }

                            ValueType.STRING -> {
                                left = false
                                size = checkMinimalValueSize(getDataTypeSize(logChannel, i))
                                sbValue.append(recordValue.asString())
                                val valueLength = sbValue.length
                                try {
                                    checkStringValue(sbValue)
                                } catch (e: WrongCharacterException) {
                                    LoggerUtils.buildError(sbValue, Flag.UNKNOWN_ERROR)
                                    logger.error(e.message)
                                }
                                if (valueLength > size) {
                                    LoggerUtils.buildError(sbValue, Flag.UNKNOWN_ERROR)
                                    logger.error(
                                        "The string is too big, length is ", valueLength,
                                        " but max. length allowed is ", size, ", ChannelId: ", channelId
                                    )
                                }
                            }

                            ValueType.BYTE -> sbValue.append(String.format("0x%02x", recordValue.asByte()))
                            else -> throw RuntimeException("unsupported valueType")
                        }
                    }
                } else {
                    // write error flag
                    LoggerUtils.buildError(sbValue, record.flag)
                    size = checkMinimalValueSize(getDataTypeSize(logChannel, i))
                }
                if (isError32) {
                    logRecordContainer[i] = LoggingRecord(channelId, recordBackup!!)
                }
            } else {
                // got no data
                LoggerUtils.buildError(sbValue, Flag.UNKNOWN_ERROR)
                size = checkMinimalValueSize(getDataTypeSize(logChannel, i))
            }
            if (left) {
                LoggerUtils.addSpaces(sbValue.length, size, sb)
                sb.append(sbValue)
            } else {
                sb.append(sbValue)
                LoggerUtils.addSpaces(sbValue.length, size, sb)
            }
            if (LoggerUtils.hasNext(logRecordContainer, i)) {
                sb.append(Const.SEPARATOR)
            }
        }
        sb.append(Const.LINESEPARATOR) // All systems with the same newline charter
        return sb.toString()
    }

    /**
     * Checks a string if it is IESData conform, e.g. wrong characters. If not it will drop a error.
     *
     * @param value
     * the string value which should be checked
     */
    @Throws(WrongCharacterException::class)
    private fun checkStringValue(sbValue: StringBuilder) {
        val value = sbValue.toString()
        if (value.startsWith(Const.ERROR)) {
            throw WrongCharacterException("Wrong character: String begins with: " + Const.ERROR)
        } else if (value.startsWith(Const.HEXADECIMAL)) {
            throw WrongCharacterException("Wrong character: String begins with: " + Const.HEXADECIMAL)
        } else if (value.contains(Const.SEPARATOR)) {
            throw WrongCharacterException(
                "Wrong character: String contains separator character: " + Const.SEPARATOR
            )
        } else if (!value.matches("^[\\x00-\\x7F]*")) {
            throw WrongCharacterException("Wrong character: Non ASCII character in String.")
        }
    }

    private fun checkMinimalValueSize(size: Int): Int {
        var size = size
        if (size < Const.VALUE_SIZE_MINIMAL) {
            size = Const.VALUE_SIZE_MINIMAL
        }
        return size
    }

    /**
     * Returns the PrintStream for logging.
     *
     * @param group
     * @param loggingInterval
     * @param date
     * @param logChannelList
     * @return the PrintStream for logging.
     */
    private fun getStream(
        group: LogIntervalContainerGroup?, loggingInterval: Int, logTimeOffset: Int,
        calendar: Calendar, logChannelList: Map<String?, LogChannel?>
    ): PrintStream? {
        val filename = LoggerUtils.buildFilename(loggingInterval, logTimeOffset, calendar)
        val file = File(directoryPath + filename)
        actualFile = file
        var out: PrintStream? = null
        try {
            if (file.exists()) {
                out = PrintStream(FileOutputStream(file, true), false, Const.CHAR_SET.toString())
            } else {
                out = PrintStream(FileOutputStream(file, true), false, Const.CHAR_SET.toString())
                val headerString = LogFileHeader.getIESDataFormatHeaderString(
                    group, file.name, loggingInterval,
                    logChannelList
                )
                out.print(headerString)
                out.flush()
            }
        } catch (e: UnsupportedEncodingException) {
            logger.error("", e)
        } catch (e: FileNotFoundException) {
            logger.error("", e)
        }
        return out
    }

    /**
     * Returns the size of a DataType / ValueType.
     *
     * @param logChannel
     * @param iterator
     * @return size of DataType / ValueType.
     */
    private fun getDataTypeSize(logChannel: LogChannel?, iterator: Int): Int {
        var size: Int
        if (logChannel != null) {
            val isByteArray = logChannel.valueType == ValueType.BYTE_ARRAY
            val isString = logChannel.valueType == ValueType.STRING
            size = if (isString) {
                // get length from channel for String / ByteArray
                logChannel.valueTypeLength!!
            } else if (isByteArray) {
                Const.HEXADECIMAL.length + logChannel.valueTypeLength!! * 2
            } else {
                // get length from channel for simple value types
                LoggerUtils.getLengthOfValueType(logChannel.valueType)
            }
        } else {
            // get length from file
            val vt = LoggerUtils.identifyValueType(iterator + Const.NUM_OF_TIME_TYPES_IN_HEADER + 1, actualFile)
            size = LoggerUtils.getLengthOfValueType(vt)
            if (vt == ValueType.BYTE_ARRAY || vt == ValueType.STRING
                && size <= Const.VALUE_SIZE_MINIMAL
            ) {
                size = LoggerUtils.getValueTypeLengthFromFile(
                    iterator + Const.NUM_OF_TIME_TYPES_IN_HEADER + 1,
                    actualFile
                )
            }
        }
        return size
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LogFileWriter::class.java)
    }
}
