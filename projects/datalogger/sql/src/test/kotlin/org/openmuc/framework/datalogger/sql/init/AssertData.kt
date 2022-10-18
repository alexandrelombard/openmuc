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
package org.openmuc.framework.datalogger.sql.init

import java.sql.JDBCType
import java.util.*

object AssertData {
    val openmucTableConstraints: List<String>
        get() {
            val tableConstrains: MutableList<String> = ArrayList()
            val tableNameList = ArrayList<String>()
            Collections.addAll(
                tableNameList, BOOLEAN_VALUE, BYTE_ARRAY_VALUE, FLOAT_VALUE, DOUBLE_VALUE, INT_VALUE,
                LONG_VALUE, BYTE_VALUE, SHORT_VALUE, STRING_VALUE
            )
            val typeList = ArrayList<JDBCType>()
            Collections.addAll(
                typeList, JDBCType.BOOLEAN, JDBCType.LONGVARBINARY, JDBCType.FLOAT, JDBCType.DOUBLE,
                JDBCType.INTEGER, JDBCType.BIGINT, JDBCType.SMALLINT, JDBCType.SMALLINT, JDBCType.VARCHAR
            )
            for (i in 0..8) {
                val sb = StringBuilder()
                sb.append("CREATE TABLE IF NOT EXISTS ").append(tableNameList[i])
                sb.append("(time TIMESTAMP NOT NULL,\n")
                sb.append("channelID VARCHAR(40) NOT NULL,")
                    .append("flag ")
                    .append(JDBCType.SMALLINT)
                    .append(" NOT NULL,")
                    .append("value ")
                sb.append(typeList[i])
                if (i == 8) {
                    sb.append(" (100)")
                }
                sb.append(",INDEX ").append(tableNameList[i]).append("Index(time)")
                sb.append(",PRIMARY KEY (channelid, time));")
                tableConstrains.add(sb.toString())
            }
            return tableConstrains
        }
}
