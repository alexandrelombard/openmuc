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

import java.util.*

/**
 * Modbus defines four different address areas called primary tables.
 */
enum class EPrimaryTable {
    COILS, DISCRETE_INPUTS, INPUT_REGISTERS, HOLDING_REGISTERS;

    companion object {
        fun getEnumfromString(enumAsString: String?): EPrimaryTable {
            var returnValue: EPrimaryTable? = null
            if (enumAsString != null) {
                for (value in values()) {
                    if (enumAsString.uppercase(Locale.getDefault()) == value.toString()) {
                        returnValue = valueOf(enumAsString.uppercase(Locale.getDefault()))
                        break
                    }
                }
            }
            if (returnValue == null) {
                throw RuntimeException(
                    enumAsString
                            + " is not supported. Use one of the following supported primary tables: " + supportedValues
                )
            }
            return returnValue
        }

        /**
         * @return all supported values as a comma separated string
         */
        val supportedValues: String
            get() {
                var supported = ""
                for (value in values()) {
                    supported += "$value, "
                }
                return supported
            }

        fun isValidValue(enumAsString: String?): Boolean {
            var returnValue = false
            for (type in values()) {
                if (type.toString().lowercase(Locale.getDefault()) == enumAsString!!.lowercase(Locale.getDefault())) {
                    returnValue = true
                    break
                }
            }
            return returnValue
        }
    }
}
