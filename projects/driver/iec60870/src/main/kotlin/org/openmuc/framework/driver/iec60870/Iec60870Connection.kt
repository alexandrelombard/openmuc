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
import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.config.ScanException
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.TypeConversionException
import org.openmuc.framework.driver.iec60870.settings.ChannelAddress
import org.openmuc.framework.driver.iec60870.settings.DeviceAddress
import org.openmuc.framework.driver.iec60870.settings.DeviceSettings
import org.openmuc.framework.driver.spi.*
import org.openmuc.framework.driver.spi.ChannelValueContainer
import org.openmuc.j60870.CauseOfTransmission
import org.openmuc.j60870.ClientConnectionBuilder
import org.openmuc.j60870.Connection
import org.openmuc.j60870.ie.IeQualifierOfInterrogation
import org.slf4j.LoggerFactory
import java.io.IOException
import java.text.MessageFormat

class Iec60870Connection(
    private val deviceAddress: DeviceAddress,
    private val deviceSettings: DeviceSettings,
    private val driverId: String
) : org.openmuc.framework.driver.spi.Connection {
    private lateinit var clientConnection: Connection
    private val iec60870listener: Iec60870ListenerList
    private val readListener: Iec60870ReadListener

    init {
        val clientConnectionBuilder = ClientConnectionBuilder(deviceAddress.hostAddress())
        val port = deviceAddress.port()
        val hostAddress = deviceAddress.hostAddress()!!.hostAddress
        try {
            setupClientSap(clientConnectionBuilder, deviceSettings)
            connect(clientConnectionBuilder, port, hostAddress)
            startListenIec60870(deviceSettings, port, hostAddress)

            this.iec60870listener = Iec60870ListenerList()
            this.readListener = Iec60870ReadListener(clientConnection)
        } catch (e: IOException) {
            throw ConnectionException(
                MessageFormat.format(
                    "Was not able to connect to {0}:{1}. {2}",
                    deviceAddress.hostAddress()!!.hostName, port, e.message
                )
            )
        }
    }

    @Throws(IOException::class)
    private fun startListenIec60870(deviceSettings: DeviceSettings, port: Int, hostAddress: String) {
        clientConnection.startDataTransfer(iec60870listener)
        iec60870listener.addListener(readListener)
        logger.debug("Driver-IEC60870: successful sent startDT act to {}:{} and got startDT con.", hostAddress, port)
    }

    @Throws(IOException::class)
    private fun connect(clientConnectionBuilder: ClientConnectionBuilder, port: Int, hostAddress: String) {
        logger.debug("Try to connect to: {}:{}", hostAddress, port)
        clientConnection = clientConnectionBuilder.build()
        logger.info("Driver-IEC60870: successful connected to {}:{}", hostAddress, port)
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
    override fun read(
        containers: List<ChannelRecordContainer>,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        // TODO: read specific values, not only general interrogation
        readListener.setContainer(containers)
        readListener.setReadTimeout(deviceSettings.readTimeout().toLong())
        try {
            clientConnection.interrogation(1, CauseOfTransmission.ACTIVATION, IeQualifierOfInterrogation(20))
            readListener.read()
        } catch (e: IOException) {
            throw ConnectionException(e)
        } catch (e: UnsupportedOperationException) {
            logger.error(e.message)
            throw e
        }
        return null
    }

    @Synchronized
    @Throws(ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer>, listener: RecordsReceivedListener?) {
        val iec60870Listen = Iec60870Listener()
        iec60870Listen.registerOpenMucListener(containers, listener, driverId, this)
        iec60870listener.addListener(iec60870Listen)
    }

    @Throws(ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer>, containerListHandle: Any?): Any? {
        for (channelValueContainer in containers) {
            var channelAddress: ChannelAddress
            try {
                channelAddress = ChannelAddress(channelValueContainer.channelAddress)
                val record = Record(
                    channelValueContainer.value, System.currentTimeMillis(), Flag.VALID
                )
                Iec60870DataHandling.writeSingleCommand(record, channelAddress, clientConnection)
                channelValueContainer.flag = Flag.VALID
            } catch (e: ArgumentSyntaxException) {
                channelValueContainer.flag = Flag.DRIVER_ERROR_CHANNEL_ADDRESS_SYNTAX_INVALID
                logger.error(e.message)
                throw UnsupportedOperationException(e)
            } catch (e: IOException) {
                channelValueContainer.flag = Flag.CONNECTION_EXCEPTION
                throw ConnectionException(e)
            } catch (e: TypeConversionException) {
                channelValueContainer.flag = Flag.DRIVER_ERROR_CHANNEL_VALUE_TYPE_CONVERSION_EXCEPTION
                logger.error(e.message)
            } catch (e: UnsupportedOperationException) {
                channelValueContainer.flag = Flag.DRIVER_ERROR_CHANNEL_ADDRESS_SYNTAX_INVALID
                logger.error(e.message)
                throw e
            }
        }
        return null
    }

    override fun disconnect() {
        clientConnection?.close()
        iec60870listener.removeAllListener()
        logger.info("Disconnected IEC 60870 driver.")
    }

    private fun setupClientSap(clientSap: ClientConnectionBuilder, deviceSettings: DeviceSettings) {
        clientSap.setPort(deviceAddress.port())
        if (deviceSettings.commonAddressFieldLength() > 0) {
            clientSap.setCommonAddressFieldLength(deviceSettings.commonAddressFieldLength())
        } else if (deviceSettings.cotFieldLength() > 0) {
            clientSap.setCotFieldLength(deviceSettings.cotFieldLength())
        } else if (deviceSettings.ioaFieldLength() > 0) {
            clientSap.setIoaFieldLength(deviceSettings.ioaFieldLength())
        } else if (deviceSettings.maxIdleTime() > 0) {
            clientSap.setMaxIdleTime(deviceSettings.maxIdleTime())
        } else if (deviceSettings.maxTimeNoAckReceived() > 0) {
            clientSap.setMaxTimeNoAckReceived(deviceSettings.maxTimeNoAckReceived())
        } else if (deviceSettings.maxTimeNoAckSent() > 0) {
            clientSap.setMaxTimeNoAckSent(deviceSettings.maxTimeNoAckSent())
        } else if (deviceSettings.maxUnconfirmedIPdusReceived() > 0) {
            clientSap.setMaxUnconfirmedIPdusReceived(deviceSettings.maxUnconfirmedIPdusReceived())
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Iec60870Connection::class.java)
    }
}
