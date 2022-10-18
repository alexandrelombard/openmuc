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

import com.rabbitmq.client.Connection
import com.rabbitmq.client.ExceptionHandler
import com.rabbitmq.client.impl.ForgivingExceptionHandler
import org.slf4j.LoggerFactory
import java.net.ConnectException

internal class AmqpExceptionHandler : ForgivingExceptionHandler(), ExceptionHandler {
    override fun handleUnexpectedConnectionDriverException(conn: Connection, exception: Throwable) {
        logger.error(
            "[{}:{}] Exception detected: {}", conn.address.hostName, conn.port,
            exception.message
        )
    }

    override fun handleConnectionRecoveryException(conn: Connection, exception: Throwable) {
        // ConnectExceptions are expected during recovery
        if (exception !is ConnectException) {
            logger.error(
                "[{}:{}] Exception in recovery: {}", conn.address.hostName, conn.port,
                exception.toString()
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AmqpExceptionHandler::class.java)
    }
}
