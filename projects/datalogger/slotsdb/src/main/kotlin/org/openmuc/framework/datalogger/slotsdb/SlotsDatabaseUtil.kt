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
package org.openmuc.framework.datalogger.slotsdb

import org.openmuc.framework.data.Record
import org.slf4j.LoggerFactory
import java.io.*

object SlotsDatabaseUtil {
    private val logger = LoggerFactory.getLogger(SlotsDatabaseUtil::class.java)
    @Throws(IOException::class)
    fun printWholeFile(file: File) {
        if (!file.name.contains(SlotsDb.Companion.FILE_EXTENSION)) {
            System.err.println(file.name + " is not a \"" + SlotsDb.Companion.FILE_EXTENSION + "\" file.")
            return
        } else {
            val dis = DataInputStream(FileInputStream(file))
            try {
                if (file.length() >= 16) {
                    logger.debug("StartTimestamp: " + dis.readLong() + "  -  StepIntervall: " + dis.readLong())
                    while (dis.available() >= 9) {
                        logger.debug(dis.readDouble().toString() + "  -\t  Flag: " + dis.readByte())
                    }
                }
            } finally {
                dis.close()
            }
        }
    }

    @Throws(IOException::class)
    fun printWholeFile(filename: String) {
        printWholeFile(File(filename))
    }
}
