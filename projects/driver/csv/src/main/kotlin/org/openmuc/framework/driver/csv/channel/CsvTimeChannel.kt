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
package org.openmuc.framework.driver.csv.channel

import org.openmuc.framework.driver.csv.exceptions.CsvException
import org.openmuc.framework.driver.csv.exceptions.NoValueReceivedYetException
import org.openmuc.framework.driver.csv.exceptions.TimeTravelException
import org.slf4j.LoggerFactory

abstract class CsvTimeChannel(protected var data: List<String>, rewind: Boolean, timestamps: LongArray) : CsvChannel {
    /** remember index of last valid sampled value  */
    protected var lastReadIndex = 0
    protected var maxIndex: Int
    protected var rewind: Boolean
    protected var isInitialised = false
    var timestamps: LongArray
    var firstTimestamp: Long
    var lastTimestamp: Long

    init {
        maxIndex = data.size - 1
        this.rewind = rewind
        this.timestamps = timestamps
        firstTimestamp = timestamps[0]
        lastTimestamp = timestamps[timestamps.size - 1]
    }

    @Throws(CsvException::class)
    protected fun searchNextIndex(samplingTime: Long): Int {
        val index: Int
        index = if (isWithinTimeperiod(samplingTime)) {
            handleWithinTimeperiod(samplingTime)
        } else { // is outside time period
            handleOutsideTimeperiod(samplingTime)
        }
        if (!isInitialised) {
            isInitialised = true
        }
        return index
    }

    @Throws(CsvException::class)
    private fun handleWithinTimeperiod(samplingTime: Long): Int {
        return if (isBehindLastReadIndex(samplingTime)) {
            getIndexByRegularSearch(samplingTime)
        } else if (isBeforeLastReadIndex(samplingTime)) {
            handleBeforeLastReadIndex(samplingTime)
        } else { // is same timestamp
            lastReadIndex
        }
    }

    @Throws(CsvException::class)
    private fun handleBeforeLastReadIndex(samplingTime: Long): Int {
        return if (rewind) {
            rewindIndex()
            getIndexByRegularSearch(samplingTime)
        } else { // rewind disabled
            throw TimeTravelException(
                "Current sampling time is before the last sampling time. Since rewind is disabled, driver can't get value for current sampling time."
            )
        }
    }

    @Throws(CsvException::class)
    private fun handleOutsideTimeperiod(samplingTime: Long): Int {
        return if (isBeforeFirstTimestamp(samplingTime)) {
            handleOutsideTimeperiodEarly(samplingTime)
        } else { // is after last timestamp
            LOGGER.warn(
                "Current sampling time is behind last available timestamp of csv file. Returning value corresponding to last timestamp in file."
            )
            maxIndex
        }
    }

    /**
     * Search in chronological order beginning from last read index. This is the regular case since the samplingTime
     * will normally increase with each read called*
     */
    private fun getIndexByRegularSearch(samplingTime: Long): Int {
        var nextTimestamp: Long
        var nextIndex: Int
        do {
            nextIndex = lastReadIndex + 1
            if (nextIndex > maxIndex) {
                return maxIndex
            }
            nextTimestamp = timestamps[nextIndex]
            lastReadIndex = nextIndex
        } while (samplingTime > nextTimestamp)
        return if (samplingTime == nextTimestamp) {
            nextIndex
        } else {
            nextIndex - 1
        }
    }

    private fun isBeforeLastReadIndex(samplingTime: Long): Boolean {
        return if (samplingTime < timestamps[lastReadIndex]) {
            true
        } else {
            false
        }
    }

    private fun rewindIndex() {
        lastReadIndex = 0
    }

    private fun isBehindLastReadIndex(samplingTime: Long): Boolean {
        return if (samplingTime > timestamps[lastReadIndex]) {
            true
        } else {
            false
        }
    }

    @Throws(CsvException::class)
    private fun handleOutsideTimeperiodEarly(samplingTime: Long): Int {
        if (isInitialised) {
            throw TimeTravelException(
                "Illogical time jump for sampling time. Driver can't find corresponding value in csv file."
            )
        } else {
            throw NoValueReceivedYetException("Sampling time before first timestamp of csv file.")
        }
    }

    private fun isWithinTimeperiod(samplingTime: Long): Boolean {
        return if (samplingTime >= firstTimestamp && samplingTime <= lastTimestamp) {
            true
        } else {
            false
        }
    }

    private fun isBeforeFirstTimestamp(samplingTime: Long): Boolean {
        return if (samplingTime < firstTimestamp) {
            true
        } else {
            false
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(CsvTimeChannel::class.java)
    }
}
