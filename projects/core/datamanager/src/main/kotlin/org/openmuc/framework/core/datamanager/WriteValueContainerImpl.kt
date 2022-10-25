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
package org.openmuc.framework.core.datamanager

import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Value
import org.openmuc.framework.dataaccess.WriteValueContainer
import org.openmuc.framework.driver.spi.ChannelValueContainer

class WriteValueContainerImpl(override val channel: ChannelImpl) :
    WriteValueContainer, ChannelValueContainer {
    override var value: Value? = null
    override var flag: Flag? = Flag.DRIVER_ERROR_UNSPECIFIED
    override var channelHandle: Any? = null
    override val channelAddress: String

    init {
        channelAddress = channel.config.channelAddress ?: ""
        this.channelHandle = channel.handle!!
    }
}
