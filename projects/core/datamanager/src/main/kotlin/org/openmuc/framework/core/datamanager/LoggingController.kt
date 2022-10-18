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
package org.openmuc.framework.core.datamanager

import org.openmuc.framework.data.Record
import org.openmuc.framework.dataaccess.*
import org.openmuc.framework.datalogger.spi.DataLoggerService
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.slf4j.LoggerFactory
import java.util.*
import java.util.stream.Collectors

class LoggingController(private val activeDataLoggers: Deque<DataLoggerService>) {
    private var logContainerMap: MutableMap<String, MutableList<LoggingRecord>?>? = null
    fun channelsHaveToBeLogged(currentAction: Action): Boolean {
        return currentAction.loggingCollections != null && !currentAction.loggingCollections!!.isEmpty()
    }

    fun triggerLogging(currentAction: Action): List<Optional<ChannelCollection?>> {
        initLoggingRecordMap()
        val filledChannels: MutableList<Optional<ChannelCollection?>> = ArrayList()
        for (loggingCollection in currentAction.loggingCollections!!) {
            val toRemove: MutableList<ChannelImpl?> = LinkedList()
            for (channel in loggingCollection!!.channels!!) {
                if (channel.getChannelState() === ChannelState.DELETED) {
                    toRemove.add(channel)
                } else if (!channel!!.config!!.isDisabled()) {
                    fillLoggingRecordMapWithChannel(channel)
                }
            }
            for (channel in toRemove) {
                loggingCollection.channels!!.remove(channel)
            }
            if (loggingCollection.channels != null && !loggingCollection.channels!!.isEmpty()) {
                filledChannels.add(Optional.of(loggingCollection))
            }
        }
        deliverLogsToLogServices(currentAction.startTime)
        return filledChannels
    }

    fun deliverLogsToEventBasedLogServices(channelRecordContainerList: List<ChannelRecordContainerImpl>) {
        initLoggingRecordMap()
        channelRecordContainerList.stream()
            .forEach { channelRecord: ChannelRecordContainerImpl -> fillLoggingRecordMapWithChannel(channelRecord.getChannel()) }
        for (dataLogger in activeDataLoggers) {
            val logContainers: List<LoggingRecord>? = logContainerMap!![dataLogger.id]
            if (!logContainers!!.isEmpty()) {
                dataLogger.logEvent(logContainers, System.currentTimeMillis())
            }
        }
    }

    private fun initLoggingRecordMap() {
        logContainerMap = HashMap()
        for (dataLogger in activeDataLoggers) {
            logContainerMap[dataLogger.id] = ArrayList()
        }
    }

    private fun fillLoggingRecordMapWithChannel(channel: ChannelImpl?) {
        val logSettings: String = channel.getLoggingSettings()
        if (logSettings != null && !logSettings.isEmpty()) {
            extendMapForDefinedLoggerFromSettings(channel, logSettings)
        } else {
            addRecordToAllLoggerWhichNotRequiresSettings(channel)
        }
    }

    private fun addRecordToAllLoggerWhichNotRequiresSettings(channel: Channel?) {
        val latestRecord = channel!!.latestRecord
        logContainerMap!!.forEach { (k: String, v: MutableList<LoggingRecord>?) ->
            if (loggerWithIdNotRequiresSettings(k)) {
                v!!.add(LoggingRecord(channel.id, latestRecord))
            }
        }
    }

    private fun loggerWithIdNotRequiresSettings(loggerId: String): Boolean {
        return activeDataLoggers.stream()
            .filter { obj: DataLoggerService -> obj.logSettingsRequired() }
            .map { logger: DataLoggerService -> logger.id }
            .noneMatch { filteredId: String -> filteredId == loggerId }
    }

    private fun extendMapForDefinedLoggerFromSettings(channel: ChannelImpl?, logSettings: String) {
        val definedLoggerInChannel = parseDefinedLogger(logSettings)
        for (definedLogger in definedLoggerInChannel) {
            if (logContainerMap!![definedLogger] != null) {
                val latestRecord: Record = channel.getLatestRecord()
                logContainerMap!![definedLogger]!!.add(LoggingRecord(channel.getId(), latestRecord))
            } else {
                logger.warn(
                    "DataLoggerService with Id {} not found for channel {}", definedLogger,
                    channel!!.config!!.getId()
                )
                logger.warn("Correct configuration in channel.xml?")
            }
        }
    }

    private fun parseDefinedLogger(logSettings: String): List<String> {
        val loggerSegments =
            logSettings.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return Arrays.stream(loggerSegments)
            .map { seg: String -> seg.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0] }
            .collect(Collectors.toList())
    }

    private fun deliverLogsToLogServices(startTime: Long) {
        for (dataLogger in activeDataLoggers) {
            val logContainers: List<LoggingRecord>? = logContainerMap!![dataLogger.id]
            dataLogger.log(logContainers, startTime)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LoggingController::class.java)
    }
}
