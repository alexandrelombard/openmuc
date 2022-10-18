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

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.nio.file.FileSystems

internal class FilePersistenceTest {
    @AfterEach
    fun cleanUp() {
        deleteDirectory(FileSystems.getDefault().getPath(DIRECTORY).toFile())
    }

    private fun deleteDirectory(directory: File) {
        if (!directory.exists()) {
            return
        }
        for (child in directory.listFiles()) {
            if (child.isDirectory) {
                deleteDirectory(child)
            } else {
                child.delete()
            }
        }
        directory.delete()
    }

    private val filePersistence: FilePersistence
        private get() = FilePersistence(DIRECTORY, 2, 1)

    @Throws(IOException::class)
    private fun write512Byte(filePersistence: FilePersistence, buffer: String) {
        filePersistence.writeBufferToFile(buffer, LOREM_IPSUM_1_KB.substring(513).toByteArray())
    }

    @Throws(IOException::class)
    private fun write512ByteUnique(filePersistence: FilePersistence, buffer: String, id: Int) {
        var message = LOREM_IPSUM_1_KB.substring(514)
        message += id
        filePersistence.writeBufferToFile(buffer, message.toByteArray())
    }

    @Throws(IOException::class)
    private fun write1KB(filePersistence: FilePersistence, buffer: String) {
        filePersistence.writeBufferToFile(buffer, LOREM_IPSUM_1_KB.substring(1).toByteArray())
    }

    @Test
    fun invalidMaxFileCount() {
        val e: Exception = Assertions.assertThrows(
            IllegalArgumentException::class.java
        ) { FilePersistence(DIRECTORY, 0, 1) }
        Assertions.assertEquals("maxFileSize is 0", e.message)
    }

    @Test
    fun writeWithTooBigPayload() {
        val filePersistence = filePersistence
        // maxFileSize is 1024 Bytes, payload + newline char = 1025 Bytes
        Assertions.assertThrows(
            IOException::class.java
        ) { filePersistence.writeBufferToFile("test", LOREM_IPSUM_1_KB.toByteArray()) }
    }

    @Test
    @Throws(IOException::class)
    fun registerBuffer() {
        val filePersistence = filePersistence
        write1KB(filePersistence, "test")
        Assertions.assertEquals("test", filePersistence.buffers[0])
        write1KB(filePersistence, "test2")
        Assertions.assertEquals("test", filePersistence.buffers[0])
        Assertions.assertEquals("test2", filePersistence.buffers[1])
    }

    @Test
    @Throws(IOException::class)
    fun writeBufferToFile() {
        val filePersistence = filePersistence
        val buffer = "test"
        val file1 = FileSystems.getDefault().getPath(DIRECTORY, buffer, "buffer.0.log").toFile()
        val file2 = FileSystems.getDefault().getPath(DIRECTORY, buffer, "buffer.1.log").toFile()
        val file3 = FileSystems.getDefault().getPath(DIRECTORY, buffer, "buffer.2.log").toFile()
        write512Byte(filePersistence, buffer) // 512 B
        Assertions.assertTrue(file1.exists() && !file2.exists() && !file3.exists())
        write512Byte(filePersistence, buffer) // 512 B + 512 B = 1024 B
        // File not full
        Assertions.assertTrue(file1.exists() && !file2.exists() && !file3.exists())
        write512Byte(filePersistence, buffer) // 1024 B + 512 B > 1024 B -> new file 512 B
        Assertions.assertTrue(file1.exists() && file2.exists() && !file3.exists())
        // maxFileCount = 2 recognized -> no new file (rotation)
        write1KB(filePersistence, buffer) // 512 B + 1024 B > 1024 B -> override file
        Assertions.assertTrue(file1.exists() && file2.exists() && !file3.exists())
    }

    @Test
    @Throws(IOException::class)
    fun writeRotationTwoFiles() {
        val filePersistence = filePersistence
        val buffer = "test"
        write512Byte(filePersistence, buffer)
        write1KB(filePersistence, buffer)

        // Newline is not part of message so length is 1 Byte less
        Assertions.assertEquals(511, filePersistence.getMessage(buffer).size)
        Assertions.assertEquals(1023, filePersistence.getMessage(buffer).size)
        // buffer empty
        Assertions.assertFalse(filePersistence.fileExistsFor(buffer))
        write1KB(filePersistence, buffer) // new file 1024 B
        write512ByteUnique(filePersistence, buffer, 1) // new file 512 B
        write512ByteUnique(filePersistence, buffer, 2) // 512 B + 512 B = 1024 B
        write512ByteUnique(filePersistence, buffer, 3) // > 1024 B message is overriden
        write512ByteUnique(filePersistence, buffer, 4) // 1024 B
        write512ByteUnique(filePersistence, buffer, 5) // > 1024 B message is overriden
        Assertions.assertEquals('3'.code, filePersistence.getMessage(buffer)[510].toInt())
        Assertions.assertEquals('4'.code, filePersistence.getMessage(buffer)[510].toInt())
        Assertions.assertEquals('5'.code, filePersistence.getMessage(buffer)[510].toInt())
        Assertions.assertFalse(filePersistence.fileExistsFor(buffer))
    }

    @Test
    @Throws(IOException::class)
    fun writeRotationThreeFiles() {
        val filePersistence = FilePersistence(DIRECTORY, 3, 1)
        val buffer = "test"
        write512Byte(filePersistence, buffer)
        write1KB(filePersistence, buffer)

        // Newline is not part of message so length is 1 Byte less
        Assertions.assertEquals(511, filePersistence.getMessage(buffer).size)
        Assertions.assertEquals(1023, filePersistence.getMessage(buffer).size)
        // buffer empty
        Assertions.assertFalse(filePersistence.fileExistsFor(buffer))
        write1KB(filePersistence, buffer) // new file 1024 B
        write512ByteUnique(filePersistence, buffer, 1) // new file 512 B
        write512ByteUnique(filePersistence, buffer, 2) // 512 B + 512 B = 1024 B
        write512ByteUnique(filePersistence, buffer, 3) // > 1024 B message is new file
        write512ByteUnique(filePersistence, buffer, 4) // 1024 B
        write512ByteUnique(filePersistence, buffer, 5) // > 1024 B message is overriden
        write512ByteUnique(filePersistence, buffer, 6) // 1024 B
        write512ByteUnique(filePersistence, buffer, 7) // > 1024 B message is overriden
        Assertions.assertEquals('3'.code, filePersistence.getMessage(buffer)[510].toInt())
        Assertions.assertEquals('4'.code, filePersistence.getMessage(buffer)[510].toInt())
        Assertions.assertEquals('5'.code, filePersistence.getMessage(buffer)[510].toInt())
        Assertions.assertEquals('6'.code, filePersistence.getMessage(buffer)[510].toInt())
        Assertions.assertEquals('7'.code, filePersistence.getMessage(buffer)[510].toInt())
        Assertions.assertFalse(filePersistence.fileExistsFor(buffer))
    }

    companion object {
        private const val DIRECTORY = "/tmp/openmuc/filepersistence"
        private const val LOREM_IPSUM_1_KB =
            "Imperdiet Volutpat Sit Himenaeos Nunc Potenti Pharetra Porta Bibendum Sem Sociosqu Maecenas Vitae Metus Varius Ut Vulputate Eleifend Netus Scelerisque Ac Lobortis Mi Iaculis In Praesent Rutrum Tristique Aenean Quam Curabitur Consectetur Mattis Suscipit Ac Adipiscing Egestas Sagittis Viverra Nullam Nisi Gravida Leo Himenaeos At Quam In Gravida Rhoncus Neque Consequat Augue Faucibus Nostra In Ullamcorper Donec Nunc Conubia Hendrerit Consectetur Massa Lacinia Tempus Massa Fringilla Ut Est Condimentum Cubilia Fermentum Tincidunt Ac Eu Purus Bibendum Urna Elit Orci Phasellus Viverra Egestas Bibendum Maecenas Mauris Ultrices Elementum Quam Facilisis Mi Mauris Auctor Nibh Cubilia Erat Massa Non Leo Sodales Fames Consectetur Lorem Eros Dui Per Augue Urna Mollis Fames Nisl Sagittis Platea Sem Eget Sagittis Nulla Eget Convallis Venenatis Faucibus Enim Proin Bibendum Egestas Imperdiet Semper Id Molestie Leo Felis Metus Platea Sapien Elementum Risus Curabitur Risus Mi Morbi Pellentesque Nostra Condimentum Nisl In Suscipi"
    }
}
