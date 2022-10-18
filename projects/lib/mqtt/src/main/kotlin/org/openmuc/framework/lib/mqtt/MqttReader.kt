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
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3SubscribeBuilder
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscription
import org.openmuc.framework.lib.mqtt.MqttConnection
import org.slf4j.LoggerFactory
import org.slf4j.helpers.MessageFormatter
import java.util.*

class MqttReader(private val connection: MqttConnection, private val pid: String) {
    private var connected = false
    private val subscribes: MutableList<SubscribeListenerTuple> = LinkedList()

    /**
     * Note that the connect method of the connection should be called after the Writer got instantiated.
     *
     * @param connection
     * the [MqttConnection] this Writer should use
     * @param pid
     * an id which is preceding every log call
     */
    init {
        addConnectedListener(connection)
        addDisconnectedListener(connection)
    }

    private fun addDisconnectedListener(connection: MqttConnection) {
        connection.addDisconnectedListener { context: MqttClientDisconnectedContext ->
            if (context.reconnector.isReconnect) {
                if (connected) {
                    warn("Disconnected! {}", context.cause.message!!)
                } else {
                    warn("Reconnect failed! Reason: {}", context.cause.message!!)
                }
                connected = false
            }
        }
    }

    private fun addConnectedListener(connection: MqttConnection) {
        connection.addConnectedListener { context: MqttClientConnectedContext ->
            for (tuple in subscribes) {
                subscribe(tuple.subscribe, tuple.listener)
            }
            connected = true
            log(
                "Connected to {}:{}", context.clientConfig.serverHost,
                context.clientConfig.serverPort
            )
        }
    }

    /**
     * Listens on all topics and notifies the listener when a new message on one of the topics comes in
     *
     * @param topics
     * List with topic string to listen on
     * @param listener
     * listener which gets notified of new messages coming in
     */
    fun listen(topics: List<String>, listener: MqttMessageListener) {
        val subscribe = buildSubscribe(topics)
        if (subscribe == null) {
            error("No topic given to listen on")
            return
        }
        if (connected) {
            subscribe(subscribe, listener)
        }
        subscribes.add(SubscribeListenerTuple(subscribe, listener))
    }

    private fun subscribe(subscribe: Mqtt3Subscribe, listener: MqttMessageListener) {
        connection.client.subscribe(subscribe) { mqtt3Publish: Mqtt3Publish ->
            listener.newMessage(mqtt3Publish.topic.toString(), mqtt3Publish.payloadAsBytes)
            if (logger.isTraceEnabled) {
                trace(
                    "Message on topic {} received, payload: {}", mqtt3Publish.topic.toString(),
                    String(mqtt3Publish.payloadAsBytes)
                )
            }
        }
    }

    private fun buildSubscribe(topics: List<String>): Mqtt3Subscribe? {
        val subscribeBuilder: Mqtt3SubscribeBuilder = Mqtt3Subscribe.builder()
        var subscribe: Mqtt3Subscribe? = null
        for (topic in topics) {
            val subscription = Mqtt3Subscription.builder().topicFilter(topic).build()
            // last topic, build the subscribe object
            if (topics[topics.size - 1] == topic) {
                subscribe = subscribeBuilder.addSubscription(subscription).build()
                break
            }
            subscribeBuilder.addSubscription(subscription)
        }
        return subscribe
    }

    private class SubscribeListenerTuple(val subscribe: Mqtt3Subscribe, val listener: MqttMessageListener)

    private fun log(message: String, vararg args: Any) {
        var message: String? = message
        message = MessageFormatter.arrayFormat(message, args).message
        logger.info("[{}] {}", pid, message)
    }

    private fun debug(message: String, vararg args: Any) {
        var message: String? = message
        message = MessageFormatter.arrayFormat(message, args).message
        logger.debug("[{}] {}", pid, message)
    }

    private fun warn(message: String, vararg args: Any) {
        var message: String? = message
        message = MessageFormatter.arrayFormat(message, args).message
        logger.warn("[{}] {}", pid, message)
    }

    private fun error(message: String, vararg args: Any) {
        var message: String? = message
        message = MessageFormatter.arrayFormat(message, args).message
        logger.error("[{}] {}", pid, message)
    }

    private fun trace(message: String, vararg args: Any) {
        var message: String? = message
        message = MessageFormatter.arrayFormat(message, args).message
        logger.trace("[{}] {}", pid, message)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttReader::class.java)
    }
}
