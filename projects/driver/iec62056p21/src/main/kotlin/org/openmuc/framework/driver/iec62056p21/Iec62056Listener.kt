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
package org.openmuc.framework.driver.iec62056p21

import org.openmuc.framework.data.DoubleValue
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.StringValue
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.framework.driver.spi.ConnectionException
import org.openmuc.framework.driver.spi.RecordsReceivedListener
import org.openmuc.j62056.DataMessage
import org.openmuc.j62056.DataSet
import org.openmuc.j62056.ModeDListener
import org.slf4j.LoggerFactory

class Iec62056Listener : ModeDListener {
    private var listener: RecordsReceivedListener? = null
    private var containers: List<ChannelRecordContainer> = listOf()
    @Synchronized
    @Throws(ConnectionException::class)
    fun registerOpenMucListener(
        containers: List<ChannelRecordContainer>,
        listener: RecordsReceivedListener?
    ) {
        this.listener = listener
        this.containers = containers
    }

    @Synchronized
    fun unregisterOpenMucListener() {
        listener = null
        containers = listOf()
    }

    override fun newDataMessage(dataMessage: DataMessage) {
        val time = System.currentTimeMillis()
        val dataSets = dataMessage.dataSets
        newRecord(dataSets, time)
    }

    @Synchronized
    private fun newRecord(dataSets: List<DataSet>, time: Long) {
        val newContainers: MutableList<ChannelRecordContainer> = ArrayList()
        for (container in containers) {
            for (dataSet in dataSets) {
                if (dataSet.address == container.channelAddress) {
                    val value = dataSet.value
                    if (value != null) {
                        try {
                            container.record = Record(DoubleValue(dataSet.value.toDouble()), time)
                            newContainers.add(container)
                        } catch (e: NumberFormatException) {
                            container.record = Record(StringValue(dataSet.value), time)
                        }
                    }
                    break
                }
            }
        }
        listener?.newRecords(newContainers)
    }

    override fun exceptionWhileListening(e: Exception) {
        logger.info("Exception while listening. Message: " + e.message)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Iec62056Listener::class.java)
    }
}
