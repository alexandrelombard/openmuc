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
package org.openmuc.framework.lib.mqtt

import org.openmuc.framework.lib.filePersistence.FilePersistence
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

/**
 * Buffer handler with RAM buffer and managed [FilePersistence]
 */
class MqttBufferHandler(maxBufferSizeKb: Long, maxFileCount: Int, maxFileSizeKb: Long, persistenceDirectory: String?) {
    private val buffer: Queue<MessageTuple> = LinkedList()
    private val maxBufferSizeBytes: Long
    private var currentBufferSize = 0L
    private val maxFileCount: Int
    private var filePersistence: FilePersistence? = null

    /**
     * Initializes buffers with specified properties.
     *
     * <br></br>
     * <br></br>
     * <table border="1" style="text-align: center">
     * <caption>Behaviour summary</caption>
     * <tr>
     * <th>maxBufferSizeKb</th>
     * <th>maxFileCount</th>
     * <th>maxFileSizeKb</th>
     * <th>RAM buffer</th>
     * <th>File buffer</th>
    </tr> *
     * <tr>
     * <td>0</td>
     * <td>0</td>
     * <td>0</td>
     * <td>Disabled</td>
     * <td>Disabled</td>
    </tr> *
     * <tr>
     * <td>0</td>
     * <td>0</td>
     * <td>&#62;0</td>
     * <td>Disabled</td>
     * <td>Disabled</td>
    </tr> *
     * <tr>
     * <td>0</td>
     * <td>&#62;0</td>
     * <td>0</td>
     * <td>Disabled</td>
     * <td>Disabled</td>
    </tr> *
     * <tr>
     * <td>0</td>
     * <td>&#62;0</td>
     * <td>&#62;0</td>
     * <td>Disabled</td>
     * <td>Enabled</td>
    </tr> *
     * <tr>
     * <td>&#62;0</td>
     * <td>0</td>
     * <td>0</td>
     * <td>Enabled</td>
     * <td>Disabled</td>
    </tr> *
     * <tr>
     * <td>&#62;0</td>
     * <td>0</td>
     * <td>&#62;0</td>
     * <td>Enabled</td>
     * <td>Disabled</td>
    </tr> *
     * <tr>
     * <td>&#62;0</td>
     * <td>&#62;0</td>
     * <td>0</td>
     * <td>Enabled</td>
     * <td>Disabled</td>
    </tr> *
     * <tr>
     * <td>&#62;0</td>
     * <td>&#62;0</td>
     * <td>&#62;0</td>
     * <td>Enabled</td>
     * <td>Enabled</td>
    </tr> *
    </table> *
     *
     * @param maxBufferSizeKb
     * maximum RAM buffer size in KiB
     * @param maxFileCount
     * maximum file count used per buffer by [FilePersistence]
     * @param maxFileSizeKb
     * maximum file size used per file by [FilePersistence]
     * @param persistenceDirectory
     * directory in which [FilePersistence] stores buffers
     */
    init {
        maxBufferSizeBytes = maxBufferSizeKb * 1024
        this.maxFileCount = maxFileCount
        filePersistence = if (isFileBufferEnabled) {
            FilePersistence(persistenceDirectory, maxFileCount, maxFileSizeKb)
        } else {
            null
        }
    }

    private val isFileBufferEnabled: Boolean
        private get() = maxFileCount > 0 && maxBufferSizeBytes > 0

    fun add(topic: String?, message: ByteArray?) {
        if (isBufferTooFull(message)) {
            handleFull(topic, message)
        } else {
            synchronized(buffer) {
                buffer.add(MessageTuple(topic, message))
                currentBufferSize += message!!.size.toLong()
            }
            if (logger.isTraceEnabled) {
                logger.trace(
                    "maxBufferSize = {}, currentBufferSize = {}, messageSize = {}", maxBufferSizeBytes,
                    currentBufferSize, message!!.size
                )
            }
        }
    }

    private fun isBufferTooFull(message: ByteArray?): Boolean {
        return currentBufferSize + message!!.size > maxBufferSizeBytes
    }

    private fun handleFull(topic: String?, message: ByteArray?) {
        if (isFileBufferEnabled) {
            addToFilePersistence()
            add(topic, message)
        } else if (message!!.size <= maxBufferSizeBytes) {
            removeNextMessage()
            add(topic, message)
        }
    }

    private fun addToFilePersistence() {
        logger.debug("move buffered messages from RAM to file")
        while (!buffer.isEmpty()) {
            val messageTuple = removeNextMessage()
            writeBufferToFile(messageTuple)
        }
        currentBufferSize = 0
    }

    private fun writeBufferToFile(messageTuple: MessageTuple) {
        try {
            synchronized(filePersistence!!) {
                filePersistence!!.writeBufferToFile(
                    messageTuple.topic!!,
                    messageTuple.message!!
                )
            }
        } catch (e: IOException) {
            logger.error(e.message)
        }
    }

    val isEmpty: Boolean
        get() = buffer.isEmpty()

    fun removeNextMessage(): MessageTuple {
        var removedMessage: MessageTuple
        synchronized(buffer) {
            removedMessage = buffer.remove()
            currentBufferSize -= removedMessage.message!!.size.toLong()
        }
        return removedMessage
    }

    val buffers: Array<String>
        get() {
            val buffers: Array<String>
            buffers = if (isFileBufferEnabled) {
                filePersistence!!.buffers
            } else {
                arrayOf()
            }
            return buffers
        }

    fun getMessageIterator(buffer: String?): Iterator<MessageTuple> {
        return MqttBufferMessageIterator(buffer, filePersistence)
    }

    fun persist() {
        if (isFileBufferEnabled) {
            try {
                filePersistence!!.restructure()
                addToFilePersistence()
            } catch (e: IOException) {
                logger.error("Buffer file restructuring error: {}", e.message)
                e.printStackTrace()
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttBufferHandler::class.java)
    }
}
