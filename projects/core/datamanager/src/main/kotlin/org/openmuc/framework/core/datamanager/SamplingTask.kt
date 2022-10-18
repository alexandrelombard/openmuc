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
import org.openmuc.framework.driver.spi.ConnectionException
import org.slf4j.LoggerFactory

class SamplingTask(
    dataManager: DataManager?, device: Device?, selectedChannels: List<ChannelRecordContainerImpl?>,
    samplingGroup: String?
) : DeviceTask() {
    var channelRecordContainers: List<ChannelRecordContainerImpl?>
    private var methodNotExceptedExceptionThrown = false
    private var unknownDriverExceptionThrown = false

    @Volatile
    private var disabled = false
    var running = false
    var startedLate = false
    var samplingGroup: String?

    init {
        this.dataManager = dataManager
        this.device = device
        channelRecordContainers = selectedChannels
        this.samplingGroup = samplingGroup
    }

    // called by main thread
    fun storeValues() {
        if (disabled) {
            return
        }
        disabled = true
        if (methodNotExceptedExceptionThrown) {
            for (channelRecordContainer in channelRecordContainers) {
                channelRecordContainer.getChannel().setFlag(Flag.ACCESS_METHOD_NOT_SUPPORTED)
            }
        } else if (unknownDriverExceptionThrown) {
            for (channelRecordContainer in channelRecordContainers) {
                channelRecordContainer.getChannel().setFlag(Flag.DRIVER_THREW_UNKNOWN_EXCEPTION)
            }
        } else {
            for (channelRecordContainer in channelRecordContainers) {
                channelRecordContainer.getChannel().setNewRecord(channelRecordContainer!!.getRecord())
            }
        }
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    protected fun executeRead() {
        // TODO must pass containerListHandle
        device!!.connection!!.read(channelRecordContainers as List<ChannelRecordContainer?>, null, samplingGroup)
    }

    protected fun taskAborted() {}
    override fun run() {
        try {
            executeRead()
        } catch (e: UnsupportedOperationException) {
            methodNotExceptedExceptionThrown = true
        } catch (e: ConnectionException) {
            // Connection to device lost. Signal to device instance and end task without notifying DataManager
            logger.warn(
                "Connection to device {} lost because {}. Trying to reconnect...", device!!.deviceConfig!!.getId(),
                e.message
            )
            synchronized(dataManager!!.disconnectedDevices) { dataManager!!.disconnectedDevices.add(device) }
            dataManager!!.interrupt()
            return
        } catch (e: Exception) {
            logger.warn("unexpected exception thrown by read funtion of driver ", e)
            unknownDriverExceptionThrown = true
        }
        for (channelRecordContainer in channelRecordContainers) {
            channelRecordContainer.getChannel().handle = channelRecordContainer!!.channelHandle
        }
        synchronized(dataManager!!.samplingTaskFinished) { dataManager!!.samplingTaskFinished.add(this) }
        dataManager!!.interrupt()
    }

    // called by main thread
    fun timeout() {
        if (disabled) {
            return
        }
        disabled = true
        if (startedLate) {
            for (driverChannel in channelRecordContainers) {
                driverChannel.getChannel().setFlag(Flag.STARTED_LATE_AND_TIMED_OUT)
            }
        } else if (running) {
            for (driverChannel in channelRecordContainers) {
                driverChannel.getChannel().setFlag(Flag.TIMEOUT)
            }
        } else {
            for (driverChannel in channelRecordContainers) {
                driverChannel.getChannel().setFlag(Flag.DEVICE_OR_INTERFACE_BUSY)
            }
            device!!.removeTask(this)
        }
    }

    override val type: DeviceTaskType
        get() = DeviceTaskType.SAMPLE

    fun deviceNotConnected() {
        for (recordContainer in channelRecordContainers) {
            recordContainer!!.setRecord(Record(Flag.COMM_DEVICE_NOT_CONNECTED))
        }
        taskAborted()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SamplingTask::class.java)
    }
}
