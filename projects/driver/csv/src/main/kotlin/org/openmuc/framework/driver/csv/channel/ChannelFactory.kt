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

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.driver.csv.ESamplingMode
import org.openmuc.framework.driver.csv.settings.DeviceSettings

object ChannelFactory {
    @Throws(ArgumentSyntaxException::class)
    fun createChannelMap(
        csvMap: Map<String?, List<String>>,
        settings: DeviceSettings
    ): HashMap<String?, CsvChannel?> {
        var channelMap = HashMap<String?, CsvChannel?>()
        when (settings.samplingMode()) {
            ESamplingMode.UNIXTIMESTAMP -> channelMap = createMapUnixtimestamp(csvMap)
            ESamplingMode.HHMMSS -> channelMap = createMapHHMMSS(csvMap, settings.rewind())
            ESamplingMode.LINE -> channelMap = createMapLine(csvMap, settings.rewind())
            else -> {}
        }
        return channelMap
    }

    @Throws(ArgumentSyntaxException::class)
    fun createMapUnixtimestamp(csvMap: Map<String?, List<String>>): HashMap<String?, CsvChannel?> {
        val channelMap = HashMap<String?, CsvChannel?>()
        var channelAddress: String
        val keys = csvMap.keys.iterator()
        val rewind = false
        while (keys.hasNext()) {
            channelAddress = keys.next()
            val data = csvMap[channelAddress]!!
            val timestamps = getTimestamps(csvMap)
            channelMap[channelAddress] = CsvChannelUnixtimestamp(data, rewind, timestamps)
        }
        return channelMap
    }

    @Throws(ArgumentSyntaxException::class)
    fun createMapHHMMSS(csvMap: Map<String?, List<String>>, rewind: Boolean): HashMap<String?, CsvChannel?> {
        val channelMap = HashMap<String?, CsvChannel?>()
        var channelAddress: String
        val keys = csvMap.keys.iterator()
        while (keys.hasNext()) {
            channelAddress = keys.next()
            val data = csvMap[channelAddress]!!
            val timestamps = getHours(csvMap)
            channelMap[channelAddress] = CsvChannelHHMMSS(data, rewind, timestamps)
        }
        return channelMap
    }

    fun createMapLine(csvMap: Map<String?, List<String>>, rewind: Boolean): HashMap<String?, CsvChannel?> {
        val channelMap = HashMap<String?, CsvChannel?>()
        var channelAddress: String
        val keys = csvMap.keys.iterator()
        while (keys.hasNext()) {
            channelAddress = keys.next()
            val data = csvMap[channelAddress]!!
            channelMap[channelAddress] = CsvChannelLine(channelAddress, data, rewind)
        }
        return channelMap
    }

    /**
     * Convert timestamps from List String to long[]
     *
     * @throws ArgumentSyntaxException
     */
    @Throws(ArgumentSyntaxException::class)
    private fun getTimestamps(csvMap: Map<String?, List<String>>): LongArray {
        val timestampsList = csvMap["unixtimestamp"]
        if (timestampsList == null || timestampsList.isEmpty()) {
            throw ArgumentSyntaxException("unixtimestamp column not availiable in file or empty")
        }
        val timestamps = LongArray(timestampsList.size)
        for (i in timestampsList.indices) {
            timestamps[i] = timestampsList[i].toLong()
        }
        return timestamps
    }

    @Throws(ArgumentSyntaxException::class)
    private fun getHours(csvMap: Map<String?, List<String>>): LongArray {
        val hoursList = csvMap["hhmmss"]
        if (hoursList == null || hoursList.isEmpty()) {
            throw ArgumentSyntaxException("hhmmss column not availiable in file or empty")
        }
        val hours = LongArray(hoursList.size)
        for (i in hoursList.indices) {
            hours[i] = hoursList[i].toLong()
        }
        return hours
    }
}
