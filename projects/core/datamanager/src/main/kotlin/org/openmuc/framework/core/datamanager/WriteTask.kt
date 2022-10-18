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
import org.openmuc.framework.driver.spi.ChannelValueContainer
import org.openmuc.framework.driver.spi.ConnectionException
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch

class WriteTask(
    dataManager: DataManager?, device: Device?, writeValueContainers: List<WriteValueContainerImpl?>,
    writeTaskFinishedSignal: CountDownLatch
) : DeviceTask(), ConnectedTask {
    private val writeTaskFinishedSignal: CountDownLatch
    var writeValueContainers: List<WriteValueContainerImpl?>

    init {
        this.dataManager = dataManager
        this.device = device
        this.writeTaskFinishedSignal = writeTaskFinishedSignal
        this.writeValueContainers = writeValueContainers
    }

    override fun run() {
        try {
            device!!.connection!!.write(writeValueContainers as List<ChannelValueContainer?>, null)
        } catch (e: UnsupportedOperationException) {
            for (valueContainer in writeValueContainers) {
                valueContainer!!.setFlag(Flag.ACCESS_METHOD_NOT_SUPPORTED)
            }
        } catch (e: ConnectionException) {
            // Connection to device lost. Signal to device instance and end task without notifying DataManager
            logger.warn(
                "Connection to device {} lost because {}. Trying to reconnect...", device!!.deviceConfig!!.getId(),
                e.message
            )
            for (valueContainer in writeValueContainers) {
                valueContainer!!.setFlag(Flag.CONNECTION_EXCEPTION)
            }
            writeTaskFinishedSignal.countDown()
            synchronized(dataManager!!.disconnectedDevices) { dataManager!!.disconnectedDevices.add(device) }
            dataManager!!.interrupt()
            return
        } catch (e: Exception) {
            logger.warn("unexpected exception thrown by write funtion of driver ", e)
            for (valueContainer in writeValueContainers) {
                valueContainer!!.setFlag(Flag.DRIVER_THREW_UNKNOWN_EXCEPTION)
            }
        }
        writeTaskFinishedSignal.countDown()
        synchronized(dataManager!!.tasksFinished) { dataManager!!.tasksFinished.add(this) }
        dataManager!!.interrupt()
    }

    override val type: DeviceTaskType
        get() = DeviceTaskType.WRITE

    /**
     * Writes entries, that the device is not connected.
     */
    override fun deviceNotConnected() {
        for (valueContainer in writeValueContainers) {
            valueContainer!!.setFlag(Flag.COMM_DEVICE_NOT_CONNECTED)
        }
        writeTaskFinishedSignal.countDown()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WriteTask::class.java)
    }
}
