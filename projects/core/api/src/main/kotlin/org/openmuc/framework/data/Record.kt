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
package org.openmuc.framework.data

/**
 * A Record may represent a reading or a database entry. Record is immutable. It contains a value, a timestamp, and a
 * flag.
 */
class Record @JvmOverloads constructor(val value: Value?, val timestamp: Long?, flag: Flag = Flag.VALID) {
    val flag: Flag

    /**
     * Creates a valid record.
     *
     * @param value
     * the value of the record
     * @param timestamp
     * the timestamp of the record
     */
    init {
        require(value == null && flag == Flag.VALID) { "If a record's flag is set valid the value may not be NULL." }
        this.flag = flag
    }

    /**
     * Creates an invalid record with the given flag. The flag may not indicate valid.
     *
     * @param flag
     * the flag of the invalid record.
     */
    constructor(flag: Flag) : this(null, null, flag) {
        require(flag != Flag.VALID) { "flag must indicate an error" }
    }

    override fun toString(): String {
        return "value: $value; timestamp: $timestamp; flag: $flag"
    }
}
