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
package org.openmuc.framework.lib.amqp

import org.openmuc.framework.lib.filePersistence.FilePersistence
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

class AmqpBufferHandler(maxBufferSize: Long, maxFileCount: Int, maxFileSize: Long, persistenceDir: String?) {
    private val buffer: Queue<AmqpMessageTuple> = LinkedList()
    private val maxBufferSizeBytes: Long
    private val maxFileCount: Int
    private var filePersistence: FilePersistence? = null
    private var currentBufferSize = 0L

    init {
        maxBufferSizeBytes = maxBufferSize * 1024
        this.maxFileCount = maxFileCount
        filePersistence = if (isFileBufferEnabled) {
            FilePersistence(persistenceDir, maxFileCount, maxFileSize)
        } else {
            null
        }
    }

    private val isFileBufferEnabled: Boolean
        get() = maxFileCount > 0 && maxBufferSizeBytes > 0

    fun add(routingKey: String, message: ByteArray) {
        if (isBufferTooFull(message)) {
            handleFull(routingKey, message)
        } else {
            synchronized(buffer) {
                buffer.add(AmqpMessageTuple(routingKey, message))
                currentBufferSize += message.size.toLong()
            }
            if (logger.isTraceEnabled) {
                logger.trace(
                    "maxBufferSize = {} B, currentBufferSize = {} B, messageSize = {} B", maxBufferSizeBytes,
                    currentBufferSize, message.size
                )
            }
        }
    }

    private fun isBufferTooFull(message: ByteArray): Boolean {
        return currentBufferSize + message.size > maxBufferSizeBytes
    }

    private fun handleFull(routingKey: String, message: ByteArray) {
        if (isFileBufferEnabled) {
            addToFilePersistence()
            add(routingKey, message)
        } else if (message.size <= maxBufferSizeBytes) {
            removeNextMessage()
            add(routingKey, message)
        }
    }

    fun removeNextMessage(): AmqpMessageTuple {
        var removedMessage: AmqpMessageTuple
        synchronized(buffer) {
            removedMessage = buffer.remove()
            currentBufferSize -= removedMessage.message.size.toLong()
        }
        return removedMessage
    }

    private fun addToFilePersistence() {
        logger.debug("moving buffered messages from RAM to file")
        while (!isEmpty) {
            val messageTuple = removeNextMessage()
            writeBufferToFile(messageTuple)
        }
        currentBufferSize = 0
    }

    private fun writeBufferToFile(messageTuple: AmqpMessageTuple) {
        try {
            synchronized(filePersistence!!) {
                filePersistence!!.writeBufferToFile(
                    messageTuple.routingKey,
                    messageTuple.message
                )
            }
        } catch (e: IOException) {
            logger.error(e.message)
        }
    }

    val isEmpty: Boolean
        get() = buffer.isEmpty()
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

    fun getMessageIterator(buffer: String?): Iterator<AmqpMessageTuple> {
        return AmqpBufferMessageIterator(buffer, filePersistence)
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
        private val logger = LoggerFactory.getLogger(AmqpBufferHandler::class.java)
    }
}
