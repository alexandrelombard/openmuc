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
package org.openmuc.framework.datalogger.sql.utils

import org.openmuc.framework.data.Value
import org.openmuc.framework.data.ValueType
import java.sql.JDBCType
import java.util.*

object SqlValues {
    val COLUMNS = Arrays.asList(
        "channelid", "channelAdress", "loggingInterval",
        "loggingTimeOffset", "unit", "valueType", "scalingFactor", "valueOffset", "listening", "loggingEvent",
        "samplingInterval", "samplingTimeOffset", "samplingGroup", "disabled", "description"
    )
    const val POSTGRESQL = "postgresql"
    const val POSTGRES = "postgres"
    const val MYSQL = "mysql"
    const val NULL = ") NULL,"
    const val AND = "' AND '"
    const val VALUE = "value"
    private val hexArray = "0123456789ABCDEF".toCharArray()
    fun getType(valueType: ValueType?): JDBCType {
        return when (valueType) {
            ValueType.BOOLEAN -> JDBCType.BOOLEAN
            ValueType.BYTE_ARRAY -> JDBCType.LONGVARBINARY
            ValueType.DOUBLE -> JDBCType.FLOAT
            ValueType.FLOAT -> JDBCType.DOUBLE
            ValueType.INTEGER -> JDBCType.INTEGER
            ValueType.LONG -> JDBCType.BIGINT
            ValueType.SHORT -> JDBCType.SMALLINT
            ValueType.BYTE -> JDBCType.SMALLINT
            ValueType.STRING -> JDBCType.VARCHAR
            else -> JDBCType.DOUBLE
        }
    }

    fun appendValue(value: Value, sb: StringBuilder) {
        when (value.javaClass.simpleName) {
            "BooleanValue" -> sb.append(value.asBoolean())
            "ByteValue" -> sb.append(value.asByte().toInt())
            "ByteArrayValue" -> byteArrayToHexString(sb, value.asByteArray())
            "DoubleValue" -> sb.append(value.asDouble())
            "FloatValue" -> sb.append(value.asFloat())
            "IntValue" -> sb.append(value.asInt())
            "LongValue" -> sb.append(value.asLong())
            "ShortValue" -> sb.append(value.asShort().toInt())
            "StringValue" -> sb.append('\'').append(value.asString()).append('\'')
            else -> {}
        }
    }

    private fun byteArrayToHexString(sb: StringBuilder, byteArray: ByteArray?) {
        val hexChars = CharArray(byteArray!!.size * 2)
        for (j in byteArray.indices) {
            val v = byteArray[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        sb.append('\'').append(hexChars).append('\'')
    }
}
