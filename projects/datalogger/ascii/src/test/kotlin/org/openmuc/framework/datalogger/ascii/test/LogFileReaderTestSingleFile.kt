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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openmuc.framework.data.DoubleValue
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.datalogger.ascii.AsciiLogger.Companion.setLastLoggedLineTimeStamp
import org.openmuc.framework.datalogger.ascii.LogFileReader
import org.openmuc.framework.datalogger.ascii.LogFileWriter
import org.openmuc.framework.datalogger.ascii.LogIntervalContainerGroup
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import java.util.*

class LogFileReaderTestSingleFile {
    var channelTestImpl = LogChannelTestImpl(
        Channel0Name, "", "Comment", "W", ValueType.DOUBLE, 0.0,
        0.0, false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
    )

    @Test
    fun tc000_t1_t2_within_available_data() {
        println("### Begin test tc000_t1_t2_within_available_data")
        val t1 = TestUtils.stringToDate(dateFormat, fileDate0 + " 01:50:00").timeInMillis
        val t2 = TestUtils.stringToDate(dateFormat, fileDate0 + " 01:51:00").timeInMillis
        val fr = LogFileReader(TestUtils.TESTFOLDERPATH, channelTestImpl)
        val records: List<Record?> = fr.getValues(t1, t2)[channelTestImpl.id]!!
        val expectedRecords: Long = 7
        val result = records.size.toLong() == expectedRecords
        print(Thread.currentThread().stackTrace[1].methodName)
        println(" records = " + records.size + " (" + expectedRecords + " expected)")
        Assertions.assertTrue(result)
    }

    @Test
    fun tc001_t1_before_available_data_t2_within() {
        println("### Begin test tc001_t1_before_available_data_t2_within")
        val t1 = TestUtils.stringToDate(dateFormat, fileDate0 + " 00:00:00").timeInMillis
        val t2 = TestUtils.stringToDate(dateFormat, fileDate0 + " 00:00:10").timeInMillis
        val fr = LogFileReader(TestUtils.TESTFOLDERPATH, channelTestImpl)
        val records: List<Record?> = fr.getValues(t1, t2)[channelTestImpl.id]!!
        val expectedRecords: Long = 0
        val result = records.size.toLong() == expectedRecords
        print(Thread.currentThread().stackTrace[1].methodName)
        println(" records = " + records.size + " (" + expectedRecords + " expected)")
        Assertions.assertTrue(result)
    }

    @Test
    fun tc002_t2_after_available_data() {
        println("### Begin test tc002_t2_after_available_data")
        val t1 = TestUtils.stringToDate(dateFormat, fileDate0 + " 01:00:00").timeInMillis
        val t2 = TestUtils.stringToDate(dateFormat, fileDate0 + " 02:00:00").timeInMillis
        val fr = LogFileReader(TestUtils.TESTFOLDERPATH, channelTestImpl)
        val records: List<Record?> = fr.getValues(t1, t2)[channelTestImpl.id]!!
        val expectedRecords: Long = 361 //
        val result = records.size.toLong() == expectedRecords
        print(Thread.currentThread().stackTrace[1].methodName)
        println(" records = " + records.size + " (" + expectedRecords + " expected)")
        Assertions.assertTrue(result)
    }

    @Test
    fun tc003_t1_t2_before_available_data() {
        println("### Begin test tc003_t1_t2_before_available_data")
        val t1 = TestUtils.stringToDate(dateFormat, fileDate0 + " 00:00:00").timeInMillis
        val t2 = TestUtils.stringToDate(dateFormat, fileDate0 + " 00:59:59").timeInMillis
        val fr = LogFileReader(TestUtils.TESTFOLDERPATH, channelTestImpl)
        val records: List<Record> = fr.getValues(t1, t2)[channelTestImpl.id] ?: listOf()
        val expectedRecords: Long = 0
        print(Thread.currentThread().stackTrace[1].methodName)
        var result = true
        var wrong = 0
        var ok = 0
        for (record in records) {
            if (record.flag == Flag.NO_VALUE_RECEIVED_YET) {
                ++ok
            } else {
                ++wrong
                result = false
            }
        }
        print(" records = " + records.size + " (" + expectedRecords + " expected); ")
        println("wrong = $wrong, ok(with Flag 7) = $ok")
        Assertions.assertTrue(result)
    }

    @Test
    fun tc004_t1_t2_after_available_data() {
        println("### Begin test tc004_t1_t2_after_available_data")

        // test 5 - startTimestampRequest & endTimestampRequest after available logged data
        val t1 = TestUtils.stringToDate(dateFormat, fileDate0 + " 03:00:01").timeInMillis
        val t2 = TestUtils.stringToDate(dateFormat, fileDate0 + " 03:59:59").timeInMillis
        val fr = LogFileReader(TestUtils.TESTFOLDERPATH, channelTestImpl)
        val records: List<Record?> = fr.getValues(t1, t2)[channelTestImpl.id]!!
        val expectedRecords: Long = 0
        val result = records.size.toLong() == expectedRecords
        print(Thread.currentThread().stackTrace[1].methodName)
        println(" records = " + records.size + " (" + expectedRecords + " expected)")
        Assertions.assertTrue(result)
    }

    @Test
    fun tc005_t1_within_available_data() {
        println("### Begin test tc005_t1_within_available_data")
        val t1 = TestUtils.stringToDate(dateFormat, fileDate0 + " 01:11:10").timeInMillis
        val result: Boolean
        val fr = LogFileReader(TestUtils.TESTFOLDERPATH, channelTestImpl)
        val record = fr.getValue(t1)[channelTestImpl.id]
        result = record != null
        print(Thread.currentThread().stackTrace[1].methodName)
        println(" record = " + result + "record = ")
        Assertions.assertTrue(result)
    }

    @Test
    fun tc006_t1_before_available_data() {
        println("### Begin test tc006_t1_before_available_data")
        val t1 = TestUtils.stringToDate(dateFormat, fileDate0 + " 00:59:00").timeInMillis
        val result: Boolean
        val fr = LogFileReader(TestUtils.TESTFOLDERPATH, channelTestImpl)
        val record = fr.getValue(t1)[channelTestImpl.id]
        println("record: $record")
        result = record == null
        print(Thread.currentThread().stackTrace[1].methodName)
        println(" no records = $result")
        Assertions.assertTrue(result)
    }

    // @Test
    fun tc007_t1_within_available_data_with_loggingInterval() {
        println("### Begin test tc007_t1_within_available_data_with_loggingInterval")
        val result: Boolean
        val t1 = TestUtils.stringToDate(dateFormat, fileDate0 + " 02:59:59").timeInMillis
        // get value looks from 02:59:59 to 3:00:00. before 3:00:00 a value exists
        val fr = LogFileReader(TestUtils.TESTFOLDERPATH, channelTestImpl)
        val record = fr.getValue(t1)[channelTestImpl.id]
        result = if (record != null) {
            true
        } else {
            false
        }
        print(Thread.currentThread().stackTrace[1].methodName)
        println(" record = $result")
        Assertions.assertTrue(result)
    }

    companion object {
        // t1 = start timestamp of requested interval
        // t2 = end timestamp of requested interval
        var fileDate0 = "20660606"
        var loggingInterval = 10000 // ms
        var loggingTimeOffset = 0 // ms
        var ext = ".dat"
        var startTimestampFile: Long = 0
        var endTimestampFile: Long = 0
        var Channel0Name = "power"
        var channelIds = arrayOf(Channel0Name)
        var dateFormat = "yyyyMMdd HH:mm:ss"
        @JvmStatic
        @BeforeAll
        fun setup() {
            println("### Setup() LogFileReaderTestSingleFile")
            TestUtils.createTestFolder()

            // File file = new File(TestUtils.TESTFOLDERPATH + fileDate0 + "_" + loggingInterval + ext);

            // if (file.exists()) {
            // Do nothing, file exists.
            // }
            // else {
            // eine Datei
            channelIds = arrayOf("power")

            // Logs 1 channel in second interval from 1 to 3 o'clock
            val logChannelList = HashMap<String, LogChannel>()
            val ch1 = LogChannelTestImpl(
                Channel0Name, "", "dummy description", "kW", ValueType.DOUBLE,
                0.0, 0.0, false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
            )
            logChannelList[Channel0Name] = ch1
            val calendar = TestUtils.stringToDate(dateFormat, fileDate0 + " 01:00:00")
            var i = 0
            while (i < 60 * 60 * 2 * (1000.0 / loggingInterval)) {
                val container1 = LoggingRecord(
                    Channel0Name,
                    Record(DoubleValue(i.toDouble()), calendar!!.timeInMillis)
                )
                val group = LogIntervalContainerGroup()
                group.add(container1)
                val lfw = LogFileWriter(TestUtils.TESTFOLDERPATH, false)
                lfw.log(group, loggingInterval, 0, calendar, logChannelList)
                setLastLoggedLineTimeStamp(loggingInterval, 0, calendar.timeInMillis)
                calendar.add(Calendar.MILLISECOND, loggingInterval)
                i++
            }
            // }
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            println("tearing down")
            TestUtils.deleteTestFolder()
        }
    }
}
