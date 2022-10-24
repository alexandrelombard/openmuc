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

/**
 * Settings needed by AmqpConnection
 */
class AmqpSettings {
    val host: String
    val port: Int
    val virtualHost: String
    val username: String
    val password: String
    val isSsl: Boolean
    val exchange: String
    val persistenceDirectory: String
    val maxFileCount: Int
    val maxFileSize: Long
    val maxBufferSize: Long
    val connectionAliveInterval: Int

    /**
     * @param host
     * the host, i.e. broker.domain.tld
     * @param port
     * the port, i.e. 5672
     * @param virtualHost
     * the virtualHost to use, i.e. /
     * @param username
     * the username, i.e. guest
     * @param password
     * the password, i.e. guest
     * @param ssl
     * whether connecting with ssl
     * @param exchange
     * the exchange to publish to
     * @param persistenceDirectory
     * directory being used by FilePersistence
     * @param maxFileCount
     * maximum file count per buffer created by FilePersistence
     * @param maxFileSize
     * maximum file size per FilePersistence buffer file
     * @param maxBufferSize
     * maximum RAM buffer size
     * @param connectionAliveInterval
     * checks every given seconds if connection is alive
     */
    constructor(
        host: String, port: Int, virtualHost: String, username: String, password: String, ssl: Boolean,
        exchange: String, persistenceDirectory: String, maxFileCount: Int, maxFileSize: Long, maxBufferSize: Long,
        connectionAliveInterval: Int
    ) {
        this.host = host
        this.port = port
        this.virtualHost = virtualHost
        this.username = username
        this.password = password
        isSsl = ssl
        this.exchange = exchange
        this.persistenceDirectory = persistenceDirectory
        this.maxFileCount = maxFileCount
        this.maxFileSize = maxFileSize
        this.maxBufferSize = maxBufferSize
        this.connectionAliveInterval = connectionAliveInterval
    }

    constructor(
        host: String, port: Int, virtualHost: String, username: String, password: String, ssl: Boolean,
        exchange: String
    ) {
        this.host = host
        this.port = port
        this.virtualHost = virtualHost
        this.username = username
        this.password = password
        isSsl = ssl
        this.exchange = exchange
        persistenceDirectory = ""
        maxFileCount = 0
        maxFileSize = 0
        maxBufferSize = 0
        connectionAliveInterval = 0
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("host = $host\n")
        sb.append("port = $port\n")
        sb.append("vHost = $virtualHost\n")
        sb.append("username = $username\n")
        sb.append("passwort = $password\n")
        sb.append(
            """
    ssl = ${isSsl}
    
    """.trimIndent()
        )
        sb.append("exchange = $exchange\n")
        sb.append("persistenceDirectory = $persistenceDirectory\n")
        sb.append("maxFileCount = $maxFileCount\n")
        sb.append("maxFileSize = $maxFileSize\n")
        sb.append("maxBufferSize = $maxBufferSize\n")
        sb.append("connectionAliveInterval = $connectionAliveInterval\n")
        return sb.toString()
    }
}
