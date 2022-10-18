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
package org.openmuc.framework.datalogger.mqtt

import org.openmuc.framework.lib.osgi.config.GenericSettings
import org.openmuc.framework.lib.osgi.config.ServiceProperty

class MqttLoggerSettings : GenericSettings() {
    init {
        // properties for connection
        properties[PORT] = ServiceProperty(PORT, "port for MQTT communication", "1883", true)
        properties[HOST] =
            ServiceProperty(HOST, "URL of MQTT broker", "localhost", true)
        properties[SSL] = ServiceProperty(SSL, "usage of ssl true/false", "false", true)
        properties[USERNAME] = ServiceProperty(USERNAME, "name of your MQTT account", null, false)
        properties[PASSWORD] =
            ServiceProperty(PASSWORD, "password of your MQTT account", null, false)
        properties[PARSER] = ServiceProperty(
            PARSER,
            "identifier of needed parser implementation",
            "openmuc",
            true
        )
        properties[WEB_SOCKET] =
            ServiceProperty(WEB_SOCKET, "usage of WebSocket true/false", "false", true)

        // properties for recovery / file buffering
        properties[CONNECTION_RETRY_INTERVAL] = ServiceProperty(
            CONNECTION_RETRY_INTERVAL,
            "connection retry interval in s",
            "10",
            true
        )
        properties[CONNECTION_ALIVE_INTERVAL] = ServiceProperty(
            CONNECTION_ALIVE_INTERVAL,
            "connection alive interval in s",
            "10",
            true
        )
        properties[PERSISTENCE_DIRECTORY] = ServiceProperty(
            PERSISTENCE_DIRECTORY,
            "directory for file buffered messages", "data/logger/mqtt", false
        )
        properties[MULTIPLE] = ServiceProperty(
            MULTIPLE,
            "if true compose log records of different channels to one mqtt message", "false", true
        )
        properties[MAX_FILE_COUNT] = ServiceProperty(
            MAX_FILE_COUNT,
            "file buffering: number of files to be created",
            "2",
            true
        )
        properties[MAX_FILE_SIZE] =
            ServiceProperty(MAX_FILE_SIZE, "file buffering: file size in kB", "5000", true)
        properties[MAX_BUFFER_SIZE] =
            ServiceProperty(MAX_BUFFER_SIZE, "file buffering: buffer size in kB", "1000", true)
        properties[RECOVERY_CHUNK_SIZE] = ServiceProperty(
            RECOVERY_CHUNK_SIZE,
            "number of messages which will be recovered simultaneously, 0 = disabled", "0", false
        )
        properties[RECOVERY_DELAY] = ServiceProperty(
            RECOVERY_DELAY,
            "delay between recovery chunk sending in ms, 0 = disabled", "0", false
        )

        // properties for LAST WILL / FIRST WILL
        properties[LAST_WILL_TOPIC] = ServiceProperty(
            LAST_WILL_TOPIC,
            "topic on which lastWillPayload will be published",
            "",
            false
        )
        properties[LAST_WILL_PAYLOAD] = ServiceProperty(
            LAST_WILL_PAYLOAD,
            "payload which will be published after (unwanted) disconnect", "", false
        )
        properties[LAST_WILL_ALWAYS] = ServiceProperty(
            LAST_WILL_ALWAYS,
            "send the last will also on planned disconnects", "false", false
        )
        properties[FIRST_WILL_TOPIC] = ServiceProperty(
            FIRST_WILL_TOPIC,
            "topic on which firstWillPayload will be published",
            "",
            false
        )
        properties[FIRST_WILL_PAYLOAD] = ServiceProperty(
            FIRST_WILL_PAYLOAD,
            "payload which will be published after connect",
            "",
            false
        )
    }

    companion object {
        const val PORT = "port"
        const val HOST = "host"
        const val SSL = "ssl"
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val PARSER = "parser"
        const val MULTIPLE = "multiple"
        const val MAX_FILE_COUNT = "maxFileCount"
        const val MAX_FILE_SIZE = "maxFileSize"
        const val MAX_BUFFER_SIZE = "maxBufferSize"
        const val CONNECTION_RETRY_INTERVAL = "connectionRetryInterval"
        const val CONNECTION_ALIVE_INTERVAL = "connectionAliveInterval"
        const val PERSISTENCE_DIRECTORY = "persistenceDirectory"
        const val LAST_WILL_TOPIC = "lastWillTopic"
        const val LAST_WILL_PAYLOAD = "lastWillPayload"
        const val LAST_WILL_ALWAYS = "lastWillAlways"
        const val FIRST_WILL_TOPIC = "firstWillTopic"
        const val FIRST_WILL_PAYLOAD = "firstWillPayload"
        const val RECOVERY_CHUNK_SIZE = "recoveryChunkSize"
        const val RECOVERY_DELAY = "recoveryDelay"
        const val WEB_SOCKET = "webSocket"
    }
}
