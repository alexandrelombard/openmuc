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
package org.openmuc.framework.server.modbus.register

import com.ghgande.j2mod.modbus.procimg.InputRegister
import com.ghgande.j2mod.modbus.procimg.Register
import org.openmuc.framework.data.*
import org.openmuc.framework.data.Record
import org.openmuc.framework.dataaccess.Channel
import java.nio.ByteBuffer

/**
 * This Class implements a linked holding register for Modbus server. The reason behind this class is to collect the
 * input over multiple registers and write into one single channnel. Therefore it is necessary to concatenate the
 * register contents.
 *
 * Bytes are submitted from one to next register after receiving. Example: [register1] -&gt; [register2] -&gt;
 * [register3] -&gt; [register4] = (represents 64 bytes Long/Double value) 0x01 0x02 -&gt; 0x03 0x04 -&gt; 0x01 0x02
 * -&gt; 0x03 0x04
 *
 * register1 submits 2 bytes to register2 register2 submits 4 bytes to register3 register3 submits 6 bytes to register4
 * register4 writes channel with 8 bytes value.
 *
 * The behavior of submission is safe against the order the registers are written.
 *
 * @author sfey
 */
class LinkedMappingHoldingRegister(
    inputRegister: MappingInputRegister,
    channel: Channel,
    private val nextRegister: LinkedMappingHoldingRegister?,
    private val valueType: ValueType,
    byteHigh: Int,
    byteLow: Int
) : MappingInputRegister(channel, byteHigh, byteLow), Register {
    private var leadingBytes: ByteArray? = null
    private var thisRegisterContent: ByteArray? = null
    private var hasLeadingRegister = false
    private val inputRegister: InputRegister

    init {
        this.inputRegister = inputRegister
        if (nextRegister != null) {
            nextRegister.hasLeadingRegister = true
        }
    }

    override fun setValue(v: Int) {
        val fromBytes = byteArrayOf(
            (v shr 24 and 0xFF).toByte(),
            (v shr 16 and 0xFF).toByte(),
            (v shr 8 and 0xFF).toByte(),
            (v and 0xFF).toByte()
        )
        setValue(fromBytes)
    }

    override fun setValue(s: Short) {
        val fromBytes = byteArrayOf((s.toInt() shr 8 and 0xFF).toByte(), (s.toInt() and 0xFF).toByte())
        setValue(fromBytes)
    }

    override fun setValue(bytes: ByteArray) {
        thisRegisterContent = bytes
        if (nextRegister != null) {
            if (hasLeadingRegister) {
                if (leadingBytes != null) {
                    nextRegister.submit(concatenate(leadingBytes, thisRegisterContent))
                }
            } else {
                nextRegister.submit(thisRegisterContent)
            }
        } else {
            if (hasLeadingRegister) {
                if (leadingBytes != null) {
                    writeChannel(newValue(valueType, concatenate(leadingBytes, thisRegisterContent)))
                } /* else wait for leadingBytes from submit */
            } else {
                writeChannel(newValue(valueType, thisRegisterContent))
            }
        }
    }

    fun submit(leading: ByteArray?) {
        leadingBytes = leading
        if (thisRegisterContent != null) {
            if (nextRegister != null) {
                nextRegister.submit(concatenate(leadingBytes, thisRegisterContent))
            } else {
                writeChannel(newValue(valueType, concatenate(leadingBytes, thisRegisterContent)))
            }
        } /* else wait for thisRegisterContent from setValue */
    }

    private fun concatenate(one: ByteArray?, two: ByteArray?): ByteArray? {
        if (one == null) {
            return two
        }
        if (two == null) {
            return one
        }
        val combined = ByteArray(one.size + two.size)
        for (i in combined.indices) {
            combined[i] = if (i < one.size) one[i] else two[i - one.size]
        }
        return combined
    }

    override fun toBytes(): ByteArray {
        return inputRegister.toBytes()
    }

    private fun writeChannel(value: Value?) {
        if (value != null) {
            if (useUnscaledValues) {
                channel.write(DoubleValue(value.asDouble() * channel.scalingFactor))
            } else {
                channel.write(value)
            }
        }
        channel.latestRecord = Record(Flag.CANNOT_WRITE_NULL_VALUE)
    }

    companion object {
        @Throws(TypeConversionException::class)
        fun newValue(fromType: ValueType?, fromBytes: ByteArray?): Value? {
            return when (fromType) {
                ValueType.BOOLEAN -> if (fromBytes!![0].toInt() == 0x00) {
                    BooleanValue(false)
                } else {
                    BooleanValue(true)
                }

                ValueType.DOUBLE -> DoubleValue(
                    ByteBuffer.wrap(
                        fromBytes
                    ).double
                )

                ValueType.FLOAT -> FloatValue(
                    ByteBuffer.wrap(
                        fromBytes
                    ).float
                )

                ValueType.LONG -> LongValue(
                    ByteBuffer.wrap(
                        fromBytes
                    ).long
                )

                ValueType.INTEGER -> IntValue(
                    ByteBuffer.wrap(
                        fromBytes
                    ).int
                )

                ValueType.SHORT -> ShortValue(
                    ByteBuffer.wrap(
                        fromBytes
                    ).short
                )

                ValueType.BYTE -> ByteValue(fromBytes!![0])
                ValueType.BYTE_ARRAY -> ByteArrayValue(fromBytes!!)
                ValueType.STRING -> StringValue(String(fromBytes!!))
                else -> null
            }
        }
    }
}
