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
package org.openmuc.framework.datalogger.ascii.utils

import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.Record.value
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.datalogger.ascii.LogFileHeader
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.slf4j.LoggerFactory
import java.io.*
import java.text.*
import java.util.*

object LoggerUtils {
    private val logger = LoggerFactory.getLogger(LoggerUtils::class.java)
    private val hexArray = "0123456789ABCDEF".toCharArray()

    /**
     * Returns all filenames of the given time span defined by the two dates
     *
     * @param loggingInterval
     * logging interval
     * @param logTimeOffset
     * logging time offset
     * @param startTimestamp
     * start time stamp
     * @param endTimestamp
     * end time stamp
     * @return a list of strings with all files names
     */
    @JvmStatic
    fun getFilenames(
        loggingInterval: Int, logTimeOffset: Int, startTimestamp: Long,
        endTimestamp: Long
    ): List<String> {
        val calendarStart: Calendar = GregorianCalendar(Locale.getDefault())
        calendarStart.timeInMillis = startTimestamp
        val calendarEnd: Calendar = GregorianCalendar(Locale.getDefault())
        calendarEnd.timeInMillis = endTimestamp

        // Rename timespanToFilenames....
        // Filename YYYYMMDD_<LoggingInterval>.dat
        val filenames: MutableList<String> = ArrayList()
        while (calendarStart.before(calendarEnd) || calendarStart == calendarEnd) {
            val filename = buildFilename(loggingInterval, logTimeOffset, calendarStart)
            filenames.add(filename)

            // set date to 00:00:00 of the next day
            calendarStart.add(Calendar.DAY_OF_MONTH, 1)
            calendarStart[Calendar.HOUR_OF_DAY] = 0
            calendarStart[Calendar.MINUTE] = 0
            calendarStart[Calendar.SECOND] = 0
            calendarStart[Calendar.MILLISECOND] = 0
        }
        return filenames
    }

    /**
     * Returns the filename, with the help of the timestamp and the interval.
     *
     * @param loggingInterval
     * logging interval
     * @param logTimeOffset
     * logging time offset
     * @param timestamp
     * timestamp
     * @return a filename from timestamp (date) and interval
     */
    @JvmStatic
    fun getFilename(loggingInterval: Int, logTimeOffset: Int, timestamp: Long): String {
        val calendar: Calendar = GregorianCalendar(Locale.getDefault())
        calendar.timeInMillis = timestamp
        return buildFilename(loggingInterval, logTimeOffset, calendar)
    }

    /**
     * Builds the Logfile name from logging interval, logging time offset and the date of the calendar
     *
     * @param loggingInterval
     * logging interval
     * @param logTimeOffset
     * logging time offset
     * @param calendar
     * Calendar for the time of the file name
     * @return logging file name
     */
    fun buildFilename(loggingInterval: Int, logTimeOffset: Int, calendar: Calendar?): String {
        val sb = StringBuilder()
        sb.append(String.format(Const.DATE_FORMAT, calendar))
        sb.append(Const.TIME_SEPERATOR)
        sb.append(loggingInterval.toString())
        if (logTimeOffset != 0) {
            sb.append(Const.TIME_SEPERATOR)
            sb.append(logTimeOffset)
        }
        sb.append(Const.EXTENSION)
        return sb.toString()
    }

    /**
     * Builds the Logfile name from string interval_timeOffset and the date of the calendar
     *
     * @param intervalTimeOffset
     * the IntervallTimeOffset
     * @param calendar
     * Calendar for the time of the file name
     * @return logfile name
     */
    fun buildFilename(intervalTimeOffset: String?, calendar: Calendar?): String {
        val sb = StringBuilder()
        sb.append(String.format(Const.DATE_FORMAT, calendar))
        sb.append(Const.TIME_SEPERATOR)
        sb.append(intervalTimeOffset)
        sb.append(Const.EXTENSION)
        return sb.toString()
    }

    /**
     * Checks if it has a next container entry.
     *
     * @param containers
     * a list with LogRecordContainer
     * @param i
     * the current position of the list
     * @return true if it has a next container entry, if not else.
     */
    fun hasNext(containers: List<LoggingRecord?>?, i: Int): Boolean {
        var result = false
        if (i <= containers!!.size - 2) {
            result = true
        }
        return result
    }

    /**
     * This method rename all *.dat files with the date from today in directoryPath into a *.old0, *.old1, ...
     *
     * @param directoryPath
     * directory path
     * @param calendar
     * Calendar for the time of the file name
     */
    fun renameAllFilesToOld(directoryPath: String?, calendar: Calendar?) {
        val date = String.format(Const.DATE_FORMAT, calendar)
        val dir = File(directoryPath)
        val files = dir.listFiles()
        if (files != null && files.size > 0) {
            for (file in files) {
                val currentName = file.name
                if (currentName.startsWith(date) && currentName.endsWith(Const.EXTENSION)) {
                    val newName = StringBuilder()
                        .append(currentName.substring(0, currentName.length - Const.EXTENSION.length))
                        .append(Const.EXTENSION_OLD)
                        .toString()
                    var j = 0
                    var fileWithNewName = File(directoryPath + newName + j)
                    while (fileWithNewName.exists()) {
                        ++j
                        fileWithNewName = File(directoryPath + newName + j)
                    }
                    if (!file.renameTo(fileWithNewName)) {
                        logger.error("Could not rename file to ", newName)
                    }
                }
            }
        } else {
            logger.error("No file found in $directoryPath")
        }
    }

    /**
     * This method renames a singel &lt;date&gt;_&lt;loggerInterval&gt;_&lt;loggerTimeOffset&gt;.dat file into a *.old0,
     * *.old1, ...
     *
     * @param directoryPath
     * directory path
     * @param loggerIntervalLoggerTimeOffset
     * logger interval with logger time offset as String separated with underline
     * @param calendar
     * calendar of the day
     */
    fun renameFileToOld(directoryPath: String?, loggerIntervalLoggerTimeOffset: String?, calendar: Calendar?) {
        val file = File(directoryPath + buildFilename(loggerIntervalLoggerTimeOffset, calendar))
        if (file.exists()) {
            val currentName = file.name
            if (logger.isTraceEnabled) {
                logger.trace(MessageFormat.format("Header not identical. Rename file {0} to old.", currentName))
            }
            var newName = currentName.substring(0, currentName.length - Const.EXTENSION.length)
            newName += Const.EXTENSION_OLD
            var j = 0
            var fileWithNewName = File(directoryPath + newName + j)
            while (fileWithNewName.exists()) {
                ++j
                fileWithNewName = File(directoryPath + newName + j)
            }
            if (!file.renameTo(fileWithNewName)) {
                logger.error("Could not rename file to $newName")
            }
        }
    }

    /**
     * Returns the calendar from today with the first hour, minute, second and millisecond.
     *
     * @param today
     * the current calendar
     * @return the calendar from today with the first hour, minute, second and millisecond
     */
    fun getCalendarTodayZero(today: Calendar): Calendar {
        val calendarZero: Calendar = GregorianCalendar(Locale.getDefault())
        calendarZero[today[Calendar.YEAR], today[Calendar.MONTH], today[Calendar.DATE], 0, 0] = 0
        calendarZero[Calendar.MILLISECOND] = 0
        return calendarZero
    }

    /**
     * This method adds a blank spaces to a StringBuilder object.
     *
     * @param length
     * length of the value to add the spaces
     * @param size
     * maximal allowed size
     * @param sb
     * StringBuilder object to add the spaces
     */
    fun addSpaces(length: Int, size: Int, sb: StringBuilder) {
        var i = length
        while (i < size) {
            sb.append(' ')
            ++i
        }
    }

    /**
     * This method adds a string value up with blank spaces from left to right.
     *
     * @param sb
     * StringBuilder in wich the spaces will appended
     * @param number
     * the number of spaces
     */
    fun appendSpaces(sb: StringBuilder, number: Int) {
        for (i in 0 until number) {
            sb.append(' ')
        }
    }

    /**
     * Construct a error value with standard error prefix and the flag as number.
     *
     * @param flag
     * the wished error flag
     * @param sbValue
     * string buffer to add the error flag
     */
    fun buildError(sbValue: StringBuilder, flag: Flag) {
        sbValue.setLength(0)
        sbValue.append(Const.ERROR).append(flag.getCode().toInt())
    }

    /**
     * Get the column number by name.
     *
     * @param line
     * the line to search
     * @param name
     * the name to search in line
     * @return the column number as int.
     */
    fun getColumnNumberByName(line: String, name: String): Int {
        val channelColumn = -1

        // erst Zeile ohne Kommentar finden, dann den Spaltennamen suchen und dessen Possitionsnummer zurueckgeben.
        if (!line.startsWith(Const.COMMENT_SIGN)) {
            val columns = line.split(Const.SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            for (i in columns.indices) {
                if (name == columns[i]) {
                    return i
                }
            }
        }
        return channelColumn
    }

    /**
     * Get the columns number by names.
     *
     * @param line
     * the line to search
     * @param names
     * the name to search in line
     * @return the column numbers mapped with the name.
     */
    fun getColumnNumbersByNames(line: String?, names: Array<String?>): Map<String?, Int>? {
        if (line!!.startsWith(Const.COMMENT_SIGN)) {
            return null
        }
        val channelColumnsMap: MutableMap<String?, Int> = HashMap()
        val columns = line.split(Const.SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        for (i in columns.indices) {
            for (name in names) {
                if (columns[i] == name) {
                    channelColumnsMap[name] = i
                }
            }
        }
        return channelColumnsMap
    }

    /**
     * Get the column number by name, in comments. It searches the line by his self. The BufferdReader has to be on the
     * begin of the file.
     *
     * @param name
     * the name to search
     * @param br
     * the BufferedReader
     * @return column number as int, -1 if name not found
     * @throws IOException
     * throws IOException If an I/O error occurs
     */
    @Throws(IOException::class)
    fun getCommentColumnNumberByName(name: String?, br: BufferedReader): Int {
        var line = br.readLine()
        while (line != null && line.startsWith(Const.COMMENT_SIGN)) {
            if (line.contains(name!!)) {
                val columns = line.split(Const.SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                for (i in columns.indices) {
                    if (name == columns[i]) {
                        return i
                    }
                }
            }
            line = try {
                br.readLine()
            } catch (e: NullPointerException) {
                return -1
            }
        }
        return -1
    }

    /**
     * Get the value which is coded in the comment
     *
     * @param colNumber
     * the number of the channel
     * @param column
     * the column
     * @param br
     * a BufferedReader
     * @return the value of a column of a specific col_num
     * @throws IOException
     * If an I/O error occurs
     */
    @Throws(IOException::class)
    fun getCommentValue(colNumber: Int, column: Int, br: BufferedReader): String {
        val columnName = String.format("%03d", colNumber)
        var line = br.readLine()
        while (line != null && line.startsWith(Const.COMMENT_SIGN)) {
            if (!line.startsWith(Const.COMMENT_SIGN + columnName)) {
                line = br.readLine()
                continue
            }
            return line.split(Const.SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[column]
            line = br.readLine()
        }
        return ""
    }

    /**
     * Identifies the ValueType of a logger value on a specific col_no
     *
     * @param columnNumber
     * column number
     * @param dataFile
     * the logger data file
     * @return the ValueType from col_num x
     */
    fun identifyValueType(columnNumber: Int, dataFile: File?): ValueType {
        val valueTypeWithSize = getValueTypeAsString(columnNumber, dataFile)
        val valueTypeWithSizeArray =
            valueTypeWithSize.split(Const.VALUETYPE_ENDSIGN.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        val valueType =
            valueTypeWithSizeArray[0].split(Const.VALUETYPE_SIZE_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[0]
        return ValueType.valueOf(valueType)
    }

    fun getValueTypeLengthFromFile(columnNumber: Int, dataFile: File?): Int {
        val valueType = getValueTypeAsString(columnNumber, dataFile)
        return getByteStringLength(valueType)
    }

    private fun getValueTypeAsString(columnNumber: Int, dataFile: File?): String {
        try {
            BufferedReader(
                InputStreamReader(FileInputStream(dataFile), Const.CHAR_SET)
            ).use { br ->
                val column = getCommentColumnNumberByName(Const.COMMENT_NAME, br)
                if (column == -1) {
                    val msg = MessageFormat.format("No element with name \"{0}\" found.", Const.COMMENT_NAME)
                    throw NoSuchElementException(msg)
                }
                return getCommentValue(columnNumber, column, br).split(Const.VALUETYPE_ENDSIGN.toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()[0]
            }
        } catch (e: IOException) {
            logger.error("Failed to get Value type as string.", e)
        }
        return ""
    }

    /**
     * Returns the predefined size of a ValueType.
     *
     * @param valueType
     * the type to get the predefined size
     * @return predefined size of a ValueType as int.
     */
    fun getLengthOfValueType(valueType: ValueType?): Int {
        return when (valueType) {
            ValueType.DOUBLE -> Const.VALUE_SIZE_DOUBLE
            ValueType.FLOAT -> Const.VALUE_SIZE_DOUBLE
            ValueType.INTEGER -> Const.VALUE_SIZE_INTEGER
            ValueType.LONG -> Const.VALUE_SIZE_LONG
            ValueType.SHORT -> Const.VALUE_SIZE_SHORT
            ValueType.BYTE_ARRAY -> Const.VALUE_SIZE_MINIMAL
            ValueType.STRING -> Const.VALUE_SIZE_MINIMAL
            ValueType.BOOLEAN, ValueType.BYTE -> Const.VALUE_SIZE_MINIMAL
            else -> Const.VALUE_SIZE_MINIMAL
        }
    }

    /**
     * Converts a byte array to an hexadecimal string
     *
     * @param sb
     * to add hex string
     * @param byteArray
     * the byte array to convert
     */
    @JvmStatic
    fun byteArrayToHexString(sb: StringBuilder, byteArray: ByteArray?) {
        val hexChars = CharArray(byteArray!!.size * 2)
        for (j in byteArray.indices) {
            val v = byteArray[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        sb.append(hexChars)
    }

    /**
     * Constructs the timestamp for every log value into a StringBuilder.
     *
     * @param sb
     * the StringBuilder to add the logger timestamp
     * @param calendar
     * Calendar with the wished time
     */
    fun setLoggerTimestamps(sb: StringBuilder, calendar: Calendar) {
        val unixtimestampSeconds = calendar.timeInMillis / 1000.0 // double for milliseconds, nanoseconds
        sb.append(String.format(Const.DATE_FORMAT, calendar))
        sb.append(Const.SEPARATOR)
        sb.append(String.format(Const.TIME_FORMAT, calendar))
        sb.append(Const.SEPARATOR)
        sb.append(String.format(Locale.ENGLISH, "%10.3f", unixtimestampSeconds))
        sb.append(Const.SEPARATOR)
    }

    /**
     * Constructs the timestamp for every log value into a StringBuilder.
     *
     * @param sb
     * the StringBuilder to add the logger timestamp
     * @param unixTimeStamp
     * unix time stamp in ms
     */
    fun setLoggerTimestamps(sb: StringBuilder, unixTimeStamp: Long) {
        val calendar: Calendar = GregorianCalendar(Locale.getDefault())
        calendar.timeInMillis = unixTimeStamp
        val unixtimestampSeconds = unixTimeStamp / 1000.0 // double for milliseconds, nanoseconds
        sb.append(String.format(Const.DATE_FORMAT, calendar))
        sb.append(Const.SEPARATOR)
        sb.append(String.format(Const.TIME_FORMAT, calendar))
        sb.append(Const.SEPARATOR)
        sb.append(String.format(Locale.ENGLISH, "%10.3f", unixtimestampSeconds))
        sb.append(Const.SEPARATOR)
    }

    fun getHeaderFromFile(filePath: String): String {
        val br: BufferedReader
        br = try {
            BufferedReader(InputStreamReader(FileInputStream(File(filePath)), Const.CHAR_SET))
        } catch (e1: IOException) {
            return ""
        }
        val sb = StringBuilder()
        try {
            var line = br.readLine()
            if (line != null) {
                sb.append(line)
                while (line != null && line.startsWith(Const.COMMENT_SIGN)) {
                    sb.append(Const.LINESEPARATOR)
                    line = br.readLine()
                    sb.append(line)
                }
            }
        } catch (e: IOException) {
            logger.error("Problems to handle file: $filePath", e)
        } finally {
            try {
                br.close()
            } catch (e: IOException) {
                logger.error("Cannot close file: $filePath", e)
            }
        }
        return sb.toString()
    }

    /**
     * Returns a RandomAccessFile of the specified file.
     *
     * @param file
     * file get the RandomAccessFile
     * @param accessMode
     * access mode
     * @return the RandomAccessFile of the specified file, `null` if an error occured.
     */
    fun getRandomAccessFile(file: File, accessMode: String?): RandomAccessFile? {
        try {
            return RandomAccessFile(file, accessMode)
        } catch (e: FileNotFoundException) {
            logger.warn("Requested logfile: '{}' not found.", file.absolutePath)
        }
        return null
    }

    @Throws(IOException::class)
    fun getPrintWriter(file: File, append: Boolean): PrintWriter {
        var writer: PrintWriter? = null
        writer = try {
            PrintWriter(OutputStreamWriter(FileOutputStream(file, append), Const.CHAR_SET))
        } catch (e: IOException) {
            logger.error("Cannot open file: " + file.absolutePath)
            throw IOException(e)
        }
        return writer
    }

    fun areHeadersIdentical(
        loggerDirectory: String?, channels: List<LogChannel?>?,
        calendar: Calendar?
    ): Map<String, Boolean> {
        val areHeadersIdentical: MutableMap<String, Boolean> = TreeMap()
        val logChannelMap: MutableMap<String, MutableList<LogChannel?>> = TreeMap()
        var key = ""
        for (logChannel in channels!!) {
            key = if (logChannel!!.loggingTimeOffset != 0) {
                logChannel.loggingInterval.toString() + Const.TIME_SEPERATOR_STRING + logChannel.loggingTimeOffset
            } else {
                logChannel.loggingInterval.toString()
            }
            if (!logChannelMap.containsKey(key)) {
                val logChannelList: MutableList<LogChannel?> = ArrayList()
                logChannelList.add(logChannel)
                logChannelMap[key] = logChannelList
            } else {
                logChannelMap[key]!!.add(logChannel)
            }
        }
        var logChannels: List<LogChannel?>
        for ((key1, value) in logChannelMap) {
            key = key1
            logChannels = value
            val fileName = buildFilename(key, calendar)
            val headerGenerated = LogFileHeader.getIESDataFormatHeaderString(fileName, logChannels)
            val oldHeader = getHeaderFromFile(loggerDirectory + fileName) + Const.LINESEPARATOR
            val isHeaderIdentical = headerGenerated == oldHeader
            areHeadersIdentical[key] = isHeaderIdentical
        }
        return areHeadersIdentical
    }

    /**
     * * fills a AsciiLogg file up.
     *
     * @param out
     * the output stream to write on
     * @param unixTimeStamp
     * unix time stamp
     * @param loggingInterval
     * logging interval
     * @param numberOfFillUpLines
     * the number to fill up lines
     * @param errorValues
     * the error value set in the line
     * @return returns the unix time stamp of the last filled up line
     */
    fun fillUp(
        out: PrintWriter?, unixTimeStamp: Long, loggingInterval: Long, numberOfFillUpLines: Long,
        errorValues: StringBuilder?
    ): Long {
        var unixTimeStamp = unixTimeStamp
        val line = StringBuilder()
        for (i in 0 until numberOfFillUpLines) {
            line.setLength(0)
            unixTimeStamp += loggingInterval
            setLoggerTimestamps(line, unixTimeStamp)
            line.append(errorValues)
            line.append(Const.LINESEPARATOR)
            out!!.append(line)
        }
        return unixTimeStamp
    }

    fun getNumberOfFillUpLines(lastUnixTimeStamp: Long, loggingInterval: Long): Long {
        var numberOfFillUpLines: Long = 0
        val currentUnixTimeStamp = System.currentTimeMillis()
        numberOfFillUpLines = (currentUnixTimeStamp - lastUnixTimeStamp) / loggingInterval
        return numberOfFillUpLines
    }

    /**
     * Returns the error value as a StringBuilder.
     *
     * @param lineArray
     * a ascii line as a array with error code
     * @return StringBuilder with appended error
     */
    fun getErrorValues(lineArray: Array<String>): StringBuilder {
        val errorValues = StringBuilder()
        val arrayLength = lineArray.size
        val errorCodeLength = Const.ERROR.length + 2
        val separatorLength = Const.SEPARATOR.length
        var length = 0
        for (i in 3 until arrayLength) {
            length = lineArray[i].length
            length -= errorCodeLength
            if (i > arrayLength - 1) {
                length -= separatorLength
            }
            appendSpaces(errorValues, length)
            errorValues.append(Const.ERROR)
            errorValues.append(Flag.DATA_LOGGING_NOT_ACTIVE.getCode().toInt())
            if (i < arrayLength - 1) {
                errorValues.append(Const.SEPARATOR)
            }
        }
        return errorValues
    }

    /**
     * Get the length from a type+length tuple. Example: "Byte_String,95"
     *
     * @param string
     * has to be a string with ByteType and length.
     * @param dataFile
     * the logger data file
     * @return the length of a ByteString.
     */
    private fun getByteStringLength(string: String): Int {
        val stringArray = string.split(Const.VALUETYPE_SIZE_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        try {
            return stringArray[1].toInt()
        } catch (e: NumberFormatException) {
            logger.warn(
                "Not able to get ValueType length from String. Set length to minimal lenght "
                        + Const.VALUE_SIZE_MINIMAL + "."
            )
        }
        return Const.VALUE_SIZE_MINIMAL
    }

    /**
     * Attempt to find the latest record within the given map of records
     *
     * @param recordsMap
     * map as given by [org.openmuc.framework.datalogger.ascii.LogFileReader.getValues]
     * @return Map of channelId and latest Record for that channel, empty map if non-existent
     */
    @JvmStatic
    fun findLatestValue(recordsMap: Map<String?, MutableList<Record?>?>?): Map<String?, Record> {
        val recordMap: MutableMap<String?, Record> = HashMap()
        for ((key, records) in recordsMap!!) {
            // find the latest record
            var latestTimestamp: Long = 0
            var latestRecord: Record? = null
            for (record in records!!) {
                if (record!!.timestamp!! > latestTimestamp) {
                    latestRecord = record
                    latestTimestamp = record.timestamp!!
                }
            }
            // only add the latest Record to the map
            if (latestRecord != null) {
                recordMap[key] = latestRecord
            }
        }
        return recordMap
    }

    /**
     * Gets all files with the .dat extension from this folder
     *
     * @return list of all data files in the folder
     */
    @JvmStatic
    fun getAllDataFiles(directoryPath: String?): List<File>? {
        val dir = File(directoryPath)
        val allFiles = dir.listFiles()
        val files: MutableList<File> = LinkedList()
        if (allFiles == null || allFiles.size == 0) {
            logger.error("No file found in $directoryPath")
            return null
        }
        for (file in allFiles) {
            val fileName = file.name
            if (fileName.endsWith(Const.EXTENSION)) {
                files.add(file)
            }
        }
        return files
    }

    /**
     * Get the date of the file with given fileName by parsing. The file name must start with the date in YYYYMMDD
     * format.
     *
     * @param fileName
     * of the file to be parsed. Must start with the date in "YYYYMMDD" format
     * @return parsed Date
     * @throws ParseException
     * when parsing of the file fails.
     */
    @JvmStatic
    @Throws(ParseException::class)
    fun getDateOfFile(fileName: String): Date {
        val dateString = fileName.substring(0, 8)
        val pattern = "yyyyMMdd"
        return SimpleDateFormat(pattern).parse(dateString)
    }

    /**
     * Get the latest file of a list of files by comparing file names The file name must start with the date in YYYYMMDD
     * format.
     *
     * @param files
     * @return file with the latest date
     */
    @JvmStatic
    fun getLatestFile(files: List<File?>): File? {
        var latestTimestamp: Long = 0
        var latestFile: File? = null
        for (file in files) {
            var timestamp: Long = 0
            timestamp = try {
                val fileName = file!!.name
                getDateOfFile(fileName).time
            } catch (ex: ParseException) {
                logger.error("Data file could not be parsed... continuing with next")
                continue
            }
            if (timestamp > latestTimestamp) {
                latestTimestamp = timestamp
                latestFile = file
            }
        }
        return latestFile
    }
}
