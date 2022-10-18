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
import org.slf4j.LoggerFactory
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.*
import java.util.*
import javax.swing.*

/**
 * Class providing a graphical UI to view the content of a .opm file
 *
 */
class SlotsDbVisualizer : JFrame() {
    var fc = JFileChooser()
    var file: File? = null
    var rowData = arrayOf(arrayOf("0", "0", "0"))
    var columnNames = arrayOf("Time", "Value", "State")
    var table = JTable(rowData, columnNames)
    var content = JScrollPane(table)

    init {
        val fileNameField = JTextField(15)
        fileNameField.isEditable = false
        val menuBar = JMenuBar()
        val fileMenu = JMenu("File")
        val openItem = JMenuItem("Open")
        openItem.addActionListener(openFileListener())
        menuBar.add(fileMenu)
        fileMenu.add(openItem)
        jMenuBar = menuBar
        contentPane = content
        title = SlotsDb.Companion.FILE_EXTENSION + " File Viewer"
        defaultCloseOperation = EXIT_ON_CLOSE
        pack()
        setLocationRelativeTo(null)
    }

    internal inner class openFileListener : ActionListener {
        override fun actionPerformed(e: ActionEvent) {
            val ret = fc.showOpenDialog(this@SlotsDbVisualizer)
            if (ret == JFileChooser.APPROVE_OPTION) {
                file = fc.selectedFile
                var res: List<Record?>? = null
                try {
                    val fo = FileObject(file)
                    res = fo.readFully()
                } catch (e1: IOException) {
                    logger.error("error read fully. ", e)
                    e1.printStackTrace()
                }
                if (res != null) {
                    val tblData = Array(res.size) { arrayOfNulls<String>(3) }
                    val cal = Calendar.getInstance()
                    for (i in res.indices) {
                        cal.timeInMillis = res[i]!!.timestamp!!

                        // tblData[i][0] = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(cal.getTime());
                        tblData[i][0] = res[i]!!.timestamp.toString()
                        tblData[i][1] = java.lang.Double.toString(res[i]!!.value!!.asDouble())
                        tblData[i][2] = Integer.toString(res[i]!!.flag.getCode().toInt())
                    }
                    table = JTable(tblData, columnNames)
                    content = JScrollPane(table)
                    contentPane = content
                    invalidate()
                    validate()
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SlotsDbVisualizer::class.java)
        private const val serialVersionUID = 1L
        @JvmStatic
        fun main(args: Array<String>) {
            val window: JFrame = SlotsDbVisualizer()
            window.isVisible = true
        }
    }
}
