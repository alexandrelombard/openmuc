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

/**
 * Channel to return value of next line in the file. Timestamps are ignored. It always starts with the first line, which
 * can be useful for simulation since every time the framework is started it starts with the same values.
 */
class CsvChannelLine(id: String?, private val data: List<String>, rewind: Boolean) : CsvChannel {
    private var lastReadIndex = -1
    private val maxIndex: Int
    private val rewind = false

    init {
        maxIndex = data.size - 1
        this.rewind = rewind
    }

    override fun readValue(sampleTime: Long): String {
        lastReadIndex++
        if (lastReadIndex > maxIndex) {
            lastReadIndex = if (rewind) {
                0
            } else {
                // once maximum is reached it always returns the maximum (value of last line in file)
                maxIndex
            }
        }
        return data[lastReadIndex]
    }
}
