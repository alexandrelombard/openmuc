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
package org.openmuc.framework.driver.ehz.iec62056_21

import java.text.ParseException

class ModeDMessage private constructor(val vendorId: String, val identifier: String, val dataSets: List<String>) {

    companion object {
        @Throws(ParseException::class)
        fun parse(frame: ByteArray?): ModeDMessage {
            var position = 0
            return try {
                println(java.lang.Byte.toString(frame!![0]))
                /* Check for start sign */if (frame[0] != '/'.code.toByte()) {
                    throw ParseException("Invalid character", 0)
                }

                /* Check for valid vendor ID (only upper case letters) */position = 1
                while (position < 4) {
                    if (!(frame[position] > 64 && frame[position] < 91)) {
                        throw ParseException("Invalid character", position)
                    }
                    position++
                }
                val vendorId = String(frame, 1, 3)

                /* Baud rate sign needs to be '0' .. '6' */if (frame[4] <= '0'.code.toByte() || frame[4] >= '6'.code.toByte()) {
                    throw ParseException("Invalid character", 4)
                }
                position = 5
                var i = 0
                /* Search for CRLF to extract identifier */
                while (!((frame[position + i].toInt() == 0x0d) && (frame[position + i + 1].toInt()) == 0x0a)) {
                    if (frame[position + i] == '!'.code.toByte()) {
                        throw ParseException("Invalid end character", position + i)
                    }
                    i++
                }
                val identifier = String(frame, 5, i - 1)
                position += i

                /* Skip next CRLF */position += 4

                /* Get data sets */
                val dataSets: MutableList<String> = ArrayList()
                while (frame[position] != '!'.code.toByte()) {
                    i = 0
                    while (frame[position + i].toInt() != 0x0d) {
                        i++
                    }
                    val dataSet = String(frame, position, i)
                    dataSets.add(dataSet)
                    position += i + 2
                }
                ModeDMessage(vendorId, identifier, dataSets)
            } catch (e: IndexOutOfBoundsException) {
                throw ParseException("Unexpected end of message", position)
            }
        }
    }
}
