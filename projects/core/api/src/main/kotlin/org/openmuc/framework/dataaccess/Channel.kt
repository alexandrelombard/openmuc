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
package org.openmuc.framework.dataaccess

import org.openmuc.framework.data.*
import java.io.IOException

/**
 * The `Channel` class is used to access a single data field of a communication device. A desired channel can
 * be obtained using the `DataAccessService`. A channel instance can be used to
 *
 *  * Access the latest record. That is the latest data record that the framework either sampled or received by
 * listening or an application set using `setLatestRecord`.
 *  * Directly read/write data from/to the corresponding communication device.
 *  * Access historical data that was stored by a data logger such as SlotsDB.
 *  * Get configuration information about this channel such as its unit.
 *
 *
 *
 * Note that only the call of the read or write functions will actually result in a corresponding read or write request
 * being sent to the communication device.
 */
interface Channel {
    /**
     * Returns the ID of this channel. The ID is usually a meaningful string. It is used to get Channel objects using
     * the `DataAccessService`.
     *
     * @return the ID of this channel.
     */
    val id: String

    /**
     * Returns the address of this channel. Returns the empty string if not configured.
     *
     * @return the address of this channel.
     */
    val channelAddress: String?

    /**
     * Returns the description of this channel. Returns the empty string if not configured.
     *
     * @return the description of this channel.
     */
    val description: String?

    /**
     * Returns the settings of this channel. Returns the empty string if not configured.
     *
     * @return the settings of this channel.
     */
    val settings: String?
    val loggingSettings: String?

    /**
     * Returns the unit of this channel. Returns the empty string if not configured. The unit is used for informational
     * purposes only. Neither the framework nor any driver does value conversions based on the configured unit.
     *
     * @return the unit of this channel.
     */
    val unit: String?

    /**
     * Returns the value type of this channel. The value type specifies how the value of the latest record of a channel
     * is stored. A data logger is encouraged to store values using the configured value type if it supports that value
     * type.
     *
     *
     * Usually an application does not need to know the value type of the channel because it can use the value type of
     * its choice by using the corresponding function of [Value] (e.g. [Value.asDouble]). Necessary
     * conversions will be done transparently.
     *
     *
     * If no value type was configured, the default [ValueType.DOUBLE] is used.
     *
     * @return the value type of this channel.
     */
    val valueType: ValueType

    /**
     * Returns the scaling factor. Returns 1.0 if the scaling factor is not configured.
     *
     *
     * The scaling factor is applied in the following cases:
     *
     *  * Values received by this channel's driver or from apps through [.setLatestRecord] are multiplied
     * with the scaling factor before they are stored in the latest record.
     *  * Values written (e.g. using [.write]) are divided by the scaling factor before they are handed to
     * the driver for transmission.
     *
     *
     * @return the scaling factor
     */
    val scalingFactor: Double

    /**
     * Returns the channel's configured sampling interval in milliseconds. Returns -1 if not configured.
     *
     * @return the channel's configured sampling interval in milliseconds.
     */
    val samplingInterval: Int

    /**
     * Returns the channel's configured sampling time offset in milliseconds. Returns the default of 0 if not
     * configured.
     *
     * @return the channel's configured sampling time offset in milliseconds.
     */
    val samplingTimeOffset: Int

    /**
     * Returns the parent's device's configured sampling timeout in milliseconds. Returns the default of 0 if not
     * configured.
     *
     * @return the parent's device's configured sampling timeout in milliseconds.
     */
    val samplingTimeout: Int

    /**
     * Returns the channel's configured logging interval in milliseconds. Returns -1 if not configured.
     *
     * @return the channel's configured logging interval in milliseconds.
     */
    val loggingInterval: Int

    /**
     * Returns the channel's configured logging time offset in milliseconds. Returns the default of 0 if not configured.
     *
     * @return the channel's configured logging time offset in milliseconds.
     */
    val loggingTimeOffset: Int

    /**
     * Returns the unique name of the communication driver that is used by this channel to read/write data.
     *
     * @return the unique name of the communication driver that is used by this channel to read/write data.
     */
    val driverName: String?

    /**
     * Returns the channel's device address.
     *
     * @return the channel's device address.
     */
    val deviceAddress: String?

    /**
     * Returns the name of the communication device that this channel belongs to. The empty string if not configured.
     *
     * @return the name of the communication device that this channel belongs to.
     */
    val deviceName: String?

    /**
     * Returns the description of the communication device that this channel belongs to. The empty string if not
     * configured.
     *
     * @return the description of the communication device that this channel belongs to.
     */
    val deviceDescription: String?

    /**
     * Returns the current channel state.
     *
     * @return the current channel state.
     */
    val channelState: ChannelState?

    /**
     * Returns the current state of the communication device that this channel belongs to.
     *
     * @return the current state of the communication device that this channel belongs to.
     */
    val deviceState: DeviceState?

    /**
     * Adds a listener that is notified of new records received by sampling or listening.
     *
     * @param listener
     * the record listener that is notified of new records.
     */
    fun addListener(listener: RecordListener)

    /**
     * Removes a record listener.
     *
     * @param listener
     * the listener shall be removed.
     */
    fun removeListener(listener: RecordListener)

    /**
     * Returns `true` if a connection to the channel's communication device exist.
     *
     * @return `true` if a connection to the channel's communication device exist.
     */
    val isConnected: Boolean
    /**
     * Returns the latest record of this channel. Every channel holds its latest record in memory. There exist three
     * possible source for the latest record:
     *
     *  * It may be provided by a communication driver that was configured to sample or listen on the channel. In this
     * case the timestamp of the record represents the moment in time that the value was received by the driver.
     *  * An application may also set the latest record using `setLatestRecord`.
     *  * Finally values written using `write` are also stored as the latest record
     *
     *
     *
     * Note that the latest record is never `NULL`. When a channel is first created its latest record is
     * automatically initialized with a flag that indicates that its value is not valid.
     *
     * @return the latest record.
     */
    /**
     * Sets the latest record of this channel. This function should only be used with channels that are neither sampling
     * nor listening. Using this function it is possible to realize "virtual" channels that get their data not from
     * drivers but from applications in the framework.
     *
     *
     * Note that the framework treats the passed record in exactly the same way as if it had been received from a
     * driver. In particular that means:
     *
     *  * If data logging is enabled for this channel the latest record is being logged by the registered loggers.
     *  * Other applications can access the value set by this function using `getLatestRecord`.
     *  * Applications are notified of the new record if they registered as listeners using `addListener`.
     *  * If a scaling factor has been configured for this channel then the value passed to this function is scaled.
     *
     *
     *
     * @param record
     * the record to be set.
     */
    var latestRecord: Record?

    /**
     * Writes the given value to the channel's corresponding data field in the connected communication device. If an
     * error occurs, the returned `Flag` will indicate this.
     *
     * @param value
     * the value that is to be written
     * @return the flag indicating whether the value was successfully written ( `Flag.VALID`) or not (any
     * other flag).
     */
    fun write(value: Value): Flag

    /**
     * Schedules a List&lt;records&gt; with future timestamps as write tasks <br></br>
     * This function will schedule single write tasks to the provided timestamps.<br></br>
     * Once this function is called, previously scheduled write tasks will be erased.<br></br>
     *
     * @param values
     * a list of future write values.
     */
    fun writeFuture(values: MutableList<FutureValue>)

    /**
     * Returns a `WriteValueContainer` that corresponds to this channel. This container can be passed to the
     * write function of `DataAccessService` to write several values in one transaction.
     *
     * @return a `WriteValueContainer` that corresponds to this channel.
     */
    val writeContainer: WriteValueContainer?

    /**
     * Actively reads a value from the channel's corresponding data field in the connected communication device. If an
     * error occurs it will be indicated in the returned record's flag.
     *
     * @return the record containing the value read, the time the value was received and a flag indicating success (
     * `Flag.VALID`) or a an error (any other flag).
     */
    fun read(): Record?

    /**
     * Returns a `ReadRecordContainer` that corresponds to this channel. This container can be passed to the
     * `read` function of `DataAccessService` to read several values in one transaction.
     *
     * @return a `ReadRecordContainer` that corresponds to this channel.
     */
    val readContainer: ReadRecordContainer?

    /**
     * Returns the logged data record whose timestamp equals the given `time`. Note that it is the data
     * logger's choice whether it stores values using the timestamp that the driver recorded when it received it or the
     * timestamp at which the value is to be logged. If the former is the case then this function is not useful because
     * it is impossible for an application to know the exact time at which a value was received. In this case use
     * `getLoggedRecords` instead.
     *
     * @param time
     * the time in milliseconds since midnight, January 1, 1970 UTC.
     * @return the record that has been stored by the framework's data logger at the given `timestamp`.
     * Returns `null` if no record exists for this point in time.
     * @throws DataLoggerNotAvailableException
     * if no data logger is installed and therefore no logged data can be accessed.
     * @throws IOException
     * if any kind of error occurs accessing the logged data.
     */
    @Throws(DataLoggerNotAvailableException::class, IOException::class)
    fun getLoggedRecord(time: Long): Record?

    /**
     * Returns a list of all logged data records with timestamps from `startTime` up until now.
     *
     * @param startTime
     * the starting time in milliseconds since midnight, January 1, 1970 UTC. inclusive
     * @return a list of all logged data records with timestamps from `startTime` up until now.
     * @throws DataLoggerNotAvailableException
     * if no data logger is installed and therefore no logged data can be accessed.
     * @throws IOException
     * if any kind of error occurs accessing the logged data.
     */
    @Throws(DataLoggerNotAvailableException::class, IOException::class)
    fun getLoggedRecords(startTime: Long): List<Record>

    /**
     * Returns a list of all logged data records with timestamps from `startTime` to `endTime`
     * inclusive.
     *
     * @param startTime
     * the starting time in milliseconds since midnight, January 1, 1970 UTC. inclusive
     * @param endTime
     * the ending time in milliseconds since midnight, January 1, 1970 UTC. inclusive
     * @return a list of all logged data records with timestamps from `startTime` to `endTime`
     * inclusive.
     * @throws DataLoggerNotAvailableException
     * if no data logger is installed and therefore no logged data can be accessed.
     * @throws IOException
     * if any kind of error occurs accessing the logged data.
     */
    @Throws(DataLoggerNotAvailableException::class, IOException::class)
    fun getLoggedRecords(startTime: Long, endTime: Long): List<Record>
}
