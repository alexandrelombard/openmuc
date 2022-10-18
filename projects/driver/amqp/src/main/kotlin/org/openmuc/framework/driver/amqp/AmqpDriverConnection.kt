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
package org.openmuc.framework.driver.amqp

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.data.ByteArrayValue
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.dataaccess.WriteValueContainer
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.openmuc.framework.driver.spi.*
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import org.openmuc.framework.lib.amqp.AmqpConnection
import org.openmuc.framework.lib.amqp.AmqpReader
import org.openmuc.framework.lib.amqp.AmqpSettings
import org.openmuc.framework.lib.amqp.AmqpWriter
import org.openmuc.framework.parser.spi.ParserService
import org.openmuc.framework.parser.spi.SerializationException
import org.openmuc.framework.security.SslManagerInterface
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeoutException

class AmqpDriverConnection(deviceAddress: String?, settings: String?) : Connection {
    private val setting: Setting
    private var connection: AmqpConnection? = null
    private val writer: AmqpWriter
    private val reader: AmqpReader
    private val parsers: MutableMap<String?, ParserService> = HashMap()
    private val lastLoggedRecords: MutableMap<String?, Long?> = HashMap()
    private var recordContainerList: MutableList<ChannelRecordContainer?>

    init {
        recordContainerList = ArrayList()
        setting = Setting(settings)
        val amqpSettings = AmqpSettings(
            deviceAddress, setting.port, setting.vhost, setting.user,
            setting.password, setting.ssl, setting.exchange, setting.persistenceDir, setting.maxFileCount,
            setting.maxFileSize.toLong(), setting.maxBufferSize.toLong(), setting.connectionAliveInterval
        )
        connection = try {
            AmqpConnection(amqpSettings)
        } catch (e: TimeoutException) {
            throw ConnectionException("Timeout while connect.", e)
        } catch (e: IOException) {
            throw ConnectionException("Not able to connect to " + deviceAddress + " " + setting.vhost, e)
        }
        writer = AmqpWriter(connection, "amqpdriver")
        reader = AmqpReader(connection)
    }

    @Throws(UnsupportedOperationException::class)
    override fun scanForChannels(settings: String?): List<ChannelScanInfo?>? {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class)
    override fun read(
        containers: List<ChannelRecordContainer?>?,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        for (container in containers!!) {
            val queue = container!!.channelAddress
            val message = reader.read(queue)
            if (message != null) {
                val record = getRecord(message, container.channel!!.valueType)
                container.setRecord(record)
            } else {
                container.setRecord(Record(Flag.NO_VALUE_RECEIVED_YET))
            }
        }
        return null
    }

    @Throws(UnsupportedOperationException::class)
    override fun startListening(containers: List<ChannelRecordContainer?>?, listener: RecordsReceivedListener?) {
        for (container in containers!!) {
            val queue = container!!.channelAddress
            reader.listen(setOf(queue)) { receivedQueue: String?, message: ByteArray ->
                val record = getRecord(message, container.channel!!.valueType)
                if (recordsIsOld(container.channel!!.id, record)) {
                    return@listen
                }
                addMessageToContainerList(record, container)
                if (recordContainerList.size >= setting.recordCollentionSize) {
                    notifyListenerAndPurgeList(listener)
                }
            }
        }
    }

    private fun recordsIsOld(channelId: String?, record: Record?): Boolean {
        val lastTs = lastLoggedRecords[channelId]
        if (lastTs == null) {
            lastLoggedRecords[channelId] = record!!.timestamp
            return false
        }
        if (record!!.timestamp == null || record.timestamp!! <= lastTs) {
            return true
        }
        lastLoggedRecords[channelId] = record.timestamp
        return false
    }

    private fun notifyListenerAndPurgeList(listener: RecordsReceivedListener?) {
        listener!!.newRecords(recordContainerList)
        recordContainerList = ArrayList()
    }

    private fun addMessageToContainerList(record: Record?, container: ChannelRecordContainer?) {
        val copiedContainer = container!!.copy()
        copiedContainer!!.setRecord(record)
        recordContainerList.add(copiedContainer)
    }

    @Throws(UnsupportedOperationException::class)
    override fun write(containers: List<ChannelValueContainer?>?, containerListHandle: Any?): Any? {
        for (container in containers!!) {
            val record = Record(container!!.value, System.currentTimeMillis())

            // ToDo: cleanup data structure
            val channel = (container as WriteValueContainer?)!!.channel
            val logRecordContainer = LoggingRecord(channel!!.id!!, record)
            if (parsers.containsKey(setting.parser)) {
                var message: ByteArray? = ByteArray(0)
                try {
                    message = parsers[setting.parser]!!.serialize(logRecordContainer)
                } catch (e: SerializationException) {
                    logger.error(e.message)
                }
                writer.write(container.channelAddress, message)
                container.flag = Flag.VALID
            } else {
                throw UnsupportedOperationException("A parser is needed to write messages")
            }
        }
        return null
    }

    override fun disconnect() {
        connection!!.disconnect()
    }

    fun setParser(parserId: String?, parser: ParserService?) {
        if (parser == null) {
            parsers.remove(parserId)
            return
        }
        parsers[parserId] = parser
    }

    private fun getRecord(message: ByteArray, valueType: ValueType?): Record? {
        val record: Record?
        record = if (parsers.containsKey(setting.parser)) {
            parsers[setting.parser]!!.deserialize(message, valueType)
        } else {
            Record(
                ByteArrayValue(message),
                System.currentTimeMillis()
            )
        }
        return record
    }

    fun setSslManager(instance: SslManagerInterface?) {
        connection!!.setSslManager(instance)
    }

    private inner class Setting internal constructor(settings: String?) {
        var port = 5672
        var vhost: String? = null
        var user: String? = null
        var password: String? = null
        private var framework: String? = null
        var parser: String? = null
        var exchange: String? = null
        private var frameworkChannelSeparator = "."
        var recordCollentionSize = 1
        var ssl = true
        var maxBufferSize = 1024
        var maxFileSize = 5120
        var maxFileCount = 0
        var persistenceDir = "data/amqp/driver"
        var connectionAliveInterval = 60

        init {
            separate(settings)
        }

        /**
         * This function extracts information out of the settings string
         *
         * @param settings
         * The settings to separate
         * @throws ArgumentSyntaxException
         * This is thrown if any setting is invalid
         */
        @Throws(ArgumentSyntaxException::class)
        private fun separate(settings: String?) {
            if (settings == null || settings.isEmpty()) {
                throw ArgumentSyntaxException("No settings given")
            }
            val settingsSplit = settings.split(Companion.SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            for (settingSplit in settingsSplit) {
                val settingPair =
                    settingSplit.split(Companion.SETTING_VALUE_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                if (settingPair.size != 2) {
                    throw ArgumentSyntaxException(
                        "Corrupt setting. Malformed setting found, should be <setting>=<value>"
                    )
                }
                val settingP0 = settingPair[0].trim { it <= ' ' }
                val settingP1 = settingPair[1].trim { it <= ' ' }
                when (settingP0) {
                    "port" -> port = parseInt(settingP1)
                    "vhost" -> vhost = settingP1
                    "user" -> user = settingP1
                    "password" -> password = settingP1
                    "framework" -> framework = settingP1
                    "parser" -> parser = settingP1.lowercase(Locale.getDefault())
                    "recordCollectionSize" -> recordCollentionSize = parseInt(settingP1)
                    "ssl" -> ssl = java.lang.Boolean.parseBoolean(settingP1)
                    "separator" -> frameworkChannelSeparator = settingP1
                    "exchange" -> exchange = settingP1
                    "maxFileSize" -> maxFileSize = parseInt(settingP1)
                    "maxFileCount" -> maxFileCount = parseInt(settingP1)
                    "maxBufferSize" -> maxBufferSize = parseInt(settingP1)
                    "persistenceDirectory" -> persistenceDir = settingP1
                    "connectionAliveInterval" -> connectionAliveInterval = parseInt(settingP1)
                    else -> throw ArgumentSyntaxException("Invalid setting given: $settingP0")
                }
            }
        }

        @Throws(ArgumentSyntaxException::class)
        fun parseInt(value: String): Int {
            return try {
                value.toInt()
            } catch (e: NumberFormatException) {
                throw ArgumentSyntaxException("Value of port is not a integer")
            }
        }

        companion object {
            private const val SEPARATOR = ";"
            private const val SETTING_VALUE_SEPARATOR = "="
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AmqpDriverConnection::class.java)
    }
}
