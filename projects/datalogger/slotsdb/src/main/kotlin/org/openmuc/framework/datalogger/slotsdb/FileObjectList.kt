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

import org.openmuc.framework.data.Record.value
import java.io.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Class representing a folder in a SlotsDatabase.<br></br>
 * <br></br>
 * ./rootnode/20110129/ID1/1298734198000.opm <br></br>
 * /1298734598000.opm <br></br>
 * /ID2/ <br></br>
 * /20110130/ID1/ <br></br>
 * /ID2/ <br></br>
 * <br></br>
 * Usually there is only 1 File in a Folder/FileObjectList<br></br>
 * But there might be more then 1 file in terms of reconfiguration.<br></br>
 * <br></br>
 *
 */
class FileObjectList( // private File folder;
    private var foldername: String
) {
    private var files: MutableList<FileObject>? = null

    /**
     * Returns first recorded timestamp of oldest FileObject in this list. If List is empty, this timestamp will be set
     * to 00:00:00 o'clock
     *
     * @return first time stamp of oldest FileObject
     */
    var firstTS: Long = 0
        private set
    private var size = 0

    /**
     * Creates a FileObjectList<br></br>
     * and creates a FileObject for every File
     *
     * @param foldername
     * name of the folder
     * @throws IOException
     * if an I/O error occurs.
     */
    init {
        // File folder = new File(foldername);
        reLoadFolder(foldername)
    }

    /**
     * Reloads the List
     *
     * @param foldername
     * containing Files
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun reLoadFolder(foldername: String) {
        this.foldername = foldername
        reLoadFolder()
    }

    /**
     * Reloads the List
     *
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun reLoadFolder() {
        var folder: File? = File(foldername)
        files = Vector(1)
        if (folder!!.isDirectory) {
            for (file in folder.listFiles()) {
                if (file.length() >= 16) { // otherwise is corrupted or empty
                    // file.
                    val split = file.name.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if ("." + split[split.size - 1] == SlotsDb.Companion.FILE_EXTENSION) {
                        files.add(FileObject(file))
                    }
                } else {
                    file.delete()
                }
            }
            if (files.size > 1) {
                sortList(files)
            }
        }
        size = files.size

        /*
         * set first Timestamp for this FileObjectList if there are no files -> first TS = TS@ 00:00:00 o'clock.
         */firstTS = if (size == 0) {
            val sdf = SimpleDateFormat("yyyyMMdd")
            try {
                sdf.parse(folder.parentFile.name)
            } catch (e: ParseException) {
                throw IOException(
                    "Unable to parse Timestamp from folder: " + folder.parentFile.name
                            + ". Expected Folder in yyyyMMdd Format!"
                )
            }
            sdf.calendar.timeInMillis
        } else {
            files.get(0).startTimeStamp
        }
        folder = null
    }

    /*
     * bubble sort to sort files in directory. usually there is only 1 file, might be 2... will also work for more. but
     * not very fast.
     */
    private fun sortList(toSort: MutableList<FileObject>) {
        var j = 0
        var tmp: FileObject
        var switched = true
        while (switched) {
            switched = false
            j++
            for (i in 0 until toSort.size - j) {
                if (toSort[i].startTimeStamp > toSort[i + 1].startTimeStamp) {
                    tmp = toSort[i]
                    toSort[i] = toSort[i + 1]
                    toSort[i + 1] = tmp
                    switched = true
                }
            }
        }
    }

    /**
     * Returns the last created FileObject
     *
     * @return last created FileObject
     */
    val currentFileObject: FileObject
        get() = get(size - 1)

    /**
     * Returns the File Object at any position in list.
     *
     * @param position
     * position as int
     * @return FileObject at position
     */
    operator fun get(position: Int): FileObject {
        return files!![position]
    }

    /**
     * Returns the size (Number of Files in this Folder/FileObjectList)
     *
     * @return number of FileObjects
     */
    fun size(): Int {
        return size
    }

    /**
     * Closes all files in this List. This will also cause DataOutputStreams to be flushed.
     *
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun closeAllFiles() {
        for (f in files!!) {
            f.close()
        }
    }

    /**
     * Returns a FileObject in this List for a certain Timestamp. If there is no FileObject containing this Value, null
     * will be returned.
     *
     * @param timestamp
     * the timestamp of the FileObject
     * @return FileObject of timestamp
     */
    fun getFileObjectForTimestamp(timestamp: Long): FileObject? {
        if (files!!.size > 1) {
            for (f in files!!) {
                if (f.startTimeStamp <= timestamp && f.timestampForLatestValue >= timestamp) {
                    // File
                    // found!
                    return f
                }
            }
        } else if (files!!.size == 1) {
            if (files!![0].startTimeStamp <= timestamp
                && files!![0].timestampForLatestValue >= timestamp
            ) {
                // contains
                // this
                // TS
                return files!![0]
            }
        }
        return null
    }

    /**
     * Returns All FileObject in this List, which contain Data starting at given timestamp.
     *
     * @param timestamp
     * timestamp of FileObjects
     * @return list of all FileObjects with timestamp
     */
    fun getFileObjectsStartingAt(timestamp: Long): List<FileObject> {
        val toReturn: MutableList<FileObject> = Vector(1)
        for (i in files!!.indices) {
            if (files!![i].timestampForLatestValue >= timestamp) {
                toReturn.add(files!![i])
            }
        }
        return toReturn
    }

    /**
     * Returns all FileObjects in this List.
     *
     * @return list of all FileObjects
     */
    val allFileObjects: List<FileObject>?
        get() = files

    /**
     * Returns all FileObjects which contain Data before ending at given timestamp.
     *
     * @param timestamp
     * time stamp
     * @return FileObject until timestamp
     */
    fun getFileObjectsUntil(timestamp: Long): List<FileObject> {
        val toReturn: MutableList<FileObject> = Vector(1)
        for (i in files!!.indices) {
            if (files!![i].startTimeStamp <= timestamp) {
                toReturn.add(files!![i])
            }
        }
        return toReturn
    }

    /**
     * Returns all FileObjects which contain Data from start to end timestamps
     *
     * @param start
     * start time stamp
     * @param end
     * end time stamp
     * @return all FileObject between start and end
     */
    fun getFileObjectsFromTo(start: Long, end: Long): List<FileObject> {
        val toReturn: MutableList<FileObject> = Vector(1)
        if (files!!.size > 1) {
            for (i in files!!.indices) {
                if (files!![i].startTimeStamp <= start && files!![i].timestampForLatestValue >= start || files!![i].startTimeStamp <= end && files!![i].timestampForLatestValue >= end
                    || (files!![i].startTimeStamp >= start
                            && files!![i].timestampForLatestValue <= end)
                ) {
                    // needed files.
                    toReturn.add(files!![i])
                }
            }
        } else if (files!!.size == 1) {
            if (files!![0].startTimeStamp <= end && files!![0].timestampForLatestValue >= start) {
                // contains
                // this
                // TS
                toReturn.add(files!![0])
            }
        }
        return toReturn
    }

    /**
     * Flushes all FileObjects in this list.
     *
     * @throws IOException
     * if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun flush() {
        for (f in files!!) {
            f.flush()
        }
    }
}
