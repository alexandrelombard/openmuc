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
package org.openmuc.framework.datalogger.ascii.test

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.openmuc.framework.datalogger.ascii.exceptions.WrongScalingException
import org.openmuc.framework.datalogger.ascii.utils.Const
import org.openmuc.framework.datalogger.ascii.utils.IESDataFormatUtils.convertDoubleToStringWithMaxLength
import org.openmuc.framework.datalogger.ascii.utils.LoggerUtils.byteArrayToHexString
import java.util.*

class MiscTests {
    var sb = StringBuilder()
    @Test
    fun testDoubleFormattingOk() {
        println("### Begin test testDoubleFormattingOk")
        val testData = TreeMap<Double, String>()
        testData[-0.0] = "+0.000" // should be +
        testData[0.0] = "+0.000"
        testData[1.0] = "+1.000"
        testData[-1.0] = "-1.000"
        testData[10.0] = "+10.000"
        testData[-10.0] = "-10.000"
        testData[10.123] = "+10.123"
        testData[-10.123] = "-10.123"
        testData[9999.999] = "+9999.999"
        testData[-9999.999] = "-9999.999"

        // decimal digits = 3
        testData[1000.123] = "+1000.123"
        testData[-1000.123] = "-1000.123"

        // decimal digits = 2
        testData[10000.123] = "+10000.12"
        testData[-10000.123] = "-10000.12"

        // decimal digits = 1
        testData[100000.123] = "+100000.1"
        testData[-100000.123] = "-100000.1"

        // decimal digits = 0
        testData[1000000.123] = "+1000000"
        testData[-1000000.123] = "-1000000"

        // max number 8 digits
        testData[99999999.0] = "+99999999"
        testData[-99999999.0] = "-99999999"
        var expectedResult: String?
        var input: Double
        val i: Iterator<Double> = testData.keys.iterator()
        while (i.hasNext()) {
            input = i.next()
            expectedResult = testData[input]
            try {
                sb.setLength(0)
                convertDoubleToStringWithMaxLength(sb, input, Const.VALUE_SIZE_DOUBLE)
                println("$input --> $sb $expectedResult")
                Assertions.assertEquals(expectedResult, sb.toString())
            } catch (e: WrongScalingException) {
                e.printStackTrace()
            }
        }
    }

    @Test
    fun testWrongScalingException() {
        println("### Begin test testWrongScalingException")
        var input = 100000000.0
        try {
            sb.setLength(0)
            convertDoubleToStringWithMaxLength(sb, input, Const.VALUE_SIZE_DOUBLE)
            Assertions.assertTrue(false, "Expected WrongScalingException")
        } catch (e: WrongScalingException) {
            Assertions.assertTrue(true)
        }
        input = -100000000.0
        try {
            sb.setLength(0)
            convertDoubleToStringWithMaxLength(sb, input, Const.VALUE_SIZE_DOUBLE)
            Assertions.assertTrue(false, "Expected WrongScalingException")
        } catch (e: WrongScalingException) {
            Assertions.assertTrue(true)
        }
    }

    @Test
    fun testByteArrayConversion() {
        sb.setLength(0)
        sb.append(Const.HEXADECIMAL)
        byteArrayToHexString(sb, BYTE_ARRAY)
        Assertions.assertEquals(sb.toString(), STRING_BYTE_ARRAY)
    }

    companion object {
        val BYTE_ARRAY = byteArrayOf(
            0x00.toByte(),
            0x01.toByte(),
            0x0A.toByte(),
            0xAA.toByte(),
            0xBB.toByte(),
            0xF7.toByte(),
            0xFF.toByte(),
            0xCA.toByte(),
            0xD5.toByte(),
            0x5E
        )
        const val STRING_BYTE_ARRAY = "0x00010AAABBF7FFCAD55E"
        @JvmStatic
        @AfterAll
        fun tearDown() {
            println("tearing down")
            TestUtils.deleteTestFolder()
        }
    }
}
