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
package org.openmuc.framework.config

import org.openmuc.framework.data.ValueType

interface ChannelConfig {
    @set:Throws(IdCollisionException::class)
    var id: String?
    var description: String?
    var channelAddress: String?
    var unit: String?
    var valueType: ValueType?
    var valueTypeLength: Int?
    var scalingFactor: Double?
    var valueOffset: Double?
    var isListening: Boolean?
    var samplingInterval: Int?
    var samplingTimeOffset: Int?
    var samplingGroup: String?
    var settings: String?
    var loggingInterval: Int?
    var reader: String?
    var loggingTimeOffset: Int?
    var isDisabled: Boolean?
    fun delete()
    val device: DeviceConfig?
    val serverMappings: List<ServerMapping?>?
    fun addServerMapping(serverMapping: ServerMapping?)
    fun deleteServerMappings(id: String?)
    var isLoggingEvent: Boolean?
    var loggingSettings: String?

    companion object {
        const val DISABLED_DEFAULT = false
        const val DESCRIPTION_DEFAULT = ""
        const val CHANNEL_ADDRESS_DEFAULT = ""
        const val UNIT_DEFAULT = ""
        @JvmField
        val VALUE_TYPE_DEFAULT = ValueType.DOUBLE
        const val BYTE_ARRAY_SIZE_DEFAULT = 10
        const val STRING_SIZE_DEFAULT = 10
        const val LISTENING_DEFAULT = false
        const val SAMPLING_INTERVAL_DEFAULT = -1
        const val SAMPLING_TIME_OFFSET_DEFAULT = 0
        const val SAMPLING_GROUP_DEFAULT = ""
        const val SETTINGS_DEFAULT = ""
        const val LOGGING_INTERVAL_DEFAULT = -1
        const val LOGGING_TIME_OFFSET_DEFAULT = 0
        const val LOGGING_EVENT_DEFAULT = false
        const val LOGGING_SETTINGS_DEFAULT = ""
        const val LOGGING_READER_DEFAULT = ""
    }
}
