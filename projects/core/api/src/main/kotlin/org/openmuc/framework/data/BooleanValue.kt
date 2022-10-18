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

class BooleanValue : Value {
    private val value: Boolean

    constructor(value: Boolean) {
        this.value = value
    }

    constructor(value: String?) {
        this.value = java.lang.Boolean.parseBoolean(value)
    }

    override fun asDouble(): Double {
        return asByte().toDouble()
    }

    override fun asFloat(): Float {
        return asByte().toFloat()
    }

    override fun asLong(): Long {
        return asByte().toLong()
    }

    override fun asInt(): Int {
        return asByte().toInt()
    }

    override fun asShort(): Short {
        return asByte().toShort()
    }

    override fun asByte(): Byte {
        return if (value) {
            1
        } else {
            0
        }
    }

    override fun asBoolean(): Boolean {
        return value
    }

    override fun asByteArray(): ByteArray {
        return byteArrayOf(asByte())
    }

    override fun toString(): String {
        return java.lang.Boolean.toString(value)
    }

    override fun asString(): String {
        return toString()
    }

    override val valueType: ValueType
        get() = ValueType.BOOLEAN
}
