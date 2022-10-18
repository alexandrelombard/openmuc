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
package org.openmuc.framework.driver.spi

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.config.ScanException

/**
 *
 * A connection represents an association to particular device. A driver returns an implementation of this interface
 * when [DriverService.connect] is called.
 *
 * The OpenMUC framework can give certain guarantees about the order of the functions it calls:
 *
 *  * Communication related functions (e.g. connect,read,write..) are never called concurrently for the same device.
 *
 *  * The framework calls read,listen,write or channelScan only if a the device is considered connected. The device is
 * only considered connected if the connect function has been called successfully.
 *  * Before a driver service is unregistered or the data manager is stopped the framework calls disconnect for all
 * connected devices. The disconnect function should do any necessary resource clean up.
 *
 *
 */
interface Connection {
    /**
     * Scan a given communication device for available data channels.
     *
     * @param settings
     * scanning settings. The syntax is driver specific.
     * @return A list of channels that were found.
     * @throws ArgumentSyntaxException
     * if the syntax of the deviceAddress or settings string is incorrect.
     * @throws UnsupportedOperationException
     * if the method is not implemented by the driver.
     * @throws ScanException
     * if an error occurs while scanning but the connection is still alive.
     * @throws ConnectionException
     * if an error occurs while scanning and the connection was closed
     */
    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ConnectionException::class
    )
    fun scanForChannels(settings: String?): List<ChannelScanInfo?>?

    /**
     * Reads the data channels that correspond to the given record containers. The read result is returned by setting
     * the record in the containers. If for some reason no value can be read the record should be set anyways. In this
     * case the record constructor that takes only a flag should be used. The flag shall best describe the reason of
     * failure. If no record is set the default Flag is `Flag.DRIVER_ERROR_UNSPECIFIED`. If the connection to
     * the device is interrupted, then any necessary resources that correspond to this connection should be cleaned up
     * and a `ConnectionException` shall be thrown.
     *
     * @param containers
     * the containers hold the information of what channels are to be read. They will be filled by this
     * function with the records read.
     * @param containerListHandle
     * the containerListHandle returned by the last read call for this exact list of containers. Will be
     * equal to `null` if this is the first read call for this container list after a connection
     * has been established. Driver implementations can optionally use this object to improve the read
     * performance.
     * @param samplingGroup
     * the samplingGroup that was configured for this list of channels that are to be read. Sometimes it may
     * be desirable to give the driver a hint on how to group several channels when reading them. This can
     * done through the samplingGroup.
     * @return the containerListHandle Object that will passed the next time the same list of channels is to be read.
     * Use this Object as a handle to improve performance or simply return `null`.
     * @throws UnsupportedOperationException
     * if the method is not implemented by the driver.
     * @throws ConnectionException
     * if the connection to the device was interrupted.
     */
    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    fun read(containers: List<ChannelRecordContainer>, containerListHandle: Any?, samplingGroup: String?): Any?

    /**
     * Starts listening on the given connection for data from the channels that correspond to the given record
     * containers. The list of containers will overwrite the list passed by the previous startListening call. Will
     * notify the given listener of new records that arrive on the data channels.
     *
     * @param containers
     * the containers identify the channels to listen on. They will be filled by this function with the
     * records received and passed to the listener.
     * @param listener
     * the listener to inform that new data has arrived.
     * @throws UnsupportedOperationException
     * if the method is not implemented by the driver.
     * @throws ConnectionException
     * if the connection to the device was interrupted.
     */
    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    fun startListening(containers: List<ChannelRecordContainer>, listener: RecordsReceivedListener?)

    /**
     * Writes the data channels that correspond to the given value containers. The write result is returned by setting
     * the flag in the containers. If the connection to the device is interrupted, then any necessary resources that
     * correspond to this connection should be cleaned up and a `ConnectionException` shall be thrown.
     *
     * @param containers
     * the containers hold the information of what channels are to be written and the values that are to
     * written. They will be filled by this function with a flag stating whether the write process was
     * successful or not.
     * @param containerListHandle
     * the containerListHandle returned by the last write call for this exact list of containers. Will be
     * equal to `null` if this is the first read call for this container list after a connection
     * has been established. Driver implementations can optionally use this object to improve the write
     * performance.
     * @return the containerListHandle Object that will passed the next time the same list of channels is to be written.
     * Use this Object as a handle to improve performance or simply return `null`.
     * @throws UnsupportedOperationException
     * if the method is not implemented by the driver.
     * @throws ConnectionException
     * if the connection to the device was interrupted.
     */
    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    fun write(containers: List<ChannelValueContainer>, containerListHandle: Any?): Any?

    /**
     * Disconnects or closes the connection. Cleans up any resources associated with the connection.
     */
    fun disconnect()
}
