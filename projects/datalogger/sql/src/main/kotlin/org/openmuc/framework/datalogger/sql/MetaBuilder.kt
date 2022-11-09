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

import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.sql.utils.PropertyHandlerProvider
import org.openmuc.framework.datalogger.sql.utils.Settings
import org.openmuc.framework.datalogger.sql.utils.SqlValues
import org.openmuc.framework.lib.osgi.config.*
import org.slf4j.LoggerFactory
import java.sql.*

class MetaBuilder(private val channels: List<LogChannel>, private val dbAccess: DbAccess) {
    private val logger = LoggerFactory.getLogger(MetaBuilder::class.java)
    private var resultComparison: StringBuilder? = null
    private var sbMetaInsert: StringBuilder? = null
    private val url: String

    init {
        val propertyHandler = PropertyHandlerProvider.propertyHandler
        url = propertyHandler?.getString(Settings.URL)!!
    }

    fun writeMetaTable() {
        val metaStructure = createMetaStructure()
        writeMetaStructure(metaStructure)
        val metaInserts = createInsertsForMetaTable()
        if (!metaInserts.toString().isEmpty()) {
            dbAccess.executeSQL(StringBuilder("TRUNCATE TABLE openmuc_meta ;"))
            dbAccess.executeSQL(metaInserts)
        }
    }

    private fun writeMetaStructure(metaString: StringBuilder) {
        dbAccess.executeSQL(metaString)
        if (url.contains(SqlValues.POSTGRESQL) && !dbAccess.timeScaleIsActive()) {
            val sbIndex = StringBuilder("CREATE INDEX IF NOT EXISTS metaIndex ON openmuc_meta (time);")
            dbAccess.executeSQL(sbIndex)
        }
        if (url.contains(SqlValues.POSTGRESQL) && dbAccess.timeScaleIsActive()) {
            try {
                dbAccess.executeQuery(
                    StringBuilder("SELECT create_hypertable('openmuc_meta', 'time', if_not_exists => TRUE);")
                )
            } catch (e: SQLException) {
                logger.error(e.message)
            }
        }
    }

    private fun createMetaStructure(): StringBuilder {
        val sbMeta = StringBuilder()
        if (url.contains(SqlValues.POSTGRES)) {
            sbMeta.append("CREATE TABLE IF NOT EXISTS openmuc_meta (time TIMESTAMPTZ NOT NULL,\n")
        } else {
            sbMeta.append("CREATE TABLE IF NOT EXISTS openmuc_meta (time TIMESTAMP NOT NULL,\n")
        }
        var channelIdLength = 30
        var channelAdressLength = 30
        var unitLength = 15
        var samplingGroupLength = 30
        var descripionLength = 30
        for (channel in channels) {
            channelIdLength = updateLengthIfHigher(channel.id, channelIdLength)
            channelAdressLength = updateLengthIfHigher(channel.channelAddress, channelAdressLength)
            samplingGroupLength = updateLengthIfHigher(channel.samplingGroup, samplingGroupLength)
            unitLength = updateLengthIfHigher(channel.unit, unitLength)
            descripionLength = updateLengthIfHigher(channel.description, descripionLength)
        }

        // sbMeta.append("driverID VARCHAR(30) NULL,")
        // .append("deviceID VARCHAR(30) NULL,")
        sbMeta.append("channelID VARCHAR($channelIdLength) NOT NULL,")
            .append("channelAdress VARCHAR(" + channelAdressLength + SqlValues.NULL)
            .append("loggingInterval VARCHAR(10) NULL,")
            .append("loggingTimeOffset VARCHAR(10) NULL,")
            .append("unit VARCHAR(" + unitLength + SqlValues.NULL)
            .append("valueType VARCHAR(20) NULL,") // .append("valueTypeLength VARCHAR(5) NULL,")
            .append("scalingFactor VARCHAR(5) NULL,")
            .append("valueOffset VARCHAR(5) NULL,")
            .append("listening VARCHAR(5) NULL,")
            .append("loggingEvent VARCHAR(5) NULL,")
            .append("samplingInterval VARCHAR(10) NULL,")
            .append("samplingTimeOffset VARCHAR(10) NULL,")
            .append("samplingGroup VARCHAR(" + samplingGroupLength + SqlValues.NULL)
            .append("disabled VARCHAR(5) NULL,")
            .append("description VARCHAR($descripionLength) NULL")
        if (!url.contains(SqlValues.POSTGRESQL)) {
            sbMeta.append(",INDEX metaIndex(time),PRIMARY KEY (channelid, time));")
        } else {
            sbMeta.append(",PRIMARY KEY (channelid, time));")
        }
        return sbMeta
    }

    /**
     * checks if the attributes length exceeds the standard value
     *
     * @param stringValue
     * Attribute of the channel
     * @param currentLength
     * Current or standard column length
     * @return column length to be used
     */
    private fun updateLengthIfHigher(stringValue: String?, currentLength: Int): Int {
        var currentLength = currentLength
        if (stringValue != null) {
            val length = stringValue.length
            if (length > currentLength) {
                currentLength = length
            }
        }
        return currentLength
    }

    /**
     * Inserts the needed data into the table openmuc_meta when there are either no prior entries in it or if the
     * metadata has changed since the last entry
     */
    private fun createInsertsForMetaTable(): StringBuilder {
        if (channels.isEmpty()) {
            logger.warn("There are no channels for meta table")
        }
        resultComparison = StringBuilder()
        sbMetaInsert = StringBuilder(
            "INSERT INTO openmuc_meta (time,channelid,channelAdress,loggingInterval,loggingTimeOffset,unit,valueType,scalingFactor,valueOffset,listening,loggingEvent,samplingInterval,samplingTimeOffset,SamplingGroup,disabled,description) "
        )
        val sbMetaInsertValues = StringBuilder("VALUES (")
        for (logChannel in channels) {
            sbMetaInsertValues.append(parseChannelToMetaInsert(logChannel))
        }
        sbMetaInsertValues.replace(sbMetaInsertValues.length - 3, sbMetaInsertValues.length, ";")
        sbMetaInsert!!.append(sbMetaInsertValues)
        try {
            if (metaEntriesChanged()) {
                return sbMetaInsert!!
            }
        } catch (e: SQLException) {
            logger.warn("Exception at reading existing meta entries: {}", e.message)
            return sbMetaInsert!!
        }
        return StringBuilder()
    }

    // ToDO: needed?
    // WHERE time IN (SELECT * FROM (SELECT time FROM openmuc_meta ORDER BY time DESC LIMIT 1) as time)
    @get:Throws(SQLException::class)
    private val existingEntries: ResultSet?
        private get() {
            val sbMetaSelect = StringBuilder(
                "SELECT channelid,channelAdress,loggingInterval,loggingTimeOffset,unit,valueType,scalingFactor,valueOffset,"
                        + "listening,samplingInterval,samplingTimeOffset,SamplingGroup,disabled,description FROM"
                        + " openmuc_meta ;"
            )
            // ToDO: needed?
            // WHERE time IN (SELECT * FROM (SELECT time FROM openmuc_meta ORDER BY time DESC LIMIT 1) as time)
            return dbAccess.executeQuery(sbMetaSelect)
        }

    private fun parseChannelToMetaInsert(logChannel: LogChannel?): String {
        val varcharLength = dbAccess.getColumnLength(SqlValues.COLUMNS, "openmuc_meta")
        val sqlTimestamp = Timestamp(System.currentTimeMillis())
        val channelAsString = StringBuilder()
        val channelAddress = logChannel!!.channelAddress
        val scalingFactor = getScalingFactor(logChannel)
        val valueOffset = getValueOffset(logChannel)
        val listening = logChannel.isListening.toString()
        val samplingInterval = logChannel.samplingInterval.toString()
        val samplingTimeOffset = logChannel.samplingTimeOffset.toString()
        val samplingGroup = logChannel.samplingGroup
        val disabled = logChannel.isDisabled.toString()
        val loggingInterval = getLoggingInterval(logChannel)
        val valueTypeLength = getValueTypeLength(logChannel)
        val loggingTimeOffset = logChannel.loggingTimeOffset.toString()
        val channelId = logChannel.id
        val unit = logChannel.unit
        val vType = logChannel.valueType
        val valueType = vType.toString()
        val loggingEvent = logChannel.isLoggingEvent.toString()
        var description = logChannel.description
        if (description == "") {
            description = "-"
        }

        // buggy -> needed?
        // List<String> newColumnNames = Arrays.asList(channelId, channelAddress, loggingInterval, loggingTimeOffset,
        // unit,
        // valueType, scalingFactor, valueOffset, listening, loggingEvent, samplingInterval, samplingTimeOffset,
        // samplingGroup, disabled, description);
        //
        // for (int i = 0; i < newColumnNames.size(); ++i) {
        // if (varcharLength.get(i) < newColumnNames.get(i).length()) {
        // increaseDescriptionColumnLength("openmuc_meta", newColumnNames.get(i), COLUMNS.get(i));
        // }
        // }
        resultComparison!!.append(channelId)
            .append(',')
            .append(channelAddress)
            .append(',')
            .append(loggingInterval)
            .append(',')
            .append(loggingTimeOffset)
            .append(',')
            .append(unit)
            .append(',')
            .append(valueType)
            .append(',') // .append(' ')
            // .append(valueTypeLength)
            // .append(',')
            .append(scalingFactor)
            .append(',')
            .append(valueOffset)
            .append(',')
            .append(listening)
            .append(',')
            .append(loggingEvent)
            .append(',')
            .append(samplingInterval)
            .append(',')
            .append(samplingTimeOffset)
            .append(',')
            .append(samplingGroup)
            .append(',')
            .append(disabled)
            .append(',')
            .append(description)
            .append(',')
        channelAsString.append('\'')
            .append(sqlTimestamp)
            .append("',\'")
            .append(channelId)
            .append("','")
            .append(channelAddress)
            .append("','")
            .append(loggingInterval)
            .append("','")
            .append(loggingTimeOffset)
            .append("','")
            .append(unit)
            .append("','")
            .append(valueType)
            .append("','") // .append(' ')
            // .append(valueTypeLength)
            // .append("','")
            .append(scalingFactor)
            .append("','")
            .append(valueOffset)
            .append("','")
            .append(listening)
            .append("','")
            .append(loggingEvent)
            .append("','")
            .append(samplingInterval)
            .append("','")
            .append(samplingTimeOffset)
            .append("','")
            .append(samplingGroup)
            .append("','")
            .append(disabled)
            .append("','")
            .append(description)
            .append("'), (")
        return channelAsString.toString()
    }

    @Throws(SQLException::class)
    private fun metaEntriesChanged(): Boolean {
        val existingEntries = existingEntries
        val metaOfExistingEntries = existingEntries!!.metaData
        val colCount = metaOfExistingEntries.columnCount
        var noEntriesExists = true
        if (colCount <= 0) {
            return true
        }
        while (existingEntries.next()) {
            noEntriesExists = false
            val entry = StringBuilder()
            for (index in 1..colCount) {
                entry.append(existingEntries.getString(index))
                entry.append(",")
            }
            if (entry != null && !resultComparison.toString().contains(entry)) {
                return true
            }
        }
        return noEntriesExists
    }

    /**
     * returns the value offset attribute of the channel
     *
     * @param logChannel
     * channel to be logged
     * @return the value offset attribute of the channel
     */
    private fun getValueOffset(logChannel: LogChannel?): String {
        val valueOffset: String
        valueOffset = if (logChannel!!.valueOffset == null) {
            "0"
        } else {
            logChannel.valueOffset.toString()
        }
        return valueOffset
    }

    /**
     * returns the scaling factor attribute of the channel
     *
     * @param logChannel
     * channel to be logged
     * @return the scaling factor attribute of the channel
     */
    private fun getScalingFactor(logChannel: LogChannel?): String {
        val scalingFactor: String
        scalingFactor = if (logChannel!!.scalingFactor == null) {
            "0"
        } else {
            logChannel.scalingFactor.toString()
        }
        return scalingFactor
    }

    /**
     * returns the logging interval attribute of the channel
     *
     * @param logChannel
     * channel to be logged
     * @return the logging interval attribute of the channel
     */
    private fun getLoggingInterval(logChannel: LogChannel?): String {
        val loggingInterval: String
        loggingInterval = if (logChannel!!.loggingInterval == null) {
            "0"
        } else {
            logChannel.loggingInterval.toString()
        }
        return loggingInterval
    }

    /**
     * returns the valuetype length attribute of the channel
     *
     * @param logChannel
     * channel to be logged
     * @return the valuetype length attribute of the channel
     */
    private fun getValueTypeLength(logChannel: LogChannel?): String {
        val valueTypeLength: String
        valueTypeLength = if (logChannel!!.valueTypeLength == null) {
            "0"
        } else {
            logChannel.valueTypeLength.toString()
        }
        return valueTypeLength
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
    private fun increaseDescriptionColumnLength(table: String, column: String, columnName: String) {
        val sbNewVarcharLength = StringBuilder()
        if (url.contains(SqlValues.MYSQL)) {
            sbNewVarcharLength.append("ALTER TABLE $table MODIFY $columnName VARCHAR (")
        } else {
            sbNewVarcharLength.append("ALTER TABLE $table ALTER COLUMN $columnName TYPE VARCHAR (")
        }
        sbNewVarcharLength.append(column.length).append(");")
        dbAccess.executeSQL(sbNewVarcharLength)
    }
}
