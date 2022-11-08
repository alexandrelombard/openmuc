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
package org.openmuc.framework.datalogger.mqtt.util

import org.openmuc.framework.datalogger.mqtt.dto.MqttLogChannel
import org.openmuc.framework.datalogger.mqtt.dto.MqttLogMsg
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.openmuc.framework.parser.spi.ParserService
import org.openmuc.framework.parser.spi.SerializationException
import org.slf4j.LoggerFactory
import java.util.stream.Collectors

class MqttLogMsgBuilder(
    private val channelsToLog: HashMap<String, MqttLogChannel>,
    private val parserService: ParserService?
) {
    fun buildLogMsg(loggingRecordList: List<LoggingRecord>, isLogMultiple: Boolean): List<MqttLogMsg> {
        return if (isLogMultiple) {
            logMultiple(loggingRecordList)
        } else {
            logSingle(loggingRecordList)
        }
    }

    private fun logSingle(loggingRecords: List<LoggingRecord>): List<MqttLogMsg> {
        val logMessages: MutableList<MqttLogMsg> = ArrayList()
        for (loggingRecord in loggingRecords) {
            try {
                val topic = channelsToLog[loggingRecord.channelId]!!.topic
                val message = parserService!!.serialize(loggingRecord) ?: byteArrayOf()
                logMessages.add(MqttLogMsg(loggingRecord.channelId, message, topic))
            } catch (e: SerializationException) {
                logger.error("failed to parse records {}", e.message)
            }
        }
        return logMessages
    }

    private fun logMultiple(loggingRecords: List<LoggingRecord>): List<MqttLogMsg> {
        val logMessages: MutableList<MqttLogMsg> = ArrayList()
        if (hasDifferentTopics()) {
            throw UnsupportedOperationException(
                "logMultiple feature is an experimental feature: logMultiple=true is not possible with "
                        + "different topics in logSettings. Set logMultiple=false OR leave it true "
                        + "and assign same topic to all channels."
            )

            // TODO make improvement: check only for given channels

            // TODO make improvement:
            // CASE A - OK
            // ch1, ch2, ch3 = 5 s - topic1
            // CASE B - NOT SUPPORTED YET
            // ch1, ch2 logInterval = 5 s - topic1
            // ch3, ch3 logInterval = 10 s - topic2
            // ch4 logInterval 20 s - topic 3
            // if isLogMultiple=true, then group channels per topic
            // or default: log warning and use logSingle instead
        } else {
            try {
                // since all topics are the same, get the topic of
                val topic = channelsToLog[loggingRecords[0].channelId]!!.topic
                val message = parserService!!.serialize(loggingRecords) ?: byteArrayOf()
                val channelIds = loggingRecords.stream()
                    .map { record: LoggingRecord? -> record!!.channelId }
                    .collect(Collectors.toList())
                    .toString()
                logMessages.add(MqttLogMsg(channelIds, message, topic))
            } catch (e: SerializationException) {
                logger.error("failed to parse records {}", e.message)
            }
        }
        return logMessages
    }

    private fun hasDifferentTopics(): Boolean {
        val distinct = channelsToLog.values.stream().map { channel: MqttLogChannel -> channel.topic }.distinct().count()
        // If the count of this stream is smaller or equal to 1, then all the elements are equal. so > 1 means unequal
        return distinct > 1
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttLogMsgBuilder::class.java)
    }
}
