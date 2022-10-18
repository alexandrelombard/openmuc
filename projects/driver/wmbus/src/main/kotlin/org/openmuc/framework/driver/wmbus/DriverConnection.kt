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
package org.openmuc.framework.driver.wmbus

import org.apache.commons.codec.binary.Hex
import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.driver.spi.*
import org.openmuc.jmbus.SecondaryAddress
import org.openmuc.jmbus.wireless.WMBusConnection

//import javax.xml.bind.DatatypeConverter;
/**
 * Class representing an MBus Connection.<br></br>
 * This class will bind to the local com-interface.<br></br>
 *
 */
class DriverConnection(
    private val con: WMBusConnection?, private val secondaryAddress: SecondaryAddress?, keyString: String?,
    private val serialInterface: WMBusInterface
) : Connection {
    var containersToListenFor: List<ChannelRecordContainer?>? = ArrayList()
        private set

    init {
        if (keyString != null) {
            val keyAsBytes: ByteArray
            keyAsBytes = try {
                Hex.decodeHex(keyString)
            } catch (e: IllegalArgumentException) {
                serialInterface.connectionClosedIndication(secondaryAddress)
                throw ArgumentSyntaxException("The key could not be converted to a byte array.")
            }
            con!!.addKey(secondaryAddress, keyAsBytes)
        }
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun scanForChannels(settings: String?): List<ChannelScanInfo?>? {
        throw UnsupportedOperationException()
    }

    override fun disconnect() {
        con!!.removeKey(secondaryAddress)
        synchronized(serialInterface) { serialInterface.connectionClosedIndication(secondaryAddress) }
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun read(
        containers: List<ChannelRecordContainer?>?,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer?>?, listener: RecordsReceivedListener?) {
        containersToListenFor = containers
        serialInterface.listener = listener
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer?>?, containerListHandle: Any?): Any? {
        throw UnsupportedOperationException()
    }
}
