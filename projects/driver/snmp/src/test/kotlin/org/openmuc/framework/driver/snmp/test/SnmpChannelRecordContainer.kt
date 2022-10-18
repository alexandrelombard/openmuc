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
package org.openmuc.framework.driver.snmp.test

import org.openmuc.framework.data.Record
import org.openmuc.framework.dataaccess.*
import org.openmuc.framework.driver.spi.ChannelRecordContainer

class SnmpChannelRecordContainer : ChannelRecordContainer {
    private var snmpRecord: Record? = null
    private var snmpChannel: SnmpChannel? = null

    internal constructor() {}
    internal constructor(channel: SnmpChannel?) {
        snmpChannel = channel
    }

    internal constructor(record: Record?, channel: SnmpChannel?) {
        snmpChannel = channel
        snmpRecord = record
    }

    override fun getRecord(): Record? {
        return snmpRecord
    }

    override val channel: Channel?
        get() = snmpChannel
    override val channelAddress: String?
        get() = snmpChannel.getChannelAddress()

    // TODO Auto-generated method stub
    override var channelHandle: Any?
        get() =// TODO Auto-generated method stub
            null
        set(handle) {
            snmpChannel = handle as SnmpChannel?
        }

    override fun setRecord(record: Record?) {
        snmpRecord = Record(record!!.value, record.timestamp, record.flag)
    }

    override fun copy(): ChannelRecordContainer? {
        val clone = SnmpChannelRecordContainer()
        clone.channelHandle = snmpChannel
        clone.setRecord(snmpRecord)
        return clone
    }
}
