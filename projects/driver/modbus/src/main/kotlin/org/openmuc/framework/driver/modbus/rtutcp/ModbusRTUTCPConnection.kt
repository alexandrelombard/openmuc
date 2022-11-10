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
package org.openmuc.framework.driver.modbus.rtutcp

import com.ghgande.j2mod.modbus.ModbusException
import com.ghgande.j2mod.modbus.ModbusIOException
import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.config.ScanException
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.Value
import org.openmuc.framework.driver.modbus.ModbusChannel.EAccess
import org.openmuc.framework.driver.modbus.ModbusConnection
import org.openmuc.framework.driver.modbus.rtutcp.bonino.ModbusRTUTCPTransaction
import org.openmuc.framework.driver.modbus.rtutcp.bonino.RTUTCPMasterConnection
import org.openmuc.framework.driver.modbus.tcp.ModbusTCPDeviceAddress
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.framework.driver.spi.ChannelValueContainer
import org.openmuc.framework.driver.spi.ConnectionException
import org.openmuc.framework.driver.spi.RecordsReceivedListener
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * TODO
 */
class ModbusRTUTCPConnection(deviceAddress: String, timeoutMs: Int) : ModbusConnection() {
    private var connection: RTUTCPMasterConnection
    private var transaction: ModbusRTUTCPTransaction? = null

    init {
        val address = ModbusTCPDeviceAddress(deviceAddress)
        try {
            connection = RTUTCPMasterConnection(InetAddress.getByName(address.ip), address.port)
            connection.timeout = timeoutMs
            connect()
        } catch (e: UnknownHostException) {
            throw RuntimeException(e.message)
        } catch (e: Exception) {
            throw RuntimeException(e.message)
        }
        logger.info("Modbus Device: $deviceAddress connected")
    }

    @Throws(ConnectionException::class)
    override fun connect() {
        if (!connection.isConnected) {
            try {
                connection.connect()
            } catch (e: Exception) {
                throw ConnectionException(e)
            }
            transaction = ModbusRTUTCPTransaction(connection)
            setTransaction(transaction)
            if (!connection.isConnected) {
                throw ConnectionException("unable to connect")
            }
        }
    }

    override fun disconnect() {
        logger.info("Disconnect Modbus TCP device")
        if (connection.isConnected) {
            connection.close()
            transaction = null
        }
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun read(
        containers: List<ChannelRecordContainer>,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {

        // reads channels one by one
        if (samplingGroup!!.isEmpty()) {
            for (container in containers) {
                val receiveTime = System.currentTimeMillis()
                val channel = getModbusChannel(container.channelAddress, EAccess.READ)
                var value: Value?
                try {
                    value = readChannel(channel)
                    if (logger.isTraceEnabled) {
                        logger.trace("Value of response: $value")
                    }
                    container.record = Record(value, receiveTime)
                } catch (e: ModbusIOException) {
                    logger.error("ModbusIOException while reading channel:" + channel.channelAddress, e)
                    disconnect()
                    throw ConnectionException("Try to solve issue with reconnect.")
                } catch (e: ModbusException) {
                    logger.error("ModbusException while reading channel: " + channel.channelAddress, e)
                    container.record = Record(Flag.DRIVER_ERROR_CHANNEL_NOT_ACCESSIBLE)
                } catch (e: Exception) {
                    // catch all possible exceptions and provide info about the channel
                    logger.error("Exception while reading channel: " + channel.channelAddress, e)
                    container.record = Record(Flag.UNKNOWN_ERROR)
                }
                if (!connection.isConnected) {
                    throw ConnectionException("Lost connection.")
                }
            }
        } else {
            readChannelGroupHighLevel(containers, containerListHandle, samplingGroup)
            if (!connection.isConnected) {
                throw ConnectionException("Lost connection.")
            }
        }
        return null
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer>, containerListHandle: Any?): Any? {
        for (container in containers) {
            val channel = getModbusChannel(container.channelAddress, EAccess.WRITE)
            try {
                container.value.let {
                    if(it != null) {
                        writeChannel(channel, it)
                        container.flag = Flag.VALID
                    }
                }
            } catch (e: ModbusIOException) {
                logger.error("ModbusIOException while writing channel:" + channel.channelAddress, e)
                disconnect()
                throw ConnectionException("Try to solve issue with reconnect.")
            } catch (e: ModbusException) {
                logger.error("ModbusException while writing channel: " + channel.channelAddress, e)
                container.flag = Flag.DRIVER_ERROR_CHANNEL_NOT_ACCESSIBLE
            } catch (e: Exception) {
                logger.error("Exception while writing channel: " + channel.channelAddress, e)
                container.flag = Flag.UNKNOWN_ERROR
            }
        }
        return null
    }

    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ConnectionException::class
    )
    override fun scanForChannels(settings: String): List<ChannelScanInfo> {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer>, listener: RecordsReceivedListener?) {
        throw UnsupportedOperationException()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ModbusRTUTCPConnection::class.java)
    }
}
