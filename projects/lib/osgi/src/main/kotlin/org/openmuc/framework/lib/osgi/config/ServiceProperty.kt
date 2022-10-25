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
class ServiceProperty(key: String, description: String, defaultValue: String, mandatory: Boolean) {
    private var key: String = ""
        set(value) {
            require(value.isNotEmpty()) {
                // key is important - therefor raise exception
                "key must not be null or empty!"
            }
            field = value
        }
    private var description: String? = null
    private var defaultValue: String = ""
    val isMandatory: Boolean
    var value: String
        private set

    init {
        this.key = key
        this.defaultValue = defaultValue
        this.description = description
        isMandatory = mandatory
        value = this.defaultValue
    }

    fun update(value: String) {
        this.value = value
    }

    override fun toString(): String {
        var optional = "# "
        if (!isMandatory) {
            optional += "(Optional) "
        }
        return "$optional$description\n$key=$defaultValue\n"
    }
}
