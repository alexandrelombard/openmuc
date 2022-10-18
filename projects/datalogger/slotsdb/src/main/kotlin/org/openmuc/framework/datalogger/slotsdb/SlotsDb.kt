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
package org.openmuc.framework.datalogger.slotsdb

import org.openmuc.framework.data.Record
import org.openmuc.framework.data.Record.value
import org.openmuc.framework.data.TypeConversionException
import org.openmuc.framework.datalogger.spi.DataLoggerService
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

@Component
class SlotsDb : DataLoggerService {
    private val loggingIntervalsById = HashMap<String?, Int?>()
    private var fileObjectProxy: FileObjectProxy? = null
    @Activate
    protected fun activate(context: ComponentContext?) {
        var rootFolder = DB_ROOT_FOLDER
        if (rootFolder == null) {
            rootFolder = DEFAULT_DB_ROOT_FOLDER
        }
        fileObjectProxy = FileObjectProxy(rootFolder)
    }

    @Deactivate
    protected fun deactivate(context: ComponentContext?) {
        // TODO
    }

    override val id: String
        get() = "slotsdb"

    @Throws(IOException::class)
    override fun getRecords(channelId: String?, startTime: Long, endTime: Long): List<Record?>? {
        return fileObjectProxy!!.read(channelId, startTime, endTime)
    }

    @Throws(IOException::class)
    override fun getLatestLogRecord(channelId: String?): Record? {
        return fileObjectProxy!!.readLatest(channelId)
    }

    override fun setChannelsToLog(channels: List<LogChannel?>?) {
        loggingIntervalsById.clear()
        for (channel in channels!!) {
            loggingIntervalsById[channel!!.id] = channel.loggingInterval
        }
    }

    override fun log(containers: List<LoggingRecord?>?, timestamp: Long) {
        for (container in containers!!) {
            var value: Double
            value = if (container!!.record.value == null) {
                Double.NaN
            } else {
                try {
                    container.record.value!!.asDouble()
                } catch (e: TypeConversionException) {
                    Double.NaN
                }
            }

            // Long timestamp = container.getRecord().getTimestamp();
            // if (timestamp == null) {
            // timestamp = 0L;
            // }
            try {
                val channelId = container.channelId
                fileObjectProxy!!.appendValue(
                    channelId, value, timestamp, container.record.flag.getCode(),
                    loggingIntervalsById[channelId]!!.toLong()
                )
            } catch (e: IOException) {
                logger.error("error logging records", e)
            }
        }
    }

    override fun logEvent(containers: List<LoggingRecord?>?, timestamp: Long) {
        logger.warn("Event logging is not implemented, yet.")
    }

    override fun logSettingsRequired(): Boolean {
        return false
    }

    companion object {
        /*
     * File extension for SlotsDB files. Only these Files will be loaded.
     */
        const val FILE_EXTENSION = ".slots"

        /*
     * Root folder for SlotsDB files
     */
        val DB_ROOT_FOLDER = System
            .getProperty(SlotsDb::class.java.getPackage().name.lowercase(Locale.getDefault()) + ".dbfolder")

        /*
     * If no other root folder is defined, data will be stored to this folder
     */
        const val DEFAULT_DB_ROOT_FOLDER = "data/slotsdb/"

        /*
     * Root Folder for JUnit Testcases
     */
        const val DB_TEST_ROOT_FOLDER = "testdata/"

        /*
     * limit open files in Hashmap
     *
     * Default Linux Configuration: (should be below)
     *
     * host:/#> ulimit -aH [...] open files (-n) 1024 [...]
     */
        val MAX_OPEN_FOLDERS = System
            .getProperty(SlotsDb::class.java.getPackage().name.lowercase(Locale.getDefault()) + ".max_open_folders")
        const val MAX_OPEN_FOLDERS_DEFAULT = 512

        /*
     * configures the data flush period. The less you flush, the faster SLOTSDB will be. unset this System Property (or
     * set to 0) to flush data directly to disk.
     */
        val FLUSH_PERIOD = System
            .getProperty(SlotsDb::class.java.getPackage().name.lowercase(Locale.getDefault()) + ".flushperiod")

        /*
     * configures how long data will at least be stored in the SLOTSDB.
     */
        val DATA_LIFETIME_IN_DAYS = System
            .getProperty(SlotsDb::class.java.getPackage().name.lowercase(Locale.getDefault()) + ".limit_days")

        /*
     * configures the maximum Database Size (in MB).
     */
        val MAX_DATABASE_SIZE = System
            .getProperty(SlotsDb::class.java.getPackage().name.lowercase(Locale.getDefault()) + ".limit_size")

        /*
     * Minimum Size for SLOTSDB (in MB).
     */
        const val MINIMUM_DATABASE_SIZE = 2

        /*
     * Initial delay for scheduled tasks (size watcher, data expiration, etc.)
     */
        const val INITIAL_DELAY = 10000

        /*
     * Interval for scanning expired, old data. Set this to 86400000 to scan every 24 hours.
     */
        const val DATA_EXPIRATION_CHECK_INTERVAL = 5000
        private val logger = LoggerFactory.getLogger(SlotsDb::class.java)
    }
}
