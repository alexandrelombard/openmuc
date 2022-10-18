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

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.openmuc.framework.data.DoubleValue
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.Record.value
import org.openmuc.framework.data.Value
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.openmuc.framework.datalogger.sql.DbAccess
import java.sql.*
import java.util.*

internal class SqlWriterTest {
    private var sqlWriter: SqlWriter? = null
    private var dbAccessMock: DbAccess? = null
    private var connection: Connection? = null
    @BeforeEach
    @Throws(SQLException::class)
    fun setup() {
        connection = TestConnectionHelper.getConnection()
        dbAccessMock = Mockito.mock(DbAccess::class.java)
        Mockito.doAnswer { invocation: InvocationOnMock ->  // pass any executed sql statements to the test connection
            TestConnectionHelper.executeSQL(connection, invocation.getArgument<Any>(0).toString())
            null
        }.`when`(dbAccessMock).executeSQL(ArgumentMatchers.any())
        sqlWriter = SqlWriter(dbAccessMock)
    }

    @Test
    @Throws(SQLException::class)
    fun writeEventBasedContainerToDb() {
        val recordList = buildLoggingRecordList(5)
        TestConnectionHelper.executeSQL(
            connection, String.format( // create table for the tests to write to
                "CREATE TABLE %s (time TIMESTAMP NOT NULL, " + "flag SMALLINT NOT NULL, \"VALUE\" DOUBLE)",
                recordList[0]!!.channelId
            )
        )
        sqlWriter!!.writeEventBasedContainerToDb(recordList)
        Mockito.verify(dbAccessMock, Mockito.times(5)).executeSQL(ArgumentMatchers.any())
        connection!!.close()
    }

    private fun buildLoggingRecordList(numOfElements: Int): List<LoggingRecord?> {
        val channelId = "testChannel"
        val value: Value = DoubleValue(5.0)
        val timestamp = 1599569019000L
        val record = Record(value, timestamp, Flag.VALID)
        return Collections.nCopies(numOfElements, LoggingRecord(channelId, record))
    }
}
