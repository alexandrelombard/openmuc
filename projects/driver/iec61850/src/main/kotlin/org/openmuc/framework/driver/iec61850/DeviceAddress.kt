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
package org.openmuc.framework.driver.iec61850

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.driver.spi.ConnectionException
import java.net.InetAddress
import java.net.UnknownHostException

class DeviceAddress(deviceAddress: String?) {
    var adress: InetAddress? = null
    var remotePort = 102

    init {
        val deviceAddresses = deviceAddress!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (deviceAddresses.size > 2) {
            throw ArgumentSyntaxException("Invalid device address syntax.")
        }
        val remoteHost = deviceAddresses[0]
        try {
            adress = InetAddress.getByName(remoteHost)
        } catch (e: UnknownHostException) {
            throw ConnectionException("Unknown host: $remoteHost", e)
        }
        if (deviceAddresses.size == 2) {
            remotePort = try {
                deviceAddresses[1].toInt()
            } catch (e: NumberFormatException) {
                throw ArgumentSyntaxException("The specified port is not an integer")
            }
        }
    }
}
