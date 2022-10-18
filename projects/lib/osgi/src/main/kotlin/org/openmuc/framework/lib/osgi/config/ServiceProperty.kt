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

/**
 * Enriches the classical property (key=value) with meta data. A list of ServiceProperties can be managed by a Settings
 * class extending [GenericSettings]
 */
class ServiceProperty(key: String?, description: String?, defaultValue: String?, mandatory: Boolean) {
    private var key: String? = null
    private var description: String? = null
    private var defaultValue: String
    val isMandatory: Boolean
    var value: String
        private set

    init {
        setKey(key)
        setDescription(description)
        setDefaultValue(defaultValue)
        isMandatory = mandatory
        value = this.defaultValue
    }

    fun update(value: String?) {
        if (value == null) {
            // avoid later null checks
            this.value = ""
        } else {
            this.value = value
        }
    }

    fun getKey(): String? {
        return key
    }

    fun getDescription(): String? {
        return description
    }

    fun getDefaultValue(): String {
        return defaultValue
    }

    private fun setKey(key: String?) {
        require(!(key == null || key.isEmpty())) {
            // key is important - therefor raise exception
            "key must not be null or empty!"
        }
        this.key = key
    }

    private fun setDescription(description: String?) {
        if (description == null) {
            // description is optional, don't raise exception here, but change it to empty string
            // to avoid countless "null" checks later in classes using this.
            this.description = ""
        } else {
            this.description = description
        }
    }

    private fun setDefaultValue(defaultValue: String?) {
        if (defaultValue == null) {
            // defaultValue is optional, don't raise exception here, but change it to empty string
            // to avoid countless "null" checks later in classes using this.
            this.defaultValue = ""
        } else {
            this.defaultValue = defaultValue
        }
    }

    override fun toString(): String {
        var optional = "# "
        if (!isMandatory) {
            optional += "(Optional) "
        }
        return "$optional$description\n$key=$defaultValue\n"
    }
}
