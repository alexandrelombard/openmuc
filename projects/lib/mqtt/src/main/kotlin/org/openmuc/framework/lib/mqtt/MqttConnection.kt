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

import com.hivemq.client.mqtt.MqttClientSslConfig
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import org.openmuc.framework.lib.mqtt.MqttSettings
import org.openmuc.framework.security.SslConfigChangeListener
import org.openmuc.framework.security.SslManagerInterface
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Represents a connection to a MQTT broker
 */
class MqttConnection(
    /**
     * @return the settings [MqttSettings] this connection was constructed with
     */
    val settings: MqttSettings
) {
    private val cancelReconnect = AtomicBoolean(false)
    private val connectedListeners: MutableList<MqttClientConnectedListener> = ArrayList()
    private val disconnectedListeners: MutableList<MqttClientDisconnectedListener> = ArrayList()
    private var sslReady = false
    private var clientBuilder: Mqtt3ClientBuilder?
    var client: Mqtt3AsyncClient
        private set
    private var sslManager: SslManagerInterface? = null

    /**
     * A connection to a MQTT broker
     *
     * @param settings
     * connection details [MqttSettings]
     */
    init {
        clientBuilder = getClientBuilder()
        client = buildClient()
    }

    val isReady: Boolean
        get() = if (settings.isSsl) {
            sslReady
        } else true

    private fun sslUpdate() {
        logger.warn("SSL configuration changed, reconnecting.")
        cancelReconnect.set(true)
        sslReady = true
        client.disconnect().whenComplete { ack: Void?, e: Throwable? ->
            clientBuilder!!.sslConfig(sslConfig)
            clientBuilder!!.identifier(UUID.randomUUID().toString())
            connect()
        }
    }

    private val connect: Mqtt3Connect
        private get() {
            val connectBuilder = Mqtt3Connect.builder()
            connectBuilder.keepAlive(settings.connectionAliveInterval)
            if (settings.isLastWillSet) {
                connectBuilder.willPublish()
                    .topic(settings.lastWillTopic)
                    .payload(settings.lastWillPayload)
                    .applyWillPublish()
            }
            if (settings.username != null) {
                connectBuilder.simpleAuth()
                    .username(settings.username)
                    .password(settings.password.toByteArray())
                    .applySimpleAuth()
            }
            return connectBuilder.build()
        }

    /**
     * Connect to the MQTT broker
     */
    fun connect() {
        client = buildClient()
        val uuid = client.config.clientIdentifier.toString()
        val time = LocalDateTime.now()
        client.connect(connect).whenComplete { ack: Mqtt3ConnAck?, e: Throwable? ->
            if (e != null && uuid == client.config.clientIdentifier.toString()) {
                logger.error("Error with connection initiated at {}: {}", time, e.message)
            }
        }
    }

    /**
     * Disconnect from the MQTT broker
     */
    fun disconnect() {
        if (settings.isLastWillAlways) {
            client.publishWith()
                .topic(settings.lastWillTopic)
                .payload(settings.lastWillPayload)
                .send()
                .whenComplete { publish: Mqtt3Publish?, e: Throwable? -> client.disconnect() }
        } else {
            client.disconnect()
        }
    }

    fun addConnectedListener(listener: MqttClientConnectedListener) {
        if (clientBuilder == null) {
            connectedListeners.add(listener)
        } else {
            clientBuilder!!.addConnectedListener(listener)
            if (!connectedListeners.contains(listener)) {
                connectedListeners.add(listener)
            }
        }
    }

    fun addDisconnectedListener(listener: MqttClientDisconnectedListener) {
        if (clientBuilder == null) {
            disconnectedListeners.add(listener)
        } else {
            clientBuilder!!.addDisconnectedListener(listener)
            if (!disconnectedListeners.contains(listener)) {
                disconnectedListeners.add(listener)
            }
        }
    }

    private fun getClientBuilder(): Mqtt3ClientBuilder {
        val clientBuilder = Mqtt3Client.builder()
            .identifier(UUID.randomUUID().toString())
            .automaticReconnect()
            .initialDelay(settings.connectionRetryInterval.toLong(), TimeUnit.SECONDS)
            .maxDelay(settings.connectionRetryInterval.toLong(), TimeUnit.SECONDS)
            .applyAutomaticReconnect()
            .serverHost(settings.host)
            .serverPort(settings.port)
        if (settings.isSsl && sslManager != null) {
            clientBuilder.sslConfig(sslConfig)
        }
        if (settings.isWebSocket) {
            clientBuilder.webSocketWithDefaultConfig()
        }
        return clientBuilder
    }

    private val sslConfig: MqttClientSslConfig
        get() = MqttClientSslConfig.builder()
            .keyManagerFactory(sslManager!!.keyManagerFactory)
            .trustManagerFactory(sslManager!!.trustManagerFactory)
            .handshakeTimeout(10, TimeUnit.SECONDS)
            .build()

    private fun buildClient(): Mqtt3AsyncClient {
        return clientBuilder!!.buildAsync()
    }

    fun setSslManager(instance: SslManagerInterface) {
        if (!settings.isSsl) {
            return
        }
        sslManager = instance
        clientBuilder = getClientBuilder()
        for (listener in connectedListeners) {
            addConnectedListener(listener)
        }
        connectedListeners.clear()
        for (listener in disconnectedListeners) {
            addDisconnectedListener(listener)
        }
        disconnectedListeners.clear()
        sslManager!!.listenForConfigChange { sslUpdate() }
        addDisconnectedListener { context: MqttClientDisconnectedContext ->
            if (cancelReconnect.getAndSet(false)) {
                context.reconnector.reconnect(false)
            } else if (context.reconnector.attempts >= 3) {
                logger.debug("Renewing client")
                context.reconnector.reconnect(false)
                clientBuilder!!.identifier(UUID.randomUUID().toString())
                connect()
            }
        }
        client = buildClient()
        if (sslManager!!.isLoaded) {
            sslReady = true
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttConnection::class.java)
    }
}
