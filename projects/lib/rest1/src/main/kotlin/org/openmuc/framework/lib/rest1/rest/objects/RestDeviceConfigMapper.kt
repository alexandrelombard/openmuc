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

import org.openmuc.framework.config.DeviceConfig
import org.openmuc.framework.config.IdCollisionException
import org.openmuc.framework.lib.rest1.exceptions.RestConfigIsNotCorrectException

object RestDeviceConfigMapper {
    fun getRestDeviceConfig(dc: DeviceConfig): RestDeviceConfig {
        val rdc = RestDeviceConfig()
        rdc.connectRetryInterval = dc.connectRetryInterval
        rdc.description = dc.description
        rdc.deviceAddress = dc.deviceAddress
        rdc.isDisabled(dc.isDisabled)
        rdc.id = dc.id
        rdc.samplingTimeout = dc.samplingTimeout
        rdc.settings = dc.settings
        return rdc
    }

    @Throws(IdCollisionException::class, RestConfigIsNotCorrectException::class)
    fun setDeviceConfig(dc: DeviceConfig, rdc: RestDeviceConfig, idFromUrl: String) {
        if (rdc.id != "" && idFromUrl != rdc.id) {
            dc.id = rdc.id
        }
        dc.connectRetryInterval = rdc.connectRetryInterval
        dc.description = rdc.description
        dc.deviceAddress = rdc.deviceAddress
        dc.isDisabled = rdc.disabled
        dc.samplingTimeout = rdc.samplingTimeout
        dc.settings = rdc.settings
    }
}
