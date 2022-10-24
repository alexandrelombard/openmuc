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

class MqttSettings(
    val host: String,
    val port: Int,
    val username: String?,
    val password: String,
    val isSsl: Boolean,
    /**
     * @return maximum buffer size in Kibibytes
     */
    val maxBufferSize: Long,
    /**
     * @return maximum file buffer size in Kibibytes
     */
    val maxFileSize: Long,
    val maxFileCount: Int,
    val connectionRetryInterval: Int,
    val connectionAliveInterval: Int,
    val persistenceDirectory: String?,
    val lastWillTopic: String,
    val lastWillPayload: ByteArray,
    private val lastWillAlways: Boolean,
    val firstWillTopic: String,
    val firstWillPayload: ByteArray,
    val recoveryChunkSize: Int,
    val recoveryDelay: Int,
    val isWebSocket: Boolean
) {

    constructor(
        host: String, port: Int, username: String?, password: String, ssl: Boolean, maxBufferSize: Long,
        maxFileSize: Long, maxFileCount: Int, connectionRetryInterval: Int, connectionAliveInterval: Int,
        persistenceDirectory: String?, webSocket: Boolean
    ) : this(
        host, port, username, password, ssl, maxBufferSize, maxFileSize, maxFileCount, connectionRetryInterval,
        connectionAliveInterval, persistenceDirectory, "", "".toByteArray(), false, "", "".toByteArray(), webSocket
    ) {
    }

    @JvmOverloads
    constructor(
        host: String,
        port: Int,
        username: String?,
        password: String,
        ssl: Boolean,
        maxBufferSize: Long,
        maxFileSize: Long,
        maxFileCount: Int,
        connectionRetryInterval: Int,
        connectionAliveInterval: Int,
        persistenceDirectory: String?,
        lastWillTopic: String = "",
        lastWillPayload: ByteArray = "".toByteArray(),
        lastWillAlways: Boolean = false,
        firstWillTopic: String = "",
        firstWillPayload: ByteArray = "".toByteArray(),
        webSocket: Boolean = false
    ) : this(
        host, port, username, password, ssl, maxBufferSize, maxFileSize, maxFileCount, connectionRetryInterval,
        connectionAliveInterval, persistenceDirectory, lastWillTopic, lastWillPayload, lastWillAlways,
        firstWillTopic, firstWillPayload, 0, 0, webSocket
    )

    val isLastWillSet: Boolean
        get() = lastWillTopic != "" && lastWillPayload.size != 0

    val isLastWillAlways: Boolean
        get() = lastWillAlways && isLastWillSet

    val isFirstWillSet: Boolean
        get() = firstWillTopic != "" && lastWillPayload.size != 0
    val isRecoveryLimitSet: Boolean
        get() = recoveryChunkSize > 0 && recoveryDelay > 0

    /**
     * Returns a string of all settings, always uses '*****' as password string.
     */
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("host=").append(host).append("\n")
        sb.append("port=").append(port).append("\n")
        sb.append("username=").append(username).append("\n")
        sb.append("password=").append("*****").append("\n")
        sb.append("ssl=").append(isSsl).append("\n")
        sb.append("webSocket=").append(isWebSocket)
        sb.append("persistenceDirectory=").append(persistenceDirectory).append("\n")
        sb.append("maxBufferSize=").append(maxBufferSize).append("\n")
        sb.append("maxFileCount=").append(maxFileCount).append("\n")
        sb.append("maxFileSize=").append(maxFileSize).append("\n")
        sb.append("connectionRetryInterval=").append(connectionRetryInterval).append("\n")
        sb.append("connectionAliveInterval=").append(connectionAliveInterval).append("\n")
        sb.append("lastWillTopic=").append(lastWillTopic).append("\n")
        sb.append("lastWillPayload=").append(String(lastWillPayload)).append("\n")
        sb.append("lastWillAlways=").append(isLastWillAlways).append("\n")
        sb.append("firstWillTopic=").append(firstWillTopic).append("\n")
        sb.append("firstWillPayload=").append(String(firstWillPayload!!))
        sb.append("recoveryChunkSize=").append(recoveryChunkSize).append("\n")
        sb.append("recoveryDelay=").append(recoveryDelay).append("\n")
        return sb.toString()
    }
}
