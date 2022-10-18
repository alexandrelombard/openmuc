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

import org.openmuc.framework.config.ChannelConfig
import org.openmuc.framework.config.IdCollisionException
import org.openmuc.framework.lib.rest1.exceptions.RestConfigIsNotCorrectException

object RestChannelConfigMapper {
    fun getRestChannelConfig(cc: ChannelConfig): RestChannelConfig {
        val rcc = RestChannelConfig()
        rcc.channelAddress = cc.channelAddress
        rcc.description = cc.description
        rcc.isDisabled = cc.isDisabled
        rcc.id = cc.id
        rcc.isListening = cc.isListening
        rcc.loggingInterval = cc.loggingInterval
        rcc.loggingTimeOffset = cc.loggingTimeOffset
        rcc.loggingSettings = cc.loggingSettings
        rcc.samplingGroup = cc.samplingGroup
        rcc.samplingInterval = cc.samplingInterval
        rcc.samplingTimeOffset = cc.samplingTimeOffset
        rcc.scalingFactor = cc.scalingFactor
        rcc.serverMappings = cc.serverMappings
        rcc.settings = cc.settings
        rcc.unit = cc.unit
        rcc.valueOffset = cc.valueOffset
        rcc.valueType = cc.valueType
        rcc.valueTypeLength = cc.valueTypeLength
        rcc.isLoggingEvent = cc.isLoggingEvent
        return rcc
    }

    @Throws(IdCollisionException::class, RestConfigIsNotCorrectException::class)
    fun setChannelConfig(cc: ChannelConfig?, rcc: RestChannelConfig?, idFromUrl: String) {
        if (cc == null) {
            throw RestConfigIsNotCorrectException("ChannelConfig is null!")
        }
        if (rcc == null) {
            throw RestConfigIsNotCorrectException()
        }
        if (rcc.id != null && !rcc.id.isEmpty() && idFromUrl != rcc.id) {
            cc.id = rcc.id
        }
        cc.channelAddress = rcc.channelAddress
        cc.description = rcc.description
        cc.isDisabled = rcc.isDisabled
        cc.isListening = rcc.isListening
        cc.loggingInterval = rcc.loggingInterval
        cc.loggingTimeOffset = rcc.loggingTimeOffset
        cc.isLoggingEvent = rcc.isLoggingEvent
        cc.loggingSettings = rcc.loggingSettings
        cc.samplingGroup = rcc.samplingGroup
        cc.samplingInterval = rcc.samplingInterval
        cc.samplingTimeOffset = rcc.samplingTimeOffset
        cc.scalingFactor = rcc.scalingFactor
        val serverMappings = rcc.serverMappings
        if (serverMappings != null) {
            for (serverMapping in cc.serverMappings!!) {
                cc.deleteServerMappings(serverMapping!!.id)
            }
            for (restServerMapping in serverMappings) {
                cc.addServerMapping(restServerMapping)
            }
        }
        cc.settings = rcc.settings
        cc.unit = rcc.unit
        cc.valueOffset = rcc.valueOffset
        cc.valueType = rcc.valueType
        cc.valueTypeLength = rcc.valueTypeLength
    }
}
