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

import org.slf4j.LoggerFactory
import java.util.*
import javax.management.openmbean.InvalidKeyException

//TODO parsing settings should be part of core.datalogger.spi Format and parsing of datalogger
// logSettings should be equal for all loggers
object MqttChannelLogSettings {
    private const val LOGGER_SEPARATOR = ";"
    private const val ELEMENT_SEPARATOR = ","
    private val logger = LoggerFactory.getLogger(MqttChannelLogSettings::class.java)
    @JvmStatic
    fun getTopic(logSettings: String?): String {
        return if (logSettings == null || logSettings.isEmpty()) {
            throw UnsupportedOperationException("TODO implement default Topic?")
        } else {
            parseTopic(logSettings)
        }
    }

    // Example logSettings
    // 1 <logSettings">amqplogger:queue=my/queue,setting=true,test=123;mqttlogger:topic=/my/topic/</logSettings>
    // 2 amqplogger:queue=my/queue,setting=true,test=123; mqttlogger:topic=/my/topic/
    // 3 mqttlogger:topic=/my/topic/
    // 4 topic=/my/topic/
    private fun parseTopic(logSettings: String): String {
        val mqttLoggerSegment =
            Arrays.stream(logSettings.split(LOGGER_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
                .filter { seg: String -> seg.contains("mqttlogger") }
                .findFirst()
                .orElseThrow { InvalidKeyException("logSettings: mqttlogger id is missing") }
        return Arrays.stream(mqttLoggerSegment.split(ELEMENT_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray())
            .filter { part: String -> part.contains("topic") }
            .map { queue: String ->
                queue.split("=".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()[1]
            }
            .findFirst()
            .orElseThrow { InvalidKeyException("logSettings: topic is missing") }
    }
}
