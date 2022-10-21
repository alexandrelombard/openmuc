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

abstract class NumberValue(private val value: Number) : Value {
    override fun asDouble(): Double {
        return value.toDouble()
    }

    override fun asFloat(): Float {
        return value.toFloat()
    }

    override fun asLong(): Long {
        return value.toLong()
    }

    override fun asInt(): Int {
        return value.toInt()
    }

    override fun asShort(): Short {
        return value.toShort()
    }

    override fun asByte(): Byte {
        return value.toByte()
    }

    override fun asBoolean(): Boolean {
        return value.toDouble() != 0.0
    }

    override fun asString(): String {
        return value.toString()
    }

    override fun toString(): String {
        return asString()
    }
}
