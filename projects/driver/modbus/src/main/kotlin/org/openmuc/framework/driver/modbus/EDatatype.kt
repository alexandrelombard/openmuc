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
package org.openmuc.framework.driver.modbus

import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import java.util.*

/**
 * Matching from Java Datatyp to Modbus Register
 *
 * One modbus register has the size of two Bytes
 */
enum class EDatatype( // not implemented yet
    /// ** 1 Register, 2 bytes, access high byte of a register */
    // BYTE_HIGH( 1),
    // not implemented yet
    /// ** 1 Register, 2 bytes, access low byte of a register */
    // BYTE_LOW( 1);
    val registerSize: Int
) {
    /** 1 Bit  */
    BOOLEAN(1),  // not implemented yet
    /// ** RHB register high byte */
    // INT8_RHB("int8_rhb", 1),
    // not implemented yet
    /// ** RLB register low byte */
    // INT8_RLB("int8_rlb", 1),

    @Deprecated("Use INT16 instead")
    SHORT(1),

    /** 1 Register, 2 bytes  */
    INT16(1),

    /** 2 Register, 4 bytes  */
    INT32(2),  // not implemented yet
    // /** 4 Register, 8 bytes */
    // INT64( 4),
    // not implemented yet
    /// ** to convert register high byte RHB to uint8 */
    // UINT8_RHB( 1),
    // not implemented yet
    /// ** to convert register low byte RLB to uint8 */
    // UINT8_RLB( 1),
    /** 1 Register, 2 bytes  */
    UINT16(1),

    /** 2 Register, 4 bytes  */
    UINT32(2),

    /** 2 Register, 4 bytes  */
    FLOAT(2),

    /** 4 Register, 8 bytes  */
    DOUBLE(4),

    /** 4 Register, 8 bytes  */
    LONG(4),

    /** n Registers, n*2 bytes. Note: 0 will be overwritten  */
    BYTEARRAY(0),

    /** n Registers, n*2 bytes. Note: max. registers 4; 0 will be overwritten  */
    BYTEARRAYLONG(0);

    companion object {
        fun getEnum(string: String?): EDatatype? {
            var string = string
            var returnValue: EDatatype? = null
            if (string != null) {
                string = string.uppercase(Locale.getDefault())
                for (type in values()) {
                    if (string == type.toString()) {
                        returnValue = type
                        break
                    } else if (string.uppercase(Locale.getDefault()).matches(BYTEARRAY.toString() + "\\[\\d+\\]")) {
                        // Special check for BYTEARRAY[n] datatyp
                        returnValue = BYTEARRAY
                        break
                    } else if (string.uppercase(Locale.getDefault()).matches(BYTEARRAYLONG.toString() + "\\[\\d+\\]")) {
                        // Special check for BYTEARRAYLONG[n] datatyp
                        returnValue = BYTEARRAYLONG
                        break
                    }
                }
            }
            if (returnValue == null) {
                throw RuntimeException(
                    string + " is not supported. Use one of the following supported datatypes: "
                            + supportedDatatypes
                )
            }
            return returnValue
        }

        /**
         * @return all supported datatypes
         */
        @JvmStatic
        val supportedDatatypes: String
            get() {
                var supported = ""
                for (type in values()) {
                    supported += "$type, "
                }
                return supported
            }

        /**
         * Checks if the datatype is valid
         *
         * @param string
         * Enum as String
         * @return true if valid, otherwise false
         */
        @JvmStatic
        fun isValid(string: String?): Boolean {
            var returnValue = false
            try {
                if (getEnum(string) != null) {
                    returnValue = true
                }
            } catch (e: RuntimeException) {
            }
            return returnValue
        }
    }
}
