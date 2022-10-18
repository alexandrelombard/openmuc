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
import org.openmuc.framework.data.Value
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.driver.ehz.iec62056_21.DataSet
import org.openmuc.framework.driver.ehz.iec62056_21.IecReceiver
import org.openmuc.framework.driver.ehz.iec62056_21.ModeDMessage
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.framework.driver.spi.ConnectionException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.text.ParseException
import java.util.*

class IecConnection(deviceAddress: String?, timeout: Int) : GeneralConnection() {
    private var receiver: IecReceiver? = null

    init {
        receiver = try {
            IecReceiver(deviceAddress)
        } catch (e: Exception) {
            throw ConnectionException("serial port not found")
        }
    }

    override fun disconnect() {
        receiver!!.close()
    }

    @Throws(ConnectionException::class)
    override fun read(containers: List<ChannelRecordContainer?>?, timeout: Int) {
        logger.trace("reading channels")
        val timestamp = System.currentTimeMillis()
        try {
            val frame = receiver!!.receiveMessage(timeout.toLong())
            val message: ModeDMessage = ModeDMessage.Companion.parse(frame)
            val dataSets = message.dataSets
            val values: MutableMap<String?, Value?> = LinkedHashMap()
            for (ds in dataSets!!) {
                val dataSet = DataSet(ds)
                val address = dataSet.address
                val value: Value? = dataSet.parseValueAsDouble()
                values[address] = value
                logger.trace("{} = {}", address, value)
            }
            GeneralConnection.Companion.handleChannelRecordContainer(containers, values, timestamp)
        } catch (e: IOException) {
            logger.error("read failed", e)
            disconnect()
            throw ConnectionException(e)
        } catch (e: ParseException) {
            logger.error("parsing failed", e)
        }
    }

    override fun scanForChannels(timeout: Int): List<ChannelScanInfo?> {
        val channelInfos: MutableList<ChannelScanInfo?> = LinkedList()
        logger.debug("scanning channels")
        try {
            val frame = receiver!!.receiveMessage(timeout.toLong())
            val message: ModeDMessage = ModeDMessage.Companion.parse(frame)
            val dataSets = message.dataSets
            for (data in dataSets!!) {
                val dataSet = DataSet(data)
                val channelAddress = dataSet.address
                val description = "Current value: " + dataSet.parseValueAsDouble() + dataSet.unit
                val valueType = ValueType.DOUBLE
                val valueTypeLength: Int? = null
                val readable = true
                val writable = false
                val channelInfo = ChannelScanInfo(
                    channelAddress, description, valueType,
                    valueTypeLength!!, readable, writable
                )
                channelInfos.add(channelInfo)
            }
        } catch (e: ParseException) {
            logger.warn("read failed", e)
        } catch (e: IOException) {
            logger.warn("read failed", e)
        }
        return channelInfos
    }

    override fun works(): Boolean {
        try {
            val frame = receiver!!.receiveMessage(1000)
            ModeDMessage.Companion.parse(frame)
        } catch (e: IOException) {
            return false
        } catch (e: ParseException) {
            return false
        }
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(IecConnection::class.java)
    }
}
