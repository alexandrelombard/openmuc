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

import org.slf4j.LoggerFactory
import java.util.*

/**
 * Intention of this class is to provide a "Special Case Object" for invalid dictionaries. See
 * [.getCleanedUpDeepCopyOfDictionary]
 */
class DictionaryPreprocessor(newDictionary: MutableMap<String, *>) {
    /**
     * @return a cleaned up, deep copy of dictionary which is not null. It is at least an empty dictionary. NOTE values
     * to a key might be null)
     */
    var cleanedUpDeepCopyOfDictionary: MutableMap<String, String> = hashMapOf()
    private var osgiInit: Boolean

    init {

        // call this first before to print original dictionary passed by MangedService updated()
        logDebugPrintDictionary(newDictionary)
        osgiInit = false
        if (newDictionary == null || newDictionary.isEmpty()) {
            cleanedUpDeepCopyOfDictionary = hashMapOf()
        } else {
            // create deep copy to not manipulate the original dictionary
            val tempDict = getDeepCopy(newDictionary)

            // clean up dictionary - remove "osgi" framework related keys which may be inside the dictionary
            // given to the updated() method of ManagedService implementation. These entries can be ignored,
            // since they are not part of the actual configuration. Removing them here safes condition checks later.
            tempDict.remove("service.pid")
            tempDict.remove("felix.fileinstall.filename")
            cleanedUpDeepCopyOfDictionary = tempDict
        }
        if (cleanedUpDeepCopyOfDictionary.isEmpty()) {
            // either it was null or empty before or by removing service.pid it became empty
            osgiInit = true
        }
    }

    /**
     * @return **true** when it was a intermediate updated() call (MangedService) during starting the OSGi framework.
     * During start the updated() is called with an dictionary = null or with dictionary which has only one
     * entry with service.pid. With this flag you can ignore such calls.
     */
    fun wasIntermediateOsgiInitCall(): Boolean {
        return osgiInit
    }

    private fun getDeepCopy(propertyDict: MutableMap<String, *>): MutableMap<String, String> {
        val propertiesCopy: MutableMap<String, String> = hashMapOf()
        val keys = propertyDict.keys
        keys.forEach { key ->
            propertiesCopy[key] = propertyDict[key] as String
        }
        return propertiesCopy
    }

    /**
     * Method for debugging purposes to print whole dictionary
     *
     *
     * If the key contains "password", "*****" is shown instead of the corresponding value (which would be the
     * password).
     *
     * @param propertyDict
     */
    private fun logDebugPrintDictionary(propertyDict: MutableMap<String, *>) {
        if (logger.isDebugEnabled) {
            if (propertyDict != null) {
                val sb = StringBuilder()
                val keys = propertyDict.keys
                keys.forEach { key ->
                    val dictValue = propertyDict[key] as String
                    if (dictValue != null) {
                        if (key != null && key.contains("password")) {
                            sb.append("$key=*****\n")
                        } else {
                            sb.append("$key=$dictValue\n")
                        }
                    } else {
                        sb.append("$key=null\n")
                    }
                }
                logger.debug("Dictionary given by ManagedService updated(): \n{}", sb.toString())
            } else {
                logger.debug("Dictionary given by ManagedService updated(): is null")
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DictionaryPreprocessor::class.java)
    }
}
