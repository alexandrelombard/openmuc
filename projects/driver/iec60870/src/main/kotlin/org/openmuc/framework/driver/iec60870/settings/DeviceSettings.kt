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

class DeviceSettings(settings: String?) : GenericSetting() {
    protected var message_fragment_timeout = -1
    protected var cot_field_length = -1
    protected var common_address_field_length = -1
    protected var ioa_field_length = -1
    protected var max_time_no_ack_received = -1
    protected var max_time_no_ack_sent = -1
    protected var max_idle_time = -1
    protected var max_unconfirmed_ipdus_received = -1
    protected var stardt_con_timeout = 5000
    protected var read_timeout = 5000

    enum class Option(private val prefix: String, private val type: Class<*>, private val mandatory: Boolean) :
        OptionI {
        MESSAGE_FRAGMENT_TIMEOUT("mft", Int::class.java, false), COT_FIELD_LENGTH(
            "cfl",
            Int::class.java,
            false
        ),
        COMMON_ADDRESS_FIELD_LENGTH("cafl", Int::class.java, false), IOA_FIELD_LENGTH(
            "ifl",
            Int::class.java,
            false
        ),
        MAX_TIME_NO_ACK_RECEIVED("mtnar", Int::class.java, false), MAX_TIME_NO_ACK_SENT(
            "mtnas",
            Int::class.java,
            false
        ),
        MAX_IDLE_TIME("mit", Int::class.java, false), MAX_UNCONFIRMED_IPDUS_RECEIVED(
            "mupr",
            Int::class.java,
            false
        ),
        STARDT_CON_TIMEOUT("sct", Int::class.java, false), READ_TIMEOUT("rt", Int::class.java, false);

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

    protected fun option(): Class<out Enum<*>?> {
        return Option::class.java
    }

    init {
        parseFields(settings!!, Option::class.java)
    }

    fun messageFragmentTimeout(): Int {
        return message_fragment_timeout
    }

    fun cotFieldLength(): Int {
        return cot_field_length
    }

    fun commonAddressFieldLength(): Int {
        return common_address_field_length
    }

    fun ioaFieldLength(): Int {
        return ioa_field_length
    }

    fun maxTimeNoAckReceived(): Int {
        return max_time_no_ack_received
    }

    fun maxTimeNoAckSent(): Int {
        return max_time_no_ack_sent
    }

    fun maxIdleTime(): Int {
        return max_idle_time
    }

    fun maxUnconfirmedIPdusReceived(): Int {
        return max_unconfirmed_ipdus_received
    }

    fun stardtConTimeout(): Int {
        return stardt_con_timeout
    }

    /**
     * Optional: read/sampling timeout in milliseconds<br></br>
     * Default timeout is 5000 milliseconds
     *
     * @return read timeout in milliseconds
     */
    fun readTimeout(): Int {
        return read_timeout
    }
}
