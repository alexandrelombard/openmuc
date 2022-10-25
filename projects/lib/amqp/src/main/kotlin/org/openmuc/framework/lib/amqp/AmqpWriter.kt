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
package org.openmuc.framework.lib.amqp

import com.rabbitmq.client.Recoverable
import com.rabbitmq.client.RecoveryListener
import org.openmuc.framework.lib.amqp.AmqpConnection
import org.slf4j.LoggerFactory

/**
 * Sends (writes) messages to an AmqpConnection
 */
class AmqpWriter(private val connection: AmqpConnection, private val pid: String) {
    private val bufferHandler: AmqpBufferHandler

    /**
     * @param connection
     * an instance of [AmqpConnection]
     * @param pid
     * pid for log messages
     */
    init {
        val s = connection.settings
        bufferHandler = AmqpBufferHandler(
            s.maxBufferSize, s.maxFileCount, s.maxFileSize,
            s.persistenceDirectory
        )
        connection.addRecoveryListener(object : RecoveryListener {
            override fun handleRecovery(recoverable: Recoverable) {
                emptyFileBuffer()
                emptyRAMBuffer()
            }

            override fun handleRecoveryStarted(recoverable: Recoverable) {}
        })
        if (connection.isConnected) {
            emptyFileBuffer()
            emptyRAMBuffer()
        }
    }

    private fun emptyFileBuffer() {
        val buffers = bufferHandler.buffers
        logger.debug("[{}] Clearing file buffer.", pid)
        if (buffers.isEmpty()) {
            logger.debug("[{}] File buffer already empty.", pid)
        }
        for (buffer in buffers) {
            val iterator = bufferHandler.getMessageIterator(buffer)
            while (iterator.hasNext()) {
                val messageTuple = iterator.next()
                if (logger.isTraceEnabled) {
                    logger.trace("[{}] Resend from file: {}", pid, String(messageTuple.message))
                }
                write(messageTuple.routingKey, messageTuple.message)
            }
        }
        logger.debug("[{}] File buffer cleared.", pid)
    }

    private fun emptyRAMBuffer() {
        logger.debug("[{}] Clearing RAM buffer.", pid)
        if (bufferHandler.isEmpty) {
            logger.debug("[{}] RAM buffer already empty.", pid)
        }
        while (!bufferHandler.isEmpty) {
            val messageTuple = bufferHandler.removeNextMessage()
            if (logger.isTraceEnabled) {
                logger.trace("[{}] Resend from memory: {}", pid, String(messageTuple.message))
            }
            write(messageTuple.routingKey, messageTuple.message)
        }
        logger.debug("[{}] RAM buffer cleared.", pid)
    }

    /**
     * Publish a message with routing key, when failing the message is buffered and republished on recovery
     *
     * @param routingKey
     * the routingKey with which to publish the message
     * @param message
     * byte array containing the message to be published
     */
    fun write(routingKey: String, message: ByteArray) {
        if (!publish(routingKey, message)) {
            bufferHandler.add(routingKey, message)
        }
    }

    private fun publish(routingKey: String, message: ByteArray): Boolean {
        try {
            connection.declareQueue(routingKey)
            connection.rabbitMqChannel!!.basicPublish(connection.exchange, routingKey, false, null, message)
        } catch (e: Exception) {
            logger.error("[{}] Could not publish message: {}", pid, e.message)
            return false
        }
        if (logger.isTraceEnabled) {
            logger.trace(
                "[{}] published with routingKey {}, payload: {}", pid, routingKey, String(
                    message
                )
            )
        }
        return true
    }

    fun shutdown() {
        logger.debug("[{}] Saving buffers.", pid)
        bufferHandler.persist()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AmqpWriter::class.java)
    }
}
