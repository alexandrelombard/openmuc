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
package org.openmuc.framework.driver.aggregator

import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.TypeConversionException
import org.openmuc.framework.dataaccess.Channel
import org.openmuc.framework.dataaccess.DataAccessService
import org.openmuc.framework.dataaccess.DataLoggerNotAvailableException
import java.io.IOException
import kotlin.math.roundToInt

abstract class AggregatorChannel(protected var channelAddress: ChannelAddress, dataAccessService: DataAccessService) {
    protected var aggregatedChannel: Channel
    protected var sourceChannel: Channel
    protected var sourceLoggingInterval: Long
    protected var aggregationInterval: Long
    protected var aggregationSamplingTimeOffset: Long

    // TODO dataAccessService wird ueber viele ebenen durchgereicht, wie kann ich das vermeiden?
    // Brauche den dataAccessService eigentlich nur hier
    init {
        aggregatedChannel = channelAddress.container.channel!!
        dataAccessService.getChannel(channelAddress.sourceChannelId).let {
            if (it == null) {
                throw AggregationException("sourceChannel is null")
            }
            sourceChannel = it
        }

        // NOTE: logging, not sampling interval because aggregator accesses logged values
        sourceLoggingInterval = sourceChannel.loggingInterval.toLong()
        aggregationInterval = aggregatedChannel.samplingInterval.toLong()
        aggregationSamplingTimeOffset = aggregatedChannel.samplingTimeOffset.toLong()
        checkIntervals()
    }

    /**
     * Performs aggregation.
     *
     * @param currentTimestamp
     * start TS.
     * @param endTimestamp
     * stop TS.
     * @return the aggregated value.
     * @throws AggregationException
     * if an error occurs.
     */
    @Throws(AggregationException::class)
    abstract fun aggregate(currentTimestamp: Long, endTimestamp: Long): Double
    @Throws(DataLoggerNotAvailableException::class, IOException::class, AggregationException::class)
    fun getLoggedRecords(currentTimestamp: Long, endTimestamp: Long): List<Record> {
        val startTimestamp = currentTimestamp - aggregationInterval
        val records = sourceChannel.getLoggedRecords(startTimestamp, endTimestamp).toMutableList()

        // for debugging - KEEP IT!
        // if (records.size() > 0) {
        // SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        // for (Record r : records) {
        // logger.debug("List records: " + sdf.format(r.getTimestamp()) + " " + r.getValue().asDouble());
        // }
        // logger.debug("start: " + sdf.format(startTimestamp) + " timestamp = " + startTimestamp);
        // logger.debug("end: " + sdf.format(endTimestamp) + " timestamp = " + endTimestamp);
        // logger.debug("List start: " + sdf.format(records.get(0).getTimestamp()));
        // logger.debug("List end: " + sdf.format(records.get(records.size() - 1).getTimestamp()));
        // }
        checkNumberOfRecords(records)
        return records
    }

    /**
     * Checks limitations of the sampling/aggregating intervals and sourceSamplingOffset
     *
     * @param sourceLoggingInterval
     * @param aggregationInterval
     * @param sourceSamplingOffset
     * @throws AggregationException
     */
    @Throws(AggregationException::class)
    private fun checkIntervals() {

        // check 1
        // -------
        if (aggregationInterval < sourceLoggingInterval) {
            throw AggregationException(
                "Sampling interval of aggregator channel must be bigger than logging interval of source channel"
            )
        }

        // check 2
        // -------
        val remainder = aggregationInterval % sourceLoggingInterval
        if (remainder != 0L) {
            throw AggregationException(
                "Sampling interval of aggregator channel must be a multiple of the logging interval of the source channel"
            )
        }

        // check 3
        // -------
        if (sourceLoggingInterval < 1000) {
            // FIXME (priority low) milliseconds are cut from the endTimestamp (refer to read method). If the logging
            // interval of the source channel is smaller than 1 second this might lead to errors.
            throw AggregationException("Logging interval of source channel musst be >= 1 second")
        }
    }

    @Throws(AggregationException::class)
    private fun checkNumberOfRecords(records: MutableList<Record>) {
        // The check if intervals are multiples of each other is done in the checkIntervals Method
        removeErrorRecords(records)
        val expectedNumberOfRecords = (aggregationInterval.toDouble() / sourceLoggingInterval).roundToInt()
        val necessaryRecords = (expectedNumberOfRecords * channelAddress.quality).roundToInt()
        val validRecords = records.size
        if (validRecords < necessaryRecords) {
            throw AggregationException(
                "Insufficent number of logged records for channel "
                        + channelAddress.container.channel?.id + ". Valid logged records: " + validRecords
                        + " Expected: " + necessaryRecords + " (at least)" + " quality:" + channelAddress.quality
                        + " aggregationInterval:" + aggregationInterval + "ms sourceLoggingInterval:"
                        + sourceLoggingInterval + "ms"
            )
        }
    }

    /**
     * Removes invalid records from the list. All records remaining a valid DOUBLE records.
     *
     * NOTE: directly manipulates the records object for all future operations!
     */
    private fun removeErrorRecords(records: MutableList<Record>) {
        val recordIterator = records.iterator()
        while (recordIterator.hasNext()) {
            val record = recordIterator.next()
            // check if the value is null or the flag isn't valid
            if (record.value == null || record.flag != Flag.VALID) {
                recordIterator.remove()
                continue
            }
            try {
                // check if the value can be converted to double
                record.value!!.asDouble()
            } catch (e: TypeConversionException) {
                // remove record since it can't be cast to double for further processing
                recordIterator.remove()
            }
        }
    }
}
