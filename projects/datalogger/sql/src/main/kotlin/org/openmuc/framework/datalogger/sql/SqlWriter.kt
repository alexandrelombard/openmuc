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

import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.openmuc.framework.datalogger.sql.utils.SqlValues
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.util.*

class SqlWriter(private val dbAccess: DbAccess) {
    private val tableListChannel: MutableList<StringBuilder>

    init {
        tableListChannel = ArrayList()
    }

    fun writeEventBasedContainerToDb(containers: List<LoggingRecord>) {
        synchronized(tableListChannel) {
            writeAsTableList(containers)
            tableListChannel.clear()
        }
    }

    private fun writeAsTableList(containers: List<LoggingRecord>) {
        // createTableList();
        addRecordsFromContainersToList(containers)
        for (table in tableListChannel) {
            if (table.toString().contains("),")) {
                table.replace(table.length - 1, table.length, ";")
                dbAccess.executeSQL(table)
            }
        }
    }

    private fun addRecordsFromContainersToList(containers: List<LoggingRecord>) {
        for (logRecordContainer in containers) {
            logRecordContainer.record.let {
                val record = it
                if(record != null) {
                    val recordTs = record.timestamp ?: 0
                    val sqlTimestamp = Timestamp(recordTs)
                    addContainerToList(sqlTimestamp, logRecordContainer)
                }
            }
        }
    }

    fun writeRecordContainerToDb(containers: List<LoggingRecord>, timestamp: Long) {
        val sqlTimestamp = Timestamp(timestamp)
        // createTableList();
        for (logRecordContainer in containers) {
            addContainerToList(sqlTimestamp, logRecordContainer)
        }
        for (table in tableListChannel) {
            if (table.toString().contains("),")) {
                table.replace(table.length - 1, table.length, ";")
                dbAccess.executeSQL(table)
            }
        }
    }

    /**
     * Continues building the insert query and calls [.addValue] using the
     * appropriate parameters for the records' value type
     *
     * @param sqlTimestamp
     * The current timestamp
     * @param logRecordContainer
     * Container object for the record
     */
    private fun addContainerToList(sqlTimestamp: Timestamp, logRecordContainer: LoggingRecord) {
        val channelId = logRecordContainer.channelId
        val record = logRecordContainer.record
        if (record?.value != null) {
            val sbChannel = StringBuilder("INSERT INTO $channelId (time,flag,\"VALUE\") VALUES ")
            val sbQuery2 = StringBuilder()
            sbQuery2.append("('")
                .append(sqlTimestamp)
                .append("',")
                .append(logRecordContainer.record?.flag?.getCode()?.toInt())
                .append(',')
            sbChannel.append(sbQuery2)
            if (record.value != null) {
                SqlValues.appendValue(record.value!!, sbChannel)
            } else {
                sbChannel.append("NULL")
            }
            sbChannel.append("),")
            Collections.addAll(tableListChannel, sbChannel)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SqlWriter::class.java)
    }
}
