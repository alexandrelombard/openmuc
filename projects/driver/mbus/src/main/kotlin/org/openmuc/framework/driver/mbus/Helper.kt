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
package org.openmuc.framework.driver.mbus

object Helper {
    const val SA_DTY = "sa:dty"
    const val SA_MAN = "sa:man"
    const val SA_DID = "sa:did"
    const val SA_VER = "sa:ver"
    const val HEXES = "0123456789ABCDEF"
    fun bytesToHex(raw: ByteArray?): String? {
        if (raw == null) {
            return null
        }
        val hex = StringBuilder(2 * raw.size)
        for (b in raw) {
            hex.append(HEXES[b.toInt() and 0xF0 shr 4]).append(HEXES[b.toInt() and 0x0F])
        }
        return hex.toString()
    }

    fun hexToBytes(s: String): ByteArray {
        val b = ByteArray(s.length / 2)
        var index: Int
        for (i in b.indices) {
            index = i * 2
            b[i] = s.substring(index, index + 2).toInt(16).toByte()
        }
        return b
    }
}
