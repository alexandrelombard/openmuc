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
package org.openmuc.framework.core.datamanager

import java.util.*

class RandomString(length: Int) {
    private val random = Random()
    private val buf: CharArray

    init {
        require(length >= 1) { "length < 1: $length" }
        buf = CharArray(length)
    }

    fun nextString(): String {
        for (idx in buf.indices) {
            buf[idx] = symbols[random.nextInt(symbols.size)]
        }
        return String(buf)
    }

    companion object {
        private val symbols = CharArray(36)

        init {
            for (idx in 0..9) {
                symbols[idx] = ('0'.code + idx).toChar()
            }
            for (idx in 10..35) {
                symbols[idx] = ('a'.code + idx - 10).toChar()
            }
        }
    }
}
