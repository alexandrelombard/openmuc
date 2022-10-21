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
package org.openmuc.framework.driver.modbus.tcp

import com.ghgande.j2mod.modbus.ModbusException
import com.ghgande.j2mod.modbus.ModbusIOException
import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction
import com.ghgande.j2mod.modbus.net.TCPMasterConnection
import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.config.ScanException
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.Value
import org.openmuc.framework.driver.modbus.ModbusChannel.EAccess
import org.openmuc.framework.driver.modbus.ModbusConnection
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.framework.driver.spi.ChannelValueContainer
import org.openmuc.framework.driver.spi.ConnectionException
import org.openmuc.framework.driver.spi.RecordsReceivedListener
import org.slf4j.LoggerFactory
import java.net.InetAddress

/**
 * Modbus connection using TCP for data transfer
 */
class ModbusTCPConnection(deviceAddress: String, private val timeoutMs: Int) : ModbusConnection() {
    private var connection: TCPMasterConnection
    private var transaction: ModbusTCPTransaction? = null

    init {
        val address = ModbusTCPDeviceAddress(deviceAddress)
        try {
            connection = TCPMasterConnection(InetAddress.getByName(address.ip))
            connection.setPort(address.port)
            connect()
        } catch (e: Exception) {
            logger.error("Unable to connect to device $deviceAddress", e)
            throw ConnectionException()
        }
        logger.info("Modbus Device: {} connected", deviceAddress)
    }

    @Throws(ConnectionException::class)
    override fun connect() {
        if (connection != null && !connection.isConnected()) {
            try {
                connection.connect()
            } catch (e: Exception) {
                throw ConnectionException(e)
            }
            connection.setTimeout(timeoutMs)
            transaction = ModbusTCPTransaction(connection)
            setTransaction(transaction)
            if (!connection.isConnected()) {
                throw ConnectionException("unable to connect")
            }
        }
    }

    override fun disconnect() {
        try {
            logger.info("Disconnect Modbus TCP device")
            if (connection.isConnected) {
                connection.close()
                transaction = null
            }
        } catch (e: Exception) {
            logger.error("Unable to disconnect connection", e)
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

                // TODO consider retries in sampling timeout (e.g. one time 12000 ms or three times 4000 ms)
                // FIXME quite inconvenient/complex to get the timeout from config, since the driver doesn't know the
                // device id!
                val receiveTime = System.currentTimeMillis()
                val channel = getModbusChannel(container.channelAddress, EAccess.READ)
                var value: Value?
                try {
                    value = readChannel(channel)
                    if (logger.isTraceEnabled) {
                        logger.trace("Value of response: {}", value.toString())
                    }
                    container.record = Record(value, receiveTime)
                } catch (e: ModbusIOException) {
                    logger.error(
                        "ModbusIOException while reading channel:" + channel.channelAddress
                                + " used timeout: " + timeoutMs + " ms", e
                    )
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
                writeChannel(channel, container.value)
                container.flag = Flag.VALID
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
        private val logger = LoggerFactory.getLogger(ModbusTCPConnection::class.java)
    }
}
