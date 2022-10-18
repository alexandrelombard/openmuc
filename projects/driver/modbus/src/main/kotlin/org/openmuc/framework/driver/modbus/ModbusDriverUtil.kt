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
package org.openmuc.framework.driver.modbus

import com.ghgande.j2mod.modbus.procimg.InputRegister
import com.ghgande.j2mod.modbus.procimg.Register
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.ghgande.j2mod.modbus.util.BitVector
import com.ghgande.j2mod.modbus.util.ModbusUtil
import org.openmuc.framework.data.*
import org.openmuc.framework.driver.modbus.util.DatatypeConversion
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.EndianInput
import org.openmuc.framework.driver.modbus.util.DatatypeConversion.EndianOutput
import org.openmuc.framework.driver.spi.ChannelValueContainer.value

object ModbusDriverUtil {
    fun getBitVectorsValue(bitVector: BitVector): Value {
        val readValue: Value
        readValue = if (bitVector.size() == 1) {
            BooleanValue(bitVector.getBit(0)) // read single bit
        } else {
            ByteArrayValue(bitVector.bytes) // read multiple bits
        }
        return readValue
    }

    fun getBitVectorFromByteArray(value: Value): BitVector {
        val bv = BitVector(value.asByteArray()!!.size * 8)
        bv.bytes = value.asByteArray()
        return bv
    }

    /**
     * Converts the registers into the datatyp of the channel
     *
     * @param registers
     * input register array
     * @param datatype
     * Edatatype
     * @return the corresponding Value Object
     */
    fun getRegistersValue(registers: Array<InputRegister?>, datatype: EDatatype?): Value {
        val registerAsByteArray = inputRegisterToByteArray(registers)
        return getValueFromByteArray(registerAsByteArray, datatype)
    }

    fun getValueFromByteArray(registerAsByteArray: ByteArray, datatype: EDatatype?): Value {
        var registerValue: Value? = null
        registerValue = when (datatype) {
            EDatatype.SHORT, EDatatype.INT16 -> ShortValue(
                ModbusUtil.registerToShort(
                    registerAsByteArray
                )
            )

            EDatatype.INT32 -> IntValue(
                ModbusUtil.registersToInt(
                    registerAsByteArray
                )
            )

            EDatatype.UINT16 ->             // TODO might need both: big/little endian support in settings
                // int uint16 = DatatypeConversion.bytes_To_UnsignedInt16(registerAsByteArray,
                // EndianInput.BYTES_ARE_BIG_ENDIAN);
                // registerValue = new IntValue(uint16);
                IntValue(
                    ModbusUtil.registerToUnsignedShort(
                        registerAsByteArray
                    )
                )

            EDatatype.UINT32 -> {
                // TODO might need both: big/little endian support in settings
                val uint32 = DatatypeConversion.bytes_To_UnsignedInt32(
                    registerAsByteArray,
                    EndianInput.BYTES_ARE_BIG_ENDIAN
                )
                LongValue(uint32)
            }

            EDatatype.FLOAT -> FloatValue(
                ModbusUtil.registersToFloat(
                    registerAsByteArray
                )
            )

            EDatatype.DOUBLE -> DoubleValue(
                ModbusUtil.registersToDouble(
                    registerAsByteArray
                )
            )

            EDatatype.LONG -> LongValue(
                ModbusUtil.registersToLong(
                    registerAsByteArray
                )
            )

            EDatatype.BYTEARRAY -> ByteArrayValue(registerAsByteArray)
            EDatatype.BYTEARRAYLONG -> LongValue(
                DatatypeConversion.bytes_To_SignedInt64(registerAsByteArray, EndianInput.BYTES_ARE_LITTLE_ENDIAN)
            )

            else -> throw RuntimeException("Datatype " + datatype.toString() + " not supported yet")
        }
        return registerValue
    }

    fun valueToRegisters(value: Value, datatype: EDatatype?): Array<Register?> {
        val registers: Array<Register?>
        registers = when (datatype) {
            EDatatype.SHORT, EDatatype.INT16 -> byteArrayToRegister(
                ModbusUtil.shortToRegister(
                    value.asShort()
                )
            )

            EDatatype.INT32 -> byteArrayToRegister(
                ModbusUtil.intToRegisters(
                    value.asInt()
                )
            )

            EDatatype.UINT16 -> {
                val registerBytesUint16 = DatatypeConversion.unsingedInt16_To_Bytes(
                    value.asInt(),
                    EndianOutput.BYTES_AS_BIG_ENDIAN
                )
                byteArrayToRegister(registerBytesUint16)
            }

            EDatatype.UINT32 -> {
                val registerBytesUint32 = DatatypeConversion.unsingedInt32_To_Bytes(
                    value.asLong(),
                    EndianOutput.BYTES_AS_BIG_ENDIAN
                )
                byteArrayToRegister(registerBytesUint32)
            }

            EDatatype.DOUBLE -> byteArrayToRegister(
                ModbusUtil.doubleToRegisters(
                    value.asDouble()
                )
            )

            EDatatype.FLOAT -> byteArrayToRegister(
                ModbusUtil.floatToRegisters(
                    value.asFloat()
                )
            )

            EDatatype.LONG -> byteArrayToRegister(
                ModbusUtil.longToRegisters(
                    value.asLong()
                )
            )

            EDatatype.BYTEARRAY -> byteArrayToRegister(value.asByteArray())
            else -> throw RuntimeException("Datatype " + datatype.toString() + " not supported yet")
        }
        return registers
    }

    /**
     * Converts an array of input registers into a byte array
     *
     * @param inputRegister
     * inputRegister array
     * @return the InputRegister[] as byte[]
     */
    private fun inputRegisterToByteArray(inputRegister: Array<InputRegister?>): ByteArray {
        val registerAsBytes = ByteArray(inputRegister.size * 2) // one register = 2 bytes
        val inputRegisterByteLength: Byte = 2
        for (i in inputRegister.indices) {
            System.arraycopy(
                inputRegister[i]!!.toBytes(), 0, registerAsBytes, i * inputRegisterByteLength,
                inputRegisterByteLength.toInt()
            )
        }
        return registerAsBytes
    }

    // TODO check byte order e.g. is an Integer!
    // TODO only works for even byteArray.length!
    @Throws(RuntimeException::class)
    private fun byteArrayToRegister(byteArray: ByteArray?): Array<Register?> {

        // TODO byteArray might has a odd number of bytes...
        val register: Array<SimpleRegister?>
        if (byteArray!!.size % 2 == 0) {
            register = arrayOfNulls(byteArray.size / 2)
            var j = 0
            // for (int i = 0; i < byteArray.length; i++) {
            for (i in 0 until byteArray.size / 2) {
                register[i] = SimpleRegister(byteArray[j], byteArray[j + 1])
                j = j + 2
            }
        } else {
            throw RuntimeException("conversion from byteArray to Register is not working for odd number of bytes")
        }
        return register
    }
}
