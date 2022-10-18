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
import org.slf4j.LoggerFactory
import java.io.*
import java.net.URLEncoder
import java.nio.charset.Charset
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class FileObjectProxy(rootNodePath: String?) {
    private val rootNode: File
    private var openFilesHM: HashMap<String, FileObjectList>
    private val encodedLabels: HashMap<String?, String?>
    private val sdf: SimpleDateFormat
    private val date: Date
    private val timer: Timer
    private var days: MutableList<File>? = null
    private var size: Long = 0

    /*
     * Flush Period in Seconds. if flush_period == 0 -> write directly to disk.
     */
    private var flush_period = 0
    private var limit_days = 0
    private var limit_size = 0
    private var max_open_files = 0
    private var strCurrentDay: String? = null
    private var currentDayFirstTS: Long = 0
    private var currentDayLastTS: Long = 0

    /**
     * Creates an instance of a FileObjectProxy<br></br>
     * The rootNodePath (output folder) usually is specified in JVM flag: org.openmuc.mux.dbprovider.slotsdb.dbfolder
     *
     * @param rootNodePath
     * root node path
     */
    init {
        var rootNodePath = rootNodePath
        timer = Timer()
        date = Date()
        sdf = SimpleDateFormat("yyyyMMdd")
        if (!rootNodePath!!.endsWith("/")) {
            rootNodePath += "/"
        }
        logger.info("Storing to: $rootNodePath")
        rootNode = File(rootNodePath)
        rootNode.mkdirs()
        openFilesHM = HashMap()
        encodedLabels = HashMap()
        loadDays()
        if (SlotsDb.Companion.FLUSH_PERIOD != null) {
            flush_period = SlotsDb.Companion.FLUSH_PERIOD.toInt()
            logger.info("Flushing Data every: " + flush_period + "s. to disk.")
            createScheduledFlusher()
        } else {
            logger.info("No Flush Period set. Writing Data directly to disk.")
        }
        if (SlotsDb.Companion.DATA_LIFETIME_IN_DAYS != null) {
            limit_days = SlotsDb.Companion.DATA_LIFETIME_IN_DAYS.toInt()
            logger.info("Maximum lifetime of stored Values: $limit_days Days.")
            createScheduledDeleteJob()
        } else {
            logger.info("Maximum lifetime of stored Values: UNLIMITED Days.")
        }
        if (SlotsDb.Companion.MAX_DATABASE_SIZE != null) {
            limit_size = SlotsDb.Companion.MAX_DATABASE_SIZE.toInt()
            if (limit_size < SlotsDb.Companion.MINIMUM_DATABASE_SIZE) {
                limit_size = SlotsDb.Companion.MINIMUM_DATABASE_SIZE
            }
            logger.info("Size Limit: $limit_size MB.")
            createScheduledSizeWatcher()
        } else {
            logger.info("Size Limit: UNLIMITED MB.")
        }
        if (SlotsDb.Companion.MAX_OPEN_FOLDERS != null) {
            max_open_files = SlotsDb.Companion.MAX_OPEN_FOLDERS.toInt()
            logger.info("Maximum open Files for Database changed to: $max_open_files")
        } else {
            max_open_files = SlotsDb.Companion.MAX_OPEN_FOLDERS_DEFAULT
            logger.info("Maximum open Files for Database is set to: $max_open_files (default).")
        }
    }

    /*
     * loads a sorted list of all days in SLOTSDB. Necessary for search- and delete jobs.
     */
    private fun loadDays() {
        days = Vector()
        for (f in rootNode.listFiles()) {
            if (f.isDirectory) {
                days.add(f)
            }
        }
        days = sortFolders(days)
    }

    private fun sortFolders(days: MutableList<File>): MutableList<File> {
        Collections.sort(days) { f1, f2 ->
            var i = 0
            try {
                i = java.lang.Long.valueOf(sdf.parse(f1.name).time).compareTo(sdf.parse(f2.name).time)
            } catch (e: ParseException) {
                logger.error("Error during sorting Files: Folder doesn't match yyyymmdd Format?")
            }
            i
        }
        return days
    }

    /**
     * Creates a Thread, that causes Data Streams to be flushed every x-seconds.<br></br>
     * Define flush-period in seconds with JVM flag: org.openmuc.mux.dbprovider.slotsdb.flushperiod
     */
    private fun createScheduledFlusher() {
        timer.schedule(Flusher(), flush_period * 1000L, flush_period * 1000L)
    }

    internal inner class Flusher : TimerTask() {
        override fun run() {
            try {
                flush()
            } catch (e: IOException) {
                logger.error("Flushing Data failed in IOException: " + e.message)
            }
        }
    }

    private fun createScheduledDeleteJob() {
        timer.schedule(
            DeleteJob(),
            SlotsDb.Companion.INITIAL_DELAY.toLong(),
            SlotsDb.Companion.DATA_EXPIRATION_CHECK_INTERVAL.toLong()
        )
    }

    internal inner class DeleteJob : TimerTask() {
        override fun run() {
            try {
                deleteFoldersOlderThen(limit_days)
            } catch (e: IOException) {
                logger.error("Deleting old Data failed in IOException: " + e.message)
            }
        }

        @Throws(IOException::class)
        private fun deleteFoldersOlderThen(limit_days: Int) {
            val limit = Calendar.getInstance()
            limit.timeInMillis = System.currentTimeMillis() - 86400000L * limit_days
            val iterator: Iterator<File> = days!!.iterator()
            try {
                while (iterator.hasNext()) {
                    val curElement = iterator.next()
                    if (sdf.parse(curElement.name).time + 86400000 < limit
                            .timeInMillis
                    ) { /*
                                                   * compare folder 's oldest value to limit
                                                   */
                        logger.info(
                            "Folder: " + curElement.name + " is older then " + limit_days
                                    + " Days. Will be deleted."
                        )
                        deleteRecursiveFolder(curElement)
                    } else {
                        /* oldest existing Folder is not to be deleted yet */
                        break
                    }
                }
                loadDays()
            } catch (e: ParseException) {
                logger.error("Error during sorting Files: Any Folder doesn't match yyyymmdd Format?")
            }
        }
    }

    private fun createScheduledSizeWatcher() {
        timer.schedule(
            SizeWatcher(),
            SlotsDb.Companion.INITIAL_DELAY.toLong(),
            SlotsDb.Companion.DATA_EXPIRATION_CHECK_INTERVAL.toLong()
        )
    }

    internal inner class SizeWatcher : TimerTask() {
        override fun run() {
            try {
                while (getDiskUsage(rootNode) / 1000000 > limit_size && days!!.size >= 2) { /*
                                                  * avoid deleting current folder
                                                  */
                    deleteOldestFolder()
                }
            } catch (e: IOException) {
                logger.error("Deleting old Data failed in IOException: " + e.message)
            }
        }

        @Throws(IOException::class)
        private fun deleteOldestFolder() {
            if (days!!.size >= 2) {
                logger.info(
                    "Exceeded Maximum Database Size: $limit_size MB. Current size: $size" / 1000000
                            + " MB. Deleting: " + days!![0].canonicalPath
                )
                deleteRecursiveFolder(days!![0])
                days!!.removeAt(0)
                clearOpenFilesHashMap()
            }
        }
    }

    @Synchronized
    private fun deleteRecursiveFolder(folder: File) {
        if (folder.exists()) {
            for (f in folder.listFiles()) {
                if (f.isDirectory) {
                    deleteRecursiveFolder(f)
                    if (f.delete()) {
                    }
                } else {
                    f.delete()
                }
            }
            folder.delete()
        }
    }

    /*
     * recursive function to get the size of a folder. sums up all files. needs an initial LONG to store size to.
     */
    @Throws(IOException::class)
    private fun getDiskUsage(folder: File): Long {
        size = 0
        recursive_size_walker(folder)
        return size
    }

    @Throws(IOException::class)
    private fun recursive_size_walker(folder: File) {
        for (f in folder.listFiles()) {
            size += f.length()
            if (f.isDirectory) {
                recursive_size_walker(f)
            }
        }
    }

    /**
     * Appends a new Value to Slots Database.
     *
     * @param id
     * ID
     * @param value
     * Value
     * @param timestamp
     * time stamp
     * @param state
     * State
     * @param storingPeriod
     * storing period
     * @throws IOException
     * if an I/O error occurs.
     */
    @Synchronized
    @Throws(IOException::class)
    fun appendValue(id: String?, value: Double, timestamp: Long, state: Byte, storingPeriod: Long) {
        var id = id
        var toStoreIn: FileObject? = null
        id = encodeLabel(id)
        val strDate = getStrDate(timestamp)

        /*
         * If there is no FileObjectList for this folder, a new one will be created. (This will be the first value
         * stored for this day) Eventually existing FileObjectLists from the day before will be flushed and closed. Also
         * the Hashtable size will be monitored, to not have too many opened Filestreams.
         */if (!openFilesHM.containsKey(id + strDate)) {
            deleteEntryFromLastDay(timestamp, id)
            controlHashtableSize()
            val first = FileObjectList(rootNode.path + "/" + strDate + "/" + id)
            openFilesHM[id + strDate] = first

            /*
             * If FileObjectList for this label does not contain any FileObjects yet, a new one will be created. Data
             * will be stored and List reloaded for next Value to store.
             */if (first.size() == 0) {
                toStoreIn = FileObject(
                    rootNode.path + "/" + strDate + "/" + id + "/" + timestamp + SlotsDb.Companion.FILE_EXTENSION
                )
                toStoreIn.createFileAndHeader(timestamp, storingPeriod)
                toStoreIn.append(value, timestamp, state)
                toStoreIn.close() /* close() also calls flush(). */
                openFilesHM[id + strDate]!!.reLoadFolder()
                return
            }
        }

        /*
         * There is a FileObjectList for this day.
         */
        val listToStoreIn = openFilesHM[id + strDate]
        if (listToStoreIn!!.size() > 0) {
            toStoreIn = listToStoreIn.currentFileObject

            /*
             * If StartTimeStamp is newer then the Timestamp of the value to store, this value can't be stored.
             */if (toStoreIn.startTimeStamp > timestamp) {
                return
            }
        }
        if (toStoreIn == null) {
            throw IOException("\"Store in\" is null.")
        }

        /*
         * The storing Period may have changed. In this case, a new FileObject must be created.
         */if (toStoreIn.storingPeriod == storingPeriod || toStoreIn.storingPeriod == 0L) {
            toStoreIn = openFilesHM[id + strDate].getCurrentFileObject()
            toStoreIn.append(value, timestamp, state)
            if (flush_period == 0) {
                toStoreIn.flush()
            } else {
                return
            }
        } else {
            /*
             * Intervall changed -> create new File (if there are no newer values for this day, or file)
             */
            if (toStoreIn.timestampForLatestValue < timestamp) {
                toStoreIn = FileObject(
                    rootNode.path + "/" + strDate + "/" + id + "/" + timestamp + SlotsDb.Companion.FILE_EXTENSION
                )
                toStoreIn.createFileAndHeader(timestamp, storingPeriod)
                toStoreIn.append(value, timestamp, state)
                if (flush_period == 0) {
                    toStoreIn.flush()
                }
                openFilesHM[id + strDate]!!.reLoadFolder()
            }
        }
    }

    @Throws(IOException::class)
    private fun encodeLabel(label: String?): String? {
        var encodedLabel = encodedLabels[label]
        if (encodedLabel == null) {
            encodedLabel = URLEncoder.encode(label, Charset.defaultCharset().toString()) // encodes label to supported
            // String for Filenames.
            encodedLabels[label] = encodedLabel
        }
        return encodedLabel
    }

    @Synchronized
    @Throws(IOException::class)
    fun read(label: String?, timestamp: Long): Record? {
        // label = URLEncoder.encode(label,Charset.defaultCharset().toString());
        // //encodes label to supported String for Filenames.
        var label = label
        label = encodeLabel(label)
        val strDate = getStrDate(timestamp)
        if (!openFilesHM.containsKey(label + strDate)) {
            controlHashtableSize()
            val fol = FileObjectList(rootNode.path + "/" + strDate + "/" + label)
            openFilesHM[label + strDate] = fol
        }
        val toReadFrom = openFilesHM[label + strDate]!!.getFileObjectForTimestamp(timestamp)
        return toReadFrom?.read(timestamp)
    }

    @Synchronized
    @Throws(IOException::class)
    fun read(label: String?, start: Long, end: Long): List<Record?> {
        var label = label
        var end = end
        if (logger.isTraceEnabled) {
            logger.trace("Called: read($label, $start, $end)")
        }
        val toReturn: MutableList<Record?> = Vector()
        if (start > end) {
            logger.trace("Invalid Read Request: startTS > endTS")
            return toReturn
        }
        if (start == end) {
            toReturn.add(read(label, start)) // let other read function handle.
            toReturn.removeAll(setOf<Any?>(null))
            return toReturn
        }
        if (end > 50000000000000L) { /*
                                      * to prevent buffer overflows. in cases of multiplication
                                      */
            end = 50000000000000L
        }

        // label = URLEncoder.encode(label,Charset.defaultCharset().toString());
        // //encodes label to supported String for Filenames.
        label = encodeLabel(label)
        val strStartDate = getStrDate(start)
        val strEndDate = getStrDate(end)
        val toRead: MutableList<FileObject?> = Vector()
        if (strStartDate != strEndDate) {
            logger.trace("Reading Multiple Days. Scanning for Folders.")
            val days: MutableList<FileObjectList> = Vector()

            /*
             * Check for Folders matching criteria: Folder contains data between start & end timestamp. Folder contains
             * label.
             */
            var strSubfolder: String
            for (folder in rootNode.listFiles()) {
                if (folder.isDirectory) {
                    if (isFolderBetweenStartAndEnd(folder.name, start, end)) {
                        if (Arrays.asList(*folder.list()).contains(label)) {
                            strSubfolder = rootNode.path + "/" + folder.name + "/" + label
                            days.add(FileObjectList(strSubfolder))
                            logger.trace(strSubfolder + " contains " + SlotsDb.Companion.FILE_EXTENSION + " files to read from.")
                        }
                    }
                }
            }
            /*
             * Sort days, because rootNode.listFiles() is unsorted. FileObjectLists MUST be sorted, otherwise data
             * output wouldn't be sorted.
             */Collections.sort(days) { f1, f2 -> java.lang.Long.valueOf(f1.firstTS).compareTo(f2.firstTS) }

            /*
             * Create a list with all file-objects that must be read for this reading request.
             */if (days.size == 0) {
                return toReturn
            } else if (days.size == 1) {
                toRead.addAll(days[0].getFileObjectsFromTo(start, end))
            } else { // days.size()>1
                toRead.addAll(days[0].getFileObjectsStartingAt(start))
                for (i in 1 until days.size - 1) {
                    toRead.addAll(days[i].allFileObjects)
                }
                toRead.addAll(days[days.size - 1].getFileObjectsUntil(end))
            }
            toRead.removeAll(setOf<Any?>(null))
        } else { // Start == End Folder -> only 1 FileObjectList must be read.
            val folder = File(rootNode.path + "/" + strStartDate + "/" + label)
            val fol: FileObjectList
            if (folder.list() != null) {
                if (folder.list().size > 0) { // Are there Files in the
                    // folder, that should be read?
                    fol = FileObjectList(rootNode.path + "/" + strStartDate + "/" + label)
                    toRead.addAll(fol.getFileObjectsFromTo(start, end))
                }
            }
        }
        logger.trace("Found " + toRead.size + " " + SlotsDb.Companion.FILE_EXTENSION + " files to read from.")

        /*
         * Read all FileObjects: first (2nd,3rd,4th....n-1) last first and last will be read separately, to not exceed
         * timestamp range.
         */if (toRead != null) {
            if (toRead.size > 1) {
                toReturn.addAll(toRead[0]!!.read(start, toRead[0].getTimestampForLatestValue()))
                toRead[0]!!.close()
                for (i in 1 until toRead.size - 1) {
                    toReturn.addAll(toRead[i]!!.readFully())
                    toRead[i]!!.close()
                }
                toReturn.addAll(
                    toRead[toRead.size - 1]!!.read(toRead[toRead.size - 1].getStartTimeStamp(), end)
                )
                toRead[toRead.size - 1]!!.close()

                /*
                 * Some Values might be null -> remove
                 */toReturn.removeAll(setOf<Any?>(null))
            } else if (toRead.size == 1) { // single FileObject
                toReturn.addAll(toRead[0]!!.read(start, end))
                toReturn.removeAll(setOf<Any?>(null))
            }
        }
        logger.trace("Selected " + SlotsDb.Companion.FILE_EXTENSION + " files contain " + toReturn.size + " Values.")
        return toReturn
    }

    @Synchronized
    @Throws(IOException::class)
    fun readLatest(label: String?): Record? {
        var label = label
        if (logger.isTraceEnabled) {
            logger.trace("Called: readLatest($label)")
        }
        label = encodeLabel(label)

        /*
         * Checks for the folder with the latest day
         */
        var latestDay: Long = 0
        var latestFolder: File? = null
        for (folder in rootNode.listFiles()) {
            if (folder.isDirectory) {
                if (getFolderTimestamp(folder.name) > latestDay) {
                    latestFolder = folder
                    latestDay = getFolderTimestamp(folder.name)
                }
            }
        }
        if (latestFolder == null) {
            return null
        }

        /*
         * Get list of all fileObjects
         */
        val strSubfolder: String
        var fileObjects: FileObjectList? = null
        if (Arrays.asList(*latestFolder.list()).contains(label)) {
            strSubfolder = rootNode.path + "/" + latestFolder.name + "/" + label
            fileObjects = FileObjectList(strSubfolder)
            logger.trace(strSubfolder + " contains " + SlotsDb.Companion.FILE_EXTENSION + " files to read from.")
        }

        /*
         * For each file get the latest Record and compare those
         */
        var toRead: List<FileObject?>? = Vector()
        toRead = fileObjects.getAllFileObjects()
        var latestTimestamp: Long = 0
        var latestRecord: Record? = null
        for (file in toRead!!) {
            val timestamp = file.timestampForLatestValue
            if (timestamp > latestTimestamp) {
                latestTimestamp = timestamp
                latestRecord = file!!.read(timestamp) // function calculates closest available timestamp to given
                // timestamp. This should always be equal though
            }
        }
        return latestRecord
    }

    /**
     * Return timestamp of the folder with given name
     *
     * @param name
     * of the folder
     * @return timestamp in ms
     */
    private fun getFolderTimestamp(name: String): Long {
        try {
            sdf.parse(name)
        } catch (e: ParseException) {
            logger.error("Unable to parse Timestamp from: " + name + " folder. " + e.message)
        }
        return sdf.calendar.timeInMillis
    }

    /**
     * Parses a Timestamp in Milliseconds from a String in yyyyMMdd Format <br></br>
     * e.g.: 25.Sept.2011: 20110925 <br></br>
     * would return: 1316901600000 ms. equal to (25.09.2011 - 00:00:00) <br></br>
     *
     * @param name
     * in "yyyyMMdd" Format
     * @param start
     * start time stamp
     * @param end
     * end time stamp
     * @return boolean true if yes else false
     */
    private fun isFolderBetweenStartAndEnd(name: String, start: Long, end: Long): Boolean {
        try {
            sdf.parse(name)
        } catch (e: ParseException) {
            logger.error("Unable to parse Timestamp from: " + name + " folder. " + e.message)
        }
        return if (start <= sdf.calendar.timeInMillis + 86399999 && sdf.calendar.timeInMillis <= end) { // if
            // start
            // <=
            // folder.lastTSofDay
            // &&
            // folder.firstTSofDay
            // <=
            // end
            true
        } else false
    }

    /*
     * strCurrentDay holds the current Day in yyyyMMdd format, because SimpleDateFormat uses a lot cpu-time.
     * currentDayFirstTS and ... currentDayLastTS mark the first and last timestamp of this day. If a TS exceeds this
     * range, strCurrentDay, currentDayFirstTS, currentDayLastTS will be updated.
     */
    @Throws(IOException::class)
    private fun getStrDate(timestamp: Long): String? {
        if (strCurrentDay != null) {
            if (timestamp >= currentDayFirstTS && timestamp <= currentDayLastTS) {
                return strCurrentDay
            }
        }
        /*
         * timestamp for other day or not initialized yet.
         */date.time = timestamp
        strCurrentDay = sdf.format(date)
        try {
            currentDayFirstTS = sdf.parse(strCurrentDay).time
        } catch (e: ParseException) {
            logger.error("Unable to parse Timestamp from: $currentDayFirstTS String.")
        }
        currentDayLastTS = currentDayFirstTS + 86399999
        return strCurrentDay
    }

    @Throws(IOException::class)
    private fun deleteEntryFromLastDay(timestamp: Long, label: String?) {
        val strDate = getStrDate(timestamp - 86400000)
        if (openFilesHM.containsKey(label + strDate)) {
            /*
             * Value for new day has been registered! Close and flush all connections! Empty Hashtable!
             */
            clearOpenFilesHashMap()
            logger.info(
                "Started logging to a new Day. <$strDate> Folder has been closed and flushed completely."
            )
            /* reload days */loadDays()
        }
    }

    @Throws(IOException::class)
    private fun clearOpenFilesHashMap() {
        val itr: Iterator<FileObjectList> = openFilesHM.values.iterator()
        while (itr.hasNext()) { // kick out everything
            itr.next().closeAllFiles()
        }
        openFilesHM = HashMap()
    }

    @Throws(IOException::class)
    private fun controlHashtableSize() {
        /*
         * hm.size() doesn't really represent the number of open files, because it contains FileObjectLists, which may
         * contain 1 ore more FileObjects. In most cases, there is only 1 File in a List. There will be a second File if
         * storage Intervall is reconfigured. Continuous reconfiguring of measurement points may lead to a
         * "Too many open files" Exception. In this case SlotsDb.MAX_OPEN_FOLDERS should be decreased...
         */
        if (openFilesHM.size > max_open_files) {
            logger.debug(
                "More then " + max_open_files
                        + " DataStreams are opened. Flushing and closing some to not exceed OS-Limit."
            )
            val itr = openFilesHM.values.iterator()
            for (i in 0 until max_open_files / 5) { // randomly kick
                // out some of
                // the
                // FileObjectLists.
                // -> the needed
                // ones will be
                // reinitialized,
                // no problem
                // here.
                itr.next().closeAllFiles()
                itr.remove()
            }
        }
    }

    /**
     * Flushes all Datastreams from all FileObjectLists and FileObjects
     *
     * @throws IOException
     * if an I/O error occurs.
     */
    @Synchronized
    @Throws(IOException::class)
    fun flush() {
        val itr: Iterator<FileObjectList> = openFilesHM.values.iterator()
        while (itr.hasNext()) {
            itr.next().flush()
        }
        logger.info("Data from " + openFilesHM.size + " Folders flushed to disk.")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileObjectProxy::class.java)
    }
}
