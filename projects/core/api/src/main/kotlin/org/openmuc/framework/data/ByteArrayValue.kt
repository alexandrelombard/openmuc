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
package org.openmuc.framework.data

import java.util.*

/**
 * ByteArrayValue is not immutable.
 */
class ByteArrayValue : Value {
    private val value: ByteArray

    /**
     * Create a new ByteArrayValue whose internal byte array will be a reference to the `value` passed to
     * this constructor. That means the passed byte array is not copied. Therefore you should not change the contents of
     * value after calling this constructor. If you want ByteArrayValue to internally store a copy of the passed value
     * then you should use the other constructor of this class instead.
     *
     * @param value
     * the byte array value.
     */
    constructor(value: ByteArray) {
        this.value = value
    }

    /**
     * Creates a new ByteArrayValue copying the byte array passed if `copy` is true.
     *
     * @param value
     * the byte array value.
     * @param copy
     * if true it will internally store a copy of value, else it will store a reference to value.
     */
    constructor(value: ByteArray, copy: Boolean) {
        if (copy) {
            this.value = value.clone()
        } else {
            this.value = value
        }
    }

    override fun asDouble(): Double {
        throw TypeConversionException()
    }

    override fun asFloat(): Float {
        throw TypeConversionException()
    }

    override fun asLong(): Long {
        throw TypeConversionException()
    }

    override fun asInt(): Int {
        throw TypeConversionException()
    }

    override fun asShort(): Short {
        throw TypeConversionException()
    }

    override fun asByte(): Byte {
        throw TypeConversionException()
    }

    override fun asBoolean(): Boolean {
        throw TypeConversionException()
    }

    override fun asByteArray(): ByteArray {
        return value
    }

    override fun toString(): String {
        return Arrays.toString(value)
    }

    override fun asString(): String {
        return toString()
    }

    override val valueType: ValueType
        get() = ValueType.BYTE_ARRAY
}
