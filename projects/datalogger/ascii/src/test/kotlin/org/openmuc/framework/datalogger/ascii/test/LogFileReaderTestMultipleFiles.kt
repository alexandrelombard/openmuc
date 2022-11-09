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
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.datalogger.ascii.AsciiLogger.Companion.setLastLoggedLineTimeStamp
import org.openmuc.framework.datalogger.ascii.LogFileReader
import org.openmuc.framework.datalogger.ascii.LogFileWriter
import org.openmuc.framework.datalogger.ascii.LogIntervalContainerGroup
import org.openmuc.framework.datalogger.ascii.utils.LoggerUtils.getAllDataFiles
import org.openmuc.framework.datalogger.ascii.utils.LoggerUtils.getLatestFile
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import java.io.File
import java.util.*

class LogFileReaderTestMultipleFiles {
    // private static String ext = ".dat";
    var channelTestImpl = LogChannelTestImpl(
        Channel0Name, "", "Comment", "W", ValueType.DOUBLE, 0.0,
        0.0, false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
    )

    @Test
    fun tc009_t1_t2_within_available_data_with_three_files() {
        println("### Begin test tc009_t1_t2_within_available_data_with_three_files")
        val t1 = TestUtils.stringToDate(dateFormat, fileDate0 + " 23:00:00").timeInMillis
        val t2 = TestUtils.stringToDate(dateFormat, fileDate2 + " 00:59:" + (60 - loggingInterval / 1000))
            .timeInMillis
        val fr = LogFileReader(TestUtils.TESTFOLDERPATH, channelTestImpl)
        val records = fr.getValues(t1, t2)[channelTestImpl.id] ?: listOf()
        val hour = 3600
        val expectedRecords = ((hour * 24 + hour * 2) / (loggingInterval / 1000)).toLong()
        print(Thread.currentThread().stackTrace[1].methodName)
        val result = records.size.toLong() == expectedRecords
        println(" records = " + records.size + " (" + expectedRecords + " expected); ")
        Assertions.assertTrue(result)
    }

    @Test
    fun tc010_test_getValues() {
        val fr = LogFileReader(TestUtils.TESTFOLDERPATH, channelTestImpl)
        val filename1 = TestUtils.TESTFOLDERPATH + fileDate1 + "_" + loggingInterval + EXT
        val file1 = File(filename1)
        if (!file1.exists()) {
            Assertions.fail<Any>("File does not exist at path " + file1.absolutePath)
        }
        val expected = 1440
        val values = fr.getValues(file1.path)
        for ((_, records) in values) {
            val actual = records.size
            Assertions.assertEquals(expected, actual)
        }
    }

    @Test
    fun tc_011_test_getAllDataFiles() {
        val dir = TestUtils.TESTFOLDERPATH
        val files = getAllDataFiles(dir)
        val expected = LinkedList<String>()
        expected.add("20770709_60000.dat")
        expected.add("20770708_60000.dat")
        expected.add("20770707_60000.dat")
        val actual = LinkedList<String>()
        for (file in files) {
            actual.add(file.name)
        }
        Assertions.assertTrue(expected.containsAll(actual))
        Assertions.assertTrue(actual.containsAll(expected))
    }

    @Test
    fun tc_012_test_getLatestFile() {
        val dir = TestUtils.TESTFOLDERPATH
        val files = getAllDataFiles(dir)
        val expected = "20770906_60000.dat"
        val file = getLatestFile(files)
        val actual = file?.name
        Assertions.assertEquals(expected, actual)
    }

    companion object {
        // t1 = start timestamp of requestet interval
        // t2 = end timestamp of requestet interval
        private const val Channel0Name = "power"
        private const val EXT = ".dat"
        var loggingTimeOffset = 0 // ms
        private const val fileDate0 = "20770707"
        private const val fileDate1 = "20770708"
        private const val fileDate2 = "20770709"
        private const val loggingInterval = 60000 // ms;

        // private static String[] channelIds = new String[] { Channel0Name };
        private const val dateFormat = "yyyyMMdd HH:mm:ss"
        @JvmStatic
        @BeforeAll
        fun setup() {
            println("### Setup() LogFileReaderTestMultipleFiles")
            TestUtils.deleteTestFolder()
            TestUtils.createTestFolder()
            // drei Dateien

            // 1 Kanal im Sekunden-Takt loggen ueber von 23 Uhr bis 1 Uhr des uebernaechsten Tages
            // --> Ergebnis muessten drei
            // Dateien sein die vom LogFileWriter erstellt wurden
            val filename0 = TestUtils.TESTFOLDERPATH + fileDate0 + "_" + loggingInterval + EXT
            val filename1 = TestUtils.TESTFOLDERPATH + fileDate1 + "_" + loggingInterval + EXT
            val filename2 = TestUtils.TESTFOLDERPATH + fileDate2 + "_" + loggingInterval + EXT
            val file0 = File(filename0)
            val file1 = File(filename1)
            val file2 = File(filename2)
            if (file0.exists()) {
                println("Delete File $filename2")
                file0.delete()
            }
            if (file1.exists()) {
                println("Delete File $filename1")
                file1.delete()
            }
            if (file2.exists()) {
                println("Delete File $filename2")
                file2.delete()
            }
            val logChannelList = HashMap<String, LogChannel>()
            val ch0 = LogChannelTestImpl(
                "power", "", "dummy description", "kW", ValueType.DOUBLE, 0.0,
                0.0, false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
            )
            logChannelList[Channel0Name] = ch0
            val calendar = TestUtils.stringToDate(dateFormat, fileDate0 + " 23:00:00")
            val hour = 3600
            var i = 0
            while (i < (hour * 24 + hour * 2 * 1000.0 / loggingInterval)) {
                val container1 = LoggingRecord(
                    Channel0Name,
                    Record(DoubleValue(1.0), calendar.timeInMillis)
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
