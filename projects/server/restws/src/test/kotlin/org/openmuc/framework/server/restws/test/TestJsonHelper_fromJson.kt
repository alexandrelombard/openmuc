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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.lib.rest1.Const
import org.openmuc.framework.lib.rest1.FromJson
import java.util.*

class TestJsonHelper_fromJson {
    @Test
    fun test_jsonToRecord() {
        var result = true
        val testMethodName = "Test_jsonToRecord"
        val elements: Set<ValueType> = EnumSet.allOf(
            ValueType::class.java
        )
        val it = elements.iterator()
        var record: Record
        var valueType: ValueType
        var i = 0
        while (it.hasNext()) {

            // build json record
            valueType = it.next()
            val jsonString = "{" + sTestRecord + sTestJsonValueArray[i] + Constants.JSON_OBJECT_END + '}'
            println("$testMethodName; ValueType: $valueType; JsonString: $jsonString")
            val json = FromJson(jsonString)
            record = json.getRecord(valueType)

            // test JsonHelper response
            if (record.timestamp != Constants.TIMESTAMP) {
                result = false
                println("$testMethodName: result is \"$result\"; error: Record timestamp is wrong.")
                break
            }
            if (record.flag.compareTo(Constants.TEST_FLAG) != 0) {
                result = false
                println(
                    testMethodName + ": result is \"" + result + "\"; error: Record flag is wrong. Should be "
                            + Constants.TEST_FLAG + " but is " + record.flag
                )
                break
            }
            result = TestTools.testValue(testMethodName, valueType, record.value)
            ++i
        }
        if (result) {
            println("$testMethodName: result is $result")
        }
        Assertions.assertTrue(result)
    }

    companion object {
        private const val stringValueWithTicks = "\"" + Constants.STRING_VALUE + "\""
        private var sTestJsonValueArray: Array<String>
        private var sTestRecord: String? = null
        @BeforeAll
        fun setup() {
            val testJsonDoubleValue = "\"value\":" + Constants.DOUBLE_VALUE
            val testJsonFloatValue = "\"value\":" + Constants.FLOAT_VALUE
            val testJsonLongValue = "\"value\":" + Constants.LONG_VALUE
            val testJsonIntegerValue = "\"value\":" + Constants.INTEGER_VALUE
            val testJsonShortValue = "\"value\":" + Constants.SHORT_VALUE
            val testJsonByteValue = "\"value\":" + Constants.BYTE_VALUE
            val testJsonBooleanValue = "\"value\":" + Constants.BOOLEAN_VALUE
            val testJsonByteArrayValue = "\"value\":" + Arrays.toString(Constants.BYTE_ARRAY_VALUE)
            val testJsonStringValue = "\"value\":" + stringValueWithTicks

            // ValueType enum: DOUBLE, FLOAT, LONG, INTEGER, SHORT, BYTE, BOOLEAN, BYTE_ARRAY, STRING
            val testJsonValueArray = arrayOf(
                testJsonDoubleValue, testJsonFloatValue, testJsonLongValue,
                testJsonIntegerValue, testJsonShortValue, testJsonByteValue, testJsonBooleanValue,
                testJsonByteArrayValue, testJsonStringValue
            )
            val testRecord = ("\"" + Const.RECORD + "\":{\"timestamp\":" + Constants.TIMESTAMP + ",\"flag\":\""
                    + Constants.TEST_FLAG.toString() + "\",")
            sTestRecord = testRecord
            sTestJsonValueArray = testJsonValueArray
        }
    }
}
