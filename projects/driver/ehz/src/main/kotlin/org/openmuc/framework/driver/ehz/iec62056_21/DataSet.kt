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

import org.openmuc.framework.data.DoubleValue

class DataSet(dataSetStr: String?) {
    val address: String
    var value: String? = null
    var unit: String? = null

    init {
        var dataSetStr = dataSetStr
        val bracket = dataSetStr!!.indexOf('(')
        address = dataSetStr.substring(0, bracket)
        dataSetStr = dataSetStr.substring(bracket)
        val separator = dataSetStr.indexOf('*')
        if (separator == -1) {
            value = dataSetStr.substring(1, dataSetStr.length - 2)
        } else {
            value = dataSetStr.substring(1, separator)
            unit = dataSetStr.substring(separator + 1, dataSetStr.length - 1)
        }
    }

    fun parseValueAsDouble(): DoubleValue {
        return try {
            DoubleValue(value!!.toDouble())
        } catch (e: NumberFormatException) {
            DoubleValue(Double.NaN)
        }
    }
}
