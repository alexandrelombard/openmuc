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
package org.openmuc.framework.datalogger.amqp

import org.openmuc.framework.lib.osgi.config.GenericSettings
import org.openmuc.framework.lib.osgi.config.ServiceProperty

class Settings : GenericSettings() {
    init {
        properties[PORT] = ServiceProperty(
            PORT,
            "port for AMQP communication",
            "5672",
            true
        )
        properties[HOST] = ServiceProperty(
            HOST,
            "URL of AMQP broker",
            "localhost",
            true
        )
        properties[SSL] = ServiceProperty(
            SSL,
            "usage of ssl true/false",
            "false",
            true
        )
        properties[USERNAME] = ServiceProperty(
            USERNAME,
            "name of your AMQP account",
            "guest",
            true
        )
        properties[PASSWORD] = ServiceProperty(
            PASSWORD,
            "password of your AMQP account",
            "guest",
            true
        )
        properties[PARSER] = ServiceProperty(
            PARSER,
            "identifier of needed parser implementation",
            "openmuc",
            true
        )
        properties[VIRTUAL_HOST] = ServiceProperty(
            VIRTUAL_HOST,
            "used virtual amqp host",
            "/",
            true
        )
        properties[FRAMEWORK] = ServiceProperty(
            FRAMEWORK,
            "framework identifier",
            null,
            false
        )
        properties[EXCHANGE] = ServiceProperty(
            EXCHANGE,
            "used amqp exchange",
            null,
            false
        )
        properties[PERSISTENCE_DIR] = ServiceProperty(
            PERSISTENCE_DIR,
            "persistence directory used for file buffer", "data/amqp/logger", true
        )
        properties[MAX_BUFFER_SIZE] = ServiceProperty(
            MAX_BUFFER_SIZE,
            "maximum RAM usage of buffer",
            "1024",
            true
        )
        properties[MAX_FILE_SIZE] = ServiceProperty(
            MAX_FILE_SIZE,
            "maximum file size per buffer file",
            "5120",
            true
        )
        properties[MAX_FILE_COUNT] = ServiceProperty(
            MAX_FILE_COUNT,
            "maximum number of files per buffer",
            "2",
            true
        )
        properties[CONNECTION_ALIVE_INTERVAL] = ServiceProperty(
            CONNECTION_ALIVE_INTERVAL,
            "interval in seconds to detect broken connections (heartbeat)", "60", true
        )
    }

    companion object {
        const val VIRTUAL_HOST = "virtualHost"
        const val SSL = "ssl"
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val FRAMEWORK = "framework"
        const val PARSER = "parser"
        const val EXCHANGE = "exchange"
        const val PORT = "port"
        const val HOST = "host"
        const val PERSISTENCE_DIR = "persistenceDirectory"
        const val MAX_FILE_COUNT = "maxFileCount"
        const val MAX_FILE_SIZE = "maxFileSize"
        const val MAX_BUFFER_SIZE = "maxBufferSize"
        const val CONNECTION_ALIVE_INTERVAL = "connectionAliveInterval"
    }
}
