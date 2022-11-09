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

import org.openmuc.framework.data.Record
import org.openmuc.framework.datalogger.spi.DataLoggerService
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.openmuc.framework.datalogger.sql.utils.PropertyHandlerProvider
import org.openmuc.framework.datalogger.sql.utils.Settings
import org.openmuc.framework.lib.osgi.config.*
import org.osgi.service.cm.ManagedService
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

class SqlLoggerService : DataLoggerService, ManagedService {
    private val settings: Settings
    private val propertyHandler: PropertyHandler
    private val eventBuffer: MutableList<LoggingRecord>
    private var writer: SqlWriter? = null
    private var reader: SqlReader? = null
    private var dbAccess: DbAccess? = null
    private var channels: List<LogChannel> = listOf()

    /**
     * Starts the h2 server if conditions are met and connects to the database.
     */
    init {
        logger.info("Activating SQL Logger")
        settings = Settings()
        eventBuffer = ArrayList()
        val pid = SqlLoggerService::class.java.name
        propertyHandler = PropertyHandler(settings, pid)
        PropertyHandlerProvider.propertyHandler = propertyHandler
    }

    private fun connect() {
        dbAccess = DbAccess()
        writer = SqlWriter(dbAccess!!)
        reader = SqlReader(dbAccess!!)
        writeMetaToDb()
        writer!!.writeEventBasedContainerToDb(eventBuffer)
        eventBuffer.clear()
    }

    private fun writeMetaToDb() {
        val metaBuilder = MetaBuilder(channels, dbAccess!!)
        metaBuilder.writeMetaTable()
        val tableSetup = TableSetup(channels, dbAccess)
        tableSetup.createOpenmucTables()
    }

    /**
     * Closes the connection, stops the timer by calling its cancel Method and stops the h2 server, if the conditions
     * for each are met, if a connection exists
     */
    fun shutdown() {
        logger.info("Deactivating SQL Logger")
        if (dbAccess != null) {
            dbAccess!!.closeConnection()
        }
    }

    override val id: String
        get() = "sqllogger"

    /**
     * Creates the metadata table to create the tables for each data type and to insert info about all the channel into
     * the metadata table
     */
    override fun setChannelsToLog(channels: List<LogChannel>) {
        this.channels = channels
        if (dbAccess != null) {
            val tableSetup = TableSetup(channels, dbAccess)
            tableSetup.createOpenmucTables()
        }
    }

    override fun log(containers: List<LoggingRecord>, timestamp: Long) {
        if (writer == null) {
            logger.warn("Sql connection not established!")
            return
        }
        writer!!.writeRecordContainerToDb(containers, timestamp)
    }

    override fun logEvent(containers: List<LoggingRecord>, timestamp: Long) {
        if (writer == null) {
            logger.debug("Sql connection not established!")
            eventBuffer.addAll(containers)
            return
        }
        writer!!.writeEventBasedContainerToDb(containers)
    }

    override fun logSettingsRequired(): Boolean {
        return false
    }

    /**
     * @return the queried data
     */
    @Throws(IOException::class)
    override fun getRecords(channelId: String, startTime: Long, endTime: Long): List<Record> {
        var records: List<Record> = ArrayList()
        for (temp in channels) {
            if (temp.id == channelId) {
                records = reader!!.readRecordListFromDb(channelId, temp.valueType, startTime, endTime)
                break
            }
        }
        return records
    }

    /**
     * Returns the Record with the highest timestamp available in all logged data for the channel with the given
     * `channelId`. If there are multiple Records with the same timestamp, results will not be consistent.
     *
     * @param channelId
     * the channel ID.
     * @return the Record with the highest timestamp available in all logged data for the channel with the given
     * `channelId`. Null if no Record was found.
     * @throws IOException
     */
    @Throws(IOException::class)
    override fun getLatestLogRecord(channelId: String): Record? {
        var record: Record? = null
        for (temp in channels) {
            if (temp.id == channelId) {
                record = reader!!.readLatestRecordFromDb(channelId, temp.valueType)
                break
            }
        }
        return record
    }

    override fun updated(propertyDict: Dictionary<String, *>) {
        val dict = DictionaryPreprocessor(propertyDict)
        if (!dict.wasIntermediateOsgiInitCall()) {
            tryProcessConfig(dict)
        }
    }

    private fun tryProcessConfig(newConfig: DictionaryPreprocessor) {
        try {
            propertyHandler.processConfig(newConfig)
            if (propertyHandler.configChanged()) {
                applyConfigChanges()
            } else if (propertyHandler.isDefaultConfig && writer == null) {
                connect()
            }
        } catch (e: ServicePropertyException) {
            logger.error("update properties failed", e)
            shutdown()
        }
    }

    private fun applyConfigChanges() {
        logger.info("Configuration changed - new configuration {}", propertyHandler.toString())
        if (writer != null) {
            shutdown()
        }
        connect()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SqlLoggerService::class.java)
    }
}
