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

import org.openmuc.framework.data.Record.value
import org.openmuc.framework.datalogger.ascii.utils.LoggerUtils.getFilename
import java.io.File
import java.io.FileNotFoundException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object TestUtils {
    const val TESTFOLDER = "test"
    val TESTFOLDERPATH = System.getProperty("user.dir") + "/" + TESTFOLDER + "/"
    fun stringToDate(format: String?, strDate: String?): Calendar {
        val sdf = SimpleDateFormat(format, Locale.GERMAN)
        var date: Date? = null
        try {
            date = sdf.parse(strDate)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        val calendar: Calendar = GregorianCalendar(Locale.getDefault())
        calendar.time = date
        return calendar
    }

    fun deleteExistingFile(loggingInterval: Int, loggingTimeOffset: Int, calendar: Calendar) {
        val filename = getFilename(loggingInterval, loggingTimeOffset, calendar.timeInMillis)
        val file = File(TESTFOLDERPATH + filename)
        if (file.exists()) {
            println("Delete File $filename")
            file.delete()
        }
    }

    fun createTestFolder() {
        val testFolder = File(TESTFOLDER)
        if (!testFolder.exists()) {
            testFolder.mkdir()
        }
    }

    fun deleteTestFolder() {
        val testFolder = File(TESTFOLDER)
        try {
            deleteRecursive(testFolder)
        } catch (e: FileNotFoundException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
    }

    @Throws(FileNotFoundException::class)
    private fun deleteRecursive(path: File): Boolean {
        if (!path.exists()) {
            println("Method deleteRecursive(): Path does not exists. " + path.absolutePath)
        }
        var ret = true
        if (path.isDirectory) {
            for (f in path.listFiles()) {
                ret = ret && deleteRecursive(f)
            }
        }
        return ret && path.delete()
    }
}
