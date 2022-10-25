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
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

/**
 * Manages properties, performs consistency checks and provides methods to access properties as int, string or boolean
 */
class PropertyHandler(settings: GenericSettings, pid: String) {
    val currentProperties: Map<String, ServiceProperty>
    private var configChanged: Boolean

    /**
     * @return **true** as long as the properties are identical to the one that were given to the constructor,
     * otherwise **false**
     */
    var isDefaultConfig: Boolean
        private set
    private val pid: String

    /**
     * Constructor
     *
     * @param settings
     * settings
     * @param pid
     * Name of class implementing ManagedService e.g. String pid = MyClass.class.getName()
     */
    init {
        currentProperties = settings.properties
        this.pid = pid
        configChanged = false
        isDefaultConfig = true
        initializePropertyFile()
    }

    private fun initializePropertyFile() {
        // FIXME check ConfigurationFileValidator and PropertyHandler for redundant checks. Maybe they could
        // use same methods?
        checkDirectory()
        // NOTE: Purpose of ConfigurationFileValidator is to initially compare file with settings class.
        // So ConfigurationFileValidator is only called ONCE at the beginning
        val serviceConfigurator = PropertyFileValidator()
        serviceConfigurator.initServiceProperties(currentProperties, pid)
    }

    private fun checkDirectory() {
        var propertyDir = System.getProperty(DEFAULT_PROPERTY_KEY)
        if (propertyDir == null) {
            propertyDir = DEFAULT_FOLDER
            System.setProperty(DEFAULT_PROPERTY_KEY, DEFAULT_FOLDER)
            logger.warn(
                "Missing systems.property for felix file install. Using default: \"{}={}\"",
                DEFAULT_PROPERTY_KEY, DEFAULT_FOLDER
            )
        }
        val path = Paths.get(propertyDir)
        if (!Files.exists(path)) {
            File(propertyDir).mkdir()
        }
    }

    @Throws(ServicePropertyException::class)
    fun processConfig(newConfig: DictionaryPreprocessor) {
        val newDictionary = newConfig.cleanedUpDeepCopyOfDictionary
        validateKeys(newDictionary)
        val oldProperties = copyOfProperties
        val keys = newDictionary.keys
        keys.forEach { key ->
            val property = currentProperties[key]
            val dictValue = newDictionary[key]

            if (dictValue != null && property != null) {
                applyNewValue(dictValue, key, property)
            }
        }
        configChanged = hasConfigChanged(oldProperties)
        if (isDefaultConfig && configChanged) {
            isDefaultConfig = false
        }
    }

    @Throws(ServicePropertyException::class)
    private fun applyNewValue(newDictValue: String, key: String, property: ServiceProperty) {
        if (property.isMandatory) {
            processMandatoryProperty(newDictValue, key, property)
        } else {
            property.update(newDictValue)
        }
    }

    @Throws(ServicePropertyException::class)
    private fun processMandatoryProperty(newDictValue: String, key: String, property: ServiceProperty) {
        if (newDictValue == "") {
            throw ServicePropertyException("mandatory property '$key' is empty")
        } else {
            property.update(newDictValue)
        }
    }

    @Throws(ServicePropertyException::class)
    private fun validateKeys(newDictionary: Map<String, *>) {
        checkForUnknownKeys(newDictionary)
        checkForMissingKeys(newDictionary)
    }

    /**
     * validate new keys (given by updated(dictionary...) against original keys (given by settings class)
     */
    @Throws(ServicePropertyException::class)
    private fun checkForMissingKeys(newDictionary: Map<String, *>) {
        for (originalKey in currentProperties.keys) {
            if (newDictionary.keys.toList().stream().noneMatch { t: String -> t == originalKey }) {
                throw ServicePropertyException(
                    "Missing Property: updated property dictionary doesn't contain key $originalKey"
                )
            }
        }
    }

    /**
     * validate original keys (given by settings class) against new keys (given by updated(dictionary...)
     */
    @Throws(ServicePropertyException::class)
    private fun checkForUnknownKeys(newDictionary: Map<String, *>) {
        val newKeys = newDictionary.keys
        newKeys.forEach {newKey ->
            if (!currentProperties.containsKey(newKey)) {
                throw ServicePropertyException(
                    "Unknown Property: '" + newKey
                            + "' New property key has been introduced, which is not part of settings class for " + pid
                )
            }
        }
    }

    private fun hasConfigChanged(oldProperties: HashMap<String, String>): Boolean {
        for ((oldKey, oldValue) in oldProperties) {
            val property = currentProperties?.get(oldKey)
            val newValue = property?.value
            if (oldValue != newValue) {
                return true
            }
        }
        return false
    }

    /**
     * Test if a key is contained in properties
     */
    fun hasValueForKey(key: String): Boolean {
        return currentProperties.containsKey(key)
    }

    /**
     * Returns a property as integer.
     *
     *
     * Possibly throws:
     *
     *
     * - [IllegalArgumentException] if the key does not exist in properties
     *
     *
     * - [NumberFormatException] if the key exists but cannot be cast to integer
     */
    fun getInt(key: String): Int {
        val prop = getOrThrow(key)
        return Integer.valueOf(prop.value)
    }

    /**
     * Returns a property as double.
     *
     *
     * Possibly throws:
     *
     *
     * - [IllegalArgumentException] if the key does not exist in properties
     *
     *
     * - [NumberFormatException] if the key exists but cannot be cast to integer
     */
    fun getDouble(key: String): Double {
        val prop = getOrThrow(key)
        return java.lang.Double.valueOf(prop.value)
    }

    /**
     * Returns a property as String.
     *
     *
     * Possibly throws:
     *
     *
     * - [IllegalArgumentException] if the key does not exist in properties
     */
    fun getString(key: String): String? {
        return getOrThrow(key).value
    }

    /**
     * Returns a property as boolean.
     *
     *
     * Possibly throws:
     *
     *
     * - [IllegalArgumentException] if the key does not exist in properties
     */
    fun getBoolean(key: String): Boolean {
        val prop = getOrThrow(key)
        return java.lang.Boolean.valueOf(prop.value)
    }

    private fun getOrThrow(key: String): ServiceProperty {
        return Optional.ofNullable(currentProperties[key])
            .orElseThrow { IllegalArgumentException("No value for key=$key") }
    }

    /**
     * @return a HashMap with value from type String not ServiceProperty! Full ServiceProperty object not necessary
     * here.
     */
    private val copyOfProperties: HashMap<String, String>
        get() {
            val oldProperties = HashMap<String, String>()
            for ((oldKey, value) in currentProperties) {
                val oldValue = value.value
                oldProperties[oldKey] = oldValue
            }
            return oldProperties
        }

    /**
     * @return **true** if config has changed after last [.processConfig] call,
     * otherwise **false**
     */
    fun configChanged(): Boolean {
        return configChanged
    }

    /**
     * Prints all keys and the corresponding values.
     *
     *
     * If the key contains "password", "*****" is shown instead of the corresponding value (which would be the
     * password).
     */
    override fun toString(): String {
        val sb = StringBuilder()
        for ((key, value) in currentProperties) {
            val propValue: String?
            propValue = if (key.contains("password")) {
                "*****"
            } else {
                value.value
            }
            sb.append("\n$key=$propValue")
        }
        return sb.toString()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PropertyHandler::class.java)
        private const val DEFAULT_FOLDER = "load/"
        private const val DEFAULT_PROPERTY_KEY = "felix.fileinstall.dir"
    }
}
