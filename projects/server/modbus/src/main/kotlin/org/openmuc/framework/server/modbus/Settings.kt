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
package org.openmuc.framework.server.modbus

import org.openmuc.framework.lib.osgi.config.GenericSettings
import org.openmuc.framework.lib.osgi.config.ServiceProperty

internal class Settings : GenericSettings() {
    init {
        properties[PORT] =
            ServiceProperty(PORT, "Port to listen on", "502", false)
        properties[ADDRESS] = ServiceProperty(
            ADDRESS,
            "IP address to listen on",
            "127.0.0.1",
            false
        )
        properties[UNITID] = ServiceProperty(
            UNITID,
            "UnitId of the slave",
            "15",
            false
        )
        properties[POOLSIZE] = ServiceProperty(
            POOLSIZE,
            "Listener thread pool size, only has affects with TCP and RTUTCP", "3", false
        )
        properties[TYPE] = ServiceProperty(
            TYPE,
            "Connection type, could be TCP, RTUTCP or UDP",
            "tcp",
            false
        )
    }

    companion object {
        const val PORT = "port"
        const val ADDRESS = "address"
        const val UNITID = "unitId"
        const val TYPE = "type"
        const val POOLSIZE = "poolsize"
    }
}
