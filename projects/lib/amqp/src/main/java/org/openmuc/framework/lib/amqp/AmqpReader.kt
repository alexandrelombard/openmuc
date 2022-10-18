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

import com.rabbitmq.client.*
import org.openmuc.framework.lib.amqp.AmqpConnection
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Gets (reads) messages from an AmqpConnection
 */
class AmqpReader(connection: AmqpConnection) {
    private val logger = LoggerFactory.getLogger(AmqpReader::class.java)
    private val connection: AmqpConnection
    private val listeners: MutableList<Listener> = ArrayList()

    /**
     * @param connection
     * an instance of [AmqpConnection]
     */
    init {
        connection.addReader(this)
        this.connection = connection
    }

    /**
     * get a message from the specified queue
     *
     * @param queue
     * the queue from which to pull a message
     * @return byte array containing the received message, null if no message was received
     */
    fun read(queue: String?): ByteArray? {
        try {
            connection.declareQueue(queue)
        } catch (e: IOException) {
            logger.error("Declaring queue failed: {}", e.message)
            return null
        }
        val response: GetResponse?
        response = try {
            connection.rabbitMqChannel.basicGet(queue, true)
        } catch (e: IOException) {
            logger.error("Could not receive message: {}", e.message)
            return null
        }
        if (response == null) {
            // no message received, queue empty
            return null
        }
        if (logger.isTraceEnabled) {
            logger.trace("message on queue {} received, payload: {}", queue, String(response.body))
        }
        return response.body
    }

    /**
     * get messages from specified queues and send them to the specified [AmqpMessageListener]
     *
     * @param queues
     * String collection with queues to receive messages via push
     * @param listener
     * received messages are sent to this listener
     */
    fun listen(queues: Collection<String>, listener: AmqpMessageListener) {
        listeners.add(Listener(queues, listener))
        for (queue in queues) {
            val deliverCallback = DeliverCallback { consumerTag: String?, message: Delivery ->
                listener.newMessage(queue, message.body)
                if (logger.isTraceEnabled) {
                    logger.trace("message on queue {} received, payload: {}", queue, String(message.body))
                }
            }
            if (connection.isConnected) {
                try {
                    connection.declareQueue(queue)
                } catch (e: IOException) {
                    logger.error("Declaring queue failed: {}", e.message)
                    continue
                }
                try {
                    connection.rabbitMqChannel.basicConsume(queue, true, deliverCallback) { consumerTag: String? -> }
                } catch (e: IOException) {
                    logger.error("Could not subscribe for messages: {}", e.message)
                }
            }
        }
    }

    fun resubscribe() {
        val listenersCopy: List<Listener> = ArrayList(listeners)
        listeners.clear()
        for (listener in listenersCopy) {
            listen(listener.queues, listener.listener)
        }
    }

    private class Listener(val queues: Collection<String>, val listener: AmqpMessageListener)
}
