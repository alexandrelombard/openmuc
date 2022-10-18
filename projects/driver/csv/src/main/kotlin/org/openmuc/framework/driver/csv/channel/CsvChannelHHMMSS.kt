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
import java.util.*

class CsvChannelHHMMSS(data: List<String>, rewind: Boolean, timestamps: LongArray) :
    CsvTimeChannel(data, rewind, timestamps) {
    @Throws(CsvException::class)
    override fun readValue(samplingTime: Long): String {
        val hhmmss = convertTimestamp(samplingTime)
        lastReadIndex = searchNextIndex(hhmmss.toLong())
        return data[lastReadIndex]
    }

    private fun convertTimestamp(samplingTime: Long): Int {
        val cal = GregorianCalendar(Locale.getDefault())
        cal.time = Date(samplingTime)
        val hour = cal[Calendar.HOUR_OF_DAY]
        val minute = cal[Calendar.MINUTE]
        val second = cal[Calendar.SECOND]

        // convert sampling time (unixtimestamp) to sampling time (hhmmss)
        // 14:25:34
        // 140000 + 2500 + 34 = 142534
        return hour * 10000 + minute * 100 + second
    }
}
