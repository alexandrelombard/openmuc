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

import org.openmuc.framework.data.*
import org.openmuc.framework.data.Flag.Companion.newFlag
import org.openmuc.framework.datalogger.ascii.utils.Const
import org.openmuc.framework.datalogger.ascii.utils.LoggerUtils
import org.openmuc.framework.datalogger.spi.LogChannel
import org.slf4j.LoggerFactory
import java.io.*
import java.text.SimpleDateFormat

class LogFileReader(private val path: String?, logChannel: LogChannel) {
    private val ids: Array<String?>
    private val loggingInterval: Int
    private val logTimeOffset: Int
    private var unixTimestampColumn = 0
    private var startTimestamp: Long = 0
    private var endTimestamp: Long = 0
    private var firstTimestampFromFile: Long

    /**
     * LogFileReader Constructor
     *
     * @param path
     * the path to the files to read from
     * @param logChannel
     * the channel to read from
     */
    init {
        ids = arrayOf(logChannel.id, Const.TIMESTAMP_STRING)
        loggingInterval = logChannel.loggingInterval!!
        logTimeOffset = logChannel.loggingTimeOffset!!
        firstTimestampFromFile = -1
    }

    /**
     * Get the values between start time stamp and end time stamp
     *
     * @param startTimestamp
     * start time stamp
     * @param endTimestamp
     * end time stamp
     * @return All records of the given time span
     */
    fun getValues(startTimestamp: Long, endTimestamp: Long): Map<String?, MutableList<Record?>> {
        this.startTimestamp = startTimestamp
        this.endTimestamp = endTimestamp
        val filenames = LoggerUtils.getFilenames(
            loggingInterval, logTimeOffset, this.startTimestamp,
            this.endTimestamp
        )
        val recordsMap: MutableMap<String?, MutableList<Record?>> = HashMap()
        for (id in ids) {
            recordsMap[id] = ArrayList()
        }
        for (i in filenames!!.indices) {
            var nextFile = false
            if (logger.isTraceEnabled) {
                logger.trace("using " + filenames[i])
            }
            var filepath: String
            filepath = if (path!!.endsWith(File.separator)) {
                path + filenames[i]
            } else {
                path + File.separatorChar + filenames[i]
            }
            if (i > 0) {
                nextFile = true
            }
            processFile(recordsMap, filepath, nextFile)
        }
        return recordsMap
    }

    /**
     * Get all records of the given file
     *
     * @param filePath
     * to be read from
     * @return All records in the given file as a Map of String channelId and List of records for this channel
     */
    fun getValues(filePath: String): Map<String?, MutableList<Record?>>? {
        startTimestamp = 0
        endTimestamp = 9223372036854775807L // max long
        var recordsMap: MutableMap<String?, MutableList<Record?>>? = HashMap()
        for (id in ids) {
            recordsMap!![id] = ArrayList()
        }
        recordsMap = processFile(recordsMap, filePath, true)
        return recordsMap
    }

    /**
     * get a single record from single channel of time stamp
     *
     * @param timestamp
     * time stamp
     * @return Record on success, otherwise null
     */
    fun getValue(timestamp: Long): Map<String?, Record?> {

        // Returns a record which lays within the interval [timestamp, timestamp +
        // loggingInterval]
        // The interval is necessary for a requested time stamp which lays between the
        // time stamps of two logged values
        // e.g.: t_request = 7, t1_logged = 5, t2_logged = 10, loggingInterval = 5
        // method will return the record of t2_logged because this lays within the
        // interval [7,12]
        // If the t_request matches exactly a logged time stamp, then the according
        // record is returned.
        val recordsMap = getValues(timestamp, timestamp)
        val recordMap: MutableMap<String?, Record?> = HashMap()
        for ((key, recordList) in recordsMap) {
            var record: Record?
            record = if (recordList == null || recordList.size == 0) {
                // no record found for requested timestamp
                null // new Record(Flag.UNKNOWN_ERROR);
            } else if (recordsMap.size == 1) {
                // t_request lays between two logged values
                recordList[0]
            } else {
                Record(Flag.UNKNOWN_ERROR)
            }
            recordMap[key] = record
        }
        return recordMap
    }

    /**
     * Reads the file line by line
     *
     * @param filepath
     * file path
     * @param nextFile
     * if it is the next file and not the first between a time span
     * @return records on success, otherwise null
     */
    private fun processFile(
        recordsMap: MutableMap<String?, MutableList<Record?>>?, filepath: String,
        nextFile: Boolean
    ): MutableMap<String?, MutableList<Record?>>? {
        var recordsMap = recordsMap
        var line: String? = null
        var currentPosition: Long = 0
        val rowSize: Long
        var firstTimestamp: Long = 0
        var firstValueLine: String? = null
        var currentTimestamp: Long = 0
        val raf = LoggerUtils.getRandomAccessFile(File(filepath), "r") ?: return null
        try {
            var channelsColumnsMap: Map<String?, Int?>? = null
            while (channelsColumnsMap == null) {
                line = raf.readLine()
                channelsColumnsMap = LoggerUtils.getColumnNumbersByNames(line, ids)
            }
            unixTimestampColumn = channelsColumnsMap[Const.TIMESTAMP_STRING]!!
            firstValueLine = raf.readLine()
            rowSize = firstValueLine.length + 1L // +1 because of "\n"

            // rewind the position to the start of the firstValue line
            currentPosition = raf.filePointer - rowSize
            firstTimestamp =
                (java.lang.Double.valueOf(firstValueLine.split(Const.SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()[unixTimestampColumn])
                        * 1000).toLong()
            if (nextFile || startTimestamp < firstTimestamp) {
                startTimestamp = firstTimestamp
            }
            if (startTimestamp >= firstTimestamp) {
                val filepos = getFilePosition(
                    loggingInterval, startTimestamp, firstTimestamp, currentPosition,
                    rowSize
                )
                raf.seek(filepos)
                currentTimestamp = startTimestamp
                while (raf.readLine().also { line = it } != null && currentTimestamp <= endTimestamp) {
                    processLine(line, channelsColumnsMap, recordsMap)
                    currentTimestamp += loggingInterval.toLong()
                }
                raf.close()
            } else {
                recordsMap = null // because the column of the channel was not identified
            }
        } catch (e: IOException) {
            logger.error(e.message)
            recordsMap = null
        }
        return recordsMap
    }

    /**
     * Process the line: ignore comments, read records
     *
     * @param line
     * the line to process
     * @param recordsMap
     * list of records
     */
    private fun processLine(
        line: String?, channelsColumnsMap: Map<String?, Int?>,
        recordsMap: MutableMap<String?, MutableList<Record?>>?
    ) {
        if (!line!!.startsWith(Const.COMMENT_SIGN)) {
            readRecordsFromLine(line, channelsColumnsMap, recordsMap)
        }
    }

    /**
     * read the records from a line.
     *
     * @param line
     * to read
     * @return Records read from line
     */
    private fun readRecordsFromLine(
        line: String?, channelsColumnsMap: Map<String?, Int?>,
        recordsMap: MutableMap<String?, MutableList<Record?>>?
    ) {
        val columnValue = line!!.split(Const.SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        try {
            val timestampS = columnValue[unixTimestampColumn].toDouble()
            val timestampMS = (timestampS * 1000).toLong()
            if (isTimestampPartOfRequestedInterval(timestampMS)) {
                for ((key, value) in channelsColumnsMap) {
                    val record = convertLogfileEntryToRecord(columnValue[value!!].trim { it <= ' ' }, timestampMS)
                    var list = recordsMap!![key]
                    if (list == null) {
                        recordsMap[key] = ArrayList()
                        list = recordsMap[key]
                    }
                    list!!.add(record)
                }
            } else {
                if (logger.isTraceEnabled) {
                    val sdf = SimpleDateFormat("HH:mm:ss.SSS")
                    logger.trace("timestampMS: " + sdf.format(timestampMS) + " " + timestampMS)
                }
            }
        } catch (e: NumberFormatException) {
            logger.warn("It's not a timestamp.\n", e.message)
        } catch (e: ArrayIndexOutOfBoundsException) {
            logger.error("Array Index Out Of Bounds Exception. ", e)
        }
    }

    /**
     * Checks if the time stamp read from file is part of the requested logging interval
     *
     * @param lineTimestamp
     * time stamp to check if it is part of the time span
     * @return true if it is a part of the requested interval, if not false.
     */
    private fun isTimestampPartOfRequestedInterval(lineTimestamp: Long): Boolean {
        var result = false

        // TODO tidy up, move to better place, is asked each line!
        if (firstTimestampFromFile == -1L) {
            firstTimestampFromFile = lineTimestamp
        }
        if (lineTimestamp >= startTimestamp && lineTimestamp <= endTimestamp) {
            result = true
        }
        return result
    }

    /**
     * Get the position of the startTimestamp, without Header.
     *
     * @param loggingInterval
     * logging interval
     * @param startTimestamp
     * start time stamp
     * @return the position of the start timestamp as long.
     */
    private fun getFilePosition(
        loggingInterval: Int, startTimestamp: Long, firstTimestampOfFile: Long,
        firstValuePos: Long, rowSize: Long
    ): Long {
        val timeOffsetMs = startTimestamp - firstTimestampOfFile
        var numberOfLinesToSkip = timeOffsetMs / loggingInterval

        // if offset isn't a multiple of loggingInterval add an additional line
        if (timeOffsetMs % loggingInterval != 0L) {
            ++numberOfLinesToSkip
        }
        return numberOfLinesToSkip * rowSize + firstValuePos
    }
    // TODO support ints, booleans, ...
    /**
     * Converts an entry from the logging file into a record
     *
     * @param strValue
     * string value
     * @param timestamp
     * time stamp
     * @return the converted logfile entry.
     */
    private fun convertLogfileEntryToRecord(strValue: String, timestamp: Long): Record? {
        var record: Record? = null
        record = if (isNumber(strValue)) {
            Record(
                DoubleValue(strValue.toDouble()),
                timestamp,
                Flag.VALID
            )
        } else {
            getRecordFromNonNumberValue(strValue, timestamp)
        }
        return record
    }

    /**
     * Returns the record from a non number value read from the logfile. This is the case if the value is an error like
     * "e0" or a normal ByteArrayValue
     *
     * @param strValue
     * string value
     * @param timestamp
     * time stamp
     * @return the value in a record.
     */
    private fun getRecordFromNonNumberValue(strValue: String, timestamp: Long): Record {
        var record: Record? = null
        if (strValue.trim { it <= ' ' }.startsWith(Const.ERROR)) {
            val errorSize = Const.ERROR.length
            val stringLength = strValue.length
            var errorFlag = strValue.substring(errorSize, errorSize + stringLength - errorSize)
            errorFlag = errorFlag.trim { it <= ' ' }
            record = if (isNumber(errorFlag)) {
                Record(null, timestamp, newFlag(errorFlag.toInt()))
            } else {
                Record(null, timestamp, Flag.NO_VALUE_RECEIVED_YET)
            }
        } else if (strValue.trim { it <= ' ' }.startsWith(Const.HEXADECIMAL)) {
            record = Record(ByteArrayValue(strValue.trim { it <= ' ' }
                .toByteArray(Const.CHAR_SET)), timestamp, Flag.VALID)
        } else {
            record = Record(StringValue(strValue.trim { it <= ' ' }), timestamp, Flag.VALID)
        }
        return record
    }

    /**
     * Checks if the string value is a number
     *
     * @param strValue
     * string value
     * @return True on success, otherwise false
     */
    private fun isNumber(strValue: String): Boolean {
        var isDecimalSeparatorFound = false
        if (!Character.isDigit(strValue[0]) && strValue[0] != Const.MINUS_SIGN && strValue[0] != Const.PLUS_SIGN) {
            return false
        }
        for (charactor in strValue.substring(1).toCharArray()) {
            if (!Character.isDigit(charactor)) {
                if (charactor == Const.DECIMAL_SEPARATOR && !isDecimalSeparatorFound) {
                    isDecimalSeparatorFound = true
                    continue
                }
                return false
            }
        }
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LogFileReader::class.java)
    }
}
