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

import org.openmuc.framework.config.DriverConfig
import org.openmuc.framework.config.IdCollisionException
import org.openmuc.framework.lib.rest1.exceptions.RestConfigIsNotCorrectException

object RestDriverConfigMapper {
    fun getRestDriverConfig(dc: DriverConfig): RestDriverConfig {
        val rdc = RestDriverConfig()
        rdc.id = dc.id
        rdc.connectRetryInterval = dc.connectRetryInterval
        rdc.isDisabled = dc.isDisabled
        rdc.samplingTimeout = dc.samplingTimeout
        return rdc
    }

    @Throws(IdCollisionException::class, RestConfigIsNotCorrectException::class)
    fun setDriverConfig(dc: DriverConfig?, rdc: RestDriverConfig?, idFromUrl: String) {
        if (dc == null) {
            throw RestConfigIsNotCorrectException("DriverConfig is null!")
        } else {
            if (rdc != null) {
                if (rdc.id != null && rdc.id != "" && idFromUrl != rdc.id) {
                    dc.id = rdc.id
                }
                dc.connectRetryInterval = rdc.connectRetryInterval
                dc.isDisabled = rdc.isDisabled
                dc.samplingTimeout = rdc.samplingTimeout
            } else {
                throw RestConfigIsNotCorrectException()
            }
        }
    }
}
