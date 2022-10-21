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
package org.openmuc.framework.driver.modbus.tcp

import com.ghgande.j2mod.modbus.Modbus
import java.util.*

class ModbusTCPDeviceAddress(deviceAddress: String) {
    val ip: String
    val port: Int

    init {
        val address = deviceAddress.lowercase(Locale.getDefault()).split(":".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        if (address.size == 1) {
            ip = address[0]
            port = Modbus.DEFAULT_PORT
        } else if (address.size == 2) {
            ip = address[0]
            port = address[1].toInt()
        } else {
            throw RuntimeException(
                "Invalid device address: '" + deviceAddress
                        + "'! Use following format: [ip:port] like localhost:1502 or 127.0.0.1:1502"
            )
        }
    }
}
