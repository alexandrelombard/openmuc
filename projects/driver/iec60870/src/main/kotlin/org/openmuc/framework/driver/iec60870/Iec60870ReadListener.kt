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
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.driver.iec60870.settings.ChannelAddress
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.j60870.*
import org.slf4j.LoggerFactory
import java.io.IOException

internal class Iec60870ReadListener(clientConnection: Connection) : ConnectionEventListener {
    private var containers: List<ChannelRecordContainer>? = null
    private val channelAddressMap = HashMap<String?, ChannelAddress>()
    private val recordMap = HashMap<String?, Record?>()
    private var timeout: Long = 0
    private var ioException: IOException? = null
    private var isReadyReading = false
    @Synchronized
    fun setContainer(containers: List<ChannelRecordContainer>) {
        this.containers = containers
        val containerIterator = containers.iterator()
        while (containerIterator.hasNext()) {
            val channelRecordContainer = containerIterator.next()
            try {
                val channelAddress = ChannelAddress(channelRecordContainer.channelAddress)
                channelAddressMap[channelRecordContainer.channel.id] = channelAddress
            } catch (e: ArgumentSyntaxException) {
                logger.error(
                    "ChannelId: " + channelRecordContainer.channel.id + "; Message: " + e.message
                )
            }
        }
    }

    fun setReadTimeout(timeout: Long) {
        this.timeout = timeout
    }

    @Synchronized
    override fun newASdu(aSdu: ASdu) {
        logger.debug("Got new ASdu")
        if (logger.isTraceEnabled) {
            logger.trace(aSdu.toString())
        }
        val timestamp = System.currentTimeMillis()
        if (!aSdu.isTestFrame) {
            val keySet: Set<String?> = channelAddressMap.keys
            val iterator = keySet.iterator()
            while (iterator.hasNext()) {
                val channelId = iterator.next()
                val channelAddress = channelAddressMap[channelId]
                if (aSdu.commonAddress == channelAddress!!.commonAddress()
                    && aSdu.typeIdentification.id == channelAddress.typeId()
                ) {
                    processRecords(aSdu, timestamp, channelId, channelAddress)
                }
            }
            isReadyReading = true
        }
    }

    override fun connectionClosed(e: IOException) {
        logger.info("Connection was closed by server.")
        ioException = e
    }

    @Throws(IOException::class)
    fun read() {
        val sleepTime: Long = 100
        var time: Long = 0
        while (ioException == null && time < timeout && !isReadyReading) {
            try {
                Thread.sleep(sleepTime)
            } catch (e: InterruptedException) {
                logger.error("", e)
            }
            time += sleepTime
        }
        if (ioException != null) {
            throw IOException()
        }
        for (channelRecordContainer in containers!!) {
            val channelId = channelRecordContainer.channel.id
            val record = recordMap[channelId]
            if (record == null || record.flag !== Flag.VALID) {
                channelRecordContainer.record = Record(Flag.DRIVER_ERROR_TIMEOUT)
            } else {
                channelRecordContainer.record = record
            }
        }
        isReadyReading = false
    }

    private fun processRecords(aSdu: ASdu, timestamp: Long, channelId: String?, channelAddress: ChannelAddress?) {
        for (informationObject in aSdu.informationObjects) {
            if (informationObject.informationObjectAddress == channelAddress!!.ioa()) {
                val record = Iec60870DataHandling.handleInformationObject(
                    aSdu, timestamp, channelAddress,
                    informationObject
                )
                recordMap[channelId] = record
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Iec60870ReadListener::class.java)
    }
}
