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
package org.openmuc.framework.lib.rest1.rest.objects

import com.google.gson.annotations.SerializedName
import org.openmuc.framework.data.Record.value

class RestUserConfig {
    var id: String? = null
        private set

    @SerializedName("password")
    var password: String? = null
        private set

    @SerializedName("oldPassword")
    val oldPassword: String? = null
    private var groups: Array<String>?
    var description: String? = null
        private set

    protected constructor() {}
    constructor(id: String?) {
        this.id = id
        password = "*****"
        groups = arrayOf("")
        description = ""
    }

    fun getGroups(): Array<String> {
        return if (groups != null) {
            groups!!.clone()
        } else {
            arrayOf()
        }
    }
}
