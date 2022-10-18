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
package org.openmuc.framework.driver.modbus.util.test

import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.EndianInput
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.EndianOutput
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.bytes_To_SignedInt16
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.bytes_To_SignedInt32
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.bytes_To_SignedInt64
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.bytes_To_SignedInt8
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.bytes_To_UnsignedInt16
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.bytes_To_UnsignedInt32
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.reverseByteOrder
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.reverseByteOrderNewArray
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.singedInt16_To_Bytes
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.singedInt32_To_Bytes
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.singedInt64_To_Bytes
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.singedInt8_To_Bytes
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.unsingedInt16_To_Bytes
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.unsingedInt32_To_Bytes
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.unsingedInt8_To_Bytes
import java.util.*

//import javax.xml.bind.DatatypeConverter;
/**
 * This test case test the datatype conversion. It covers tests from datatype to byte[] and vice versa
 *
 * TODO: Currently not all conversions are tested
 */
class DatatypeConversionTest {
    // BE = BigEndian;
    // LE = LittleEndian
    var bytes8_Value_MaxPositive_BE = Hex.decodeHex("7FFFFFFFFFFFFFFF")
    var bytes8_Value_2_BE = Hex.decodeHex("0000000000000002")
    var bytes8_Value_1_BE = Hex.decodeHex("0000000000000001")
    var bytes8_Value_0_BE = Hex.decodeHex("0000000000000000")
    var bytes8_Value_Minus_1_BE = Hex.decodeHex("FFFFFFFFFFFFFFFF")
    var bytes8_Value_Minus_2_BE = Hex.decodeHex("FFFFFFFFFFFFFFFE")
    var bytes8_Value_MaxNegative_BE = Hex.decodeHex("8000000000000000")
    var bytes4_Value_MaxPositive_BE = Hex.decodeHex("7FFFFFFF")
    var bytes4_Value_2_BE = Hex.decodeHex("00000002")
    var bytes4_Value_1_BE = Hex.decodeHex("00000001")
    var bytes4_Value_0_BE = Hex.decodeHex("00000000")
    var bytes4_Value_Minus_1_BE = Hex.decodeHex("FFFFFFFF")
    var bytes4_Value_Minus_2_BE = Hex.decodeHex("FFFFFFFE")
    var bytes4_Value_MaxNegative_BE = Hex.decodeHex("80000000")
    var bytes2_Value_MaxPositive_BE = Hex.decodeHex("7FFF")
    var bytes2_Value_2_BE = Hex.decodeHex("0002")
    var bytes2_Value_1_BE = Hex.decodeHex("0001")
    var bytes2_Value_0_BE = Hex.decodeHex("0000")
    var bytes2_Value_Minus_1_BE = Hex.decodeHex("FFFF")
    var bytes2_Value_Minus_2_BE = Hex.decodeHex("FFFE")
    var bytes2_Value_MaxNegative_BE = Hex.decodeHex("8000")
    var bytes8_Value_MaxPositive_LE = reverseByteOrderNewArray(bytes8_Value_MaxPositive_BE)
    var bytes8_Value_2_LE = reverseByteOrderNewArray(bytes8_Value_2_BE)
    var bytes8_Value_1_LE = reverseByteOrderNewArray(bytes8_Value_1_BE)
    var bytes8_Value_0_LE = reverseByteOrderNewArray(bytes8_Value_0_BE)
    var bytes8_Value_Minus_1_LE = reverseByteOrderNewArray(bytes8_Value_Minus_1_BE)
    var bytes8_Value_Minus_2_LE = reverseByteOrderNewArray(bytes8_Value_Minus_2_BE)
    var bytes8_Value_MaxNegative_LE = reverseByteOrderNewArray(bytes8_Value_MaxNegative_BE)
    var bytes4_Value_MaxPositive_LE = reverseByteOrderNewArray(bytes4_Value_MaxPositive_BE)
    var bytes4_Value_2_LE = reverseByteOrderNewArray(bytes4_Value_2_BE)
    var bytes4_Value_1_LE = reverseByteOrderNewArray(bytes4_Value_1_BE)
    var bytes4_Value_0_LE = reverseByteOrderNewArray(bytes4_Value_0_BE)
    var bytes4_Value_Minus_1_LE = reverseByteOrderNewArray(bytes4_Value_Minus_1_BE)
    var bytes4_Value_Minus_2_LE = reverseByteOrderNewArray(bytes4_Value_Minus_2_BE)
    var bytes4_Value_MaxNegative_LE = reverseByteOrderNewArray(bytes4_Value_MaxNegative_BE)
    var bytes2_Value_MaxPositive_LE = reverseByteOrderNewArray(bytes2_Value_MaxPositive_BE)
    var bytes2_Value_2_LE = reverseByteOrderNewArray(bytes2_Value_2_BE)
    var bytes2_Value_1_LE = reverseByteOrderNewArray(bytes2_Value_1_BE)
    var bytes2_Value_0_LE = reverseByteOrderNewArray(bytes2_Value_0_BE)
    var bytes2_Value_Minus_1_LE = reverseByteOrderNewArray(bytes2_Value_Minus_1_BE)
    var bytes2_Value_Minus_2_LE = reverseByteOrderNewArray(bytes2_Value_Minus_2_BE)
    var bytes2_Value_MaxNegative_LE = reverseByteOrderNewArray(bytes2_Value_MaxNegative_BE)
    var bytes1_Value_MaxPositive = byteArrayOf(0x7F.toByte())
    var bytes1_Value_2 = byteArrayOf(0x02.toByte())
    var bytes1_Value_1 = byteArrayOf(0x01.toByte())
    var bytes1_Value_0 = byteArrayOf(0x00.toByte())
    var bytes1_Value_Minus_1 = byteArrayOf(0xFF.toByte())
    var bytes1_Value_Minus_2 = byteArrayOf(0xFE.toByte())
    var bytes1_Value_MaxNegative = byteArrayOf(0x80.toByte())
    val MAX_UNSIGNED_INT32 = 4294967295L
    val MAX_UNSIGNED_INT16 = 65535
    val MAX_SIGNED_INT16 = 32767
    val MIN_SIGNED_INT16 = -32768
    val MAX_SIGNED_INT8 = 127
    val MIN_SIGNED_INT8 = -128
    @Test
    @Throws(DecoderException::class)
    fun test_reverseByteOrder() {
        val array1 = Hex.decodeHex("00000002")
        val array1Reverse = Hex.decodeHex("02000000")
        reverseByteOrder(array1)
        Assertions.assertTrue(Arrays.equals(array1, array1Reverse))
        val array2 = Hex.decodeHex("00000002")
        val array2Reverse = Hex.decodeHex("02000000")
        Assertions.assertTrue(Arrays.equals(array2Reverse, reverseByteOrderNewArray(array2)))
    }

    @Test
    fun test_bytes_To_SignedInt64_BigEndian() {
        var signedInt64: Long
        signedInt64 = bytes_To_SignedInt64(
            bytes8_Value_MaxPositive_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(Long.MAX_VALUE, signedInt64)
        signedInt64 = bytes_To_SignedInt64(bytes8_Value_2_BE, EndianInput.BYTES_ARE_BIG_ENDIAN)
        Assertions.assertEquals(2, signedInt64)
        signedInt64 = bytes_To_SignedInt64(bytes8_Value_1_BE, EndianInput.BYTES_ARE_BIG_ENDIAN)
        Assertions.assertEquals(1, signedInt64)
        signedInt64 = bytes_To_SignedInt64(bytes8_Value_0_BE, EndianInput.BYTES_ARE_BIG_ENDIAN)
        Assertions.assertEquals(0, signedInt64)
        signedInt64 = bytes_To_SignedInt64(
            bytes8_Value_Minus_1_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(-1, signedInt64)
        signedInt64 = bytes_To_SignedInt64(
            bytes8_Value_Minus_2_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(-2, signedInt64)
        signedInt64 = bytes_To_SignedInt64(
            bytes8_Value_MaxNegative_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(Long.MIN_VALUE, signedInt64)
    }

    fun test_bytes_To_SignedInt64_LittleEndian() {
        var signedInt64: Long
        signedInt64 = bytes_To_SignedInt64(
            bytes8_Value_MaxPositive_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(Long.MAX_VALUE, signedInt64)
        signedInt64 = bytes_To_SignedInt64(bytes8_Value_2_LE, EndianInput.BYTES_ARE_LITTLE_ENDIAN)
        Assertions.assertEquals(2, signedInt64)
        signedInt64 = bytes_To_SignedInt64(bytes8_Value_1_LE, EndianInput.BYTES_ARE_LITTLE_ENDIAN)
        Assertions.assertEquals(1, signedInt64)
        signedInt64 = bytes_To_SignedInt64(bytes8_Value_0_LE, EndianInput.BYTES_ARE_LITTLE_ENDIAN)
        Assertions.assertEquals(0, signedInt64)
        signedInt64 = bytes_To_SignedInt64(
            bytes8_Value_Minus_1_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(-1, signedInt64)
        signedInt64 = bytes_To_SignedInt64(
            bytes8_Value_Minus_2_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(-2, signedInt64)
        signedInt64 = bytes_To_SignedInt64(
            bytes8_Value_MaxNegative_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(Long.MIN_VALUE, signedInt64)
    }

    @Test
    fun test_signedInt64_To_Bytes_BigEndian() {
        var bytes: ByteArray
        bytes = singedInt64_To_Bytes(Long.MAX_VALUE, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_MaxPositive_BE))
        bytes = singedInt64_To_Bytes(2L, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_2_BE))
        bytes = singedInt64_To_Bytes(1L, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_1_BE))
        bytes = singedInt64_To_Bytes(0L, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_0_BE))
        bytes = singedInt64_To_Bytes(-1L, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_Minus_1_BE))
        bytes = singedInt64_To_Bytes(-2L, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_Minus_2_BE))
        bytes = singedInt64_To_Bytes(Long.MIN_VALUE, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_MaxNegative_BE))
    }

    @Test
    fun test_signedInt64_To_Bytes_LittleEndian() {
        var bytes: ByteArray
        bytes = singedInt64_To_Bytes(Long.MAX_VALUE, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_MaxPositive_LE))
        bytes = singedInt64_To_Bytes(2L, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_2_LE))
        bytes = singedInt64_To_Bytes(1L, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_1_LE))
        bytes = singedInt64_To_Bytes(0L, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_0_LE))
        bytes = singedInt64_To_Bytes(-1L, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_Minus_1_LE))
        bytes = singedInt64_To_Bytes(-2L, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_Minus_2_LE))
        bytes = singedInt64_To_Bytes(Long.MIN_VALUE, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes8_Value_MaxNegative_LE))
    }

    @Test
    fun test_bytes_To_SignedInt32_BigEndian() {
        var signedInt32: Int
        signedInt32 = bytes_To_SignedInt32(
            bytes4_Value_MaxPositive_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(Int.MAX_VALUE, signedInt32)
        signedInt32 = bytes_To_SignedInt32(bytes4_Value_2_BE, EndianInput.BYTES_ARE_BIG_ENDIAN)
        Assertions.assertEquals(2, signedInt32)
        signedInt32 = bytes_To_SignedInt32(bytes4_Value_1_BE, EndianInput.BYTES_ARE_BIG_ENDIAN)
        Assertions.assertEquals(1, signedInt32)
        signedInt32 = bytes_To_SignedInt32(bytes4_Value_0_BE, EndianInput.BYTES_ARE_BIG_ENDIAN)
        Assertions.assertEquals(0, signedInt32)
        signedInt32 = bytes_To_SignedInt32(
            bytes4_Value_Minus_1_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(-1, signedInt32)
        signedInt32 = bytes_To_SignedInt32(
            bytes4_Value_Minus_2_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(-2, signedInt32)
        signedInt32 = bytes_To_SignedInt32(
            bytes4_Value_MaxNegative_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(Int.MIN_VALUE, signedInt32)
    }

    @Test
    fun test_bytes_To_SignedInt32_LittelEndian() {
        var signedInt32: Int
        signedInt32 = bytes_To_SignedInt32(
            bytes4_Value_MaxPositive_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(Int.MAX_VALUE, signedInt32)
        signedInt32 = bytes_To_SignedInt32(bytes4_Value_2_LE, EndianInput.BYTES_ARE_LITTLE_ENDIAN)
        Assertions.assertEquals(2, signedInt32)
        signedInt32 = bytes_To_SignedInt32(bytes4_Value_1_LE, EndianInput.BYTES_ARE_LITTLE_ENDIAN)
        Assertions.assertEquals(1, signedInt32)
        signedInt32 = bytes_To_SignedInt32(bytes4_Value_0_LE, EndianInput.BYTES_ARE_LITTLE_ENDIAN)
        Assertions.assertEquals(0, signedInt32)
        signedInt32 = bytes_To_SignedInt32(
            bytes4_Value_Minus_1_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(-1, signedInt32)
        signedInt32 = bytes_To_SignedInt32(
            bytes4_Value_Minus_2_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(-2, signedInt32)
        signedInt32 = bytes_To_SignedInt32(
            bytes4_Value_MaxNegative_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(Int.MIN_VALUE, signedInt32)
    }

    @Test
    fun test_bytes_To_UnignedInt32_BigEndian() {
        var unsignedInt32: Long
        unsignedInt32 = bytes_To_UnsignedInt32(
            bytes4_Value_MaxPositive_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(java.lang.Long.decode("0x7FFFFFFF") as Long, unsignedInt32)
        unsignedInt32 = bytes_To_UnsignedInt32(bytes4_Value_2_BE, EndianInput.BYTES_ARE_BIG_ENDIAN)
        Assertions.assertEquals(java.lang.Long.decode("0x00000002") as Long, unsignedInt32)
        unsignedInt32 = bytes_To_UnsignedInt32(bytes4_Value_1_BE, EndianInput.BYTES_ARE_BIG_ENDIAN)
        Assertions.assertEquals(java.lang.Long.decode("0x00000001") as Long, unsignedInt32)
        unsignedInt32 = bytes_To_UnsignedInt32(bytes4_Value_0_BE, EndianInput.BYTES_ARE_BIG_ENDIAN)
        Assertions.assertEquals(java.lang.Long.decode("0x00000000") as Long, unsignedInt32)
        unsignedInt32 = bytes_To_UnsignedInt32(
            bytes4_Value_Minus_1_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(java.lang.Long.decode("0xFFFFFFFF") as Long, unsignedInt32)
        unsignedInt32 = bytes_To_UnsignedInt32(
            bytes4_Value_Minus_2_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(java.lang.Long.decode("0xFFFFFFFE") as Long, unsignedInt32)
        unsignedInt32 = bytes_To_UnsignedInt32(
            bytes4_Value_MaxNegative_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(java.lang.Long.decode("0x80000000") as Long, unsignedInt32)
    }

    @Test
    fun test_bytes_To_UnignedInt32_LittleEndian() {
        var unsignedInt32: Long
        unsignedInt32 = bytes_To_UnsignedInt32(
            bytes4_Value_MaxPositive_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(java.lang.Long.decode("0x7FFFFFFF") as Long, unsignedInt32)
        unsignedInt32 = bytes_To_UnsignedInt32(
            bytes4_Value_2_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(java.lang.Long.decode("0x00000002") as Long, unsignedInt32)
        unsignedInt32 = bytes_To_UnsignedInt32(
            bytes4_Value_1_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(java.lang.Long.decode("0x00000001") as Long, unsignedInt32)
        unsignedInt32 = bytes_To_UnsignedInt32(
            bytes4_Value_0_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(java.lang.Long.decode("0x00000000") as Long, unsignedInt32)
        unsignedInt32 = bytes_To_UnsignedInt32(
            bytes4_Value_Minus_1_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(java.lang.Long.decode("0xFFFFFFFF") as Long, unsignedInt32)
        unsignedInt32 = bytes_To_UnsignedInt32(
            bytes4_Value_Minus_2_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(java.lang.Long.decode("0xFFFFFFFE") as Long, unsignedInt32)
        unsignedInt32 = bytes_To_UnsignedInt32(
            bytes4_Value_MaxNegative_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(java.lang.Long.decode("0x80000000") as Long, unsignedInt32)
    }

    @Test
    fun test_signedInt32_To_Bytes_BigEndian() {
        var bytes: ByteArray
        bytes = singedInt32_To_Bytes(Int.MAX_VALUE, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_MaxPositive_BE))
        bytes = singedInt32_To_Bytes(2, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_2_BE))
        bytes = singedInt32_To_Bytes(1, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_1_BE))
        bytes = singedInt32_To_Bytes(0, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_0_BE))
        bytes = singedInt32_To_Bytes(-1, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_Minus_1_BE))
        bytes = singedInt32_To_Bytes(-2, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_Minus_2_BE))
        bytes = singedInt32_To_Bytes(Int.MIN_VALUE, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_MaxNegative_BE))
    }

    @Test
    fun test_signedInt32_To_Bytes_LittleEndian() {
        var bytes: ByteArray
        bytes = singedInt32_To_Bytes(Int.MAX_VALUE, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_MaxPositive_LE))
        bytes = singedInt32_To_Bytes(2, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_2_LE))
        bytes = singedInt32_To_Bytes(1, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_1_LE))
        bytes = singedInt32_To_Bytes(0, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_0_LE))
        bytes = singedInt32_To_Bytes(-1, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_Minus_1_LE))
        bytes = singedInt32_To_Bytes(-2, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_Minus_2_LE))
        bytes = singedInt32_To_Bytes(Int.MIN_VALUE, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_MaxNegative_LE))
    }

    @Test
    @Throws(DecoderException::class)
    fun test_unsignedInt32_To_Bytes_BigEndian() {
        var bytes: ByteArray
        bytes = unsingedInt32_To_Bytes(MAX_UNSIGNED_INT32, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, Hex.decodeHex("FFFFFFFF")))
        bytes = unsingedInt32_To_Bytes(2, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_2_BE))
        bytes = unsingedInt32_To_Bytes(1, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_1_BE))
        bytes = unsingedInt32_To_Bytes(0, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_0_BE))
    }

    @Test
    @Throws(DecoderException::class)
    fun test_unsignedInt32_To_Bytes_LittleEndian() {
        var bytes: ByteArray
        bytes = unsingedInt32_To_Bytes(MAX_UNSIGNED_INT32, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, Hex.decodeHex("FFFFFFFF")))
        bytes = unsingedInt32_To_Bytes(2, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_2_LE))
        bytes = unsingedInt32_To_Bytes(1, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_1_LE))
        bytes = unsingedInt32_To_Bytes(0, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes4_Value_0_LE))
    }

    @Test
    fun test_bytes_To_SignedInt16_BigEndian() {
        var signedInt16: Int
        signedInt16 = bytes_To_SignedInt16(
            bytes2_Value_MaxPositive_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(32767, signedInt16)
        signedInt16 = bytes_To_SignedInt16(bytes2_Value_2_BE, EndianInput.BYTES_ARE_BIG_ENDIAN)
        Assertions.assertEquals(2, signedInt16)
        signedInt16 = bytes_To_SignedInt16(bytes2_Value_1_BE, EndianInput.BYTES_ARE_BIG_ENDIAN)
        Assertions.assertEquals(1, signedInt16)
        signedInt16 = bytes_To_SignedInt16(bytes2_Value_0_BE, EndianInput.BYTES_ARE_BIG_ENDIAN)
        Assertions.assertEquals(0, signedInt16)
        signedInt16 = bytes_To_SignedInt16(
            bytes2_Value_Minus_1_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(-1, signedInt16)
        signedInt16 = bytes_To_SignedInt16(
            bytes2_Value_Minus_2_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(-2, signedInt16)
        signedInt16 = bytes_To_SignedInt16(
            bytes2_Value_MaxNegative_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        )
        Assertions.assertEquals(-32768, signedInt16)
    }

    @Test
    fun test_bytes_To_SignedInt16_LittleEndian() {
        var signedInt16: Int
        signedInt16 = bytes_To_SignedInt16(
            bytes2_Value_MaxPositive_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(32767, signedInt16)
        signedInt16 = bytes_To_SignedInt16(bytes2_Value_2_LE, EndianInput.BYTES_ARE_LITTLE_ENDIAN)
        Assertions.assertEquals(2, signedInt16)
        signedInt16 = bytes_To_SignedInt16(bytes2_Value_1_LE, EndianInput.BYTES_ARE_LITTLE_ENDIAN)
        Assertions.assertEquals(1, signedInt16)
        signedInt16 = bytes_To_SignedInt16(bytes2_Value_0_LE, EndianInput.BYTES_ARE_LITTLE_ENDIAN)
        Assertions.assertEquals(0, signedInt16)
        signedInt16 = bytes_To_SignedInt16(
            bytes2_Value_Minus_1_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(-1, signedInt16)
        signedInt16 = bytes_To_SignedInt16(
            bytes2_Value_Minus_2_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(-2, signedInt16)
        signedInt16 = bytes_To_SignedInt16(
            bytes2_Value_MaxNegative_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        )
        Assertions.assertEquals(-32768, signedInt16)
    }

    @Test
    fun test_bytes_To_UnignedInt16_BigEndian() {
        var unsignedInt16: Long
        unsignedInt16 = bytes_To_UnsignedInt16(
            bytes2_Value_MaxPositive_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        ).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0x7FFF") as Long, unsignedInt16)
        unsignedInt16 = bytes_To_UnsignedInt16(bytes2_Value_2_BE, EndianInput.BYTES_ARE_BIG_ENDIAN).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0x0002") as Long, unsignedInt16)
        unsignedInt16 = bytes_To_UnsignedInt16(bytes2_Value_1_BE, EndianInput.BYTES_ARE_BIG_ENDIAN).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0x0001") as Long, unsignedInt16)
        unsignedInt16 = bytes_To_UnsignedInt16(bytes2_Value_0_BE, EndianInput.BYTES_ARE_BIG_ENDIAN).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0x0000") as Long, unsignedInt16)
        unsignedInt16 = bytes_To_UnsignedInt16(
            bytes2_Value_Minus_1_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        ).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0xFFFF") as Long, unsignedInt16)
        unsignedInt16 = bytes_To_UnsignedInt16(
            bytes2_Value_Minus_2_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        ).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0xFFFE") as Long, unsignedInt16)
        unsignedInt16 = bytes_To_UnsignedInt16(
            bytes2_Value_MaxNegative_BE,
            EndianInput.BYTES_ARE_BIG_ENDIAN
        ).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0x8000") as Long, unsignedInt16)
    }

    @Test
    fun test_bytes_To_UnignedInt16_LittleEndian() {
        var unsignedInt16: Long
        unsignedInt16 = bytes_To_UnsignedInt16(
            bytes2_Value_MaxPositive_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        ).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0x7FFF") as Long, unsignedInt16)
        unsignedInt16 = bytes_To_UnsignedInt16(
            bytes2_Value_2_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        ).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0x0002") as Long, unsignedInt16)
        unsignedInt16 = bytes_To_UnsignedInt16(
            bytes2_Value_1_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        ).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0x0001") as Long, unsignedInt16)
        unsignedInt16 = bytes_To_UnsignedInt16(
            bytes2_Value_0_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        ).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0x0000") as Long, unsignedInt16)
        unsignedInt16 = bytes_To_UnsignedInt16(
            bytes2_Value_Minus_1_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        ).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0xFFFF") as Long, unsignedInt16)
        unsignedInt16 = bytes_To_UnsignedInt16(
            bytes2_Value_Minus_2_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        ).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0xFFFE") as Long, unsignedInt16)
        unsignedInt16 = bytes_To_UnsignedInt16(
            bytes2_Value_MaxNegative_LE,
            EndianInput.BYTES_ARE_LITTLE_ENDIAN
        ).toLong()
        Assertions.assertEquals(java.lang.Long.decode("0x8000") as Long, unsignedInt16)
    }

    @Test
    fun test_signedInt16_To_Bytes_BigEndian() {
        var bytes: ByteArray
        bytes = singedInt16_To_Bytes(MAX_SIGNED_INT16, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_MaxPositive_BE))
        bytes = singedInt16_To_Bytes(2, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_2_BE))
        bytes = singedInt16_To_Bytes(1, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_1_BE))
        bytes = singedInt16_To_Bytes(0, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_0_BE))
        bytes = singedInt16_To_Bytes(-1, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_Minus_1_BE))
        bytes = singedInt16_To_Bytes(-2, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_Minus_2_BE))
        bytes = singedInt16_To_Bytes(MIN_SIGNED_INT16, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_MaxNegative_BE))
    }

    @Test
    fun test_signedInt16_To_Bytes_LittleEndian() {
        var bytes: ByteArray
        bytes = singedInt16_To_Bytes(MAX_SIGNED_INT16, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_MaxPositive_LE))
        bytes = singedInt16_To_Bytes(2, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_2_LE))
        bytes = singedInt16_To_Bytes(1, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_1_LE))
        bytes = singedInt16_To_Bytes(0, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_0_LE))
        bytes = singedInt16_To_Bytes(-1, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_Minus_1_LE))
        bytes = singedInt16_To_Bytes(-2, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_Minus_2_LE))
        bytes = singedInt16_To_Bytes(MIN_SIGNED_INT16, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_MaxNegative_LE))
    }

    @Test
    @Throws(DecoderException::class)
    fun test_unsignedInt16_To_Bytes_BigEndian() {
        var bytes: ByteArray
        bytes = unsingedInt16_To_Bytes(MAX_UNSIGNED_INT16, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, Hex.decodeHex("FFFF")))
        bytes = unsingedInt16_To_Bytes(2, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_2_BE))
        bytes = unsingedInt16_To_Bytes(1, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_1_BE))
        bytes = unsingedInt16_To_Bytes(0, EndianOutput.BYTES_AS_BIG_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_0_BE))
    }

    @Test
    @Throws(DecoderException::class)
    fun test_unsignedInt16_To_Bytes_LittleEndian() {
        var bytes: ByteArray
        bytes = unsingedInt16_To_Bytes(MAX_UNSIGNED_INT16, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, Hex.decodeHex("FFFF")))
        bytes = unsingedInt16_To_Bytes(2, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_2_LE))
        bytes = unsingedInt16_To_Bytes(1, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_1_LE))
        bytes = unsingedInt16_To_Bytes(0, EndianOutput.BYTES_AS_LITTLE_ENDIAN)
        Assertions.assertTrue(Arrays.equals(bytes, bytes2_Value_0_LE))
    }

    @Test
    fun test_bytes_To_SignedInt8() {
        var signedInt8: Int
        signedInt8 = bytes_To_SignedInt8(bytes1_Value_MaxPositive)
        Assertions.assertEquals(127, signedInt8)
        signedInt8 = bytes_To_SignedInt8(bytes1_Value_2)
        Assertions.assertEquals(2, signedInt8)
        signedInt8 = bytes_To_SignedInt8(bytes1_Value_1)
        Assertions.assertEquals(1, signedInt8)
        signedInt8 = bytes_To_SignedInt8(bytes1_Value_0)
        Assertions.assertEquals(0, signedInt8)
        signedInt8 = bytes_To_SignedInt8(bytes1_Value_Minus_1)
        Assertions.assertEquals(-1, signedInt8)
        signedInt8 = bytes_To_SignedInt8(bytes1_Value_Minus_2)
        Assertions.assertEquals(-2, signedInt8)
        signedInt8 = bytes_To_SignedInt8(bytes1_Value_MaxNegative)
        Assertions.assertEquals(-128, signedInt8)
    }

    @Test
    fun test_bytes_To_UnignedInt8() {
        var unsignedInt8: Long

        // unsignedInt8 = DatatypeConversion.bytes_To_UnsignedInt8(bytes1_Value_MaxPositive);
        // assertEquals((long) Long.decode("0x7F"), unsignedInt8);
        //
        // unsignedInt8 = DatatypeConversion.bytes_To_UnsignedInt8(bytes1_Value_2);
        // assertEquals((long) Long.decode("0x02"), unsignedInt8);
        //
        // unsignedInt8 = DatatypeConversion.bytes_To_UnsignedInt8(bytes1_Value_1);
        // assertEquals((long) Long.decode("0x01"), unsignedInt8);
        //
        // unsignedInt8 = DatatypeConversion.bytes_To_UnsignedInt8(bytes1_Value_0);
        // assertEquals((long) Long.decode("0x00"), unsignedInt8);
        //
        // unsignedInt8 = DatatypeConversion.bytes_To_UnsignedInt8(bytes1_Value_Minus_1);
        // assertEquals((long) Long.decode("0xFF"), unsignedInt8);
        //
        // unsignedInt8 = DatatypeConversion.bytes_To_UnsignedInt8(bytes1_Value_Minus_2);
        // assertEquals((long) Long.decode("0xFE"), unsignedInt8);
        //
        // unsignedInt8 = DatatypeConversion.bytes_To_UnsignedInt8(bytes1_Value_MaxNegative);
        // assertEquals((long) Long.decode("0x80"), unsignedInt8);
    }

    @Test
    fun test_signedInt8_To_Bytes() {
        var bytes: ByteArray
        bytes = singedInt8_To_Bytes(MAX_SIGNED_INT8)
        Assertions.assertTrue(Arrays.equals(bytes, bytes1_Value_MaxPositive))
        bytes = singedInt8_To_Bytes(2)
        Assertions.assertTrue(Arrays.equals(bytes, bytes1_Value_2))
        bytes = singedInt8_To_Bytes(1)
        Assertions.assertTrue(Arrays.equals(bytes, bytes1_Value_1))
        bytes = singedInt8_To_Bytes(0)
        Assertions.assertTrue(Arrays.equals(bytes, bytes1_Value_0))
        bytes = singedInt8_To_Bytes(-1)
        Assertions.assertTrue(Arrays.equals(bytes, bytes1_Value_Minus_1))
        bytes = singedInt8_To_Bytes(-2)
        Assertions.assertTrue(Arrays.equals(bytes, bytes1_Value_Minus_2))
        bytes = singedInt8_To_Bytes(MIN_SIGNED_INT8)
        Assertions.assertTrue(Arrays.equals(bytes, bytes1_Value_MaxNegative))
    }

    @Test
    fun test_unsignedInt8_To_Bytes() {
        val UNSIGNED_INT8_MAX = 255
        val MAX_UNSINGND_INT8_BYTE = byteArrayOf(0xFF.toByte())
        var bytes: ByteArray
        bytes = unsingedInt8_To_Bytes(UNSIGNED_INT8_MAX)
        Assertions.assertTrue(Arrays.equals(bytes, MAX_UNSINGND_INT8_BYTE))
        bytes = unsingedInt8_To_Bytes(2)
        Assertions.assertTrue(Arrays.equals(bytes, bytes1_Value_2))
        bytes = unsingedInt8_To_Bytes(1)
        Assertions.assertTrue(Arrays.equals(bytes, bytes1_Value_1))
        bytes = unsingedInt8_To_Bytes(0)
        Assertions.assertTrue(Arrays.equals(bytes, bytes1_Value_0))
    }
}
