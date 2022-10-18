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

import org.openmuc.framework.data.*
import org.openmuc.framework.dataaccess.*
import java.io.IOException

class SnmpChannel : Channel {
    override val id: String? = null
    override var channelAddress: String? = null
        private set
    override val description: String? = null
    override val unit: String? = null
    override val valueType: ValueType? = null
    override val samplingInterval = 0
    override val samplingTimeOffset = 0
    override val samplingTimeout = 0
    override var deviceAddress: String? = null
        private set
    override val settings: String? = null

    internal constructor() {}
    internal constructor(deviceAddress: String?, address: String?) {
        channelAddress = address
        this.deviceAddress = deviceAddress
    }

    override val loggingSettings: String
        get() = ""
    override val loggingInterval: Int
        get() = 0
    override val loggingTimeOffset: Int
        get() = 0
    override val driverName: String?
        get() = null
    override val deviceName: String?
        get() = null
    override val deviceDescription: String?
        get() = null
    override val channelState: ChannelState?
        get() = null
    override val deviceState: DeviceState?
        get() = null

    override fun addListener(listener: RecordListener?) {}
    override fun removeListener(listener: RecordListener?) {}
    override val isConnected: Boolean
        get() = false
    override var latestRecord: Record?
        get() = null
        set(record) {}

    override fun write(value: Value?): Flag? {
        return null
    }

    override fun writeFuture(values: List<FutureValue?>?) {}
    override val writeContainer: WriteValueContainer?
        get() = null

    override fun read(): Record? {
        return null
    }

    override val readContainer: ReadRecordContainer?
        get() = null

    @Throws(DataLoggerNotAvailableException::class, IOException::class)
    override fun getLoggedRecord(time: Long): Record? {
        return null
    }

    @Throws(DataLoggerNotAvailableException::class, IOException::class)
    override fun getLoggedRecords(startTime: Long): List<Record?>? {
        return null
    }

    @Throws(DataLoggerNotAvailableException::class, IOException::class)
    override fun getLoggedRecords(startTime: Long, endTime: Long): List<Record?>? {
        return null
    }

    override val scalingFactor: Double
        get() = 0
}
