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
package org.openmuc.framework.driver.modbus.util

/**
 * Since Java supports only signed data types, this class provides methods to convert byte arrays in signed and unsigned
 * integers and vice versa (UNIT8, INT8, UNIT16, INT16, UINT32, INT32). These conversions are usually needed when
 * receiving messages from a hardware or sending messages to a hardware respectively.
 *
 */
object DatatypeConversion {
    private const val INT64_BYTE_LENGTH = 8
    private const val INT32_BYTE_LENGTH = 4
    private const val INT16_BYTE_LENGTH = 2
    private const val INT8_BYTE_LENGTH = 1

    /**
     * Reverses the byte order. If the given byte[] are in big endian order you will get little endian order and vice
     * versa
     *
     *
     * Example:<br></br>
     * input: bytes = {0x0A, 0x0B, 0x0C, 0x0D}<br></br>
     * output: bytes = {0x0D, 0x0C, 0x0B, 0x0A}<br></br>
     *
     * @param bytes
     * byte[] to reverse
     */
    @JvmStatic
    fun reverseByteOrder(bytes: ByteArray) {
        val indexLength = bytes.size - 1
        val halfLength = bytes.size / 2
        for (i in 0 until halfLength) {
            val index = indexLength - i
            val temp = bytes[i]
            bytes[i] = bytes[index]
            bytes[index] = temp
        }
    }

    /**
     * Reverses the byte order. <br></br>
     * Equal to reverseByteOrder Method but it doesn't change the input bytes. Method is working on a copy of input
     * bytes so it does
     *
     * @param bytes
     * byte[] to reverse
     * @return reversed bytes
     */
    @JvmStatic
    fun reverseByteOrderNewArray(bytes: ByteArray): ByteArray {
        val reversedBytes = bytes.clone()
        reverseByteOrder(reversedBytes)
        return reversedBytes
    }

    /**
     * Converts bytes to signed Int64
     *
     * @param bytes
     * 8 bytes where byte[0] is most significant byte and byte[7] is the least significant byte
     * @param endian
     * endian byte order
     * @return bytes as singed int 64
     */
    @JvmStatic
    fun bytes_To_SignedInt64(bytes: ByteArray, endian: EndianInput): Long {
        return if (bytes.size > 0 && bytes.size <= INT64_BYTE_LENGTH) {
            var returnValue: Long = 0
            val length = bytes.size - 1
            if (endian == EndianInput.BYTES_ARE_LITTLE_ENDIAN) {
                reverseByteOrder(bytes)
            }
            for (i in 0..length) {
                val shift = length - i shl 3
                returnValue = returnValue or ((bytes[i].toInt() and 0xff).toLong() shl shift)
            }
            returnValue
        } else {
            throw IllegalArgumentException(
                "Unable to convert bytes due to wrong number of bytes. Minimum 1 byte, maximum " + INT64_BYTE_LENGTH
                        + " bytes needed for conversion."
            )
        }
    }

    /**
     * Converts signed Int64 (long) to 8 bytes
     *
     * @param value
     * signed Int64
     * @param endian
     * endian byte order
     * @return 8 bytes where the most significant byte is byte[0] and the least significant byte is byte[7]
     */
    @JvmStatic
    fun singedInt64_To_Bytes(value: Long, endian: EndianOutput): ByteArray {
        val bytes = ByteArray(INT64_BYTE_LENGTH)
        bytes[0] = (value and -0x100000000000000L shr 56).toByte()
        bytes[1] = (value and 0x00FF000000000000L shr 48).toByte()
        bytes[2] = (value and 0x0000FF0000000000L shr 40).toByte()
        bytes[3] = (value and 0x000000FF00000000L shr 32).toByte()
        bytes[4] = (value and 0x00000000FF000000L shr 24).toByte()
        bytes[5] = (value and 0x0000000000FF0000L shr 16).toByte()
        bytes[6] = (value and 0x000000000000FF00L shr 8).toByte()
        bytes[7] = (value and 0x00000000000000FFL).toByte()
        if (endian == EndianOutput.BYTES_AS_LITTLE_ENDIAN) {
            reverseByteOrder(bytes)
        }
        return bytes
    }

    /**
     * Converts bytes to signed Int32
     *
     * @param bytes
     * 4 bytes where byte[0] is most significant byte and byte[3] is the least significant byte
     * @param endian
     * endian byte order
     * @return bytes as signed int 32
     */
    @JvmStatic
    fun bytes_To_SignedInt32(bytes: ByteArray, endian: EndianInput): Int {
        return if (bytes.size == INT32_BYTE_LENGTH) {
            var returnValue = 0
            val length = bytes.size - 1
            if (endian == EndianInput.BYTES_ARE_LITTLE_ENDIAN) {
                reverseByteOrder(bytes)
            }
            for (i in 0..length) {
                val shift = length - i shl 3
                returnValue = (returnValue.toLong() or ((bytes[i].toInt() and 0xff).toLong() shl shift)).toInt()
            }
            returnValue
        } else {
            throw IllegalArgumentException(
                "Unable to convert bytes due to wrong number of bytes. Minimum 1 byte, maximum " + INT32_BYTE_LENGTH
                        + " bytes needed for conversion."
            )
        }
    }

    /**
     * Converts bytes to unsigned Int32
     *
     * @param bytes
     * 4 bytes where byte[0] is most significant byte and byte[3] least significant byte
     * @param endian
     * endian byte order
     * @return unsigned Int32 as long
     */
    @JvmStatic
    fun bytes_To_UnsignedInt32(bytes: ByteArray, endian: EndianInput): Long {
        return if (bytes.size == INT32_BYTE_LENGTH) {
            if (endian == EndianInput.BYTES_ARE_LITTLE_ENDIAN) {
                reverseByteOrder(bytes)
            }
            val firstbyte = 0x000000FF and bytes[0].toInt()
            val secondByte = 0x000000FF and bytes[1].toInt()
            val thirdByte = 0x000000FF and bytes[2].toInt()
            val forthByte = 0x000000FF and bytes[3].toInt()
            (firstbyte shl 24 or (secondByte shl 16) or (thirdByte shl 8) or forthByte).toLong() and 0xFFFFFFFFL
        } else {
            throw IllegalArgumentException(
                "Unable to convert bytes due to wrong number of bytes. "
                        + INT32_BYTE_LENGTH + " bytes needed for conversion."
            )
        }
    }

    /**
     * Converts unsigned Int32 to 4 bytes
     *
     * @param value
     * unsigned Int32 (long)
     * @param endian
     * endian byte order
     * @return 4 bytes where the most significant byte is byte[0] and the least significant byte is byte[3]
     */
    @JvmStatic
    fun unsingedInt32_To_Bytes(value: Long, endian: EndianOutput): ByteArray {
        require(value >= 0) { "Invalid value: $value Only positive values are allowed!" }
        val bytes = ByteArray(INT32_BYTE_LENGTH)
        bytes[0] = (value and 0xFF000000L shr 24).toByte()
        bytes[1] = (value and 0x00FF0000L shr 16).toByte()
        bytes[2] = (value and 0x0000FF00L shr 8).toByte()
        bytes[3] = (value and 0x000000FFL).toByte()
        if (endian == EndianOutput.BYTES_AS_LITTLE_ENDIAN) {
            reverseByteOrder(bytes)
        }
        return bytes
    }

    /**
     * Converts signed Int32 to 4 bytes
     *
     * @param value
     * signed Int32
     * @param endian
     * endian byte order
     * @return 4 bytes where the most significant byte is byte[0] and the least significant byte is byte[3]
     */
    @JvmStatic
    fun singedInt32_To_Bytes(value: Int, endian: EndianOutput): ByteArray {
        val bytes = ByteArray(INT32_BYTE_LENGTH)
        bytes[0] = (value.toLong() and 0xFF000000L shr 24).toByte()
        bytes[1] = (value.toLong() and 0x00FF0000L shr 16).toByte()
        bytes[2] = (value.toLong() and 0x0000FF00L shr 8).toByte()
        bytes[3] = (value.toLong() and 0x000000FFL).toByte()
        if (endian == EndianOutput.BYTES_AS_LITTLE_ENDIAN) {
            reverseByteOrder(bytes)
        }
        return bytes
    }

    /**
     * Converts bytes to signed Int16
     *
     * @param bytes
     * 2 bytes where byte[0] is most significant byte and byte[1] is the least significant byte
     * @param endian
     * endian byte order
     * @return signed Int16
     */
    @JvmStatic
    fun bytes_To_SignedInt16(bytes: ByteArray, endian: EndianInput): Int {
        return if (bytes.size == INT16_BYTE_LENGTH) {
            var returnValue: Short = 0
            val length = bytes.size - 1
            if (endian == EndianInput.BYTES_ARE_LITTLE_ENDIAN) {
                reverseByteOrder(bytes)
            }
            for (i in 0..length) {
                val shift = length - i shl 3
                returnValue = (returnValue.toLong() or ((bytes[i].toInt() and 0xff).toLong() shl shift)).toShort()
            }
            returnValue.toInt()
        } else {
            throw IllegalArgumentException(
                "Unable to convert bytes due to wrong number of bytes. "
                        + INT16_BYTE_LENGTH + " bytes needed for conversion."
            )
        }
    }

    /**
     * Converts bytes to unsigned Int16
     *
     * @param bytes
     * 2 bytes where byte[0] is most significant byte and byte[1] least significant byte
     * @param endian
     * endian byte order
     * @return unsigned Int16
     */
    @JvmStatic
    fun bytes_To_UnsignedInt16(bytes: ByteArray, endian: EndianInput): Int {
        return if (bytes.size == INT16_BYTE_LENGTH) {
            if (endian == EndianInput.BYTES_ARE_LITTLE_ENDIAN) {
                reverseByteOrder(bytes)
            }
            val firstbyte = 0x000000FF and 0x00.toByte().toInt()
            val secondByte = 0x000000FF and 0x00.toByte().toInt()
            val thirdByte = 0x000000FF and bytes[0].toInt()
            val forthByte = 0x000000FF and bytes[1].toInt()
            firstbyte shl 24 or (secondByte shl 16) or (thirdByte shl 8) or forthByte and -0x1
        } else {
            throw IllegalArgumentException(
                "Unable to convert bytes due to wrong number of bytes. Minimum 1, maximum " + INT16_BYTE_LENGTH
                        + " bytes needed for conversion."
            )
        }
    }

    /**
     * Converts unsigned Int16 to 2 bytes
     *
     * @param value
     * unsigned Int16
     * @param endian
     * endian byte order
     * @return 2 bytes where the most significant byte is byte[0] and the least significant byte is byte[1]
     */
    @JvmStatic
    fun unsingedInt16_To_Bytes(value: Int, endian: EndianOutput): ByteArray {
        require(value >= 0) { "Invalid value: $value Only positive values are allowed!" }
        val bytes = ByteArray(INT16_BYTE_LENGTH)
        bytes[0] = (value and 0x0000FF00 shr 8).toByte()
        bytes[1] = (value and 0x000000FF).toByte()
        if (endian == EndianOutput.BYTES_AS_LITTLE_ENDIAN) {
            reverseByteOrder(bytes)
        }
        return bytes
    }

    /**
     * Converts signed Int16 to 2 bytes
     *
     * @param value
     * signed Int16
     * @param endian
     * endian byte order
     * @return 2 bytes where the most significant byte is byte[0] and the least significant byte is byte[1]
     */
    @JvmStatic
    fun singedInt16_To_Bytes(value: Int, endian: EndianOutput): ByteArray {
        val bytes = ByteArray(INT16_BYTE_LENGTH)
        bytes[0] = (value and 0x0000FF00 shr 8).toByte()
        bytes[1] = (value and 0x000000FF).toByte()
        if (endian == EndianOutput.BYTES_AS_LITTLE_ENDIAN) {
            reverseByteOrder(bytes)
        }
        return bytes
    }

    /**
     * Converts bytes to signed Int8
     *
     * @param bytes
     * 1 byte
     * @return signed Int8
     */
    @JvmStatic
    fun bytes_To_SignedInt8(bytes: ByteArray): Int {
        return if (bytes.size == INT8_BYTE_LENGTH) {
            var returnValue: Byte = 0
            val length = bytes.size - 1
            for (i in 0..length) {
                val shift = length - i shl 3
                returnValue = (returnValue.toLong() or ((bytes[i].toInt() and 0xff).toLong() shl shift)).toByte()
            }
            returnValue.toInt()
        } else {
            throw IllegalArgumentException(
                "Unable to convert bytes due to wrong number of bytes. "
                        + INT8_BYTE_LENGTH + " bytes needed for conversion."
            )
        }
    }

    /**
     * Converts the specified byte of an byte array to unsigned int
     *
     * @param data
     * byte array which contains
     * @param index
     * index of the byte which should be returned as unsigned int8. Index can be used for little and big
     * endian support.
     * @return bytes as unsigned int 8
     */
    fun bytes_To_UnsignedInt8(data: ByteArray, index: Int): Int {
        if (index < 0 || index >= data.size) {
            throw IndexOutOfBoundsException("Negative index. Index must be >= 0")
        }
        return bytes_To_UnsignedInt8(data[index])
    }

    /**
     * Converts a single byte to unsigned Int8
     *
     * @param singleByte
     * byte to convert
     *
     * @return unsigned Int8
     */
    fun bytes_To_UnsignedInt8(singleByte: Byte): Int {
        return 0x000000FF and singleByte.toInt()
    }

    /**
     * Converts unsigned Int8 to 1 byte
     *
     * @param value
     * unsigned Int8
     * @return 1 byte
     */
    @JvmStatic
    fun unsingedInt8_To_Bytes(value: Int): ByteArray {
        require(value >= 0) { "Invalid value: $value Only positive values are allowed!" }
        val bytes = ByteArray(INT8_BYTE_LENGTH)
        bytes[0] = (value and 0x000000FF).toByte()
        return bytes
    }

    /**
     * Converts signed Int8 to 1 bytes
     *
     * @param value
     * signed Int8
     * @return 1 byte
     */
    @JvmStatic
    fun singedInt8_To_Bytes(value: Int): ByteArray {
        val bytes = ByteArray(INT8_BYTE_LENGTH)
        bytes[0] = (value and 0x000000FF).toByte()
        return bytes
    }

    enum class ByteSelect {
        LOW_BYTE, HIGH_BYTE
    }

    enum class EndianInput {
        BYTES_ARE_LITTLE_ENDIAN, BYTES_ARE_BIG_ENDIAN
    }

    enum class EndianOutput {
        BYTES_AS_LITTLE_ENDIAN, BYTES_AS_BIG_ENDIAN
    }
}
