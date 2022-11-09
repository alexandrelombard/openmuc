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
package org.openmuc.framework.server.restws.test

import org.junit.jupiter.api.Assertions
import org.openmuc.framework.data.TypeConversionException
import org.openmuc.framework.data.Value
import org.openmuc.framework.data.ValueType
import java.util.*

object TestTools {
    fun testValue(Test_method: String, valueType: ValueType?, value: Value?): Boolean {
        var result = true
        if (value == null) {
            result = false
            println("$Test_method: result is \"$result\"; error: Value is null.")
        }
        try {
            checkValueConversion(valueType, value)
        } catch (e: TypeConversionException) {
            result = false
            println(
                "$Test_method result is \"$result\"; error: ValueType is wrong;\n errormsg: $e"
            )
        }
        checkValueValue(Test_method, valueType, value)
        return result
    }

    @Throws(TypeConversionException::class)
    fun checkValueConversion(valueType: ValueType?, value: Value?) {
        when (valueType) {
            ValueType.BOOLEAN -> value!!.asBoolean()
            ValueType.BYTE -> value!!.asByte()
            ValueType.BYTE_ARRAY -> value!!.asByteArray()
            ValueType.DOUBLE -> value!!.asDouble()
            ValueType.FLOAT -> value!!.asFloat()
            ValueType.INTEGER -> value!!.asInt()
            ValueType.LONG -> value!!.asLong()
            ValueType.SHORT -> value!!.asShort()
            ValueType.STRING -> value!!.asString()
            else -> throw TypeConversionException("Unknown ValueType")
        }
    }

    fun checkValueValue(Test_method: String, valueType: ValueType?, value: Value?) {
        when (valueType) {
            ValueType.BOOLEAN -> Assertions.assertEquals(
                Constants.BOOLEAN_VALUE, value!!.asBoolean(),
                "$Test_method: Expected boolean is not equal the actual"
            )

            ValueType.BYTE -> Assertions.assertEquals(
                Constants.BYTE_VALUE,
                value!!.asByte(),
                "$Test_method: Expected byte is not equal the actual"
            )

            ValueType.BYTE_ARRAY -> if (!Arrays.equals(Constants.BYTE_ARRAY_VALUE, value!!.asByteArray())) {
                Assertions.assertTrue(false, "$Test_method: Expected byte[] is not equal the actual")
            }

            ValueType.DOUBLE -> Assertions.assertEquals(
                Constants.DOUBLE_VALUE, value!!.asDouble(), 0.00001,
                "$Test_method: Expected double is not equal the actual"
            )

            ValueType.FLOAT -> Assertions.assertEquals(
                Constants.FLOAT_VALUE.toDouble(), value!!.asFloat().toDouble(), 0.00001,
                "$Test_method: Expected double is not equal the actual"
            )

            ValueType.INTEGER -> Assertions.assertEquals(
                Constants.INTEGER_VALUE, value!!.asInt(),
                "$Test_method: Expected int is not equal the actual"
            )

            ValueType.LONG -> Assertions.assertEquals(
                Constants.LONG_VALUE.toLong(),
                value!!.asLong(),
                "$Test_method: Expected long is not equal the actual"
            )

            ValueType.SHORT -> Assertions.assertEquals(
                Constants.SHORT_VALUE, value!!.asShort(),
                "$Test_method: Expected short is not equal the actual"
            )

            ValueType.STRING -> Assertions.assertEquals(
                Constants.STRING_VALUE, value!!.asString(),
                "$Test_method: Expected String is not equal the actual"
            )

            else -> {}
        }
    }
}
