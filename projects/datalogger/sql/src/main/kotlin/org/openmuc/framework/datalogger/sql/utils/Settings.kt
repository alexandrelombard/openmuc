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
package org.openmuc.framework.datalogger.sql.utils

import org.openmuc.framework.lib.osgi.config.GenericSettings
import org.openmuc.framework.lib.osgi.config.ServiceProperty

class Settings : GenericSettings() {
    init {
        val defaultUrl = "jdbc:h2:retry:file:./data/h2/h2;AUTO_SERVER=TRUE;MODE=MYSQL"
        properties[URL] = ServiceProperty(
            URL,
            "URL of the used database",
            defaultUrl,
            true
        )
        properties[USER] = ServiceProperty(
            USER,
            "User of the used database",
            "openmuc",
            true
        )
        properties[PASSWORD] = ServiceProperty(
            PASSWORD,
            "Password for the database user",
            "openmuc",
            true
        )
        properties[SSL] = ServiceProperty(
            SSL,
            "SSL needed for the database connection",
            "false",
            false
        )
        properties[SOCKET_TIMEOUT] = ServiceProperty(
            SOCKET_TIMEOUT,
            "seconds after a timeout is thrown",
            "5",
            false
        )
        properties[TCP_KEEP_ALIVE] = ServiceProperty(
            TCP_KEEP_ALIVE,
            "keep tcp connection alive",
            "true",
            false
        )
        properties[PSQL_PASS] = ServiceProperty(
            PSQL_PASS,
            "password for postgresql",
            "postgres",
            true
        )
        properties[TIMEZONE] = ServiceProperty(
            TIMEZONE,
            "local time zone",
            "Europe/Berlin",
            false
        )
    }

    companion object {
        @JvmField
        var URL = "url"
        var USER = "user"
        var PASSWORD = "password"
        var SSL = "ssl"
        var SOCKET_TIMEOUT = "socket_timeout"
        var TCP_KEEP_ALIVE = "tcp_keep_alive"
        var PSQL_PASS = "psql_pass"
        var TIMEZONE = "timezone"
    }
}
