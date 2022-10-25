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

import org.openmuc.framework.config.ServerMapping
import org.openmuc.framework.data.ValueType

class RestChannelConfig {
    var id: String = ""
    var channelAddress: String = ""
    var description: String = ""
    var unit: String? = null
    var valueType: ValueType = ValueType.UNKNOWN
    var valueTypeLength: Int = 0
    var scalingFactor: Double? = null
    var valueOffset: Double? = null
    var isListening: Boolean = false
    var samplingInterval: Int = 0
    var samplingTimeOffset: Int = 0
    var samplingGroup: String? = null
    var settings: String? = null
    var loggingInterval: Int = 0
    var loggingTimeOffset: Int = 0
    var loggingSettings: String? = null
    var isLoggingEvent: Boolean = false
    var isDisabled: Boolean = false
    var serverMappings: List<ServerMapping> = arrayListOf()
}
