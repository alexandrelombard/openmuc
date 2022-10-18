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
package org.openmuc.framework.datalogger.ascii.utils

import org.openmuc.framework.data.Record.value
import org.openmuc.framework.datalogger.ascii.exceptions.WrongScalingException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

object IESDataFormatUtils {
    /**
     * Convert a double value into a string with the maximal allowed length of maxLength.
     *
     * @param value
     * the value to convert
     * @param maxLength
     * The maximal allowed length with all signs.
     * @param sbValue
     * StringBuffer for the return value
     *
     * @throws WrongScalingException
     * will thrown if converted value is bigger then maxLength
     */
    @JvmStatic
    @Throws(WrongScalingException::class)
    fun convertDoubleToStringWithMaxLength(sbValue: StringBuilder, value: Double, maxLength: Int) {
        val format: String
        var valueWork = value
        val lValue = (valueWork * 10000.0).toLong()
        valueWork = lValue / 10000.0
        if (lValue >= 0) {
            if (lValue shr 63 != 0L) {
                valueWork *= -1.0
            }
            format = '+'.toString() + getFormat(valueWork)
        } else {
            format = getFormat(valueWork)
        }
        val df = DecimalFormat(format, DecimalFormatSymbols(Locale.ENGLISH))
        val doubleString = df.format(valueWork)
        if (doubleString.length > maxLength) {
            throw WrongScalingException(
                "Double value (" + value + ") too large for conversion into max length "
                        + maxLength + "! Try to scale value."
            )
        }
        sbValue.append(doubleString)
    }

    private fun getFormat(value: Double): String {
        val lValue = value.toLong()
        val format: String
        format = if (lValue > 999999 || lValue < -999999) {
            "#######0"
        } else if (lValue > 99999 || lValue < -99999) {
            "#####0.0"
        } else if (lValue > 9999 || lValue < -9999) {
            "####0.00"
        } else {
            "###0.000"
        }
        return format
    }
}
