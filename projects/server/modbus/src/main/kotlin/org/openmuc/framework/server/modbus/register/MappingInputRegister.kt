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
import org.openmuc.framework.dataaccess.Channel
import java.nio.ByteBuffer
import kotlin.Exception
import kotlin.Int
import kotlin.Short

abstract class MappingInputRegister(
    protected var channel: Channel,
    protected var highByte: Int,
    protected var lowByte: Int
) : InputRegister {
    var useUnscaledValues = false

    init {
        try {
            val scalingProperty = System.getProperty("org.openmuc.framework.server.modbus.useUnscaledValues")
            useUnscaledValues = scalingProperty.toBoolean()
        } catch (e: Exception) {
            /* will stick to default setting. */
        }
    }

    override fun getValue(): Int {
        /*
         * toBytes always only contains two bytes. So cast from short.
         */
        return ByteBuffer.wrap(toBytes()).short.toInt()
    }

    override fun toUnsignedShort(): Int {
        val shortVal = ByteBuffer.wrap(toBytes()).short
        return shortVal.toInt() and 0xFFFF
    }

    override fun toShort(): Short {
        return ByteBuffer.wrap(toBytes()).short
    }
}
