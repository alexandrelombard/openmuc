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
import org.openmuc.framework.lib.amqp.AmqpSettings
import org.openmuc.framework.security.SslConfigChangeListener
import org.openmuc.framework.security.SslManagerInterface
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeoutException

/**
 * Represents a connection to an AMQP broker
 */
class AmqpConnection(val settings: AmqpSettings) {
    private val recoveryListeners: MutableList<RecoveryListener> = ArrayList()
    private val readers: MutableList<AmqpReader> = ArrayList()
    var exchange: String? = null
        private set
    private var connection: Connection? = null
    var rabbitMqChannel: Channel? = null
        private set
    private var sslManager: SslManagerInterface? = null
    var isConnected = false
        private set

    /**
     * A connection to an AMQP broker
     *
     * @param settings
     * connection details [AmqpSettings]
     * @throws IOException
     * when connection fails
     * @throws TimeoutException
     * when connection fails due time out
     */
    init {
        if (!settings.isSsl) {
            logger.info("Starting amqp connection without ssl")
            val factory = getConnectionFactoryForSsl(settings)
            try {
                connect(settings, factory)
            } catch (e: Exception) {
                logger.error("Connection could not be created: {}", e.message)
            }
        }
    }

    private fun getConnectionFactoryForSsl(settings: AmqpSettings): ConnectionFactory {
        val factory = ConnectionFactory()
        if (settings.isSsl) {
            factory.useSslProtocol(sslManager!!.sslContext)
            factory.enableHostnameVerification()
        }
        factory.host = settings.host
        factory.port = settings.port
        factory.virtualHost = settings.virtualHost
        factory.username = settings.username
        factory.password = settings.password
        factory.exceptionHandler = AmqpExceptionHandler()
        factory.requestedHeartbeat = settings.connectionAliveInterval
        return factory
    }

    @Throws(IOException::class)
    private fun connect(settings: AmqpSettings, factory: ConnectionFactory) {
        establishConnection(factory)
        if (connection == null) {
            logger.warn("Created connection is null, check your config\n{}", settings)
            return
        }
        isConnected = true
        logger.info("Connection established successfully!")
        addRecoveryListener(object : RecoveryListener {
            override fun handleRecovery(recoverable: Recoverable) {
                logger.debug("Connection recovery completed")
                isConnected = true
            }

            override fun handleRecoveryStarted(recoverable: Recoverable) {
                logger.debug("Connection recovery started")
                isConnected = false
            }
        })
        rabbitMqChannel = connection!!.createChannel()
        exchange = settings.exchange
        rabbitMqChannel.exchangeDeclare(exchange, "topic", true)
        if (logger.isTraceEnabled) {
            logger.trace(
                "Connected to {}:{} on virtualHost {} as user {}", settings.host, settings.port,
                settings.virtualHost, settings.username
            )
        }
    }

    private fun establishConnection(factory: ConnectionFactory) {
        try {
            connection = factory.newConnection()
        } catch (e: Exception) {
            logger.error("Error at creation of new connection: {}", e.message)
        }
    }

    private fun sslUpdate() {
        logger.warn("SSL configuration changed, reconnecting.")
        disconnect()
        val factory = getConnectionFactoryForSsl(settings)
        try {
            connect(settings, factory)
            if (connection == null) {
                logger.error("connection after calling ssl update is null")
                return
            }
            for (listener in recoveryListeners) {
                (connection as Recoverable).addRecoveryListener(listener)
                listener.handleRecovery(connection as Recoverable?)
            }
            for (reader in readers) {
                reader.resubscribe()
            }
        } catch (e: IOException) {
            logger.error("Reconnection failed. Reason: {}", e.message)
        }
        logger.warn("Reconnection completed.")
    }

    /**
     * Close the channel and connection
     */
    fun disconnect() {
        if (rabbitMqChannel == null || connection == null) {
            return
        }
        try {
            rabbitMqChannel!!.close()
            connection!!.close()
            if (logger.isTraceEnabled) {
                logger.trace("Successfully disconnected")
            }
        } catch (e: IOException) {
            logger.error("failed to close connection: {}", e.message)
        } catch (e: TimeoutException) {
            logger.error("failed to close connection: {}", e.message)
        } catch (e: ShutdownSignalException) {
            logger.error("failed to close connection: {}", e.message)
        }
    }

    /**
     * Declares the passed queue as a durable queue
     *
     * @param queue
     * the queue that should be declared
     * @throws IOException
     * if an I/O problem is encountered
     */
    @Throws(IOException::class)
    fun declareQueue(queue: String?) {
        if (!DECLARED_QUEUES.contains(queue)) {
            try {
                rabbitMqChannel!!.queueDeclarePassive(queue)
                rabbitMqChannel!!.queueBind(queue, exchange, queue)
                DECLARED_QUEUES.add(queue)
                if (logger.isTraceEnabled) {
                    logger.trace("Queue {} declared", queue)
                }
            } catch (e: Exception) {
                logger.debug("Channel {} not found, start to create it...", queue)
                initDeclare(queue)
            }
        }
    }

    fun addRecoveryListener(listener: RecoveryListener) {
        recoveryListeners.add(listener)
        if (connection == null) {
            return
        }
        (connection as Recoverable).addRecoveryListener(listener)
    }

    fun addReader(reader: AmqpReader) {
        readers.add(reader)
    }

    @Throws(IOException::class)
    private fun initDeclare(queue: String?) {
        if (connection == null) {
            logger.error("declaring queue stopped, because connection to broker is null")
            return
        }
        try {
            rabbitMqChannel = connection!!.createChannel()
        } catch (e: Exception) {
            logger.error("Queue {} could not be declared.", queue)
            return
        }
        rabbitMqChannel.exchangeDeclare(exchange, "topic", true)
        rabbitMqChannel.queueDeclare(queue, true, false, false, null)
    }

    fun setSslManager(instance: SslManagerInterface?) {
        if (!settings.isSsl) {
            return
        }
        sslManager = instance
        sslManager!!.listenForConfigChange(SslConfigChangeListener { sslUpdate() })
        val factory = getConnectionFactoryForSsl(settings)
        if (sslManager!!.isLoaded) {
            try {
                connect(settings, factory)
            } catch (e: Exception) {
                logger.error("Connection with SSL couldn't be created")
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AmqpConnection::class.java)
        private val DECLARED_QUEUES: MutableList<String?> = ArrayList()
    }
}
