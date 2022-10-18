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

import org.openmuc.framework.data.Record
import org.openmuc.framework.data.Record.value
import org.openmuc.framework.datalogger.ascii.utils.Const
import org.openmuc.framework.datalogger.ascii.utils.LoggerUtils
import org.openmuc.framework.datalogger.spi.DataLoggerService
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.slf4j.LoggerFactory
import java.io.*
import java.util.*

@Component
class AsciiLogger : DataLoggerService {
    private val loggerDirectory: String?
    private val logChannelList = HashMap<String?, LogChannel?>()
    private var isFillUpFiles = true

    constructor() {
        loggerDirectory = if (DIRECTORY == null) {
            Const.DEFAULT_DIRECTORY
        } else {
            DIRECTORY.trim { it <= ' ' }
        }
        createDirectory(loggerDirectory)
    }

    constructor(loggerDirectory: String?) {
        this.loggerDirectory = loggerDirectory
        createDirectory(loggerDirectory)
    }

    @Activate
    protected fun activate(context: ComponentContext?) {
        logger.info("Activating Ascii Logger")
        setSystemProperties()
    }

    @Deactivate
    protected fun deactivate(context: ComponentContext?) {
        logger.info("Deactivating Ascii Logger")
    }

    private fun createDirectory(loggerDirectory: String?) {
        logger.trace("using directory: {}", loggerDirectory)
        val asciidata = File(loggerDirectory)
        if (!asciidata.exists() && !asciidata.mkdirs()) {
            logger.error("Could not create logger directory: " + asciidata.absolutePath)
            // TODO: weitere Behandlung,
        }
    }

    override val id: String
        get() = "asciilogger"

    /**
     * Will called if OpenMUC starts the logger
     */
    override fun setChannelsToLog(logChannels: List<LogChannel?>?) {
        val calendar: Calendar = GregorianCalendar(Locale.getDefault())
        logChannelList.clear()
        logger.trace("channels to log:")
        for (logChannel in logChannels!!) {
            if (logger.isTraceEnabled) {
                logger.trace("channel.getId() " + logChannel!!.id)
                logger.trace("channel.getLoggingInterval() " + logChannel.loggingInterval)
            }
            logChannelList[logChannel!!.id] = logChannel
        }
        if (isFillUpFiles) {
            val areHeaderIdentical = LoggerUtils.areHeadersIdentical(
                loggerDirectory, logChannels,
                calendar
            )
            for ((key, isHeaderIdentical) in areHeaderIdentical!!) {
                if (isHeaderIdentical) {
                    // Fill file up with error flag 32 (DATA_LOGGING_NOT_ACTIVE)
                    if (logger.isTraceEnabled) {
                        logger.trace(
                            "Fill file " + LoggerUtils.buildFilename(key, calendar) + " up with error flag 32."
                        )
                    }
                    fillUpFileWithErrorCode(loggerDirectory, key, calendar)
                } else {
                    // rename file in old file (if file is existing), because of configuration has
                    // changed
                    LoggerUtils.renameFileToOld(loggerDirectory, key, calendar)
                }
            }
        } else {
            LoggerUtils.renameAllFilesToOld(loggerDirectory, calendar)
        }
    }

    @Synchronized
    override fun log(loggingRecords: List<LoggingRecord?>?, timestamp: Long) {
        val logIntervalGroups = HashMap<List<Int>, LogIntervalContainerGroup>()

        // add each container to a group with the same logging interval
        for (container in loggingRecords!!) {
            var logInterval = -1
            var logTimeOffset = 0
            var logTimeArray = Arrays.asList(logInterval, logTimeOffset)
            val channelId = container!!.channelId
            if (logChannelList.containsKey(channelId)) {
                logInterval = logChannelList[channelId]!!.loggingInterval!!
                logTimeOffset = logChannelList[channelId]!!.loggingTimeOffset!!
                logTimeArray = Arrays.asList(logInterval, logTimeOffset)
            } else {
                // TODO there might be a change in the channel config file
            }
            if (logIntervalGroups.containsKey(logTimeArray)) {
                // add the container to an existing group
                val group = logIntervalGroups[logTimeArray]
                group!!.add(container)
            } else {
                // create a new group and add the container
                val group = LogIntervalContainerGroup()
                group.add(container)
                logIntervalGroups[logTimeArray] = group
            }
        }

        // alle gruppen loggen
        val it: Iterator<Map.Entry<List<Int>, LogIntervalContainerGroup>> = logIntervalGroups.entries.iterator()
        var logTimeArray: List<Int>
        val calendar: Calendar = GregorianCalendar(Locale.getDefault())
        while (it.hasNext()) {
            logTimeArray = it.next().key
            val group = logIntervalGroups[logTimeArray]
            val fileOutHandler = LogFileWriter(loggerDirectory, isFillUpFiles)
            calendar.timeInMillis = timestamp
            fileOutHandler.log(group, logTimeArray[0], logTimeArray[1], calendar, logChannelList)
            setLastLoggedLineTimeStamp(logTimeArray[0], logTimeArray[1], calendar.timeInMillis)
        }
    }

    @Throws(IOException::class)
    override fun getRecords(channelId: String?, startTime: Long, endTime: Long): List<Record?>? {
        val logChannel = logChannelList[channelId]
        var reader: LogFileReader? = null
        return if (logChannel != null) {
            reader = LogFileReader(loggerDirectory, logChannel)
            reader.getValues(startTime, endTime)[channelId]
        } // TODO: hier einfuegen, dass nach Logdateien gesucht werden soll, die vorhanden
        else {
            throw IOException("ChannelID ($channelId) not available. It's not a logging Channel.")
        }
    }

    /**
     * Get the latest logged Record for the given value. This is achieved by searching within a few times the
     * loggingInterval from the current time for any record and then selecting the one with the highest timestamp
     *
     * @param channelId
     * to be searched
     * @return latest Record
     */
    @Throws(IOException::class)
    override fun getLatestLogRecord(channelId: String?): Record? {
        val logChannel = logChannelList[channelId]
        var reader: LogFileReader? = null
        if (logChannel == null) {
            throw IOException("ChannelID ($channelId) not available. It's not a logging Channel.")
        }
        reader = LogFileReader(loggerDirectory, logChannel)
        // attempt to find a record within the last day
        val endTime = System.currentTimeMillis()
        val startTime = endTime - MS_PER_DAY
        var recordsMap = reader.getValues(startTime, endTime)
        var latestRecordsMap = LoggerUtils.findLatestValue(recordsMap)
        var record = latestRecordsMap!![channelId]
        if (record != null) {
            return record
        }

        // Fallback: read all files and find the latest record within these
        val files = LoggerUtils.getAllDataFiles(loggerDirectory) ?: return null
        val file = LoggerUtils.getLatestFile(files) ?: return null
        recordsMap = reader.getValues(file.path)
        latestRecordsMap = LoggerUtils.findLatestValue(recordsMap)
        record = latestRecordsMap[channelId]
        return record
    }

    private fun setSystemProperties() {

        // FIXME: better to use a constant here instead of dynamic name in case the
        // package name changes in future then
        // the system.properties entry will be out dated.
        val fillUpPropertyStr =
            AsciiLogger::class.java.getPackage().name.lowercase(Locale.getDefault()) + ".fillUpFiles"
        val fillUpProperty = System.getProperty(fillUpPropertyStr)
        if (fillUpProperty != null) {
            isFillUpFiles = java.lang.Boolean.parseBoolean(fillUpProperty)
            logger.debug("Property: {} is set to {}", fillUpPropertyStr, isFillUpFiles)
        } else {
            logger.debug("Property: {} not found in system.properties. Using default value: true", fillUpPropertyStr)
            isFillUpFiles = true
        }
    }

    override fun logEvent(containers: List<LoggingRecord?>?, timestamp: Long) {
        logger.warn("Event logging is not implemented, yet.")
    }

    override fun logSettingsRequired(): Boolean {
        return false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AsciiLogger::class.java)
        private val DIRECTORY = System
            .getProperty(AsciiLogger::class.java.getPackage().name.lowercase(Locale.getDefault()) + ".directory")
        private val lastLoggedLineList = HashMap<String?, Long>()
        private const val MS_PER_DAY: Long = 86400000
        fun getLastLoggedLineTimeStamp(loggingInterval: Int, loggingOffset: Int): Long? {
            return lastLoggedLineList[loggingInterval.toString() + Const.TIME_SEPERATOR_STRING + loggingOffset]
        }

        fun setLastLoggedLineTimeStamp(loggerInterval_loggerTimeOffset: String?, lastTimestamp: Long) {
            lastLoggedLineList[loggerInterval_loggerTimeOffset] = lastTimestamp
        }

        @JvmStatic
        fun setLastLoggedLineTimeStamp(loggingInterval: Int, loggingOffset: Int, lastTimestamp: Long) {
            lastLoggedLineList[loggingInterval.toString() + Const.TIME_SEPERATOR_STRING + loggingOffset] = lastTimestamp
        }

        @JvmStatic
        fun fillUpFileWithErrorCode(
            directoryPath: String?, loggerInterval_loggerTimeOffset: String?,
            calendar: Calendar?
        ): Long {
            val filename = LoggerUtils.buildFilename(loggerInterval_loggerTimeOffset, calendar)
            val file = File(directoryPath + filename)
            val raf = LoggerUtils.getRandomAccessFile(file, "r")
            var out: PrintWriter? = null
            var firstLogLine = ""
            var lastLogLine = ""
            var loggingInterval: Long = 0
            loggingInterval = if (loggerInterval_loggerTimeOffset!!.contains(Const.TIME_SEPERATOR_STRING)) {
                loggerInterval_loggerTimeOffset.split(Const.TIME_SEPERATOR_STRING.toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()[0].toLong()
            } else {
                loggerInterval_loggerTimeOffset.toLong()
            }
            var lastLogLineTimeStamp: Long = 0
            if (raf != null) {
                try {
                    var line = raf.readLine()
                    if (line != null) {
                        while (line!!.startsWith(Const.COMMENT_SIGN)) {
                            // do nothing with this data, only for finding the begin of logging
                            line = raf.readLine()
                        }
                        firstLogLine = raf.readLine()
                    }

                    // read last line backwards and read last line
                    val readedByte = ByteArray(1)
                    var filePosition = file.length() - 2
                    var charString: String
                    while (lastLogLine.isEmpty() && filePosition > 0) {
                        raf.seek(filePosition)
                        val readedBytes = raf.read(readedByte)
                        if (readedBytes == 1) {
                            charString = String(readedByte, Const.CHAR_SET)
                            if (charString == Const.LINESEPARATOR_STRING) {
                                lastLogLine = raf.readLine()
                            } else {
                                filePosition -= 1
                            }
                        } else {
                            filePosition = -1 // leave the while loop
                        }
                    }
                    raf.close()
                    val firstLogLineLength = firstLogLine.length
                    val lastLogLineLength = lastLogLine.length
                    if (firstLogLineLength != lastLogLineLength) {
                        /**
                         * TODO: different size of logging lines, probably the last one is corrupted we have to fill it up
                         * restOfLastLine = completeLastLine(firstLogLine, lastLogLine); raf.writeChars(restOfLastLine);
                         */
                        // File is corrupted rename to old
                        LoggerUtils.renameFileToOld(directoryPath, loggerInterval_loggerTimeOffset, calendar)
                        logger.error("File is coruppted, could not fill up, renamed it. " + file.absolutePath)
                        return 0L
                    } else {
                        val lastLogLineArray =
                            lastLogLine.split(Const.SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        val errorValues = LoggerUtils.getErrorValues(lastLogLineArray)
                        lastLogLineTimeStamp = (lastLogLineArray[2].toDouble() * 1000.0).toLong()
                        out = LoggerUtils.getPrintWriter(file, true)
                        var numberOfFillUpLines = LoggerUtils.getNumberOfFillUpLines(
                            lastLogLineTimeStamp,
                            loggingInterval
                        )
                        while (numberOfFillUpLines > 0) {
                            lastLogLineTimeStamp = LoggerUtils.fillUp(
                                out, lastLogLineTimeStamp, loggingInterval,
                                numberOfFillUpLines, errorValues
                            )
                            numberOfFillUpLines =
                                LoggerUtils.getNumberOfFillUpLines(lastLogLineTimeStamp, loggingInterval)
                        }
                        out.close()
                        setLastLoggedLineTimeStamp(loggerInterval_loggerTimeOffset, lastLogLineTimeStamp)
                    }
                } catch (e: IOException) {
                    logger.error("Could not read file " + file.absolutePath, e)
                    LoggerUtils.renameFileToOld(directoryPath, loggerInterval_loggerTimeOffset, calendar)
                } finally {
                    try {
                        raf.close()
                        out?.close()
                    } catch (e: IOException) {
                        logger.error("Could not close file " + file.absolutePath)
                    }
                }
            }
            return lastLogLineTimeStamp
        }
    }
}
