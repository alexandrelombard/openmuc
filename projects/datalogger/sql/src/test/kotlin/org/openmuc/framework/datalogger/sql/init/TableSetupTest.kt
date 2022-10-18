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

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.openmuc.framework.data.Record.value
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.sql.DbAccess
import org.openmuc.framework.datalogger.sql.MetaBuilder
import org.openmuc.framework.datalogger.sql.TableSetup
import org.openmuc.framework.datalogger.sql.TestConnectionHelper
import org.openmuc.framework.datalogger.sql.utils.PropertyHandlerProvider.Companion.instance
import org.openmuc.framework.datalogger.sql.utils.Settings
import org.openmuc.framework.lib.osgi.config.PropertyHandler
import java.sql.*
import java.util.*

internal class TableSetupTest {
    private var tableSetup: TableSetup? = null
    private var metaBuilder: MetaBuilder? = null
    private var accessMock: DbAccess? = null
    private var channelList: MutableList<LogChannel?>? = null
    private var connection: Connection? = null
    @BeforeEach
    @Throws(SQLException::class)
    fun setupInitializer() {
        accessMock = Mockito.mock(DbAccess::class.java)
        channelList = ArrayList()
        channelList.add(getMockedChannel("gridPower"))
        channelList.add(getMockedChannel("pvPower"))
        val resultMocked = Mockito.mock(ResultSet::class.java)
        val resultMetaMock = Mockito.mock(ResultSetMetaData::class.java)
        Mockito.`when`(resultMetaMock.columnCount).thenReturn(0)
        Mockito.`when`(resultMocked.metaData).thenReturn(resultMetaMock)
        Mockito.`when`(accessMock.executeQuery(ArgumentMatchers.any())).thenReturn(resultMocked)
        Mockito.`when`(accessMock.getColumnLength(ArgumentMatchers.anyList(), ArgumentMatchers.anyString())).thenReturn(
            Collections.nCopies(20, 20)
        )
        val propHandlerMock = Mockito.mock(
            PropertyHandler::class.java
        )
        Mockito.`when`(propHandlerMock.getString(Settings.URL)).thenReturn("jdbc:h2")
        instance!!.propertyHandler = propHandlerMock
        tableSetup = TableSetup(channelList, accessMock)
        metaBuilder = MetaBuilder(channelList, accessMock)
        connection = TestConnectionHelper.getConnection()
    }

    private fun getMockedChannel(channelId: String): LogChannel {
        val channelMock = Mockito.mock(LogChannel::class.java)
        Mockito.`when`(channelMock.id).thenReturn(channelId)
        Mockito.`when`(channelMock.valueType).thenReturn(ValueType.DOUBLE)
        Mockito.`when`(channelMock.description).thenReturn("")
        Mockito.`when`(channelMock.channelAddress).thenReturn("address")
        Mockito.`when`(channelMock.unit).thenReturn("W")
        Mockito.`when`(channelMock.samplingGroup).thenReturn("sg")
        Mockito.`when`(channelMock.description).thenReturn("")
        return channelMock
    }

    @Test
    @Throws(SQLException::class)
    fun initNewMetaTable() {
        metaBuilder!!.writeMetaTable()
        val sqlCaptor = ArgumentCaptor.forClass(
            StringBuilder::class.java
        )
        Mockito.verify(accessMock, Mockito.atLeastOnce()).executeSQL(sqlCaptor.capture())
        val returnedBuilder = sqlCaptor.allValues
        for (sb in returnedBuilder) {
            val sqlConstraint = sb.toString()
            TestConnectionHelper.executeSQL(connection, sqlConstraint)
            if (sqlConstraint.startsWith("INSERT INTO openmuc_meta")) {
                Assertions.assertTrue(sqlConstraint.contains(INSERT_META_ENTRIES_PATTERN))
                Assertions.assertTrue(sqlConstraint.contains("gridPower") && sqlConstraint.contains("pvPower"))
            }
        }
        connection!!.close()
    }

    @Test
    @Throws(SQLException::class)
    fun createOpenmucTables() {
        tableSetup!!.createOpenmucTables()
        val sqlCaptor = ArgumentCaptor.forClass(
            StringBuilder::class.java
        )
        Mockito.verify(accessMock, Mockito.atLeastOnce()).executeSQL(sqlCaptor.capture())
        val returnedBuilder = sqlCaptor.allValues
        val expectedConstrains = AssertData.getOpenmucTableConstraints()
        for (i in channelList!!.indices) {
            val channelId = channelList!![i]!!.id
            Assertions.assertTrue(returnedBuilder[i].toString().contains(channelId!!))

            // test if the sql statements can be executed without errors
            TestConnectionHelper.executeSQL(connection, returnedBuilder[i].toString())
        }
        connection!!.close()
    }

    companion object {
        private const val INSERT_META_ENTRIES_PATTERN =
            "INSERT INTO openmuc_meta (time,channelid,channelAdress,loggingInterval,loggingTimeOffset,unit,valueType,scalingFactor,valueOffset,listening,loggingEvent,samplingInterval,samplingTimeOffset,SamplingGroup,disabled,description) VALUES"
    }
}
