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

import org.openmuc.framework.data.Record
import org.openmuc.framework.datalogger.mqtt.dto.MqttLogChannel
import org.openmuc.framework.datalogger.mqtt.dto.MqttLogMsg
import org.openmuc.framework.datalogger.mqtt.util.MqttLogMsgBuilder
import org.openmuc.framework.datalogger.spi.DataLoggerService
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.openmuc.framework.lib.mqtt.MqttConnection
import org.openmuc.framework.lib.mqtt.MqttSettings
import org.openmuc.framework.lib.mqtt.MqttWriter
import org.openmuc.framework.lib.osgi.config.*
import org.openmuc.framework.parser.spi.ParserService
import org.openmuc.framework.security.SslManagerInterface
import org.osgi.service.cm.ManagedService
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import java.util.stream.Collectors

class MqttLogger : DataLoggerService, ManagedService {
    private val channelsToLog = HashMap<String?, MqttLogChannel>()
    private val availableParsers = HashMap<String?, ParserService>()
    private val propertyHandler: PropertyHandler
    private var parser: String? = null
    private var isLogMultiple = false
    private var mqttWriter: MqttWriter
    private var sslManager: SslManagerInterface? = null
    private var configLoaded = false

    init {
        val pid = MqttLogger::class.java.name
        val settings = MqttLoggerSettings()
        propertyHandler = PropertyHandler(settings, pid)
        val mqttSettings = createMqttSettings()
        val connection = MqttConnection(mqttSettings)
        mqttWriter = MqttWriter(connection, this.id)
    }

    override fun setChannelsToLog(logChannels: List<LogChannel?>?) {
        // FIXME Datamanger should only pass logChannels which should be logged by MQTT Logger
        // right now all channels are passed to the data logger and dataloger has to
        // decide/parse which channels it hast to log
        channelsToLog.clear()
        for (logChannel in logChannels!!) {
            if (logChannel!!.loggingSettings!!.contains(Companion.id)) {
                val mqttLogChannel = MqttLogChannel(logChannel)
                channelsToLog[logChannel.id] = mqttLogChannel
            }
        }
        printChannelsConsideredByMqttLogger(logChannels)
    }

    /**
     * mainly for debugging purposes
     */
    private fun printChannelsConsideredByMqttLogger(logChannels: List<LogChannel?>?) {
        val mqttLogChannelsSb = StringBuilder()
        mqttLogChannelsSb.append("channels configured for mqttlogging:\n")
        channelsToLog.keys.stream().forEach { channelId: String? -> mqttLogChannelsSb.append(channelId).append("\n") }
        val nonMqttLogChannelsSb = StringBuilder()
        nonMqttLogChannelsSb.append("channels not configured for mqttlogger:\n")
        for (logChannel in logChannels!!) {
            if (!logChannel!!.loggingSettings!!.contains(Companion.id)) {
                nonMqttLogChannelsSb.append(logChannel.id).append("\n")
            }
        }
        logger.debug(mqttLogChannelsSb.toString())
        logger.debug(nonMqttLogChannelsSb.toString())
    }

    override fun logEvent(containers: List<LoggingRecord?>?, timestamp: Long) {
        log(containers, timestamp)
    }

    override fun logSettingsRequired(): Boolean {
        return true
    }

    override fun log(loggingRecordList: List<LoggingRecord?>?, timestamp: Long) {
        if (!isLoggerReady) {
            logger.warn("Skipped logging values, still loading")
            return
        }

        // logger.info("============================");
        // loggingRecordList.stream().map(LoggingRecord::getChannelId).forEach(id -> logger.info(id));

        // FIXME refactor OpenMUC core - actually the datamanager should only call logger.log()
        // with channels configured for this logger. If this is the case the containsKey check could be ignored
        // The filter serves as WORKAROUND to process only channels which were configured for mqtt logger
        val logRecordsForMqttLogger = loggingRecordList!!.stream()
            .filter { record: LoggingRecord? ->
                channelsToLog.containsKey(
                    record!!.channelId
                )
            }
            .collect(Collectors.toList())

        // channelsToLog.values().stream().map(channel -> channel.topic).distinct().count();

        // Concept of the MqttLogMsgBuilder:
        // 1. cleaner code
        // 2. better testability: MqttLogMsgBuilder can be easily created in a test and the output of
        // MqttLogMsgBuilder.build() can be verified. It takes the input from logger.log() method, processes it
        // and creates ready to use messages for the mqttWriter
        val logMsgBuilder = MqttLogMsgBuilder(channelsToLog, availableParsers[parser])
        val logMessages = logMsgBuilder.buildLogMsg(logRecordsForMqttLogger, isLogMultiple)
        for (msg in logMessages!!) {
            logTraceMqttMessage(msg)
            mqttWriter.write(msg!!.topic, msg.message)
        }
    }

    private fun logTraceMqttMessage(msg: MqttLogMsg?) {
        if (logger.isTraceEnabled) {
            logger.trace("{}\n{}: {}", msg!!.channelId, msg.topic, String(msg.message!!))
        }
    }

    private val isParserAvailable: Boolean
        private get() {
            if (availableParsers.containsKey(parser)) {
                return true
            }
            logger.warn("Parser with parserId {} is not available.", parser)
            return false
        }
    private val isLoggerReady: Boolean
        private get() = mqttWriter.connection.isReady && configLoaded && isParserAvailable

    @Throws(IOException::class)
    override fun getRecords(channelId: String?, startTime: Long, endTime: Long): List<Record?>? {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun getLatestLogRecord(channelId: String?): Record? {
        throw UnsupportedOperationException()
    }

    /**
     * Connect to MQTT broker
     */
    private fun connect() {
        val settings = createMqttSettings()
        val connection = MqttConnection(settings)
        connection.setSslManager(sslManager)
        mqttWriter = MqttWriter(connection, this.id)
        if (settings.isSsl) {
            if (isLoggerReady) {
                logger.info("Connecting to MQTT Broker")
                mqttWriter.connection.connect()
            } else {
                logger.info("Writer is not ready yet")
            }
        } else {
            logger.info("Connecting to MQTT Broker")
            mqttWriter.connection.connect()
        }
    }

    private fun createMqttSettings(): MqttSettings {
        // @formatter:off
        val settings = MqttSettings(
            propertyHandler.getString(MqttLoggerSettings.Companion.HOST),
            propertyHandler.getInt(MqttLoggerSettings.Companion.PORT),
            propertyHandler.getString(MqttLoggerSettings.Companion.USERNAME),
            propertyHandler.getString(MqttLoggerSettings.Companion.PASSWORD),
            propertyHandler.getBoolean(MqttLoggerSettings.Companion.SSL),
            propertyHandler.getInt(MqttLoggerSettings.Companion.MAX_BUFFER_SIZE).toLong(),
            propertyHandler.getInt(MqttLoggerSettings.Companion.MAX_FILE_SIZE).toLong(),
            propertyHandler.getInt(MqttLoggerSettings.Companion.MAX_FILE_COUNT),
            propertyHandler.getInt(MqttLoggerSettings.Companion.CONNECTION_RETRY_INTERVAL),
            propertyHandler.getInt(MqttLoggerSettings.Companion.CONNECTION_ALIVE_INTERVAL),
            propertyHandler.getString(MqttLoggerSettings.Companion.PERSISTENCE_DIRECTORY),
            propertyHandler.getString(MqttLoggerSettings.Companion.LAST_WILL_TOPIC),
            propertyHandler.getString(MqttLoggerSettings.Companion.LAST_WILL_PAYLOAD).toByteArray(),
            propertyHandler.getBoolean(MqttLoggerSettings.Companion.LAST_WILL_ALWAYS),
            propertyHandler.getString(MqttLoggerSettings.Companion.FIRST_WILL_TOPIC),
            propertyHandler.getString(MqttLoggerSettings.Companion.FIRST_WILL_PAYLOAD).toByteArray(),
            propertyHandler.getInt(MqttLoggerSettings.Companion.RECOVERY_CHUNK_SIZE),
            propertyHandler.getInt(MqttLoggerSettings.Companion.RECOVERY_DELAY),
            propertyHandler.getBoolean(MqttLoggerSettings.Companion.WEB_SOCKET)
        )
        // @formatter:on
        logger.info("MqttSettings for MqttConnection \n", settings.toString())
        return settings
    }

    override fun updated(propertyDict: Dictionary<String?, *>?) {
        val dict = DictionaryPreprocessor(propertyDict)
        if (!dict.wasIntermediateOsgiInitCall()) {
            tryProcessConfig(dict)
        }
    }

    private fun tryProcessConfig(newConfig: DictionaryPreprocessor) {
        try {
            propertyHandler.processConfig(newConfig)
            if (!propertyHandler.configChanged() && propertyHandler.isDefaultConfig) {
                // tells us:
                // 1. if we get till here then updated(dict) was processed without errors and
                // 2. the values from cfg file are identical to the default values
                // logger.info("new properties: changed={}, isDefault={}", propertyHandler.configChanged(),
                // propertyHandler.isDefaultConfig());
                applyConfigChanges()
            }
            if (propertyHandler.configChanged()) {
                applyConfigChanges()
            }
        } catch (e: ServicePropertyException) {
            logger.error("update properties failed", e)
            shutdown()
        }
    }

    private fun applyConfigChanges() {
        configLoaded = true
        logger.info("Configuration changed - new configuration {}", propertyHandler.toString())
        parser = propertyHandler.getString(MqttLoggerSettings.Companion.PARSER)
        isLogMultiple = propertyHandler.getBoolean(MqttLoggerSettings.Companion.MULTIPLE)
        shutdown()
        connect()
    }

    fun shutdown() {
        // Saves RAM buffer to file and terminates running reconnects
        mqttWriter.shutdown()
        if (!mqttWriter.isConnected && mqttWriter.isInitialConnect) {
            return
        }
        logger.info("closing MQTT connection")
        if (mqttWriter.isConnected) {
            mqttWriter.connection.disconnect()
        }
    }

    fun addParser(parserId: String?, parserService: ParserService) {
        logger.info("put parserID {} to PARSERS", parserId)
        availableParsers[parserId] = parserService
    }

    fun removeParser(parserId: String?) {
        availableParsers.remove(parserId)
    }

    fun setSslManager(instance: SslManagerInterface?) {
        sslManager = instance
        mqttWriter.connection.setSslManager(sslManager)
        // if sslManager is already loaded, then connect
        if (sslManager!!.isLoaded) {
            shutdown()
            connect()
        }
        // else mqttConnection connects automatically
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttLogger::class.java)
        val id = "mqttlogger"
            get() = Companion.field
    }
}
