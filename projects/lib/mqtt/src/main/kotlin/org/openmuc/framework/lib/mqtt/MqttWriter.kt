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
package org.openmuc.framework.lib.mqtt

import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedContext
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import org.slf4j.LoggerFactory
import org.slf4j.helpers.MessageFormatter
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

open class MqttWriter(connection: MqttConnection, pid: String) {
    val connection: MqttConnection
    var connected = false
        private set
    private val cancelReconnect = AtomicBoolean(false)
    private var timeOfConnectionLoss: LocalDateTime? = null
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val buffer: MqttBufferHandler
    private val pid: String

    init {
        this.connection = connection
        addConnectedListener()
        addDisconnectedListener()
        val s = connection.settings
        buffer = MqttBufferHandler(
            s.maxBufferSize, s.maxFileCount, s.maxFileSize,
            s.persistenceDirectory
        )
        this.pid = pid
    }

    private fun addConnectedListener() {
        connection.addConnectedListener { context: MqttClientConnectedContext ->

            // FIXME null checks currently workaround for MqttWriterTest, it is not set there
            var serverHost = "UNKNOWN"
            var serverPort = "UNKNOWN"
            if (context.clientConfig != null) {
                serverHost = context.clientConfig.serverHost
                serverPort = context.clientConfig.serverPort.toString()
            }
            log("connected to broker {}:{}", serverHost, serverPort)
            connected = true
            val settings = connection.settings
            if (settings.isFirstWillSet) {
                write(settings.firstWillTopic, settings.firstWillPayload)
            }
            val recovery = Thread({ emptyBuffer() }, "MqttRecovery")
            recovery.start()
        }
    }

    private fun emptyFileBuffer() {
        log("Clearing file buffer.")
        val buffers = buffer.buffers
        if (buffers.isEmpty()) {
            log("File buffer already empty.")
        }
        var messageCount = 0
        val chunkSize = connection.settings.recoveryChunkSize
        val delay = connection.settings.recoveryDelay
        for (buffer in buffers) {
            val iterator = this.buffer.getMessageIterator(buffer)
            while (iterator.hasNext()) {
                if (!connected) {
                    warn("Recovery from file buffer interrupted by connection loss.")
                    return
                }
                val messageTuple = iterator.next()
                if (logger.isTraceEnabled) {
                    trace("Resend from file: {}", String(messageTuple.message))
                }
                write(messageTuple.topic, messageTuple.message)
                messageCount++
                if (connection.settings.isRecoveryLimitSet && messageCount == chunkSize) {
                    messageCount = 0
                    try {
                        Thread.sleep(delay.toLong())
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
        }
        log("Empty file buffer done.")
    }

    private fun emptyBuffer() {
        log("Clearing memory (RAM) buffer.")
        if (buffer.isEmpty) {
            log("Memory buffer already empty.")
        }
        var messageCount = 0
        val chunkSize = connection.settings.recoveryChunkSize
        val delay = connection.settings.recoveryDelay
        while (!buffer.isEmpty) {
            if (!connected) {
                warn("Recovery from memory buffer interrupted by connection loss.")
                return
            }
            val messageTuple = buffer.removeNextMessage()
            if (logger.isTraceEnabled) {
                trace("Resend from memory: {}", String(messageTuple.message))
            }
            write(messageTuple.topic, messageTuple.message)
            messageCount++
            if (connection.settings.isRecoveryLimitSet && messageCount == chunkSize) {
                messageCount = 0
                try {
                    Thread.sleep(delay.toLong())
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
        log("Empty memory buffer done.")
        emptyFileBuffer()
    }

    private fun addDisconnectedListener() {
        connection.addDisconnectedListener { context: MqttClientDisconnectedContext ->
            if (cancelReconnect.getAndSet(false)) {
                context.reconnector.reconnect(false)
            }
            if (context.reconnector.isReconnect) {
                val serverHost = context.clientConfig.serverHost
                val cause = context.cause.message
                val source = context.source.name
                if (connected) {
                    handleDisconnect(serverHost, cause)
                } else {
                    handleFailedReconnect(serverHost, cause, source)
                }
            }
        }
    }

    private fun handleFailedReconnect(serverHost: String, cause: String?, source: String) {
        if (isInitialConnect) {
            timeOfConnectionLoss = LocalDateTime.now()
        }
        val d = Duration.between(timeOfConnectionLoss, LocalDateTime.now()).seconds * 1000
        val duration = sdf.format(Date(d - TimeZone.getDefault().rawOffset))
        warn(
            "Reconnect failed: broker '{}'. Source: '{}'. Cause: '{}'. Connection lost at: {}, duration {}",
            serverHost, source, cause!!, dateFormatter.format(timeOfConnectionLoss), duration
        )
    }

    val isInitialConnect: Boolean
        get() = timeOfConnectionLoss == null

    private fun handleDisconnect(serverHost: String, cause: String?) {
        timeOfConnectionLoss = LocalDateTime.now()
        connected = false
        warn("Connection lost: broker '{}'. Cause: '{}'", serverHost, cause!!)
    }

    /**
     * Publishes a message to the specified topic
     *
     * @param topic
     * the topic on which to publish the message
     * @param message
     * the message to be published
     */
    fun write(topic: String, message: ByteArray) {
        if (connected) {
            startPublishing(topic, message)
        } else {
            warn("No connection to broker - adding message to buffer")
            buffer.add(topic, message)
        }
    }

    private fun startPublishing(topic: String, message: ByteArray) {
        publish(topic, message).whenComplete { publish: Mqtt3Publish, exception: Throwable? ->
            if (exception != null) {
                warn(
                    "Connection issue: {} message could not be sent. Adding message to buffer",
                    exception.message!!
                )
                buffer.add(topic, message)
            } else if (logger.isTraceEnabled) {
                trace("Message successfully delivered on topic {}", topic)
            }
        }
    }

    open fun publish(topic: String, message: ByteArray): CompletableFuture<Mqtt3Publish> {
        return connection.client.publishWith().topic(topic).payload(message).send()
    }

    private fun log(message: String, vararg args: Any) {
        var message: String = message
        message = MessageFormatter.arrayFormat(message, args).message
        logger.info("[{}] {}", pid, message)
    }

    private fun debug(message: String, vararg args: Any) {
        var message: String = message
        message = MessageFormatter.arrayFormat(message, args).message
        logger.debug("[{}] {}", pid, message)
    }

    private fun warn(message: String, vararg args: Any) {
        var message: String = message
        message = MessageFormatter.arrayFormat(message, args).message
        logger.warn("[{}] {}", pid, message)
    }

    private fun error(message: String, vararg args: Any) {
        var message: String = message
        message = MessageFormatter.arrayFormat(message, args).message
        logger.error("[{}] {}", pid, message)
    }

    private fun trace(message: String, vararg args: Any) {
        var message: String = message
        message = MessageFormatter.arrayFormat(message, args).message
        logger.trace("[{}] {}", pid, message)
    }

    fun shutdown() {
        connected = false
        cancelReconnect.set(true)
        log("Saving buffers.")
        buffer.persist()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttWriter::class.java)
    }
}
