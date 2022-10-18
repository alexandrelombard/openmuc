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
import java.util.concurrent.CountDownLatch

class ReadTask(
    dataManager: DataManager?, device: Device?, selectedChannels: List<ChannelRecordContainerImpl>,
    readTaskFinishedSignal: CountDownLatch
) : DeviceTask(), ConnectedTask {
    private val readTaskFinishedSignal: CountDownLatch
    var channelRecordContainers: List<ChannelRecordContainerImpl>
    protected var methodNotExceptedExceptionThrown = false
    protected var unknownDriverExceptionThrown = false

    @Volatile
    protected var disabled = false
    var startedLate = false

    init {
        this.dataManager = dataManager
        this.device = device
        channelRecordContainers = selectedChannels
        this.readTaskFinishedSignal = readTaskFinishedSignal
    }

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
            for (driverChannel in channelRecordContainers) {
                driverChannel.setRecord(Record(Flag.ACCESS_METHOD_NOT_SUPPORTED))
            }
            readTaskFinishedSignal.countDown()
            synchronized(dataManager!!.disconnectedDevices) { dataManager!!.disconnectedDevices.add(device) }
            dataManager!!.interrupt()
            return
        } catch (e: Exception) {
            logger.warn("unexpected exception thrown by read funtion of driver ", e)
            unknownDriverExceptionThrown = true
        }
        taskFinished()
    }

    override val type: DeviceTaskType
        get() = DeviceTaskType.READ

    override fun deviceNotConnected() {
        for (recordContainer in channelRecordContainers) {
            recordContainer.setRecord(Record(Flag.COMM_DEVICE_NOT_CONNECTED))
        }
        taskAborted()
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    protected fun executeRead() {
        device!!.connection!!.read(channelRecordContainers as List<ChannelRecordContainer?>, true, "")
    }

    protected fun taskFinished() {
        disabled = true
        val now = System.currentTimeMillis()
        if (methodNotExceptedExceptionThrown) {
            for (driverChannel in channelRecordContainers) {
                driverChannel.setRecord(Record(null, now, Flag.ACCESS_METHOD_NOT_SUPPORTED))
            }
        } else if (unknownDriverExceptionThrown) {
            for (driverChannel in channelRecordContainers) {
                driverChannel.setRecord(Record(null, now, Flag.DRIVER_THREW_UNKNOWN_EXCEPTION))
            }
        }
        readTaskFinishedSignal.countDown()
        synchronized(dataManager!!.tasksFinished) { dataManager!!.tasksFinished.add(this) }
        dataManager!!.interrupt()
    }

    protected fun taskAborted() {
        readTaskFinishedSignal.countDown()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReadTask::class.java)
    }
}
