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

class WriteValueContainerImpl(override val channel: ChannelImpl) : WriteValueContainer, ChannelValueContainer {
    private override var value: Value? = null
    private override var flag = Flag.DRIVER_ERROR_UNSPECIFIED
    private var channelHandle: Any
    private val channelAddress: String

    init {
        channelAddress = channel.config!!.getChannelAddress()
        channelHandle = channel.handle!!
    }

    override fun setValue(value: Value?) {
        this.value = value
    }

    override fun getValue(): Value {
        return value!!
    }

    override fun getFlag(): Flag {
        return flag
    }

    override fun getChannelAddress(): String {
        return channelAddress
    }

    override fun getChannelHandle(): Any {
        return channelHandle
    }

    override fun setChannelHandle(handle: Any) {
        channelHandle = handle
    }

    override fun setFlag(flag: Flag) {
        this.flag = flag
    }
}
