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

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openmuc.framework.data.DoubleValue
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.datalogger.ascii.AsciiLogger.Companion.fillUpFileWithErrorCode
import org.openmuc.framework.datalogger.ascii.AsciiLogger.Companion.setLastLoggedLineTimeStamp
import org.openmuc.framework.datalogger.ascii.LogFileWriter
import org.openmuc.framework.datalogger.ascii.LogIntervalContainerGroup
import org.openmuc.framework.datalogger.ascii.utils.LoggerUtils.findLatestValue
import org.openmuc.framework.datalogger.ascii.utils.LoggerUtils.getDateOfFile
import org.openmuc.framework.datalogger.ascii.utils.LoggerUtils.getFilename
import org.openmuc.framework.datalogger.ascii.utils.LoggerUtils.getFilenames
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class LoggerUtilsTest {
    var lfw = LogFileWriter(TestUtils.TESTFOLDERPATH, true)
    @Test
    fun tc_501_test_getFilenames() {
        val expecteds: MutableList<String> = ArrayList()
        expecteds.add("20151005_1000_500.dat")
        expecteds.add("20151006_1000_500.dat")
        expecteds.add("20151007_1000_500.dat")
        expecteds.add("20151008_1000_500.dat")
        val actual = getFilenames(1000, 500, 1444031465000L, 1444290665000L)
        var i = 0
        for (expected in expecteds) {
            Assertions.assertEquals(actual[i++], expected)
        }
    }

    @Test
    fun tc_502_test_getFilename() {
        val expected = "20151005_1000_500.dat"
        val actual = getFilename(1000, 500, 1444031465000L)
        Assertions.assertEquals(actual, expected)
    }

    // ####################################################################################################################
    // ####################################################################################################################
    // ####################################################################################################################
    @Test
    fun tc_503_test_fillUpFileWithErrorCode() {
        val lastTimestamp = fillUpFileWithErrorCode(
            TestUtils.TESTFOLDERPATH,
            Integer.toString(loggingInterval), calendar
        )
        setLastLoggedLineTimeStamp(loggingInterval, loggingTimeOffset, lastTimestamp)
        val group = getGroup(calendar.timeInMillis, 3)
        calendar = GregorianCalendar()
        val sub = (calendar.timeInMillis % 10L).toInt()
        calendar.add(Calendar.MILLISECOND, -sub + 10)
        lfw.log(group, loggingInterval, loggingTimeOffset, calendar, logChannelList)
        setLastLoggedLineTimeStamp(loggingInterval, loggingTimeOffset, calendar.timeInMillis)
        val ch1 = LogChannelTestImpl(
            ch01, "", "dummy description", dummy, ValueType.DOUBLE, 0.0,
            0.0, false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
        )
    }

    @Test
    fun tc_504_test_getDateOfFile() {
        try {
            val pattern = "yyyyMMdd"
            val expectedDate = SimpleDateFormat(pattern).parse("20151005")
            val fileName = "20151005_1000_500.dat"
            val actualDate = getDateOfFile(fileName)
            val expected = expectedDate.time
            val actual = actualDate.time
            Assertions.assertEquals(expected, actual)
        } catch (ex: ParseException) {
            Assertions.fail<Any>(
                """${ex.message} 
${ex.stackTrace}"""
            )
        }
    }

    @Test
    fun tc_505_test_findLatestValue() {
        val recordsMap: MutableMap<String, MutableList<Record>> = HashMap()
        for (j in 0..4) {
            val records: MutableList<Record> = LinkedList()
            for (i in 0..19) {
                val timestamp = i.toLong()
                val value = DoubleValue((i + j).toDouble())
                val record = Record(value, timestamp)
                records.add(record)
            }
            recordsMap["channel$j"] = records
        }
        val latestValue = findLatestValue(recordsMap)
        for (j in 0..4) {
            val actual = latestValue["channel$j"]!!.value!!.asDouble()
            val expected = 19.0 + j
            Assertions.assertEquals(expected, actual)
        }
    }

    companion object {
        private const val loggingInterval = 1 // ms;
        private const val loggingTimeOffset = 0 // ms;
        private const val ch01 = "Double"
        private const val dummy = "dummy"
        private val logChannelList = HashMap<String, LogChannel>()
        private var calendar: Calendar = GregorianCalendar()
        @JvmStatic
        @BeforeAll
        fun setup() {
            val sub = (calendar.timeInMillis % 10L).toInt()
            calendar.add(Calendar.MILLISECOND, -loggingInterval * 5 - sub)
            TestUtils.createTestFolder()
            TestUtils.deleteExistingFile(loggingInterval, loggingTimeOffset, calendar)
            val ch1 = LogChannelTestImpl(
                ch01, "", "dummy description", dummy, ValueType.DOUBLE, 0.0,
                0.0, false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
            )
            logChannelList[ch01] = ch1
            val timeStamp = calendar.timeInMillis
            for (i in 0..4) {
                val lfw = LogFileWriter(TestUtils.TESTFOLDERPATH, false)
                val group = getGroup(timeStamp, i)
                lfw.log(group, loggingInterval, loggingTimeOffset, calendar, logChannelList)
                setLastLoggedLineTimeStamp(loggingInterval, loggingTimeOffset, calendar.timeInMillis)
                calendar.add(Calendar.MILLISECOND, loggingInterval)
            }
        }

        private fun getGroup(timeStamp: Long, i: Int): LogIntervalContainerGroup {
            val group = LogIntervalContainerGroup()
            val container1 = LoggingRecord(ch01, Record(DoubleValue(i * 7 - 0.555), timeStamp))
            group.add(container1)
            return group
        }
    }
}
