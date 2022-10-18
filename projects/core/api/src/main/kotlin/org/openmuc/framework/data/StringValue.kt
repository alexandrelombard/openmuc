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

import java.nio.charset.Charset

class StringValue(private val value: String) : Value {
    override fun asDouble(): Double {
        return try {
            value.toDouble()
        } catch (e: NumberFormatException) {
            throw TypeConversionException()
        }
    }

    override fun asFloat(): Float {
        return try {
            value.toFloat()
        } catch (e: NumberFormatException) {
            throw TypeConversionException()
        }
    }

    override fun asLong(): Long {
        return try {
            value.toLong()
        } catch (e: NumberFormatException) {
            throw TypeConversionException()
        }
    }

    override fun asInt(): Int {
        return try {
            value.toInt()
        } catch (e: NumberFormatException) {
            throw TypeConversionException()
        }
    }

    override fun asShort(): Short {
        return try {
            value.toShort()
        } catch (e: NumberFormatException) {
            throw TypeConversionException()
        }
    }

    override fun asByte(): Byte {
        return try {
            value.toByte()
        } catch (e: NumberFormatException) {
            throw TypeConversionException()
        }
    }

    override fun asBoolean(): Boolean {
        return java.lang.Boolean.parseBoolean(value)
    }

    override fun asByteArray(): ByteArray? {
        return value.toByteArray(charset)
    }

    override fun toString(): String {
        return value
    }

    override fun asString(): String {
        return toString()
    }

    override val valueType: ValueType?
        get() = ValueType.STRING

    companion object {
        private val charset = Charset.forName("US-ASCII")
    }
}
