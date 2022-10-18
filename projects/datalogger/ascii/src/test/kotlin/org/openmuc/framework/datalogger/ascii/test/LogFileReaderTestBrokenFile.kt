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
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.datalogger.ascii.LogFileReader
import org.openmuc.framework.datalogger.ascii.utils.LoggerUtils.getAllDataFiles

class LogFileReaderTestBrokenFile {
    private var fileDate: String? = null
    var dateFormat = "yyyyMMdd HH:mm:ss"
    var channelTestImpl = LogChannelTestImpl(
        Channel0Name, "", "Comment", "W", ValueType.DOUBLE, 0.0,
        0.0, false, 1000, 0, "", loggingInterval, loggingTimeOffset, false, false
    )

    @Test
    fun tc200_logfile_does_not_exist() {
        println("### Begin test tc200_logfile_does_not_exist")
        fileDate = "20131201"
        val t1 = TestUtils.stringToDate(dateFormat, "$fileDate 12:00:00").timeInMillis
        val t2 = TestUtils.stringToDate(dateFormat, "$fileDate 12:00:10").timeInMillis
        val fr = LogFileReader(TestUtils.TESTFOLDERPATH, channelTestImpl)
        val records: List<Record?> = fr.getValues(t1, t2)[channelTestImpl.getId()]!!
        val expectedRecords: Long = 0
        print(Thread.currentThread().stackTrace[1].methodName)
        println(" records = " + records.size + " (" + expectedRecords + " expected)")
        if (records.size.toLong() == expectedRecords) {
            Assertions.assertTrue(true)
        } else {
            Assertions.assertTrue(false)
        }
    }

    // @Ignore
    // @Test
    // public void tc201_no_header_in_logfile() {
    //
    // System.out.println("### Begin test tc201_no_header_in_logfile");
    //
    // fileDate = "20131202";
    //
    // String ext = ".dat";
    // long startTimestampFile = TestUtils.stringToDate(dateFormat, fileDate + " 12:00:00").getTime();
    // long endTimestampFile = TestUtils.stringToDate(dateFormat, fileDate + " 12:00:30").getTime();
    // String[] channelIds = new String[] { "power", "energy" };
    //
    // String filename = TestUtils.TESTFOLDER + "/" + fileDate + "_" + loggingInterval + ext;
    // createLogFileWithoutHeader(filename, channelIds, startTimestampFile, endTimestampFile, loggingInterval);
    //
    // long t1 = TestUtils.stringToDate(dateFormat, fileDate + " 12:00:00").getTime();
    // long t2 = TestUtils.stringToDate(dateFormat, fileDate + " 12:00:10").getTime();
    //
    // LogFileReader fr = new LogFileReader(TestUtils.TESTFOLDERPATH, channelTestImpl);
    // List<Record> records = fr.getValues(t1, t2);
    //
    // long expectedRecords = 0;
    // System.out.print(Thread.currentThread().getStackTrace()[1].getMethodName());
    // System.out.println(" records = " + records.size() + " (" + expectedRecords + " expected)");
    //
    // if (records.size() == expectedRecords) {
    // assertTrue(true);
    // }
    // else {
    // assertTrue(false);
    // }
    //
    // }
    // @Ignore
    // @Test
    // public void tc202_channelId_not_in_header() {
    //
    // System.out.println("### Begin test tc201_no_header_in_logfile");
    //
    // fileDate = "20131202";
    //
    // String ext = ".dat";
    // long startTimestampFile = TestUtils.stringToDate(dateFormat, fileDate + " 12:00:00").getTime();
    // long endTimestampFile = TestUtils.stringToDate(dateFormat, fileDate + " 12:00:30").getTime();
    // String[] channelIds = new String[] { "energy" };
    //
    // String filename = TestUtils.TESTFOLDER + "/" + fileDate + "_" + loggingInterval + ext;
    // createLogFile(filename, channelIds, startTimestampFile, endTimestampFile, loggingInterval);
    //
    // long t1 = TestUtils.stringToDate(dateFormat, fileDate + " 12:00:00").getTime();
    // long t2 = TestUtils.stringToDate(dateFormat, fileDate + " 12:00:10").getTime();
    //
    // LogFileReader fr = new LogFileReader(TestUtils.TESTFOLDERPATH, channelTestImpl);
    // List<Record> records = fr.getValues(t1, t2);
    //
    // long expectedRecords = 0;
    // System.out.print(Thread.currentThread().getStackTrace()[1].getMethodName());
    // System.out.println(" records = " + records.size() + " (" + expectedRecords + " expected)");
    //
    // if (records.size() == expectedRecords) {
    // assertTrue(true);
    // }
    // else {
    // assertTrue(false);
    // }
    // }
    @Test
    fun tc203_no_file_in_directory() {
        val files = getAllDataFiles(TestUtils.TESTFOLDERPATH)
        Assertions.assertNull(files)
    }

    companion object {
        private const val loggingInterval = 1000 // ms
        var loggingTimeOffset = 0 // ms
        private const val Channel0Name = "power"
        @AfterAll
        fun tearDown() {
            println("tearing down")
            TestUtils.deleteTestFolder()
        }
    }
}
