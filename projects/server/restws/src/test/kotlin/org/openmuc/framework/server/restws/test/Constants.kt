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
package org.openmuc.framework.server.restws.test

import org.openmuc.framework.data.Flag

object Constants {
    const val DOUBLE_VALUE = 3.2415
    const val FLOAT_VALUE = 3.2415f
    const val INTEGER_VALUE = 10513
    const val LONG_VALUE = 12345678
    const val SHORT_VALUE: Short = 1234
    const val BYTE_VALUE: Byte = 123
    const val BOOLEAN_VALUE = true
    val BYTE_ARRAY_VALUE = byteArrayOf(0, 1, 9, 10, 15, 16, 17, 127, -127, -81, -16, -1)
    const val STRING_VALUE = "TestString"
    val TEST_FLAG = Flag.VALID
    const val TIMESTAMP = 1417783028138L
    const val JSON_OBJECT_BEGIN = "{"
    const val JSON_OBJECT_END = "}"
}
