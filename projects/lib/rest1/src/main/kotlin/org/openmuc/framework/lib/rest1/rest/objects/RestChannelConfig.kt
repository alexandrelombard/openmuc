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
    var id: String? = null
    var channelAddress: String? = null
    var description: String? = null
    var unit: String? = null
    var valueType: ValueType? = null
    var valueTypeLength: Int? = null
    var scalingFactor: Double? = null
    var valueOffset: Double? = null
    var isListening: Boolean? = null
    var samplingInterval: Int? = null
    var samplingTimeOffset: Int? = null
    var samplingGroup: String? = null
    var settings: String? = null
    var loggingInterval: Int? = null
    var loggingTimeOffset: Int? = null
    var loggingSettings: String? = null
    var isLoggingEvent: Boolean? = null
    var isDisabled: Boolean? = null
    var serverMappings: List<ServerMapping?>? = null
}
