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

import org.openmuc.framework.data.*
import org.openmuc.framework.datalogger.sql.utils.PropertyHandlerProvider
import org.openmuc.framework.datalogger.sql.utils.Settings
import org.openmuc.framework.datalogger.sql.utils.SqlValues
import org.openmuc.framework.lib.osgi.config.*
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.SQLException
import java.text.MessageFormat
import java.util.*

open class DbAccess {
    private val logger = LoggerFactory.getLogger(DbAccess::class.java)
    private val url: String
    private val dbConnector: DbConnector

    constructor() {
        dbConnector = DbConnector()
        val propertyHandler = PropertyHandlerProvider.propertyHandler!!
        url = propertyHandler.getString(Settings.URL)!!
        if (url.contains("h2") && url.contains("tcp")) {
            dbConnector.startH2Server()
        }
    }

    private constructor(connector: DbConnector) { // for testing
        url = ""
        dbConnector = connector
    }

    /**
     * Converts StringBuilder to String
     *
     * @param sb
     * StringBuilder to convert
     */
    fun executeSQL(sb: StringBuilder) {
        val sql = sb.toString()
        Thread.currentThread().contextClassLoader = this.javaClass.classLoader
        if (!dbConnector.isConnected) {
            dbConnector.connectionToDb
        }
        synchronized(dbConnector) { synchronizeStatement(sql) }
    }

    private fun synchronizeStatement(sql: String) {
        try {
            dbConnector.createStatementWithConnection().use { statement -> statement.execute(sql) }
        } catch (e: SQLException) {
            logger.error(MessageFormat.format("Error executing SQL: \n{0}", sql), e.message)
            logger.error(MessageFormat.format("SQLState:     {0}", e.sqlState))
            logger.error(MessageFormat.format("VendorError:  {0}", e.errorCode))
        }
    }

    @Throws(SQLException::class)
    fun executeQuery(sb: StringBuilder?): ResultSet {
        val statement = dbConnector.createStatementWithConnection()
        return statement.executeQuery(sb.toString())
    }

    fun timeScaleIsActive(): Boolean {
        val sbExtensions = StringBuilder("SELECT * FROM pg_extension;")
        try {
            dbConnector.createStatementWithConnection().executeQuery(sbExtensions.toString()).use { resultSet ->
                while (resultSet.next()) {
                    return resultSet.getString("extname").contains("timescale")
                }
            }
        } catch (e: SQLException) {
            logger.error(e.message)
        }
        return false
    }

    /**
     * Queries the database for a columns length and then returns it as a list of ints
     *
     * @param columns
     * List containing all column names
     * @param table
     * name of the table
     * @return a list containing each columns length
     */
    fun getColumnLength(columns: List<String?>?, table: String?): List<Int> {
        var table = table
        val columnsLength = ArrayList<Int>()
        if (url.contains(SqlValues.POSTGRESQL)) {
            table = table!!.lowercase(Locale.getDefault())
        }
        for (column in columns!!) {
            val sbVarcharLength = StringBuilder()
            sbVarcharLength.append("select character_maximum_length from information_schema.columns")
                .append(" where table_name = '" + table + "' AND column_name = '" + column!!.lowercase(Locale.getDefault()) + "';")
            try {
                if (!dbConnector.isConnected) {
                    dbConnector.connectionToDb
                }
                val rsLength = executeQuery(sbVarcharLength)
                rsLength.next()
                columnsLength.add(rsLength.getInt(1))
            } catch (e: SQLException) {
                logger.debug(e.message)
                columnsLength.add(0)
            }
        }
        return columnsLength
    }

    fun closeConnection() {
        dbConnector.closeConnection()
    }

    /**
     * Retrieves data from database and adds it to records
     */
    fun queryRecords(sb: StringBuilder, valuetype: ValueType?): List<Record> {
        // retrieve numeric values from database and add them to the records list
        val records: MutableList<Record> = ArrayList()
        if (!dbConnector.isConnected) {
            dbConnector.connectionToDb
        }
        try {
            executeQuery(sb).use { resultSet ->
                while (resultSet.next()) {
                    if (valuetype === ValueType.STRING) {
                        val rc = Record(
                            StringValue(resultSet.getString(SqlValues.VALUE)),
                            resultSet.getTimestamp("time").time, Flag.VALID
                        )
                        records.add(rc)
                    } else if (valuetype === ValueType.BYTE_ARRAY) {
                        val rc = Record(
                            ByteArrayValue(resultSet.getBytes(SqlValues.VALUE)),
                            resultSet.getTimestamp("time").time, Flag.VALID
                        )
                        records.add(rc)
                    } else if (valuetype === ValueType.BOOLEAN) {
                        val rc = Record(
                            BooleanValue(resultSet.getBoolean(SqlValues.VALUE)),
                            resultSet.getTimestamp("time").time, Flag.VALID
                        )
                        records.add(rc)
                    } else {
                        val rc = Record(
                            DoubleValue(resultSet.getDouble(SqlValues.VALUE)),
                            resultSet.getTimestamp("time").time, Flag.VALID
                        )
                        records.add(rc)
                    }
                }
            }
        } catch (e: SQLException) {
            val sql = sb.toString()
            logger.error(MessageFormat.format("Error executing SQL: \n{0}", sql), e.message)
        }
        return records
    }

    companion object {
        @JvmStatic
        fun getTestInstance(connector: DbConnector): DbAccess {
            return DbAccess(connector)
        }
    }
}
