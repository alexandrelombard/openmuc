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

class ChannelScanInfo @JvmOverloads constructor(
    channelAddress: String?, description: String, valueType: ValueType, valueTypeLength: Int,
    readable: Boolean = true, writable: Boolean = true, metaData: String = "", unit: String = ""
) {
    val channelAddress: String
    val description: String
    val valueType: ValueType
    val valueTypeLength: Int
    val isReadable: Boolean
    val isWritable: Boolean
    val metaData: String
    val unit: String

    init {
        require(!(channelAddress == null || channelAddress.isEmpty())) { "Channel Address may not be empty." }
        this.channelAddress = channelAddress
        this.description = description
        this.valueType = valueType
        this.valueTypeLength = valueTypeLength
        isReadable = readable
        isWritable = writable
        this.metaData = metaData
        this.unit = unit
    }
}
