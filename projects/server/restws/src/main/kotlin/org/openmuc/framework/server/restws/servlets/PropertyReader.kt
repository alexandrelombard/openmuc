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
package org.openmuc.framework.server.restws.servlets

import org.slf4j.LoggerFactory
import kotlin.Array
import kotlin.Exception
import kotlin.String

class PropertyReader private constructor() {
    // Map<ORIGIN, [METHODS, HEADERS]>
    var propertyMap: MutableMap<String, ArrayList<String>> = hashMapOf()
        private set
    var isCorsEnabled = false
        private set

    init {
        loadAllProperties()
    }

    private fun loadAllProperties() {
        propertyMap = HashMap()
        isCorsEnabled = getProperty("enable_cors").toBoolean()
        if (isCorsEnabled) {
            val urls = getPropertyList("url_cors")
            val methods = getPropertyList("methods_cors")
            val headers = getPropertyList("headers_cors")
            for (i in urls.indices) {
                val methodHeader = ArrayList<String>()
                methodHeader.add(methods[i])
                methodHeader.add(headers[i])
                propertyMap[urls[i]] = methodHeader
            }
        }
    }

    private fun getPropertyList(key: String): Array<String> {
        return getProperty(key).split(SEPERATOR.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
    }

    private fun getProperty(key: String): String {
        val baseKey = "org.openmuc.framework.server.restws."
        var property: String
        try {
            property = System.getProperty(baseKey + key)
        } catch (e: Exception) {
            logger.error("Necessary system properties for CORS handling are missing. {}{}", baseKey, key)
            isCorsEnabled = false
            property = ""
        }
        return property
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PropertyReader::class.java)
        private const val SEPERATOR = ";"
        val instance: PropertyReader = PropertyReader()
    }
}
