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
import org.openmuc.framework.data.*
import org.openmuc.framework.datalogger.ascii.AsciiLogger.Companion.setLastLoggedLineTimeStamp
import org.openmuc.framework.datalogger.ascii.LogFileReader
import org.openmuc.framework.datalogger.ascii.LogFileWriter
import org.openmuc.framework.datalogger.ascii.LogIntervalContainerGroup
import org.openmuc.framework.datalogger.ascii.utils.Const
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import java.io.File
import java.util.*

class LogFileWriterTest {
    var lfw = LogFileWriter(TestUtils.TESTFOLDERPATH, true)
    @Test
    fun tc300_check_if_new_file_is_created_on_day_change() {
        println("### Begin test tc300_check_if_new_file_is_created_on_day_change")
        val filename1 = TestUtils.TESTFOLDERPATH + fileDate1 + "_" + loggingInterval + ext
        val filename2 = TestUtils.TESTFOLDERPATH + fileDate2 + "_" + loggingInterval + ext
        val file1 = File(filename1)
        val file2 = File(filename2)
        val assertT = file1.exists() && file2.exists()
        println(Thread.currentThread().stackTrace[1].methodName)
        println(" " + file1.absolutePath)
        println(" " + file2.absolutePath)
        println(" Two files created = $assertT")
        Assertions.assertTrue(assertT)
    }

    // @Test
    // public void tc302_check_file_fill_up_at_logging_at_day_change() {
    // // TODO:
    // second_setup();
    // System.out.println("### Begin test tc301_check_file_fill_up_at_logging_at_day_change");
    //
    // int valuesToWrite = 5;
    //
    // calendar.add(Calendar.MILLISECOND, loggingInterval * valuesToWrite);
    //
    // LogIntervalContainerGroup group = getSecondGroup(calendar.getTimeInMillis(), 4);
    // lfw.log(group, loggingInterval, loggingTimeOffset, calendar, logChannelList);
    //
    // LogChannelTestImpl ch1 = new LogChannelTestImpl(ch01, "dummy description", dummy, ValueType.FLOAT,
    // loggingInterval, loggingTimeOffset);
    // LogFileReader lfr = new LogFileReader(TestUtils.TESTFOLDERPATH, ch1);
    //
    // List<Record> recordList = lfr.getValues(calendar.getTimeInMillis() - loggingInterval * 5,
    // calendar.getTimeInMillis());
    // int receivedRecords = recordList.size();
    //
    // int numErrorFlags = 0;
    // for (Record record : recordList) {
    // if (record.getFlag().equals(Flag.DATA_LOGGING_NOT_ACTIVE)) {
    // ++numErrorFlags;
    // }
    // }
    //
    // Boolean assertT;
    // if (receivedRecords == valuesToWrite && numErrorFlags == valuesToWrite - 1) {
    // assertT = true;
    // }
    // else {
    // assertT = false;
    // }
    // System.out.println(Thread.currentThread().getStackTrace()[1].getMethodName());
    // System.out.println(" records = " + receivedRecords + " (" + valuesToWrite + " expected)");
    // System.out
    // .println(" records with error flag 32 = " + numErrorFlags + " (" + (valuesToWrite - 1) + " expected)");
    //
    // assertTrue(assertT);
    // }
    // private void second_setup() {
    //
    // System.out.println("### second_setup() LogFileWriterTest");
    //
    // TestSuite.createTestFolder();
    //
    // // 2 Kanaele im Stunden-Takt loggen von 12 Uhr bis 12 Uhr in den naechsten Tage hinein
    // // --> Ergebnis muessten zwei Dateien sein die vom LogFileWriter erstellt wurden
    //
    // String filename1 = TestUtils.TESTFOLDERPATH + fileDate1 + "_" + loggingInterval + ext;
    // String filename2 = TestUtils.TESTFOLDERPATH + fileDate2 + "_" + loggingInterval + ext;
    //
    // File file1 = new File(filename1);
    // File file2 = new File(filename2);
    //
    // if (file1.exists()) {
    // System.out.println("Delete File " + filename1);
    // file1.delete();
    // }
    // if (file2.exists()) {
    // System.out.println("Delete File " + filename2);
    // file2.delete();
    // }
    //
    // LogChannelTestImpl ch1 = new LogChannelTestImpl(ch01, "dummy description", dummy, ValueType.FLOAT,
    // loggingInterval, loggingTimeOffset);
    //
    // logChannelList.put(ch01, ch1);
    //
    // long timeStamp = calendar.getTimeInMillis();
    //
    // // writes 24 records for 2 channels from 12 o'clock till 12 o'clock of the other day
    // AsciiLogger.setLastLoggedLineTimeStamp(loggingInterval, loggingTimeOffset, 0); // Set to 0, for deleting
    // // timestamp of previous test
    // for (int i = 0; i < ((60 * 10) * (1000d / loggingInterval)); ++i) {
    //
    // LogFileWriter lfw = new LogFileWriter(TestUtils.TESTFOLDERPATH, true);
    //
    // LogIntervalContainerGroup group = getSecondGroup(timeStamp, i);
    // lfw.log(group, loggingInterval, loggingTimeOffset, calendar, logChannelList);
    // calendar.add(Calendar.MILLISECOND, loggingInterval);
    // }
    // }
    @Test
    fun tc301_check_file_fill_up_at_logging() {
        println("### Begin test tc301_check_file_fill_up_at_logging")
        val valuesToWrite = 5
        calendar.add(Calendar.MILLISECOND, loggingInterval * valuesToWrite - 10)
        val group = getGroup(calendar.timeInMillis, 3, true, 0x11.toByte(), "nope")
        lfw.log(group, loggingInterval, loggingTimeOffset, calendar, logChannelList)
        setLastLoggedLineTimeStamp(loggingInterval, loggingTimeOffset, calendar.timeInMillis)
        val ch1 = LogChannelTestImpl(
            ch01, "", "dummy description", dummy, ValueType.FLOAT, 0.0, 0.0,
            false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
        )
        val lfr = LogFileReader(TestUtils.TESTFOLDERPATH, ch1)
        val recordList: List<Record?> = lfr
            .getValues(calendar.timeInMillis - loggingInterval * 5, calendar.timeInMillis)[ch01]!!
        val receivedRecords = recordList.size
        var numErrorFlags = 0
        for (record in recordList) {
            if (record!!.flag == Flag.DATA_LOGGING_NOT_ACTIVE) {
                ++numErrorFlags
            }
        }
        val assertT = receivedRecords == valuesToWrite && numErrorFlags == valuesToWrite - 1
        println(Thread.currentThread().stackTrace[1].methodName)
        println(" records = $receivedRecords ($valuesToWrite expected)")
        println(" records with error flag 32 = " + numErrorFlags + " (" + (valuesToWrite - 1) + " expected)")
        Assertions.assertTrue(assertT)
    }

    companion object {
        // t1 = start timestamp of requestet interval
        // t2 = end timestamp of requestet interval
        private const val loggingInterval = 10000 // ms;
        private const val loggingTimeOffset = 0 // ms;
        private const val ext = ".dat"
        private const val dateFormat = "yyyyMMdd HH:mm:s"
        private const val fileDate1 = "20880808"
        private const val fileDate2 = "20880809"
        private const val ch01 = "FLOAT"
        private const val ch02 = "DOUBLE"
        private const val ch03 = "BOOLEAN"
        private const val ch04 = "SHORT"
        private const val ch05 = "INTEGER"
        private const val ch06 = "LONG"
        private const val ch07 = "BYTE"
        private const val ch08 = "STRING"
        private const val ch09 = "BYTE_ARRAY"
        private const val dummy = "dummy"

        // private static String[] channelIds = new String[] { ch01, ch02, ch03, ch04, ch05, ch06, ch07, ch08, ch09 };
        private const val time = " 23:55:00"
        private const val testStringValueCorrect =
            "qwertzuiop+asdfghjkl#<yxcvbnm,.-^1234567890 !$%&/()=?QWERTZUIOP*ASDFGHJKL'>YXCVBNM;:_"
        private const val testStringValueIncorrect = ("qwertzuiop+asdfghjkl#<yxcvbnm,.-^1234567890 " + Const.SEPARATOR
                + "!$%&/()=?QWERTZUIOP*SDFGHJKL'>YXCVBNM;:_")
        private val testByteArray = byteArrayOf(1, 2, 3, 4, -5, -9, 0)
        private const val valueLength = 100
        private val valueLengthByteArray = testByteArray.size
        private val logChannelList = HashMap<String, LogChannel>()
        private val calendar = TestUtils.stringToDate(dateFormat, fileDate1 + time)
        @JvmStatic
        @BeforeAll
        fun setup() {
            println("### Setup() LogFileWriterTest")
            TestUtils.createTestFolder()

            // 2 Kanaele im Stunden-Takt loggen von 12 Uhr bis 12 Uhr in den naechsten Tage hinein
            // --> Ergebnis muessten zwei Dateien sein die vom LogFileWriter erstellt wurden
            val filename1 = TestUtils.TESTFOLDERPATH + fileDate1 + "_" + loggingInterval + ext
            val filename2 = TestUtils.TESTFOLDERPATH + fileDate2 + "_" + loggingInterval + ext
            val file1 = File(filename1)
            val file2 = File(filename2)
            if (file1.exists()) {
                println("Delete File $filename1")
                file1.delete()
            }
            if (file2.exists()) {
                println("Delete File $filename2")
                file2.delete()
            }
            val ch1 = LogChannelTestImpl(
                ch01, "", "dummy description", dummy, ValueType.FLOAT, 0.0, 0.0,
                false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
            )
            val ch2 = LogChannelTestImpl(
                ch02, "", "dummy description", dummy, ValueType.DOUBLE, 0.0,
                0.0, false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
            )
            val ch3 = LogChannelTestImpl(
                ch03, "", "dummy description", dummy, ValueType.BOOLEAN, 0.0,
                0.0, false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
            )
            val ch4 = LogChannelTestImpl(
                ch04, "", "dummy description", dummy, ValueType.SHORT, 0.0, 0.0,
                false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
            )
            val ch5 = LogChannelTestImpl(
                ch05, "", "dummy description", dummy, ValueType.INTEGER, 0.0,
                0.0, false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
            )
            val ch6 = LogChannelTestImpl(
                ch06, "", "dummy description", dummy, ValueType.LONG, 0.0, 0.0,
                false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
            )
            val ch7 = LogChannelTestImpl(
                ch07, "", "dummy description", dummy, ValueType.BYTE, 0.0, 0.0,
                false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
            )
            val ch8 = LogChannelTestImpl(
                ch08, "", "dummy description", dummy, ValueType.STRING, 0.0,
                0.0, false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, valueLength, false
            )
            val ch9 = LogChannelTestImpl(
                ch09, "", "dummy description", dummy, ValueType.BYTE_ARRAY, 0.0,
                0.0, false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, valueLengthByteArray, false
            )
            logChannelList[ch01] = ch1
            logChannelList[ch02] = ch2
            logChannelList[ch03] = ch3
            logChannelList[ch04] = ch4
            logChannelList[ch05] = ch5
            logChannelList[ch06] = ch6
            logChannelList[ch07] = ch7
            logChannelList[ch08] = ch8
            logChannelList[ch09] = ch9
            val timeStamp = calendar.timeInMillis
            var boolValue: Boolean
            var byteValue: Byte = 0
            var testString: String

            // writes 24 records for 2 channels from 12 o'clock till 12 o'clock of the other day
            setLastLoggedLineTimeStamp(loggingInterval, loggingTimeOffset, 0) // Set to 0, for deleting
            // timestamp of previous test
            var i = 0
            while (i < 60 * 10 * (1000.0 / loggingInterval)) {
                if (i % 2 > 0) {
                    boolValue = true
                    testString = testStringValueCorrect
                } else {
                    boolValue = false
                    testString = testStringValueIncorrect
                }
                val lfw = LogFileWriter(TestUtils.TESTFOLDERPATH, false)
                val group = getGroup(timeStamp, i, boolValue, byteValue, testString)
                lfw.log(group, loggingInterval, loggingTimeOffset, calendar, logChannelList)
                setLastLoggedLineTimeStamp(loggingInterval, loggingTimeOffset, calendar.timeInMillis)
                calendar.add(Calendar.MILLISECOND, loggingInterval)
                ++byteValue
                ++i
            }
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            println("tearing down")
            TestUtils.deleteTestFolder()
        }

        private fun getGroup(
            timeStamp: Long, i: Int, boolValue: Boolean, byteValue: Byte,
            testString: String
        ): LogIntervalContainerGroup {
            val group = LogIntervalContainerGroup()
            val container1 = LoggingRecord(ch01, Record(FloatValue(i * -7 - 0.555f), timeStamp))
            val container2 = LoggingRecord(ch02, Record(DoubleValue(i * +7 - 0.555), timeStamp))
            val container3 = LoggingRecord(ch03, Record(BooleanValue(boolValue), timeStamp))
            val container4 = LoggingRecord(ch04, Record(ShortValue(i.toShort()), timeStamp))
            val container5 = LoggingRecord(ch05, Record(IntValue(i), timeStamp))
            val container6 = LoggingRecord(ch06, Record(LongValue((i * 1000000).toLong()), timeStamp))
            val container7 = LoggingRecord(ch07, Record(ByteValue(byteValue), timeStamp))
            val container8 = LoggingRecord(ch08, Record(StringValue(testString), timeStamp))
            val container9 = LoggingRecord(ch09, Record(ByteArrayValue(testByteArray), timeStamp))
            group.add(container1)
            group.add(container2)
            group.add(container3)
            group.add(container4)
            group.add(container5)
            group.add(container6)
            group.add(container7)
            group.add(container8)
            group.add(container9)
            return group
        }
    }
}
