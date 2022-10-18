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
package org.openmuc.framework.datalogger.ascii.test

import org.openmuc.framework.data.ValueType
import org.openmuc.framework.datalogger.spi.LogChannel

class LogChannelTestImpl(
    override val id: String,
    override val channelAddress: String,
    override val description: String,
    override val unit: String,
    override val valueType: ValueType,
    override val scalingFactor: Double,
    override val valueOffset: Double,
    override val isListening: Boolean,
    override val samplingInterval: Int,
    override val samplingTimeOffset: Int,
    override val samplingGroup: String,
    override val loggingInterval: Int,
    override val loggingTimeOffset: Int,
    override val isDisabled: Boolean,
    override val isLoggingEvent: Boolean
) : LogChannel {
    override var valueTypeLength: Int? = null
        private set

    constructor(
        id: String, channelAddress: String, description: String, unit: String, valueType: ValueType,
        scalingFactor: Double, valueOffset: Double, listening: Boolean, samplingInterval: Int,
        samplingTimeOffset: Int, samplingGroup: String, loggingInterval: Int, loggingTimeOffset: Int,
        disabled: Boolean, valueLength: Int, isEventLogging: Boolean
    ) : this(
        id, description, channelAddress, unit, valueType, scalingFactor, valueOffset, listening, samplingInterval,
        samplingTimeOffset, samplingGroup, loggingInterval, loggingTimeOffset, disabled, isEventLogging
    ) {
        valueTypeLength = valueLength
    }

    override val loggingSettings: String
        get() = "default"
}
