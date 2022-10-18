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

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.openmuc.framework.data.Record.value
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.datalogger.sql.DbAccess.Companion.getTestInstance
import java.sql.*

class SqlReaderTest {
    private var sqlReader: SqlReader? = null
    private var dbAccess: DbAccess? = null
    private var dbAccessSpy: DbAccess? = null
    private var dbConnectorMock: DbConnector? = null
    private var connection: Connection? = null
    private val channelId = "testChannel"
    private val valueType = ValueType.DOUBLE
    @BeforeEach
    @Throws(SQLException::class)
    fun setup() {
        connection = TestConnectionHelper.getConnection()
        dbConnectorMock = Mockito.mock(DbConnector::class.java)
        dbAccess = getTestInstance(dbConnectorMock) // Real DbAccess with mock DbConnector to prevent null
        // pointer exception in queryRecords
        dbAccessSpy = Mockito.spy(dbAccess) // DbAccess with modified executeQuery
        Mockito.doAnswer { invocation: InvocationOnMock ->
            TestConnectionHelper.executeQuery(
                connection,
                invocation.getArgument<Any>(0).toString()
            )
        }
            .`when`(dbAccessSpy).executeQuery(ArgumentMatchers.any())
        sqlReader = SqlReader(dbAccessSpy)
    }

    @Test
    @Throws(SQLException::class)
    fun readLatestRecordFromDb() {
        writeTestRecords()
        val record = sqlReader!!.readLatestRecordFromDb(channelId, valueType)
        Assertions.assertTrue(record!!.value!!.asDouble() == 2.0)
        connection!!.close()
    }

    @Throws(SQLException::class)
    fun writeTestRecords() {
        TestConnectionHelper.executeSQL(
            connection,
            String.format("CREATE TABLE %s (time TIMESTAMP NOT NULL, " + "\"VALUE\" DOUBLE)", channelId)
        )
        TestConnectionHelper.executeSQL(
            connection,
            String.format("INSERT INTO %s (time, \"VALUE\") VALUES ('2020-09-08 14:43:39.0', 1)", channelId)
        )
        TestConnectionHelper.executeSQL(
            connection,
            String.format("INSERT INTO %s (time, \"VALUE\") VALUES ('2021-09-08 14:43:39.0', 2)", channelId)
        ) // Latest
        // Date
        TestConnectionHelper.executeSQL(
            connection,
            String.format("INSERT INTO %s (time, \"VALUE\") VALUES ('2020-09-08 13:43:39.0', 3)", channelId)
        )
    }
}
