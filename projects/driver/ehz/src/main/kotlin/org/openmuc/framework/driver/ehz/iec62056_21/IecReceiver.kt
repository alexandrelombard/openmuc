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
package org.openmuc.framework.driver.ehz.iec62056_21

import org.openmuc.jrxtx.*
import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.io.IOException
import java.io.InterruptedIOException

class IecReceiver(iface: String?) {
    // public static final int PROTOCOL_NORMAL = 0;
    // public static final int PROTOCOL_SECONDARY = 1;
    // public static final int PROTOCOL_HDLC = 2;
    //
    // public static final int MODE_DATA_READOUT = 0;
    // public static final int MODE_PROGRAMMING = 1;
    // public static final int MODE_BINARY_HDLC = 2;,
    private var serialPort: SerialPort?
    private val msgBuffer = ByteArray(10000)
    private val inputBuffer = ByteArray(2000)
    private val inStream: DataInputStream

    private inner class Timeout(private val time: Long) : Thread() {
        var isEnd = false
            private set

        override fun run() {
            try {
                sleep(time)
            } catch (e: InterruptedException) {
            }
            isEnd = true
            return
        }
    }

    init {
        serialPort = SerialPortBuilder.newBuilder(iface)
            .setBaudRate(9600)
            .setDataBits(DataBits.DATABITS_7)
            .setStopBits(StopBits.STOPBITS_1)
            .setParity(Parity.EVEN)
            .build()
        inStream = DataInputStream(serialPort.getInputStream())
        if (inStream.available() > 0) {
            inStream.skip(inStream.available().toLong())
        }
        try {
            Thread.sleep(100)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    @Throws(IOException::class)
    fun receiveMessage(msTimeout: Long): ByteArray {
        val time: Timeout = Timeout(msTimeout)
        time.start()
        var bufferIndex = 0
        var start = false
        var end = false
        inStream.skip(inStream.available().toLong()) // inStream to current state
        do {
            if (inStream.available() > 0) {
                val read = inStream.read(inputBuffer)
                for (i in 0 until read) {
                    val input = inputBuffer[i]
                    if (!start && input == '/'.code.toByte()) {
                        start = true
                        bufferIndex = 0
                    }
                    msgBuffer[bufferIndex] = input
                    bufferIndex++
                    if (input == '!'.code.toByte() && start) {
                        end = true
                    }
                }
            }
            if (end && start) {
                break
            }
            try {
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        } while (!time.isEnd())
        if (time.isEnd()) {
            throw InterruptedIOException("Timeout")
        }
        val frame = ByteArray(bufferIndex)
        for (i in 0 until bufferIndex) {
            frame[i] = msgBuffer[i]
        }
        return frame
    }

    fun changeBaudrate(baudrate: Int) {
        try {
            logger.debug("Change baudrate to: {}.", baudrate)
            serialPort!!.baudRate = baudrate
        } catch (e: IOException) {
            logger.warn("Failed to change the baud rate.", e)
        }
    }

    fun close() {
        try {
            serialPort!!.close()
        } catch (e: IOException) {
            logger.warn("Failed to close the serial port properly.", e)
        }
        serialPort = null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(IecReceiver::class.java)
    }
}
