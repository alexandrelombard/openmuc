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

import org.openmuc.framework.data.ValueType
import org.openmuc.framework.datalogger.ascii.utils.Const
import org.openmuc.framework.datalogger.ascii.utils.LoggerUtils
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import java.util.*

object LogFileHeader {
    private const val OTHER_STRING = "other"
    private const val TRUE_STRING = "TRUE"
    private const val FALSE_STRING = "FALSE"

    /**
     * Generate the standard IES Data Format Header.
     *
     * @param group
     * a group of the LogIntervallContainer
     * @param filename
     * the name of the file to add the header
     * @param loggingInterval
     * logging interval in ms
     * @param logChannelList
     * a list of all channels for this file
     * @return the header as a string
     */
    fun getIESDataFormatHeaderString(
        group: LogIntervalContainerGroup?, filename: String,
        loggingInterval: Int, logChannelList: Map<String?, LogChannel?>
    ): String {
        val sb = StringBuilder()
        setHeaderTop(sb, loggingInterval, filename)

        // write channel specific header informations
        var colNumber = 4
        for (loggingRecord in group.getList()) {
            val channelId = loggingRecord!!.channelId
            val logChannel = logChannelList[channelId]
            appendChannelSpecificComment(sb, logChannel, colNumber)
            ++colNumber
        }
        val containers = group.getList()
        appendColumnHeaderTimestamp(sb)
        val iterator: Iterator<LoggingRecord?> = containers!!.iterator()
        while (iterator.hasNext()) {
            sb.append(iterator.next()!!.channelId)
            if (iterator.hasNext()) {
                sb.append(Const.SEPARATOR)
            }
        }
        sb.append(Const.LINESEPARATOR)
        return sb.toString()
    }

    /**
     * Generate the standard IES Data Format Header
     *
     * @param filename
     * the name of the file to add the header
     * @param logChannelList
     * a list of all channels for this file
     * @return the header as a string
     */
    fun getIESDataFormatHeaderString(filename: String, logChannelList: List<LogChannel?>): String {
        val sb0 = StringBuilder()
        val sb1 = StringBuilder()
        setHeaderTop(sb0, logChannelList[0]!!.loggingInterval!!, filename)

        // write channel specific header informations
        var colNumber = 4
        val iterator: Iterator<LogChannel?> = logChannelList.listIterator()
        while (iterator.hasNext()) {
            val logChannel = iterator.next()
            appendChannelSpecificComment(sb0, logChannel, colNumber)
            sb1.append(logChannel!!.id)
            if (iterator.hasNext()) {
                sb1.append(Const.SEPARATOR)
            }
            ++colNumber
        }
        appendColumnHeaderTimestamp(sb0)
        sb0.append(sb1)
        sb0.append(Const.LINESEPARATOR)
        return sb0.toString()
    }

    /**
     * Appends channel specific comments to a StringBuilder
     *
     * @param sb
     * @param logChannel
     * @param colNumber
     */
    private fun appendChannelSpecificComment(sb: StringBuilder, logChannel: LogChannel?, colNumber: Int) {
        var unit = logChannel!!.unit
        if (unit == "") {
            unit = "0"
        }
        val vType = logChannel.valueType
        val valueType = vType.toString()
        var valueTypeLength = 0
        valueTypeLength =
            if (vType == ValueType.BYTE_ARRAY || vType == ValueType.STRING) {
                logChannel.valueTypeLength!!
            } else {
                LoggerUtils.getLengthOfValueType(vType)
            }
        var description = logChannel.description
        if (description == "") {
            description = "-"
        }
        createRow(
            sb, String.format("%03d", colNumber), logChannel.id, FALSE_STRING, TRUE_STRING, unit,
            OTHER_STRING, valueType, valueTypeLength, description
        )
    }

    /**
     * Append column headers, the timestamps, in a StringBuilder
     *
     * @param sb
     */
    private fun appendColumnHeaderTimestamp(sb: StringBuilder) {

        // write column headers
        sb.append("YYYYMMDD")
        sb.append(Const.SEPARATOR)
        sb.append("hhmmss")
        sb.append(Const.SEPARATOR)
        sb.append("unixtimestamp")
        sb.append(Const.SEPARATOR)
    }

    /**
     * Sets the top of the header.
     *
     * @param sb
     * @param loggingInterval
     * @param filename
     */
    private fun setHeaderTop(sb: StringBuilder, loggingInterval: Int, filename: String) {
        val timestepSeconds = (loggingInterval / 1000.0).toString()
        val seperator = Const.SEPARATOR

        // write general header informations
        appendStrings(sb, "#ies_format_version: ", Const.ISEFORMATVERSION.toString(), Const.LINESEPARATOR_STRING)
        appendStrings(sb, "#file: ", filename, Const.LINESEPARATOR_STRING)
        appendStrings(sb, "#file_info: ", Const.FILEINFO, Const.LINESEPARATOR_STRING)
        appendStrings(sb, "#timezone: ", diffLocalUTC, Const.LINESEPARATOR_STRING)
        appendStrings(sb, "#timestep_sec: ", timestepSeconds, Const.LINESEPARATOR_STRING)
        appendStrings(
            sb, "#", "col_no", seperator, "col_name", seperator, "confidential", seperator, "measured",
            seperator, "unit", seperator, "category", seperator, Const.COMMENT_NAME, Const.LINESEPARATOR_STRING
        )
        createRow(
            sb, "001", "YYYYMMDD", FALSE_STRING, FALSE_STRING, "0", "time", "INTEGER", 8,
            "Date [human readable]"
        )
        createRow(sb, "002", "hhmmss", FALSE_STRING, FALSE_STRING, "0", "time", "SHORT", 6, "Time [human readable]")
        createRow(
            sb, "003", "unixtimestamp", FALSE_STRING, FALSE_STRING, "s", "time", "DOUBLE", 14,
            "lapsed seconds from 01-01-1970"
        )
    }

    /**
     * Construct a header row with predefined separators and comment signs.
     *
     * @param colNumber
     * column number example: #001
     * @param colName
     * column name example: YYYYMMDD
     * @param confidential
     * false or true
     * @param measured
     * false or true
     * @param unit
     * example: kWh
     * @param category
     * example: time
     * @param valueType
     * example: DOUBLE
     * @param valueTypeLength
     * example: 8
     * @param comment
     * a comment
     */
    private fun createRow(
        sb: StringBuilder, colNumber: String, colName: String?, confidential: String,
        measured: String, unit: String?, category: String, valueType: String, valueTypeLength: Int, comment: String?
    ) {
        val seperator = Const.SEPARATOR
        val commentSign = Const.COMMENT_SIGN
        val vtEndSign = Const.VALUETYPE_ENDSIGN
        val vtSizeSep = Const.VALUETYPE_SIZE_SEPARATOR
        var valueTypeLengthString = ""
        if (valueTypeLength != 0) {
            valueTypeLengthString += valueTypeLength
        }
        appendStrings(
            sb, commentSign, colNumber, seperator, colName!!, seperator, confidential, seperator, measured,
            seperator, unit!!, seperator, category, seperator, valueType, vtSizeSep, valueTypeLengthString, vtEndSign,
            comment!!, Const.LINESEPARATOR_STRING
        )
    }

    /**
     * appendStrings appends a any String to a StringBuilder
     *
     * @param sb
     * StringBuilder to append a String
     * @param s
     * the String to append
     */
    private fun appendStrings(sb: StringBuilder, vararg s: String) {
        for (element in s) {
            sb.append(element)
        }
    }

    /**
     * Calculates the difference between the configured local time and the Coordinated Universal Time (UTC) without
     * daylight saving time and returns it as a string.
     *
     * @return the difference between local time and UTC as string.
     */
    private val diffLocalUTC: String
        private get() {
            val ret: String
            var time: Long = 0
            val calendar: Calendar = GregorianCalendar(Locale.getDefault())
            time = calendar.timeZone.rawOffset.toLong()
            time /= (1000 * 60 * 60).toLong()
            ret = if (time >= 0) {
                "+ $time"
            } else {
                "- $time"
            }
            return ret
        }
}
