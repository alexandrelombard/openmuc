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
package org.openmuc.framework.lib.osgi.config

import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors

/**
 * Validates the config file for the registered pid e.g. load/org.openmuc.framework.myproject.MyClass.cfg
 */
class PropertyFileValidator {
    private val logger = LoggerFactory.getLogger(this.javaClass) as Logger
    private var serviceProperties: Map<String?, ServiceProperty?>? = null
    private var pid: String? = null
    private var existingProperties: List<String>? = null
    private var filename: String? = null
    fun initServiceProperties(serviceProperties: Map<String?, ServiceProperty?>?, pid: String) {
        this.pid = pid
        this.serviceProperties = serviceProperties
        File(RESOURCE_DIR).mkdir()
        filename = RESOURCE_DIR + pid + ".cfg"
        // File f = new File("load/" + pid + ".cfg");
        val f = File(filename)
        if (!f.exists()) {
            writePropertyFile()
        } else {
            readExistingProperties()
            checkForMissingPropertiesInFile()
            checkForUnsetPropertiesInFile()
            checkForDeprecatedProperties()
        }
    }

    private fun writePropertyFile() {
        try {
            logger.warn("New empty config file: {}", filename)
            val myWriter = FileWriter(filename)
            for (property in serviceProperties!!.values) {
                myWriter.write(property.toString())
            }
            myWriter.close()
        } catch (e: IOException) {
            logger.error("Failed to write property file", e)
        }
    }

    private fun readExistingProperties() {
        try {
            Files.lines(Paths.get(filename), Charset.defaultCharset()).use { lines ->
                existingProperties = lines.collect(
                    Collectors.toList()
                )
            }
        } catch (e: IOException) {
            logger.error(e.message)
        }
    }

    private fun checkForMissingPropertiesInFile() {
        for (key in serviceProperties!!.keys) {
            if (existingProperties!!.stream().noneMatch { s: String ->
                    s.contains(
                        key!!
                    )
                }) {
                logger.warn("{} is missing in {}", key, filename)
            }
        }
    }

    private fun checkForUnsetPropertiesInFile() {
        existingProperties!!.stream()
            .filter { prop: String -> prop.endsWith("=") }
            .forEach { prop: String? -> logger.warn("{} is not set {}", prop, pid) }
    }

    private fun checkForDeprecatedProperties() {
        for (existingProp in existingProperties!!) {
            if (!existingProp.contains("#") && !existingProp.isEmpty()
                && serviceProperties!!.keys.stream().noneMatch { key: String? ->
                    existingProp.contains(
                        key!!
                    )
                }
            ) {
                logger.warn("{} in {} is deprecated", existingProp, filename)
            }
        }
    }

    companion object {
        private val RESOURCE_DIR = System.getProperty("felix.fileinstall.dir") + "/"
    }
}
