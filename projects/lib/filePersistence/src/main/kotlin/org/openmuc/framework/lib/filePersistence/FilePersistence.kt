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
package org.openmuc.framework.lib.filePersistence

import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.*

/**
 * Provides configurable RAM friendly file persistence functionality
 */
class FilePersistence(directory: String?, maxFileCount: Int, maxFileSizeKb: Long) {
    private val DIRECTORY: Path
    private var maxFileCount = 0
    private val MAX_FILE_SIZE_BYTES: Long
    private val nextFile: MutableMap<String, Int> = HashMap()
    private val readBytes: MutableMap<String, Long> = HashMap()

    /**
     * @param directory
     * the directory in which files are stored
     * @param maxFileCount
     * the maximum number of files created. Must be greater than 0
     * @param maxFileSizeKb
     * the maximum file size in kB when fileSize is reached a new file is created or the oldest overwritten
     */
    init {
        DIRECTORY = FileSystems.getDefault().getPath(directory)
        // convert to byte since bytes are used internally to compare with payload
        MAX_FILE_SIZE_BYTES = maxFileSizeKb * 1024
        setMaxFileCount(maxFileCount)
        createDirectory()
    }

    private fun setMaxFileCount(maxFileCount: Int) {
        this.maxFileCount = maxFileCount
        require(this.maxFileCount > 0) { "maxFileSize is 0" }
    }

    private fun createDirectory() {
        if (!DIRECTORY.toFile().exists()) {
            if (!DIRECTORY.toFile().mkdirs()) {
                logger.error("The directory {} could not be created", DIRECTORY)
            }
        }
    }

    /**
     * @param buffer
     * directory without file name. Filename is automatically added by FilePersistence
     * @param payload
     * the data to be written. needs to be smaller than MAX_FILE_SIZE
     * @throws IOException
     * when writing fails
     */
    @Throws(IOException::class)
    fun writeBufferToFile(buffer: String, payload: ByteArray) {

        // buffer = topic for mqtt e.g. topic/test/openmuc
        checkPayLoadSize(payload.size)
        registerBuffer(buffer)
        val filePath = Paths.get(DIRECTORY.toString(), buffer, DEFAULT_FILENAME)
        val file = createFileIfNotExist(filePath)
        if (isFileFull(file.length(), payload.size)) {
            handleFullFile(buffer, payload, file)
        } else {
            appendToFile(file, payload)
        }
    }

    @Throws(IOException::class)
    private fun registerBuffer(buffer: String) {
        if (!BUFFERS.contains(buffer)) {
            BUFFERS.add(buffer)
            writeBufferList()
        }
    }

    @Throws(IOException::class)
    private fun removeBufferIfEmpty(buffer: String) {
        if (!fileExistsFor(buffer)) {
            val buffersChanged = BUFFERS.remove(buffer)
            if (buffersChanged) {
                writeBufferList()
            }
        }
    }

    @Throws(IOException::class)
    private fun writeBufferList() {
        val buffers = Paths.get(DIRECTORY.toString(), "buffer_list").toFile()
        val writer = FileWriter(buffers)
        for (registeredBuffer in BUFFERS) {
            writer.write(
                """
    $registeredBuffer
    
    """.trimIndent()
            )
        }
        writer.close()
    }

    val buffers: Array<String>
        get() {
            if (BUFFERS.isEmpty()) {
                val buffers = Paths.get(DIRECTORY.toString(), "buffer_list")
                if (buffers.toFile().exists()) {
                    try {
                        BUFFERS.addAll(Files.readAllLines(buffers))
                    } catch (e: IOException) {
                        logger.error("Could not read buffer_list. Message: {}", e.message)
                    }
                }
            }
            return BUFFERS.toTypedArray()
        }

    @Throws(IOException::class)
    private fun createFileIfNotExist(filePath: Path): File {
        val file = filePath.toFile()
        if (!file.exists()) {
            logger.info("create new file: {}", file.absolutePath)
            val storagePath = Paths.get(file.absolutePath)
            Files.createDirectories(storagePath.parent)
            Files.createFile(storagePath)
        }
        return file
    }

    @Throws(IOException::class)
    private fun appendToFile(file: File, payload: ByteArray) {
        val fileStream = FileOutputStream(file, true)
        fileStream.write(payload)
        fileStream.write("\n".toByteArray())
        fileStream.close()
    }

    @Throws(IOException::class)
    private fun handleFullFile(filePath: String, payload: ByteArray, file: File) {
        if (maxFileCount > 1) {
            handleMultipleFiles(filePath, payload, file)
        } else {
            handleSingleFile(filePath, payload, file)
        }
    }

    private fun handleSingleFile(filePath: String, payload: ByteArray, file: File) {
        throw UnsupportedOperationException("right now only maxFileCount >= 2 supported")
    }

    @Throws(IOException::class)
    private fun handleMultipleFiles(buffer: String, payload: ByteArray, file: File) {
        var nextFile = nextFile.getOrDefault(buffer, 1)
        val newFileName = DEFAULT_FILE_PREFIX + '.' + nextFile + '.' + DEFAULT_FILE_SUFFIX
        if (++nextFile == maxFileCount) {
            nextFile = 1
        }
        this.nextFile[buffer] = nextFile
        val path = Paths.get(DIRECTORY.toString(), buffer, DEFAULT_FILENAME)
        val newPath = Paths.get(DIRECTORY.toString(), buffer, newFileName)
        Files.move(path, newPath, StandardCopyOption.REPLACE_EXISTING)
        logger.info("move file from: {} to {}", path, newPath)
        Files.createFile(path)
        appendToFile(path.toFile(), payload)
    }

    private fun isFileFull(fileLength: Long, payloadLength: Int): Boolean {
        return fileLength + payloadLength + 1 > MAX_FILE_SIZE_BYTES
    }

    @Throws(IOException::class)
    private fun checkPayLoadSize(payloadLength: Int) {
        if (payloadLength >= MAX_FILE_SIZE_BYTES) {
            throw IOException("Payload is bigger than maxFileSize. Current maxFileSize is ${MAX_FILE_SIZE_BYTES / 1024}kB")
        }
    }

    /**
     * @param buffer
     * the name of the buffer (e.g. the topic or queue name)
     * @return if a file buffer exists
     */
    fun fileExistsFor(buffer: String?): Boolean {
        val fileName = DEFAULT_FILE_PREFIX + ".0." + DEFAULT_FILE_SUFFIX
        return Paths.get(DIRECTORY.toString(), buffer, fileName).toFile().exists()
    }

    fun getMessage(buffer: String): ByteArray {
        val filePath = getOldestFilePath(buffer)
        val position = getFilePosition(filePath.toString())
        var message = ""
        try {
            message = readLine(buffer, filePath.toFile(), position)
        } catch (e: IOException) {
            logger.error("An error occurred while reading the buffer {}. Error message: {}", buffer, e.message)
        }
        return message.toByteArray()
    }

    @Throws(IOException::class)
    private fun readLine(buffer: String, file: File, position: Long): String {
        var position = position
        val fileStream = FileInputStream(file)
        val lineBuilder = StringBuilder()
        fileStream.skip(position)
        var nextChar = fileStream.read()
        position++
        while (nextChar != -1 && nextChar != '\n'.code) {
            lineBuilder.appendCodePoint(nextChar)
            nextChar = fileStream.read()
            position++
        }
        fileStream.close()
        setFilePosition(file.toString(), position)
        deleteIfEmpty(file, position)
        removeBufferIfEmpty(buffer)
        return lineBuilder.toString()
    }

    @Throws(IOException::class)
    private fun deleteIfEmpty(file: File, position: Long) {
        val fileStream = FileInputStream(file)
        fileStream.skip(position)
        val nextChar = fileStream.read()
        fileStream.close()
        if (nextChar == -1) {
            val deleted = file.delete()
            if (!deleted) {
                throw IOException("Empty file could not be deleted!")
            } else {
                setFilePosition(file.toString(), 0L)
            }
        }
    }

    private fun setFilePosition(filePathString: String, position: Long) {
        readBytes[filePathString] = position
    }

    private fun getFilePosition(filePathString: String): Long {
        var position = readBytes[filePathString]
        if (position == null) {
            position = 0L
        }
        return position
    }

    private fun getOldestFilePath(buffer: String): Path {
        val directoryPath = Paths.get(DIRECTORY.toString(), buffer)
        val bufferFiles = directoryPath.toFile().list { file: File?, s: String -> s.endsWith(".log") }
        var oldestFile = DEFAULT_FILENAME
        if (bufferFiles.size > 1) {
            oldestFile = findOldestFile(buffer)
        }
        return Paths.get(directoryPath.toString(), oldestFile)
    }

    private fun findOldestFile(buffer: String): String {
        var oldestFile = DEFAULT_FILENAME
        var nextFile = nextFile.getOrDefault(buffer, 1)
        for (i in 0 until maxFileCount) {
            val fileName = DEFAULT_FILE_PREFIX + '.' + nextFile + '.' + DEFAULT_FILE_SUFFIX
            if (Paths.get(DIRECTORY.toString(), buffer, fileName).toFile().exists()) {
                oldestFile = fileName
                break
            }
            if (++nextFile == maxFileCount) {
                nextFile = 1
            }
        }
        return oldestFile
    }

    @Throws(IOException::class)
    fun restructure() {
        for (buffer in buffers) {
            val bufferPath = getOldestFilePath(buffer)
            val position = getFilePosition(bufferPath.toString())
            if (position == 0L) {
                continue
            }
            var temp = bufferPath.parent
            temp = Paths.get(temp.toString(), "temp")
            try {
                Files.move(bufferPath, temp, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: DirectoryNotEmptyException) {
                logger.error("$bufferPath -> $temp")
            }
            Files.createFile(bufferPath)
            val inputStream = FileInputStream(temp.toFile())
            inputStream.skip(position)
            val outputStream = FileOutputStream(bufferPath.toFile(), true)
            var nextChar = inputStream.read()
            while (nextChar != -1) {
                outputStream.write(nextChar)
                nextChar = inputStream.read()
            }
            inputStream.close()
            outputStream.close()
            temp.toFile().delete()
            setFilePosition(bufferPath.toString(), 0L)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FilePersistence::class.java)
        private val BUFFERS: MutableList<String> = ArrayList()
        const val DEFAULT_FILENAME = "buffer.0.log"
        const val DEFAULT_FILE_PREFIX = "buffer"
        const val DEFAULT_FILE_SUFFIX = "log"
    }
}
