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
package org.openmuc.framework.driver.ehz

import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.Value
import org.openmuc.framework.driver.spi.*

abstract class GeneralConnection : Connection {
    @Throws(ConnectionException::class)
    abstract fun read(containers: List<ChannelRecordContainer?>?, timeout: Int)
    abstract fun scanForChannels(timeout: Int): List<ChannelScanInfo?>
    abstract fun works(): Boolean
    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun scanForChannels(settings: String?): List<ChannelScanInfo?>? {
        return scanForChannels(20000)
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun read(
        containers: List<ChannelRecordContainer?>?,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        read(containers, TIMEOUT)
        return null
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer?>?, listener: RecordsReceivedListener?) {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer?>?, containerListHandle: Any?): Any? {
        throw UnsupportedOperationException()
    }

    companion object {
        const val TIMEOUT = 10000
        protected fun handleChannelRecordContainer(
            containers: List<ChannelRecordContainer?>?,
            values: Map<String?, Value?>, timestamp: Long
        ) {
            for (container in containers!!) {
                val address = container!!.channelAddress
                val value = values[address] ?: continue
                val record = Record(value, timestamp, Flag.VALID)
                container.setRecord(record)
            }
        }
    }
}
