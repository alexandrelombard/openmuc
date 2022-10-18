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
package org.openmuc.framework.driver.knx

import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.framework.driver.spi.RecordsReceivedListener
import org.slf4j.LoggerFactory
import tuwien.auto.calimero.DetachEvent
import tuwien.auto.calimero.GroupAddress
import tuwien.auto.calimero.process.ProcessEvent
import tuwien.auto.calimero.process.ProcessListener

class KnxProcessListener : ProcessListener {
    private var containers: List<ChannelRecordContainer?>?
    private var listener: RecordsReceivedListener?
    private val cachedValues: MutableMap<GroupAddress, ByteArray>

    init {
        cachedValues = LinkedHashMap()
        containers = null
        listener = null
    }

    @Synchronized
    fun registerOpenMucListener(
        containers: List<ChannelRecordContainer?>?,
        listener: RecordsReceivedListener?
    ) {
        this.containers = containers
        this.listener = listener
    }

    @Synchronized
    fun unregisterOpenMucListener() {
        containers = null
        listener = null
    }

    /*
     * (non-Javadoc)
     * 
     * @see tuwien.auto.calimero.process.ProcessListener#groupWrite(tuwien.auto.calimero.process.ProcessEvent)
     */
    override fun groupWrite(e: ProcessEvent) {
        if (listener != null) {
            val timestamp = System.currentTimeMillis()
            for (container in containers!!) {
                val groupDP = container!!.channelHandle as KnxGroupDP?
                if (groupDP!!.mainAddress == e.destination) {
                    val value = groupDP.knxValue
                    value!!.setData(e.asdu)
                    logger.debug("Group write: " + e.destination)
                    val record = Record(value.openMucValue, timestamp, Flag.VALID)
                    listener!!.newRecords(createNewRecords(container, record))
                    break
                }
            }
        }
        cachedValues[e.destination] = e.asdu
    }

    /*
     * (non-Javadoc)
     * 
     * @see tuwien.auto.calimero.process.ProcessListener#detached(tuwien.auto.calimero.DetachEvent)
     */
    override fun detached(e: DetachEvent) {}
    fun getCachedValues(): Map<GroupAddress, ByteArray> {
        return cachedValues
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KnxProcessListener::class.java)
        private fun createNewRecords(
            container: ChannelRecordContainer?,
            record: Record
        ): List<ChannelRecordContainer?> {
            val recordContainers: MutableList<ChannelRecordContainer?> = ArrayList()
            container!!.setRecord(record)
            recordContainers.add(container)
            return recordContainers
        }
    }
}
