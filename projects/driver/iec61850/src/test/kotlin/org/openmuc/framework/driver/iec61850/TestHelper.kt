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

import com.beanit.iec61850bean.*
import java.io.IOException
import java.net.DatagramSocket
import java.net.ServerSocket

object TestHelper {
    private const val MIN_PORT_NUMBER = 50000
    private const val PORT_SCOPE = 10000

    /* should not be thrown */
    val availablePort: Int
        get() {
            var port = MIN_PORT_NUMBER
            var isAvailable = false
            while (!isAvailable) {
                port = (Math.random() * PORT_SCOPE).toInt() + MIN_PORT_NUMBER
                var ss: ServerSocket? = null
                var ds: DatagramSocket? = null
                try {
                    ss = ServerSocket(port)
                    ss.reuseAddress = true
                    ds = DatagramSocket(port)
                    ds.reuseAddress = true
                    isAvailable = true
                } catch (e: IOException) {
                    isAvailable = false
                } finally {
                    ds?.close()
                    if (ss != null) {
                        try {
                            ss.close()
                        } catch (e: IOException) {
                            /* should not be thrown */
                        }
                    }
                }
            }
            return port
        }

    @Throws(SclParseException::class, IOException::class)
    fun runServer(
        sclFilePath: String?, port: Int, serverSap: ServerSap?, serversServerModel: ServerModel?,
        eventListener: ServerEventListener?
    ): ServerSap {
        var serverSap = serverSap
        var serversServerModel = serversServerModel
        serverSap = ServerSap(port, 0, null, SclParser.parse(sclFilePath)[0], null)
        serverSap.port = port
        serverSap.startListening(eventListener)
        serversServerModel = serverSap.modelCopy
        return serverSap
    }
}
