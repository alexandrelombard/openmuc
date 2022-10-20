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

class CsvChannelUnixtimestamp(data: List<String>, rewind: Boolean, timestamps: LongArray) :
    CsvTimeChannel(data, rewind, timestamps) {
    @Throws(CsvException::class)
    override fun readValue(sampleTime: Long): String {
        lastReadIndex = searchNextIndex(sampleTime)
        return data[lastReadIndex]
    }
}
