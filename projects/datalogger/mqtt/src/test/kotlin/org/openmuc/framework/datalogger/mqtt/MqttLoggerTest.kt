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
package org.openmuc.framework.datalogger.mqtt

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.openmuc.framework.datalogger.mqtt.MqttLogger
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.openmuc.framework.lib.mqtt.MqttConnection
import org.openmuc.framework.lib.mqtt.MqttSettings
import org.openmuc.framework.lib.mqtt.MqttWriter
import org.openmuc.framework.lib.osgi.config.PropertyHandler
import java.util.*

//FIXME provide unit tests
class MqttLoggerTest {
    // FIXME
    @Disabled
    @Test
    fun testDifferentTopics() {
        val logger = MqttLogger()
        val logChannelMock1 = Mockito.mock(LogChannel::class.java)
        Mockito.`when`(logChannelMock1.id).thenReturn("Channel1")
        Mockito.`when`(logChannelMock1.loggingSettings).thenReturn("topic1")
        val logChannelMock2 = Mockito.mock(LogChannel::class.java)
        Mockito.`when`(logChannelMock2.id).thenReturn("Channel2")
        Mockito.`when`(logChannelMock2.loggingSettings).thenReturn("topic2")
        val channels: MutableList<LogChannel?> = ArrayList()
        channels.add(logChannelMock1)
        channels.add(logChannelMock2)
        logger.setChannelsToLog(channels)
        val dict: Dictionary<String?, String> = Hashtable()
        dict.put(MqttLoggerSettings.PORT, "1883")
        dict.put(MqttLoggerSettings.HOST, "localhost")
        dict.put(MqttLoggerSettings.SSL, "false")
        dict.put(MqttLoggerSettings.USERNAME, "")
        dict.put(MqttLoggerSettings.PASSWORD, "")
        dict.put(MqttLoggerSettings.PARSER, "openmuc")
        dict.put(MqttLoggerSettings.MULTIPLE, "false")
        dict.put(MqttLoggerSettings.MAX_FILE_COUNT, "1")
        dict.put(MqttLoggerSettings.MAX_FILE_SIZE, "2000")
        dict.put(MqttLoggerSettings.MAX_BUFFER_SIZE, "100")
        logger.updated(dict)
        val records: MutableList<LoggingRecord?> = ArrayList()
        records.add(LoggingRecord("Channel1", null))
        records.add(LoggingRecord("Channel2", null))
        logger.log(records, System.currentTimeMillis())
        Mockito.verify(records[0], Mockito.times(0)).channelId
    }

    /**
     * Complete test of file buffering from logger's point of view.
     *
     *
     * Scenario: Logger connects to a broker, after some while connection to broker is interrupted. Now, logger should
     * log into a file. After some time the connection to the broker is reestablished. Now logger should transfer all
     * buffered messages to the broker and clear the file (buffer) afterwards. At the same time new live logs should be
     * send to the broker as well (in parallel)
     */
    @Disabled
    @Test
    fun testFileBuffering() {

        // Involves: mqtt logger, lib-mqtt, lib-FilePersistence
        // Note: lib-FilePersistence has it own tests for correct parameter handling

        // 1. start logger and connect to a BrokerMock (just print messages to terminal)
        // (executor which calls log every second)

        // 2. log a few messages to terminal

        // 3. interrupt/close connection of BrokerMock

        // 4. logger should log into file. check if it does.

        // 5. reconnect to the BrokerMock

        // 6. empty file buffer and send (historical) messages to broker AND send live log messages to broker as well
    }

    companion object {
        // @BeforeAll
        fun connect() {
            val packageName = MqttLogger::class.java.getPackage().name.lowercase(Locale.getDefault())
            System.setProperty("$packageName.host", "localhost")
            System.setProperty("$packageName.port", "1883")
            System.setProperty("$packageName.username", "guest")
            System.setProperty("$packageName.password", "guest")
            System.setProperty("$packageName.topic", "device/data")
            System.setProperty("$packageName.maxFileCount", "2")
            System.setProperty("$packageName.maxFileSize", "1")
            System.setProperty("$packageName.maxBufferSize", "1")
            val pid = MqttLogger::class.java.name
            val settings = MqttLoggerSettings()
            val propertyHandler = PropertyHandler(settings, pid)
            val Mqttsettings = MqttSettings(
                propertyHandler.getString(MqttLoggerSettings.HOST),
                propertyHandler.getInt(MqttLoggerSettings.PORT), propertyHandler.getString(MqttLoggerSettings.USERNAME),
                propertyHandler.getString(MqttLoggerSettings.PASSWORD),
                propertyHandler.getBoolean(MqttLoggerSettings.SSL),
                propertyHandler.getInt(MqttLoggerSettings.MAX_BUFFER_SIZE).toLong(),
                propertyHandler.getInt(MqttLoggerSettings.MAX_FILE_SIZE).toLong(),
                propertyHandler.getInt(MqttLoggerSettings.MAX_FILE_COUNT),
                propertyHandler.getInt(MqttLoggerSettings.CONNECTION_RETRY_INTERVAL),
                propertyHandler.getInt(MqttLoggerSettings.CONNECTION_ALIVE_INTERVAL),
                propertyHandler.getString(MqttLoggerSettings.PERSISTENCE_DIRECTORY)
            )
            val connection = MqttConnection(Mqttsettings)
            val mqttWriter = MqttWriter(connection, "mqttlogger")
            mqttWriter.connection.connect()
        }
    }
}
