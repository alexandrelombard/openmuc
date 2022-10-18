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
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.framework.driver.spi.ConnectionException
import org.slf4j.LoggerFactory

class StartListeningTask(
    dataManager: DataManager?, device: Device?,
    selectedChannels: List<ChannelRecordContainerImpl?>
) : DeviceTask(), ConnectedTask {
    var selectedChannels: List<ChannelRecordContainerImpl?>

    init {
        this.dataManager = dataManager
        this.device = device
        this.selectedChannels = selectedChannels
    }

    override fun run() {
        try {
            device!!.connection!!.startListening(selectedChannels as List<ChannelRecordContainer?>, dataManager)
        } catch (e: UnsupportedOperationException) {
            for (chRecContainer in selectedChannels) {
                chRecContainer.getChannel().setFlag(Flag.ACCESS_METHOD_NOT_SUPPORTED)
            }
        } catch (e: ConnectionException) {
            // Connection to device lost. Signal to device instance and end task
            // without notifying DataManager
            logger.warn(
                "Connection to device {} lost because {}. Trying to reconnect...", device!!.deviceConfig!!.getId(),
                e.message
            )
            device!!.disconnectedSignal()
            return
        } catch (e: Exception) {
            logger.error(
                "unexpected exception by startListeningFor function of driver: "
                        + device!!.deviceConfig!!.driverParent!!.getId(), e
            )
            // TODO set flag?
        }
        synchronized(dataManager!!.tasksFinished) { dataManager!!.tasksFinished.add(this) }
        dataManager!!.interrupt()
    }

    override val type: DeviceTaskType
        get() = DeviceTaskType.START_LISTENING_FOR

    override fun deviceNotConnected() {}

    companion object {
        private val logger = LoggerFactory.getLogger(StartListeningTask::class.java)
    }
}
