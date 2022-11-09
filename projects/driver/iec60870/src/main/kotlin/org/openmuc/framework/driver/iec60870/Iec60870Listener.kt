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

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.data.Record
import org.openmuc.framework.driver.iec60870.settings.ChannelAddress
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.framework.driver.spi.Connection
import org.openmuc.framework.driver.spi.ConnectionException
import org.openmuc.framework.driver.spi.RecordsReceivedListener
import org.openmuc.j60870.*
import org.slf4j.LoggerFactory
import java.io.IOException

class Iec60870Listener : ConnectionEventListener {
    private var listener: RecordsReceivedListener? = null
    private var containers: List<ChannelRecordContainer>? = null
    private var channelAddresses: MutableList<ChannelAddress>? = ArrayList()
    private var driverId: String? = null
    private var connection: Connection? = null
    @Synchronized
    @Throws(ConnectionException::class)
    fun registerOpenMucListener(
        containers: List<ChannelRecordContainer>,
        listener: RecordsReceivedListener?, driverId: String?, connection: Connection?
    ) {
        this.containers = containers
        this.listener = listener
        this.driverId = driverId
        this.connection = connection
        val containerIterator = containers.iterator()
        while (containerIterator.hasNext()) {
            val channelRecordContainer = containerIterator.next()
            try {
                val channelAddress = ChannelAddress(
                    channelRecordContainer.channelAddress
                )
                channelAddresses!!.add(channelAddress)
            } catch (e: ArgumentSyntaxException) {
                logger.error(
                    "ChannelId: " + channelRecordContainer.channel?.id + "; Message: " + e.message
                )
            }
        }
    }

    @Synchronized
    fun unregisterOpenMucListener() {
        containers = null
        listener = null
        channelAddresses = null
    }

    @Synchronized
    override fun newASdu(aSdu: ASdu) {
        logger.debug("Got new ASdu")
        if (logger.isTraceEnabled) {
            logger.trace(aSdu.toString())
        }
        if (listener != null) {
            val timestamp = System.currentTimeMillis()
            if (!aSdu.isTestFrame) {
                val channelAddressIterator: Iterator<ChannelAddress> = channelAddresses!!.iterator()
                var i = 0
                while (channelAddressIterator.hasNext()) {
                    val channelAddress = channelAddressIterator.next()
                    if (aSdu.commonAddress == channelAddress.commonAddress()
                        && aSdu.typeIdentification.id == channelAddress.typeId()
                    ) {
                        processRecords(aSdu, timestamp, i, channelAddress)
                    }
                    ++i
                }
            }
        } else {
            logger.warn("Listener object is null.")
        }
    }

    private fun processRecords(aSdu: ASdu, timestamp: Long, i: Int, channelAddress: ChannelAddress) {
        for (informationObject in aSdu.informationObjects) {
            if (informationObject.informationObjectAddress == channelAddress.ioa()) {
                val record = Iec60870DataHandling.handleInformationObject(
                    aSdu, timestamp, channelAddress,
                    informationObject
                )
                newRecords(i, record)
            }
        }
    }

    override fun connectionClosed(e: IOException) {
        logger.info("Connection was closed by server.")
        listener?.connectionInterrupted(driverId!!, connection!!)
    }

    private fun newRecords(i: Int, record: Record) {
        if (logger.isTraceEnabled) {
            logger.trace("Set new Record: $record")
        }
        listener?.newRecords(creatNewChannelRecordContainer(containers!![i], record))
    }

    private fun creatNewChannelRecordContainer(
        container: ChannelRecordContainer,
        record: Record
    ): List<ChannelRecordContainer> {
        val channelRecordContainerList: MutableList<ChannelRecordContainer> = ArrayList()
        container.record = record
        channelRecordContainerList.add(container)
        return channelRecordContainerList
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Iec60870Listener::class.java)
    }
}
