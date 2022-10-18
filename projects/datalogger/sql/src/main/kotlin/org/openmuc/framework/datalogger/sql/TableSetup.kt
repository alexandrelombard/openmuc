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
package org.openmuc.framework.datalogger.sql

import org.openmuc.framework.data.ValueType
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.sql.utils.PropertyHandlerProvider
import org.openmuc.framework.datalogger.sql.utils.Settings
import org.openmuc.framework.datalogger.sql.utils.SqlValues
import org.openmuc.framework.datalogger.sql.utils.TabelNames
import org.openmuc.framework.lib.osgi.config.*
import org.slf4j.LoggerFactory
import java.sql.JDBCType
import java.sql.SQLException
import java.text.MessageFormat

class TableSetup(private val channels: List<LogChannel?>?, private val dbAccess: DbAccess?) {
    private val logger = LoggerFactory.getLogger(TableSetup::class.java)
    private val url: String

    init {
        val propertyHandler: PropertyHandler = PropertyHandlerProvider.Companion.getInstance().getPropertyHandler()
        url = propertyHandler.getString(Settings.Companion.URL)
    }

    /**
     * Increases the length of a column
     *
     * @param table
     * Table to be altered
     * @param column
     * Length to be set for the column
     * @param columnName
     * Column to be altered
     */
    private fun increaseDescriptionColumnLength(table: String, column: String?, columnName: String?) {
        val sbNewVarcharLength = StringBuilder()
        if (url.contains(SqlValues.MYSQL)) {
            sbNewVarcharLength.append("ALTER TABLE $table MODIFY $columnName VARCHAR (")
        } else {
            sbNewVarcharLength.append("ALTER TABLE $table ALTER COLUMN $columnName TYPE VARCHAR (")
        }
        sbNewVarcharLength.append(column!!.length).append(");")
        dbAccess!!.executeSQL(sbNewVarcharLength)
    }

    /**
     * Creates and executes the queries to create a table for each data type. Following methods are used to create the
     * queries: [.appendTimestamp] to append the timestamp column to the query
     *
     *
     * This method further creates linked table using createLinkedTable() and inserts all data present in local db is
     * set.
     */
    fun createOpenmucTables() {
        var execute = true
        for (temp in channels!!) {
            val sb = StringBuilder()
            val channelId = temp!!.id
            sb.append("CREATE TABLE IF NOT EXISTS ").append(channelId)
            appendTimestamp(sb)
            sb.append("flag ").append(JDBCType.SMALLINT).append(" NOT NULL,").append("\"VALUE\" ")
            when (temp.valueType) {
                ValueType.BOOLEAN -> sb.append(JDBCType.BOOLEAN)
                ValueType.BYTE -> sb.append(JDBCType.SMALLINT)
                ValueType.BYTE_ARRAY -> if (url.contains(SqlValues.POSTGRESQL)) {
                    sb.append("BYTEA")
                } else if (url.contains(SqlValues.MYSQL)) {
                    sb.append(JDBCType.BLOB)
                } else {
                    sb.append(JDBCType.LONGVARBINARY)
                }

                ValueType.DOUBLE -> if (url.contains(SqlValues.POSTGRESQL)) {
                    sb.append("DOUBLE PRECISION")
                } else {
                    sb.append(JDBCType.DOUBLE)
                }

                ValueType.FLOAT -> sb.append(JDBCType.FLOAT)
                ValueType.INTEGER -> sb.append(JDBCType.INTEGER)
                ValueType.LONG -> sb.append(JDBCType.BIGINT)
                ValueType.SHORT -> sb.append(JDBCType.SMALLINT)
                ValueType.STRING -> {
                    sb.append(JDBCType.VARCHAR)
                    sb.append(" (")
                    sb.append(temp.valueTypeLength)
                    sb.append(')')
                }

                else -> {
                    execute = false
                    logger.error(
                        "Unable to create table for channel {}, reason: unknown ValueType {}", temp.id,
                        temp.valueType
                    )
                }
            }
            if (execute) {
                appendMySqlIndex(channelId, sb)
                sb.append(",PRIMARY KEY (time));")
                dbAccess!!.executeSQL(sb)
                activatePostgreSqlIndex(channelId)
                activateTimescaleDbHypertable(channelId)
            }
        }
        // reduceSizeOfChannelIdCol(tableNameList);
    }

    private fun reduceSizeOfChannelIdCol(tableNameList: List<String>) {
        // FIXME
        for (logChannel in channels!!) {
            val channelId = logChannel!!.id
            val columns: List<String?> = listOf("channelid")
            val varcharLength = dbAccess!!.getColumnLength(columns, TabelNames.DOUBLE_VALUE)
            if (varcharLength!![0]!! < channelId!!.length) {
                for (table in tableNameList) {
                    increaseDescriptionColumnLength(table, channelId, columns[0])
                }
            }
        }
    }

    /**
     * Append MySQL specific query to create a tables' Index
     *
     * @param tableNameList
     * List containing the names of all data type tables
     * @param i
     * Index for the tableNameList
     * @param sb
     * StringBuilder for the query
     */
    private fun appendMySqlIndex(name: String?, sb: StringBuilder) {
        if (!url.contains(SqlValues.POSTGRESQL)) {
            sb.append(",INDEX ").append(name).append("Index(time)")
        }
    }

    /**
     * Sends query to turn this table into a timescale hypertable
     *
     * @param tableNameList
     * List containing the names of all data type tables
     * @param i
     * Index for the tableNameList
     */
    private fun activateTimescaleDbHypertable(name: String?) {
        if (url.contains(SqlValues.POSTGRESQL) && dbAccess!!.timeScaleIsActive()) {
            try {
                dbAccess.executeQuery(
                    StringBuilder("SELECT create_hypertable('$name', 'time', if_not_exists => TRUE);")
                )
            } catch (e: SQLException) {
                logger.error(MessageFormat.format("{0}test", e.message))
            }
        }
    }

    /**
     * Execute PostgreSQl specific query to create Index if timescale is not activated
     *
     * @param tableNameList
     * List containing the names of all data type tables
     * @param i
     * Index for the tableNameList
     */
    private fun activatePostgreSqlIndex(name: String?) {
        if (url.contains(SqlValues.POSTGRESQL) && !dbAccess!!.timeScaleIsActive()) {
            val sbIndex = StringBuilder("CREATE INDEX IF NOT EXISTS ")
            sbIndex.append(name).append("Index ON ").append(name).append(" (time);")
            dbAccess.executeSQL(sbIndex)
        }
    }

    /**
     * @param typeList
     * List containing all JDBC data types
     * @param i
     * Index for typeList
     * @param sb
     * StringBuilder containing the query
     */
    private fun appendTypeList(typeList: List<JDBCType>, i: Int, sb: StringBuilder) {
        if (i == 1) {
            byteArrayDataType(typeList, i, sb)
        } else if (i == 3) {
            doubleDataType(typeList, i, sb)
        } else {
            sb.append(typeList[i])
        }
    }

    /**
     * Appends TIMESTAMPTZ(timezone) if PostgreSQL is used or TIMSTAMP if not
     *
     * @param sb
     * StingBuilder of the query
     */
    private fun appendTimestamp(sb: StringBuilder) {
        if (url.contains(SqlValues.POSTGRES)) {
            sb.append("(time TIMESTAMPTZ NOT NULL,\n")
        } else {
            sb.append("(time TIMESTAMP NOT NULL,\n")
        }
    }

    /**
     * Appends "DOUBLE PRECISION" to the query if PostgreSQL is used or double if not
     *
     * @param typeList
     * List containing all JDBC data types
     * @param i
     * Index for typeList
     * @param sb
     * StringBuilder containing the query
     */
    private fun doubleDataType(typeList: List<JDBCType>, i: Int, sb: StringBuilder) {
        if (url.contains(SqlValues.POSTGRESQL)) {
            sb.append("DOUBLE PRECISION")
        } else {
            sb.append(typeList[i])
        }
    }

    /**
     * Appends the appropriate data type for byte array to the query depending on the used database
     *
     * @param typeList
     * List containing all JDBC data types
     * @param i
     * Index for typeList
     * @param sb
     * StringBuilder containing the query
     */
    private fun byteArrayDataType(typeList: List<JDBCType>, i: Int, sb: StringBuilder) {
        if (url.contains(SqlValues.POSTGRESQL)) {
            sb.append("BYTEA")
        } else if (url.contains(SqlValues.MYSQL)) {
            sb.append("BLOB")
        } else {
            sb.append(typeList[i])
        }
    }
}
