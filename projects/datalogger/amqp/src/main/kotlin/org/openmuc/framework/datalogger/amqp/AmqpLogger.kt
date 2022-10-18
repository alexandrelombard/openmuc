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
package org.openmuc.framework.datalogger.amqp

import com.google.gson.Gson
import org.openmuc.framework.data.Record
import org.openmuc.framework.datalogger.spi.DataLoggerService
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.openmuc.framework.lib.amqp.AmqpConnection
import org.openmuc.framework.lib.amqp.AmqpSettings
import org.openmuc.framework.lib.amqp.AmqpWriter
import org.openmuc.framework.lib.osgi.config.*
import org.openmuc.framework.parser.spi.ParserService
import org.openmuc.framework.parser.spi.SerializationException
import org.openmuc.framework.security.SslManagerInterface
import org.osgi.service.cm.ConfigurationException
import org.osgi.service.cm.ManagedService
import org.slf4j.LoggerFactory
import java.util.*
import javax.management.openmbean.InvalidKeyException

class AmqpLogger : DataLoggerService, ManagedService {
    private val channelsToLog = HashMap<String?, LogChannel?>()
    private val parsers = HashMap<String, ParserService>()
    private val propertyHandler: PropertyHandler
    private val settings: Settings
    private var writer: AmqpWriter? = null
    private var connection: AmqpConnection? = null
    private var sslManager: SslManagerInterface? = null
    private var configLoaded = false
    private val sslLoaded = false
    private val listening = false

    init {
        val pid = AmqpLogger::class.java.name
        settings = Settings()
        propertyHandler = PropertyHandler(settings, pid)
    }

    override val id: String
        get() = "amqplogger"

    override fun setChannelsToLog(logChannels: List<LogChannel?>?) {
        channelsToLog.clear()
        for (logChannel in logChannels!!) {
            val channelId = logChannel!!.id
            channelsToLog[channelId] = logChannel
        }
    }

    @Synchronized
    override fun log(containers: List<LoggingRecord?>?, timestamp: Long) {
        if (!isLoggerReady) {
            logger.warn("Skipped logging values, still loading")
            return
        }
        if (writer == null) {
            logger.warn("AMQP connection is not established")
            return
        }
        iterateContainersToLog(containers)

        // ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
        // Future future = executor.submit(() -> {
        //
        // });
        //
        // int currentLoggingInterval = channelsToLog.get(containers.get(0).getChannelId()).getLoggingInterval();
        // executor.schedule(() -> {
        // future.cancel(true);
        // logger.warn("Logging execution needs too much time, skipping...");
        // }, currentLoggingInterval, TimeUnit.SECONDS);
    }

    private fun iterateContainersToLog(containers: List<LoggingRecord?>?) {
        for (loggingRecord in containers!!) {
            val channelId = loggingRecord!!.channelId
            if (channelsToLog.containsKey(channelId)) {
                executeLog(loggingRecord)
            }
        }
    }

    private fun executeLog(loggingRecord: LoggingRecord?) {
        val channelId = loggingRecord!!.channelId
        val message: ByteArray?
        message =
            if (parsers.containsKey(propertyHandler.getString(Settings.Companion.PARSER))) {
                parseMessage(loggingRecord)
            } else {
                val gson = Gson()
                gson.toJson(loggingRecord.record).toByteArray()
            }
        if (message == null) {
            return
        }
        writer!!.write(getQueueName(channelId), message)
    }

    private fun parseMessage(loggingRecord: LoggingRecord?): ByteArray? {
        return try {
            parsers[propertyHandler.getString(Settings.Companion.PARSER)]!!
                .serialize(loggingRecord)
        } catch (e: SerializationException) {
            logger.error(e.message)
            null
        }
    }

    private fun getQueueName(channelId: String): String {
        val logChannelMeta = channelsToLog[channelId]
        val logSettings = logChannelMeta!!.loggingSettings
        return if (logSettings == null || logSettings.isEmpty()) {
            propertyHandler.getString(Settings.Companion.FRAMEWORK) + channelId
        } else {
            parseDefinedQueue(logSettings)
        }
    }

    private fun parseDefinedQueue(logSettings: String): String {
        val amqpLoggerSegment = Arrays.stream(logSettings.split(";".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray())
            .filter { seg: String -> seg.contains("amqplogger") }
            .map { seg: String -> seg.replace(':', ',') }
            .findFirst()
            .orElseThrow { InvalidKeyException() }
        return Arrays.stream(amqpLoggerSegment.split(",".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray())
            .filter { part: String -> part.contains("queue") }
            .map { queue: String -> queue.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1] }
            .findFirst()
            .orElseThrow { InvalidKeyException() }
    }

    override fun logEvent(loggingRecords: List<LoggingRecord?>?, timestamp: Long) {
        log(loggingRecords, timestamp)
    }

    override fun logSettingsRequired(): Boolean {
        return true
    }

    override fun getRecords(channelId: String?, startTime: Long, endTime: Long): List<Record?>? {
        throw UnsupportedOperationException()
    }

    override fun getLatestLogRecord(channelId: String?): Record? {
        throw UnsupportedOperationException()
    }

    @Throws(ConfigurationException::class)
    override fun updated(propertyDict: Dictionary<String?, *>?) {
        val dict = DictionaryPreprocessor(propertyDict)
        if (!dict.wasIntermediateOsgiInitCall()) {
            tryProcessConfig(dict)
        }
    }

    private fun tryProcessConfig(newConfig: DictionaryPreprocessor) {
        try {
            propertyHandler.processConfig(newConfig)
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
        if (writer != null) {
            shutdown()
        }
        connect()
    }

    private fun connect() {
        if (configLoaded) {
            logger.info("Start connection to amqp backend...")
            val amqpSettings = createAmqpSettings()
            try {
                connection = AmqpConnection(amqpSettings)
                writer = AmqpWriter(connection, id)
                connection!!.setSslManager(sslManager)
            } catch (e: Exception) {
                e.printStackTrace()
                logger.error(e.message)
                logger.error("Check your configuration!")
            }
        }
    }

    private val isLoggerReady: Boolean
        private get() {
            val sslNeeded = propertyHandler.getBoolean(Settings.Companion.SSL)
            return if (sslNeeded) {
                isLoggerReadyForSsl
            } else configLoaded
        }
    private val isLoggerReadyForSsl: Boolean
        private get() = if (sslManager == null) {
            false
        } else configLoaded && sslManager!!.isLoaded

    private fun createAmqpSettings(): AmqpSettings {
        // @formatter:off
        // @formatter:on
        return AmqpSettings(
            propertyHandler.getString(Settings.Companion.HOST),
            propertyHandler.getInt(Settings.Companion.PORT),
            propertyHandler.getString(Settings.Companion.VIRTUAL_HOST),
            propertyHandler.getString(Settings.Companion.USERNAME),
            propertyHandler.getString(Settings.Companion.PASSWORD),
            propertyHandler.getBoolean(Settings.Companion.SSL),
            propertyHandler.getString(Settings.Companion.EXCHANGE),
            propertyHandler.getString(Settings.Companion.PERSISTENCE_DIR),
            propertyHandler.getInt(Settings.Companion.MAX_FILE_COUNT),
            propertyHandler.getInt(Settings.Companion.MAX_FILE_SIZE).toLong(),
            propertyHandler.getInt(Settings.Companion.MAX_BUFFER_SIZE).toLong(),
            propertyHandler.getInt(Settings.Companion.CONNECTION_ALIVE_INTERVAL)
        )
    }

    fun addParser(parserId: String, parserService: ParserService) {
        parsers[parserId] = parserService
    }

    fun removeParser(parserId: String) {
        parsers.remove(parserId)
    }

    fun shutdown() {
        logger.info("closing AMQP connection")
        if (connection != null) {
            writer!!.shutdown()
            connection!!.disconnect()
        }
    }

    fun setSslManager(instance: SslManagerInterface?) {
        sslManager = instance
        connect()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AmqpLogger::class.java)
    }
}
