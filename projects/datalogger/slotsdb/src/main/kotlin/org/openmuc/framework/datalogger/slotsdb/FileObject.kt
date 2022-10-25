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

import org.openmuc.framework.data.DoubleValue
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Flag.Companion.newFlag
import org.openmuc.framework.data.Record
import java.io.*
import java.nio.ByteBuffer
import java.util.*

class FileObject {
    /**
     * Return the Timestamp of the first stored Value in this File.
     *
     * @return timestamp as long
     */
    var startTimeStamp // byte 0-7 in file (cached)
            : Long = 0
        private set

    /**
     * Returns the step frequency in seconds.
     *
     * @return step frequency in seconds
     */
    var storingPeriod // byte 8-15 in file (cached)
            : Long = 0
        private set
    private val dataFile: File?
    private var dos: DataOutputStream? = null
    private var bos: BufferedOutputStream? = null
    private var fos: FileOutputStream? = null
    private var dis: DataInputStream? = null
    private var fis: FileInputStream? = null
    private var canWrite: Boolean
    private var canRead: Boolean

    /*
     * File length will be cached to avoid system calls an improve I/O Performance
     */
    private var length: Long = 0

    constructor(filename: String?) {
        canWrite = false
        canRead = false
        dataFile = File(filename)
        length = dataFile.length()
        if (dataFile.exists() && length >= 16) {
            /*
             * File already exists -> get file Header (startTime and step-frequency) TODO: compare to starttime and
             * frequency in constructor! new file needed? update to file-array!
             */
            try {
                fis = FileInputStream(dataFile)
                try {
                    dis = DataInputStream(fis)
                    try {
                        startTimeStamp = dis!!.readLong()
                        storingPeriod = dis!!.readLong()
                    } finally {
                        if (dis != null) {
                            dis!!.close()
                            dis = null
                        }
                    }
                } finally {
                    if (dis != null) {
                        dis!!.close()
                        dis = null
                    }
                }
            } finally {
                if (fis != null) {
                    fis!!.close()
                    fis = null
                }
            }
        }
    }

    constructor(file: File?) {
        canWrite = false
        canRead = false
        dataFile = file
        length = dataFile!!.length()
        if (dataFile.exists() && length >= 16) {
            /*
             * File already exists -> get file Header (startTime and step-frequency)
             */
            fis = FileInputStream(dataFile)
            try {
                dis = DataInputStream(fis)
                try {
                    startTimeStamp = dis!!.readLong()
                    storingPeriod = dis!!.readLong()
                } finally {
                    if (dis != null) {
                        dis!!.close()
                        dis = null
                    }
                }
            } finally {
                if (fis != null) {
                    fis!!.close()
                    fis = null
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun enableOutput() {
        /*
         * Close Input Streams, for enabling output.
         */
        if (dis != null) {
            dis!!.close()
            dis = null
        }
        if (fis != null) {
            fis!!.close()
            fis = null
        }

        /*
         * enabling output
         */if (fos == null || dos == null || bos == null) {
            fos = FileOutputStream(dataFile, true)
            bos = BufferedOutputStream(fos)
            dos = DataOutputStream(bos)
        }
        canRead = false
        canWrite = true
    }

    @Throws(IOException::class)
    private fun enableInput() {
        /*
         * Close Output Streams for enabling input.
         */
        if (dos != null) {
            dos!!.flush()
            dos!!.close()
            dos = null
        }
        if (bos != null) {
            bos!!.close()
            bos = null
        }
        if (fos != null) {
            fos!!.close()
            fos = null
        }

        /*
         * enabling input
         */if (fis == null || dis == null) {
            fis = FileInputStream(dataFile)
            dis = DataInputStream(fis)
        }
        canWrite = false
        canRead = true
    }

    /**
     * creates the file, if it doesn't exist.
     *
     * @param startTimeStamp
     * for file header
     * @param stepIntervall
     * for file header
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun createFileAndHeader(startTimeStamp: Long, stepIntervall: Long) {
        if (!dataFile!!.exists() || length < 16) {
            dataFile.parentFile.mkdirs()
            if (dataFile.exists() && length < 16) {
                dataFile.delete() // file corrupted (header shorter that 16
            }
            // bytes)
            dataFile.createNewFile()
            this.startTimeStamp = startTimeStamp
            storingPeriod = stepIntervall

            /*
             * Do not close Output streams, because after writing the header -> data will follow!
             */fos = FileOutputStream(dataFile)
            bos = BufferedOutputStream(fos)
            dos = DataOutputStream(bos)
            dos!!.writeLong(startTimeStamp)
            dos!!.writeLong(stepIntervall)
            dos!!.flush()
            length += 16 /* wrote 2*8 Bytes */
            canWrite = true
            canRead = false
        }
    }

    @Throws(IOException::class)
    fun append(value: Double, timestamp: Long, flag: Byte) {
        val writePosition = getBytePosition(timestamp)
        if (writePosition == length) {
            /*
             * value for this timeslot has not been saved yet "AND" some value has been stored in last timeslot
             */
            if (!canWrite) {
                enableOutput()
            }
            dos!!.writeDouble(value)
            dos!!.writeByte(flag.toInt())
            length += 9
        } else {
            if (length > writePosition) {
                /*
                 * value has already been stored for this timeslot -> handle? AVERAGE, MIN, MAX, LAST speichern?!
                 */
            } else {
                /*
                 * there are missing some values missing -> fill up with NaN!
                 */
                if (!canWrite) {
                    enableOutput()
                }
                val rowsToFillWithNan = (writePosition - length) / 9 // TODO:
                // stimmt
                // Berechnung?
                for (i in 0 until rowsToFillWithNan) {
                    dos!!.writeDouble(Double.NaN) // TODO: festlegen welcher Wert
                    // undefined sein soll NaN
                    // ok?
                    dos!!.writeByte(Flag.NO_VALUE_RECEIVED_YET.getCode().toInt()) // TODO:
                    // festlegen
                    // welcher Wert
                    // undefined sein
                    // soll 00 ok?
                    length += 9
                }
                dos!!.writeDouble(value)
                dos!!.writeByte(flag.toInt())
                length += 9
            }
        }
        /*
         * close(); OutputStreams will not be closed or flushed. Data will be written to disk after calling flush()
         * method.
         */
    }

    val timestampForLatestValue: Long
        get() = startTimeStamp + ((length - 16) / 9 - 1) * storingPeriod

    /**
     * calculates the position in a file for a certain timestamp
     *
     * @param timestamp
     * the searched timestamp
     * @return position the position of the timestamp
     */
    private fun getBytePosition(timestamp: Long): Long {
        return if (timestamp >= startTimeStamp) {

            /*
             * get position for timestamp 117 000: 117 000 - 100 000 = 17 000 17 * 000 / 5 000 = 3.4 Math.round(3.4) = 3
             * 3*(8+1) = 27 27 + 16 = 43 = position to store to!
             */
            // long pos = (Math.round((double) (timestamp - startTimeStamp) /
            // storagePeriod) * 9) + 16; /* slower */
            var pos = (timestamp - startTimeStamp).toDouble() / storingPeriod
            if (pos % 1 != 0.0) { /* faster */
                pos = Math.round(pos).toDouble()
            }
            (pos * 9 + 16).toLong()
        } else {
            // not in file! should never happen...
            -1
        }
    }

    /*
     * Calculates the closest timestamp to wanted timestamp getByteposition does a similar thing (Math.round()), for
     * byte position.
     */
    private fun getClosestTimestamp(timestamp: Long): Long {
        // return Math.round((double) (timestamp -
        // startTimeStamp)/storagePeriod)*storagePeriod+startTimeStamp; /*
        // slower */
        var ts = (timestamp - startTimeStamp).toDouble() / storingPeriod
        if (ts % 1 != 0.0) {
            ts = Math.round(ts).toDouble()
        }
        return ts.toLong() * storingPeriod + startTimeStamp
    }

    @Throws(IOException::class)
    fun read(timestamp: Long): Record? {
        var timestamp = timestamp
        timestamp = getClosestTimestamp(timestamp) // round to: startTimestamp
        // + n*stepIntervall
        if (timestamp >= startTimeStamp && timestamp <= timestampForLatestValue) {
            if (!canRead) {
                enableInput()
            }
            fis!!.channel.position(getBytePosition(timestamp))
            val toReturn = dis!!.readDouble()
            if (!java.lang.Double.isNaN(toReturn)) {
                return Record(
                    DoubleValue(toReturn), timestamp, newFlag(
                        dis!!.readByte().toInt()
                    )
                )
            }
        }
        return null
    }

    /**
     * Returns a List of Value Objects containing the measured Values between provided start and end timestamp
     *
     * @param start
     * start timestamp
     * @param end
     * end timestamp
     * @return a list of records
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun read(start: Long, end: Long): List<Record?> {
        var start = start
        var end = end
        start = getClosestTimestamp(start) // round to: startTimestamp +
        // n*stepIntervall
        end = getClosestTimestamp(end) // round to: startTimestamp +
        // n*stepIntervall
        val toReturn: MutableList<Record?> = Vector()
        if (start < end) {
            if (start < startTimeStamp) {
                // of this file.
                start = startTimeStamp
            }
            if (end > timestampForLatestValue) {
                end = timestampForLatestValue
            }
            if (!canRead) {
                enableInput()
            }
            var timestampcounter = start
            val startPos = getBytePosition(start)
            val endPos = getBytePosition(end)
            fis!!.channel.position(startPos)
            val b = ByteArray((endPos - startPos).toInt() + 9)
            dis!!.read(b, 0, b.size)
            val bb = ByteBuffer.wrap(b)
            bb.rewind()
            for (i in (0..(endPos - startPos) / 9)) {
                val d = bb.double
                val s = newFlag(bb.get().toInt())
                if (!java.lang.Double.isNaN(d)) {
                    toReturn.add(Record(DoubleValue(d), timestampcounter, s))
                }
                timestampcounter += storingPeriod
            }
        } else if (start == end) {
            toReturn.add(read(start))
            toReturn.removeAll(setOf<Any?>(null))
        }
        return toReturn // Always return a list -> might be empty -> never is
        // null, to avoid NP's
    }

    @Throws(IOException::class)
    fun readFully(): List<Record?> {
        return read(startTimeStamp, timestampForLatestValue)
    }

    /**
     * Closes and Flushes underlying Input- and OutputStreams
     *
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun close() {
        canRead = false
        canWrite = false
        if (dos != null) {
            dos!!.flush()
            dos!!.close()
            dos = null
        }
        if (fos != null) {
            fos!!.close()
            fos = null
        }
        if (dis != null) {
            dis!!.close()
            dis = null
        }
        if (fis != null) {
            fis!!.close()
            fis = null
        }
    }

    /**
     * Flushes the underlying Data Streams.
     *
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun flush() {
        if (dos != null) {
            dos!!.flush()
        }
    }
}
