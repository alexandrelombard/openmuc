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
package org.openmuc.framework.driver.mqtt

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.config.ScanException
import org.openmuc.framework.data.ByteArrayValue
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.openmuc.framework.driver.spi.*
import org.openmuc.framework.lib.mqtt.MqttConnection
import org.openmuc.framework.lib.mqtt.MqttReader
import org.openmuc.framework.lib.mqtt.MqttSettings
import org.openmuc.framework.lib.mqtt.MqttWriter
import org.openmuc.framework.parser.spi.ParserService
import org.openmuc.framework.parser.spi.SerializationException
import org.openmuc.framework.security.SslManagerInterface
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.StringReader
import java.util.*

class MqttDriverConnection(host: String, settings: String) : Connection {
    private val mqttConnection: MqttConnection
    private val mqttWriter: MqttWriter
    private val mqttReader: MqttReader
    private val parsers: MutableMap<String, ParserService> = HashMap()
    private val lastLoggedRecords: MutableMap<String, Long> = HashMap()
    private val recordContainerList: MutableList<ChannelRecordContainer> = ArrayList()
    private val settings = Properties()

    init {
        val mqttSettings = getMqttSettings(host, settings)
        mqttConnection = MqttConnection(mqttSettings)
        val pid = "mqttdriver"
        mqttWriter = MqttWriter(mqttConnection, pid)
        mqttReader = MqttReader(mqttConnection, pid)
        if (!mqttSettings.isSsl) {
            mqttConnection.connect()
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun getMqttSettings(host: String, settings: String?): MqttSettings {
        var settings = settings
        settings = settings!!.replace(";".toRegex(), "\n")
        try {
            this.settings.load(StringReader(settings))
        } catch (e: IOException) {
            throw ArgumentSyntaxException("Could not read settings string")
        }
        val port = this.settings.getProperty("port").toInt()
        val username = this.settings.getProperty("username")
        val password = this.settings.getProperty("password")
        val ssl = java.lang.Boolean.parseBoolean(this.settings.getProperty("ssl"))
        val maxBufferSize = this.settings.getProperty("maxBufferSize", "0").toLong()
        val maxFileSize = this.settings.getProperty("maxFileSize", "0").toLong()
        val maxFileCount = this.settings.getProperty("maxFileCount", "1").toInt()
        val connectionRetryInterval = this.settings.getProperty("connectionRetryInterval", "10").toInt()
        val connectionAliveInterval = this.settings.getProperty("connectionAliveInterval", "10").toInt()
        val persistenceDirectory = this.settings.getProperty("persistenceDirectory", "data/driver/mqtt")
        val lastWillTopic = this.settings.getProperty("lastWillTopic", "")
        val lastWillPayload = this.settings.getProperty("lastWillPayload", "").toByteArray()
        val lastWillAlways = java.lang.Boolean.parseBoolean(this.settings.getProperty("lastWillAlways", "false"))
        val firstWillTopic = this.settings.getProperty("firstWillTopic", "")
        val firstWillPayload = this.settings.getProperty("firstWillPayload", "").toByteArray()
        val webSocket = java.lang.Boolean.parseBoolean(this.settings.getProperty("webSocket", "false"))
        return MqttSettings(
            host, port, username, password, ssl, maxBufferSize, maxFileSize, maxFileCount,
            connectionRetryInterval, connectionAliveInterval, persistenceDirectory, lastWillTopic, lastWillPayload,
            lastWillAlways, firstWillTopic, firstWillPayload, webSocket
        )
    }

    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ConnectionException::class
    )
    override fun scanForChannels(settings: String): List<ChannelScanInfo> {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun read(
        containers: List<ChannelRecordContainer>,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer>, listener: RecordsReceivedListener?) {
        val topics: MutableList<String> = ArrayList()
        for (container in containers) {
            topics.add(container.channelAddress)
        }
        if (topics.isEmpty()) {
            return
        }
        mqttReader.listen(topics) { topic: String, message: ByteArray ->
            val channel = containers[topics.indexOf(topic)].channel
            val record = getRecord(message, channel!!.valueType)
            if (recordIsOld(channel.id, record)) {
                return@listen
            }
            addMessageToContainerList(record, containers[topics.indexOf(topic)])
            if (recordContainerList.size >= settings.getProperty("recordCollectionSize", "1").toInt()) {
                notifyListenerAndPurgeList(listener)
            }
        }
    }

    private fun notifyListenerAndPurgeList(listener: RecordsReceivedListener?) {
        logTraceNewRecord()
        listener!!.newRecords(recordContainerList)
        recordContainerList.clear()
    }

    private fun addMessageToContainerList(record: Record, container: ChannelRecordContainer) {
        val copiedContainer = container.copy()
        copiedContainer.record = record
        recordContainerList.add(copiedContainer)
    }

    private fun recordIsOld(channelId: String, record: Record): Boolean {
        val lastTimestamp = lastLoggedRecords[channelId]
        if (lastTimestamp == null) {
            lastLoggedRecords[channelId] = record.timestamp ?: 0
            return false
        }
        if (record.timestamp == null || record.timestamp!! <= lastTimestamp) {
            return true
        }
        lastLoggedRecords[channelId] = record.timestamp ?: 0
        return false
    }

    private fun getRecord(message: ByteArray, valueType: ValueType?): Record {
        return parsers[settings.getProperty("parser")]?.deserialize(message, valueType) ?: Record(ByteArrayValue(message), System.currentTimeMillis())
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer>, containerListHandle: Any?): Any? {
        for (container in containers) {
            val record = Record(container.value, System.currentTimeMillis())
            val loggingRecord = LoggingRecord(container.channelAddress, record)
            if (parsers.containsKey(settings.getProperty("parser"))) {
                val message = try {
                    parsers[settings.getProperty("parser")]!!.serialize(loggingRecord)
                } catch (e: SerializationException) {
                    logger.error(e.message)
                    continue
                }
                mqttWriter.write(container.channelAddress, message!!)
                container.flag = Flag.VALID
            } else {
                logger.error("A parser is needed to write messages and none have been registered.")
                throw UnsupportedOperationException()
            }
        }
        return null
    }

    override fun disconnect() {
        mqttWriter.shutdown()
        mqttConnection.disconnect()
    }

    fun setParser(parserId: String, parser: ParserService?) {
        if (parser == null) {
            parsers.remove(parserId)
            return
        }
        parsers[parserId] = parser
    }

    private fun logTraceNewRecord() {
        if (logger.isTraceEnabled) {
            val sb = StringBuilder()
            sb.append("new records")
            for (container in recordContainerList) {
                sb.append(
                    """
    
    topic: ${sb.append(container!!.channelAddress)}
    
    """.trimIndent()
                )
                sb.append("record: " + container.record.toString())
            }
            logger.trace(sb.toString())
        }
    }

    fun setSslManager(instance: SslManagerInterface) {
        if (mqttConnection.settings.isSsl) {
            logger.debug("SSLManager registered in driver")
            mqttConnection.setSslManager(instance)
            if (instance.isLoaded) {
                mqttConnection.connect()
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttDriverConnection::class.java)
    }
}
