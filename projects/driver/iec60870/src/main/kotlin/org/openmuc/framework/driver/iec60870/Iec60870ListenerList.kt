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
package org.openmuc.framework.driver.iec60870

import org.openmuc.j60870.ASdu
import org.openmuc.j60870.ConnectionEventListener
import java.io.IOException

internal class Iec60870ListenerList : ConnectionEventListener {
    var connectionEventListeners: MutableList<ConnectionEventListener> = ArrayList()
    fun addListener(connectionEventListener: ConnectionEventListener) {
        connectionEventListeners.add(connectionEventListener)
    }

    fun removeAllListener() {
        connectionEventListeners.clear()
    }

    override fun newASdu(aSdu: ASdu) {
        for (connectionEventListener in connectionEventListeners) {
            connectionEventListener.newASdu(aSdu)
        }
    }

    override fun connectionClosed(e: IOException) {
        for (connectionEventListener in connectionEventListeners) {
            connectionEventListener.connectionClosed(e)
        }
    }
}
