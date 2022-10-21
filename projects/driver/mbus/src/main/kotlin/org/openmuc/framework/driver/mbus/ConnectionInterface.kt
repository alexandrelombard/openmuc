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
package org.openmuc.framework.driver.mbus

import org.openmuc.jmbus.MBusConnection

/**
 * Class representing an MBus Connection.<br></br>
 * This class will bind to the local com-interface.<br></br>
 *
 */
class ConnectionInterface {
    var deviceCounter = 0
        private set
    var mBusConnection: MBusConnection
        private set
    var isOpen = true
        private set
    val interfaceAddress: String
    var delay = 0
        private set
    private var interfaces: MutableMap<String, ConnectionInterface> = hashMapOf()

    constructor(
        mBusConnection: MBusConnection, serialPortName: String, delay: Int,
        interfaces: MutableMap<String, ConnectionInterface>
    ) {
        interfaceAddress = serialPortName
        this.mBusConnection = mBusConnection
        this.interfaces = interfaces
        this.delay = delay
        interfaces[interfaceAddress] = this
    }

    constructor(
        mBusConnection: MBusConnection, host: String, port: Int, delay: Int,
        interfaces: MutableMap<String, ConnectionInterface>
    ) {
        interfaceAddress = host + port
        this.mBusConnection = mBusConnection
        this.interfaces = interfaces
        this.delay = delay
        interfaces[interfaceAddress] = this
    }

    fun increaseConnectionCounter() {
        deviceCounter++
    }

    fun decreaseConnectionCounter() {
        deviceCounter--
        if (deviceCounter == 0) {
            close()
        }
    }

    fun close() {
        synchronized(interfaces) {
            mBusConnection.close()
            isOpen = false
            interfaces.remove(interfaceAddress)
        }
    }
}
