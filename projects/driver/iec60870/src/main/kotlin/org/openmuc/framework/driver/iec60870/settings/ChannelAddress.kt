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
package org.openmuc.framework.driver.iec60870.settings

class ChannelAddress(channelAddress: String?) : GenericSetting() {
    protected var common_address = 1
    protected var type_id = 0
    protected var ioa = 0
    protected var data_type = "v"
    protected var index = -1
    protected var multiple = 1
    protected var select = false

    protected enum class Option(
        private val prefix: String,
        private val type: Class<*>,
        private val mandatory: Boolean
    ) : OptionI {
        COMMON_ADDRESS("ca", Int::class.java, true), TYPE_ID("t", Int::class.java, true), IOA(
            "ioa",
            Int::class.java,
            true
        ),
        DATA_TYPE("dt", String::class.java, false), INDEX("i", Int::class.java, false), MULTIPLE(
            "m",
            Int::class.java,
            false
        ),
        SELECT("s", Boolean::class.java, false);

        override fun prefix(): String {
            return prefix
        }

        override fun type(): Class<*> {
            return type
        }

        override fun mandatory(): Boolean {
            return mandatory
        }
    }

    init {
        parseFields(channelAddress!!, Option::class.java)
    }

    /**
     * Type Identification
     *
     * @return type id as integer
     */
    fun typeId(): Int {
        return type_id
    }

    /**
     * Information Object Address
     *
     * @return IOA as integer
     */
    fun ioa(): Int {
        return ioa
    }

    /**
     * The common address of device
     *
     * @return the comman address as integer
     */
    fun commonAddress(): Int {
        return common_address
    }

    /**
     * Meanings if boolean is TRUE<br></br>
     * v (value) / ts (timestamp) / iv (in/valid) / nt (not topical) / sb (substituted) / bl (blocked) / ov (overflow) /
     * ei (elapsed time invalid) / ca (counter was adjusted since last reading) / cy (counter overflow occurred in the
     * corresponding integration period)
     *
     * @return the data type as string
     */
    fun dataType(): String {
        return data_type
    }

    /**
     * Optional: only needed if VARIABLE STRUCTURE QUALIFIER of APSDU is 1
     *
     * @return the index as integer
     */
    fun index(): Int {
        return index
    }

    /**
     * Optional: Take multiple IOAs or indices to one value. Only few data types e.g. Binary Types.
     *
     * @return the multiple value as integer
     */
    fun multiple(): Int {
        return multiple
    }

    /**
     * Optional: Qualifier execute/select<br></br>
     * Default id false for execute
     *
     * @return true for select and false for execute
     */
    fun select(): Boolean {
        return select
    }
}
