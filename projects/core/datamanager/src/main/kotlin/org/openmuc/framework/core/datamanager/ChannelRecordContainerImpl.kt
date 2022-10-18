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
import org.openmuc.framework.data.Record
import org.openmuc.framework.driver.spi.ChannelRecordContainer

class ChannelRecordContainerImpl private constructor(override val channel: ChannelImpl, record: Record) :
    ChannelRecordContainer {
    private val channelAddress: String
    private override var record: Record
    private var channelHandle: Any

    constructor(channel: ChannelImpl) : this(channel, defaulRecord) {}

    init {
        channelAddress = channel.config!!.getChannelAddress()
        channelHandle = channel.handle!!
        this.record = record
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

    override fun copy(): ChannelRecordContainer {
        val copiedRecord = Record(record.value, record.timestamp, record.flag)
        return ChannelRecordContainerImpl(channel, copiedRecord)
    }

    override fun getRecord(): Record {
        return record
    }

    override fun setRecord(record: Record) {
        this.record = record
    }

    companion object {
        private val defaulRecord = Record(Flag.DRIVER_ERROR_CHANNEL_NOT_ACCESSIBLE)
    }
}
