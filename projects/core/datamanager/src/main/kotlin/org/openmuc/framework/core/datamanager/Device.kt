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

import org.openmuc.framework.config.*
import org.openmuc.framework.config.ChannelConfig
import org.openmuc.framework.config.DriverConfig
import org.openmuc.framework.config.DriverInfo
import org.openmuc.framework.config.ServerMapping
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.dataaccess.*
import org.openmuc.framework.dataaccess.Channel
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.driver.spi.*
import org.slf4j.LoggerFactory
import java.util.*

class Device(
    dataManager: DataManager, deviceConfig: DeviceConfigImpl, currentTime: Long,
    logChannels: MutableList<LogChannel>
) {
    private val eventList: LinkedList<DeviceEvent>
    private val taskList: LinkedList<DeviceTask>
    var deviceConfig: DeviceConfigImpl
    var dataManager: DataManager
    var connection: Connection? = null
    var state: DeviceState? = null
        private set

    init {
        eventList = LinkedList()
        taskList = LinkedList()
        this.dataManager = dataManager
        this.deviceConfig = deviceConfig
        if (deviceConfig.isDisabled) {
            state = DeviceState.DISABLED
            for (channelConfig in deviceConfig.channelConfigsById.values) {
                channelConfig.channel = ChannelImpl(
                    dataManager, channelConfig, ChannelState.DISABLED,
                    Flag.DISABLED, currentTime, logChannels
                )
            }
        } else if (deviceConfig.driverParent!!.activeDriver == null) {
            state = DeviceState.DRIVER_UNAVAILABLE
            logger.warn("No driver bundle available for configured driver: '{}'.", deviceConfig.driver?.id)
            for (channelConfig in deviceConfig.channelConfigsById.values) {
                channelConfig.channel = ChannelImpl(
                    dataManager, channelConfig, ChannelState.DRIVER_UNAVAILABLE,
                    Flag.DRIVER_UNAVAILABLE, currentTime, logChannels
                )
            }
        } else {
            state = DeviceState.CONNECTING
            for (channelConfig in deviceConfig.channelConfigsById.values) {
                channelConfig.channel = ChannelImpl(
                    dataManager, channelConfig, ChannelState.CONNECTING,
                    Flag.CONNECTING, currentTime, logChannels
                )
            }
        }
    }

    fun configChangedSignal(
        newDeviceConfig: DeviceConfigImpl,
        currentTime: Long,
        logChannels: MutableList<LogChannel>
    ) {
        val oldDeviceConfig = deviceConfig
        deviceConfig = newDeviceConfig
        newDeviceConfig.device = this
        if (state === DeviceState.DISABLED) {
            if (newDeviceConfig.isDisabled) {
                setStatesForNewDevice(
                    oldDeviceConfig, DeviceState.DISABLED, ChannelState.DISABLED, Flag.DISABLED,
                    currentTime, logChannels
                )
            } else if (deviceConfig.driverParent!!.activeDriver == null) {
                setStatesForNewDevice(
                    oldDeviceConfig, DeviceState.DRIVER_UNAVAILABLE, ChannelState.DRIVER_UNAVAILABLE,
                    Flag.DRIVER_UNAVAILABLE, currentTime, logChannels
                )
            } else {
                setStatesForNewDevice(
                    oldDeviceConfig, DeviceState.CONNECTING, ChannelState.CONNECTING, Flag.CONNECTING,
                    currentTime, logChannels
                )
                connect()
            }
        } else if (state === DeviceState.DRIVER_UNAVAILABLE) {
            if (newDeviceConfig.isDisabled) {
                setStatesForNewDevice(
                    oldDeviceConfig, DeviceState.DISABLED, ChannelState.DISABLED, Flag.DISABLED,
                    currentTime, logChannels
                )
            } else {
                setStatesForNewDevice(
                    oldDeviceConfig, DeviceState.DRIVER_UNAVAILABLE, ChannelState.DRIVER_UNAVAILABLE,
                    Flag.DRIVER_UNAVAILABLE, currentTime, logChannels
                )
            }
        } else if (state === DeviceState.CONNECTING) {
            setStatesForNewDevice(
                oldDeviceConfig, DeviceState.CONNECTING, ChannelState.CONNECTING, Flag.CONNECTING,
                currentTime, logChannels
            )
            if (newDeviceConfig.isDisabled) {
                addEvent(DeviceEvent.DISABLED)
            } else if (oldDeviceConfig.isDisabled) {
                eventList.remove(DeviceEvent.DISABLED)
            }
        } else if (state === DeviceState.DISCONNECTING) {
            setStatesForNewDevice(
                oldDeviceConfig, DeviceState.DISCONNECTING, ChannelState.DISCONNECTING,
                Flag.DISCONNECTING, currentTime, logChannels
            )
            if (newDeviceConfig.isDisabled) {
                addEvent(DeviceEvent.DISABLED)
            } else if (oldDeviceConfig.isDisabled) {
                eventList.remove(DeviceEvent.DISABLED)
            }
        } else if (state === DeviceState.WAITING_FOR_CONNECTION_RETRY) {
            if (newDeviceConfig.isDisabled) {
                setStatesForNewDevice(
                    oldDeviceConfig, DeviceState.DISABLED, ChannelState.DISABLED, Flag.DISABLED,
                    currentTime, logChannels
                )
            } else {
                setStatesForNewDevice(
                    oldDeviceConfig, DeviceState.WAITING_FOR_CONNECTION_RETRY,
                    ChannelState.WAITING_FOR_CONNECTION_RETRY, Flag.WAITING_FOR_CONNECTION_RETRY, currentTime,
                    logChannels
                )
            }
        } else {
            if (newDeviceConfig.isDisabled) {
                if (state === DeviceState.CONNECTED) {
                    eventList.add(DeviceEvent.DISABLED)
                    // TODO disable all readworkers
                    setStatesForNewConnectedDevice(
                        oldDeviceConfig, DeviceState.DISCONNECTING,
                        ChannelState.DISCONNECTING, Flag.DISCONNECTING, currentTime, logChannels
                    )
                    disconnect()
                } else {
                    // Adding the disabled event will automatically disconnect the device as soon as the active task is
                    // finished
                    eventList.add(DeviceEvent.DISABLED)
                    // Update channels anyway to update the log channels
                    updateChannels(
                        oldDeviceConfig, ChannelState.DISCONNECTING, Flag.DISCONNECTING, currentTime,
                        logChannels
                    )
                }
            } else {
                updateChannels(
                    oldDeviceConfig, ChannelState.CONNECTED, Flag.NO_VALUE_RECEIVED_YET, currentTime,
                    logChannels
                )
            }
        }
    }

    private fun updateChannels(
        oldDeviceConfig: DeviceConfigImpl?, channelState: ChannelState, flag: Flag,
        currentTime: Long, logChannels: MutableList<LogChannel>
    ) {
        var listeningChannels: MutableList<ChannelRecordContainerImpl> = arrayListOf()
        for ((key, newChannelConfig) in deviceConfig.channelConfigsById) {
            val oldChannelConfig = oldDeviceConfig!!.channelConfigsById[key]
            if (oldChannelConfig == null) {
                listeningChannels = initalizeListenChannels(
                    channelState, flag, currentTime, logChannels,
                    listeningChannels, newChannelConfig
                )
            } else {
                updateConfig(currentTime, logChannels, oldChannelConfig, newChannelConfig)
            }
        }
        addStartListeningTask(StartListeningTask(dataManager, this, listeningChannels))
    }

    private fun updateConfig(
        currentTime: Long, logChannels: MutableList<LogChannel>, oldChannelConfig: ChannelConfigImpl,
        newChannelConfig: ChannelConfigImpl
    ) {
        newChannelConfig.channel = oldChannelConfig.channel
        newChannelConfig.channel!!.config = newChannelConfig
        newChannelConfig.channel!!.setNewDeviceState(
            oldChannelConfig.state,
            newChannelConfig.channel!!.latestRecord!!.flag
        )
        val newChannelConfigChannel = newChannelConfig.channel!!
        if (!newChannelConfig.isDisabled && newChannelConfig.loggingInterval > 0) {
            dataManager.addToLoggingCollections(newChannelConfig.channel, currentTime)
            logChannels.add(newChannelConfig)
        } else if (!oldChannelConfig.isDisabled && oldChannelConfig.loggingInterval > 0) {
            dataManager.removeFromLoggingCollections(newChannelConfigChannel)
        } else if (!oldChannelConfig.isDisabled && oldChannelConfig.loggingInterval == ChannelConfig.LOGGING_INTERVAL_DEFAULT && oldChannelConfig.isLoggingEvent && oldChannelConfig.isListening) {
            logChannels.add(newChannelConfig)
        }
        if (newChannelConfig.isSampling) {
            dataManager.addToSamplingCollections(newChannelConfig.channel!!, currentTime)
        } else if (oldChannelConfig.isSampling) {
            dataManager.removeFromSamplingCollections(newChannelConfigChannel)
        }
        if (newChannelConfig.channelAddress != oldChannelConfig.channelAddress) {
            newChannelConfig.channel!!.handle = null
        }
    }

    private fun initalizeListenChannels(
        channelState: ChannelState,
        flag: Flag,
        currentTime: Long,
        logChannels: MutableList<LogChannel>,
        listeningChannels: MutableList<ChannelRecordContainerImpl> = LinkedList(),
        newChannelConfig: ChannelConfigImpl?
    ): MutableList<ChannelRecordContainerImpl> {
        val listeningChannels = listeningChannels
        if (newChannelConfig!!.state !== ChannelState.DISABLED) {
            if (newChannelConfig!!.isListening) {
                newChannelConfig.channel = ChannelImpl(
                    dataManager, newChannelConfig, ChannelState.LISTENING,
                    Flag.NO_VALUE_RECEIVED_YET, currentTime, logChannels
                )
                listeningChannels.add(newChannelConfig.channel!!.createChannelRecordContainer())
            } else if (newChannelConfig.samplingInterval != ChannelConfig.SAMPLING_INTERVAL_DEFAULT) {
                newChannelConfig.channel = ChannelImpl(
                    dataManager, newChannelConfig, ChannelState.SAMPLING,
                    Flag.NO_VALUE_RECEIVED_YET, currentTime, logChannels
                )
                dataManager.addToSamplingCollections(newChannelConfig.channel!!, currentTime)
            } else {
                newChannelConfig.channel = ChannelImpl(
                    dataManager, newChannelConfig, channelState, flag,
                    currentTime, logChannels
                )
            }
        } else {
            newChannelConfig!!.channel = ChannelImpl(
                dataManager, newChannelConfig, channelState, flag, currentTime,
                logChannels
            )
        }
        return listeningChannels
    }

    private fun addEvent(event: DeviceEvent) {
        val i: Iterator<DeviceEvent> = eventList.iterator()
        while (i.hasNext()) {
            if (i.next() == event) {
                return
            }
        }
        eventList.add(event)
    }

    private fun setStatesForNewDevice(
        oldDeviceConfig: DeviceConfigImpl?, deviceState: DeviceState,
        channelState: ChannelState, flag: Flag, currentTime: Long, logChannels: MutableList<LogChannel>
    ) {
        state = deviceState
        for ((key, channelConfigImpl) in deviceConfig.channelConfigsById) {
            val oldChannelConfig = oldDeviceConfig!!.channelConfigsById[key]
            if (oldChannelConfig == null) {
                channelConfigImpl.channel = ChannelImpl(
                    dataManager, channelConfigImpl, channelState, flag,
                    currentTime, logChannels
                )
            } else {
                channelConfigImpl.channel = oldChannelConfig.channel
                channelConfigImpl.channel!!.config = channelConfigImpl
                channelConfigImpl.channel!!.setNewDeviceState(channelState, flag)
                if (!channelConfigImpl.isDisabled) {
                    if (channelConfigImpl.loggingInterval > 0 && !channelConfigImpl.isLoggingEvent) {
                        dataManager.addToLoggingCollections(channelConfigImpl.channel, currentTime)
                        logChannels.add(channelConfigImpl)
                    } else if (channelConfigImpl.loggingInterval == ChannelConfig.LOGGING_INTERVAL_DEFAULT && channelConfigImpl.isLoggingEvent && channelConfigImpl.isListening) {
                        logChannels.add(channelConfigImpl)
                    }
                }
            }
        }
    }

    private fun setStatesForNewConnectedDevice(
        oldDeviceConfig: DeviceConfigImpl?, DeviceState: DeviceState,
        channelState: ChannelState, flag: Flag, currentTime: Long, logChannels: MutableList<LogChannel>
    ) {
        state = DeviceState
        var listeningChannels: MutableList<ChannelRecordContainerImpl?>? = null
        for ((key, newChannelConfig) in deviceConfig.channelConfigsById) {
            val oldChannelConfig = oldDeviceConfig!!.channelConfigsById[key]
            if (oldChannelConfig == null) {
                if (newChannelConfig.state !== ChannelState.DISABLED) {
                    if (newChannelConfig.isListening) {
                        if (listeningChannels == null) {
                            listeningChannels = LinkedList()
                        }
                        listeningChannels.add(newChannelConfig.channel!!.createChannelRecordContainer())
                        newChannelConfig.channel = ChannelImpl(
                            dataManager, newChannelConfig,
                            ChannelState.LISTENING, Flag.NO_VALUE_RECEIVED_YET, currentTime, logChannels
                        )
                    } else if (newChannelConfig.samplingInterval != ChannelConfig.SAMPLING_INTERVAL_DEFAULT) {
                        newChannelConfig.channel = ChannelImpl(
                            dataManager, newChannelConfig, ChannelState.SAMPLING,
                            Flag.NO_VALUE_RECEIVED_YET, currentTime, logChannels
                        )
                        dataManager.addToSamplingCollections(newChannelConfig.channel!!, currentTime)
                    } else {
                        newChannelConfig.channel = ChannelImpl(
                            dataManager, newChannelConfig, channelState, flag,
                            currentTime, logChannels
                        )
                    }
                } else {
                    newChannelConfig.channel = ChannelImpl(
                        dataManager, newChannelConfig, channelState, flag,
                        currentTime, logChannels
                    )
                }
            } else {
                newChannelConfig.channel = oldChannelConfig.channel
                newChannelConfig.channel!!.config = newChannelConfig
                newChannelConfig.channel!!.setNewDeviceState(channelState, flag)
                if (!newChannelConfig.isDisabled) {
                    if (newChannelConfig.loggingInterval > 0
                        && !newChannelConfig.isLoggingEvent
                    ) {
                        dataManager.addToLoggingCollections(newChannelConfig.channel, currentTime)
                        logChannels.add(newChannelConfig)
                    } else if (newChannelConfig
                            .loggingInterval == ChannelConfig.LOGGING_INTERVAL_DEFAULT && newChannelConfig.isLoggingEvent
                        && newChannelConfig.isListening
                    ) {
                        logChannels.add(newChannelConfig)
                    }
                }
            }
        }
    }

    private fun setStates(DeviceState: DeviceState, channelState: ChannelState, flag: Flag) {
        state = DeviceState
        for (channelConfig in deviceConfig.channelConfigsById.values) {
            if (channelConfig.state !== ChannelState.DISABLED) {
                channelConfig.state = channelState
                if (channelConfig.channel?.latestRecord?.flag !== Flag.SAMPLING_AND_LISTENING_DISABLED) {
                    channelConfig.channel!!.setFlag(flag)
                }
            }
        }
    }

    fun driverRegisteredSignal() {
        if (state === DeviceState.DRIVER_UNAVAILABLE) {
            setStates(DeviceState.CONNECTING, ChannelState.CONNECTING, Flag.CONNECTING)
            connect()
        } else if (state === DeviceState.DISCONNECTING) {
            eventList.add(DeviceEvent.DRIVER_REGISTERED)
        }
    }

    fun driverDeregisteredSignal() {
        if (state === DeviceState.DISABLED) {
            if (dataManager.activeDeviceCountDown-- == 0) {
                dataManager.driverRemovedSignal!!.countDown()
            }
        } else if (state === DeviceState.CONNECTED) {
            eventList.addFirst(DeviceEvent.DRIVER_DEREGISTERED)
            disableSampling()
            removeAllTasksOfThisDevice()
            setStates(DeviceState.DISCONNECTING, ChannelState.DISCONNECTING, Flag.DISCONNECTING)
            disconnect()
        } else if (state === DeviceState.WAITING_FOR_CONNECTION_RETRY) {
            disableConnectionRetry()
            setStates(DeviceState.DRIVER_UNAVAILABLE, ChannelState.DRIVER_UNAVAILABLE, Flag.DRIVER_UNAVAILABLE)
            dataManager.activeDeviceCountDown--
            if (dataManager.activeDeviceCountDown == 0) {
                dataManager.driverRemovedSignal!!.countDown()
            }
        } else {
            // add driver deregistered event always to the front of the queue
            eventList.addFirst(DeviceEvent.DRIVER_DEREGISTERED)
        }
    }

    fun deleteSignal() {
        if (state === DeviceState.DRIVER_UNAVAILABLE || state === DeviceState.DISABLED) {
            setDeleted()
        } else if (state === DeviceState.WAITING_FOR_CONNECTION_RETRY) {
            disableConnectionRetry()
            setDeleted()
        } else if (state === DeviceState.CONNECTED) {
            eventList.add(DeviceEvent.DELETED)
            setStates(DeviceState.DISCONNECTING, ChannelState.DISCONNECTING, Flag.DISCONNECTING)
            disconnect()
        } else {
            eventList.add(DeviceEvent.DELETED)
        }
    }

    fun connectedSignal(currentTime: Long) {
        taskList.removeFirst()
        if (eventList.isEmpty()) {
            setConnected(currentTime)
            executeNextTask()
        } else {
            handleEventQueueWhenConnected()
        }
    }

    fun connectFailureSignal(currentTime: Long) {
        taskList.removeFirst()
        if (eventList.isEmpty()) {
            setStates(
                DeviceState.WAITING_FOR_CONNECTION_RETRY, ChannelState.WAITING_FOR_CONNECTION_RETRY,
                Flag.WAITING_FOR_CONNECTION_RETRY
            )
            dataManager.addReconnectDeviceToActions(this, currentTime + deviceConfig.connectRetryInterval)
            removeAllTasksOfThisDevice()
        } else {
            handleEventQueueWhenDisconnected()
        }
    }

    // TODO is this function thread save?
    @Synchronized
    fun disconnectedSignal() {
        // TODO in rare cases where the RecordsReceivedListener causes the disconnectSignal while a SamplingTask is
        // still sampling this could cause problems
        removeAllTasksOfThisDevice()
        if (eventList.isEmpty()) {
            setStates(DeviceState.CONNECTING, ChannelState.CONNECTING, Flag.CONNECTING)
            connect()
        } else {
            handleEventQueueWhenDisconnected()
        }
    }

    fun connectRetrySignal() {
        setStates(DeviceState.CONNECTING, ChannelState.CONNECTING, Flag.CONNECTING)
        connect()
    }

    private fun disableConnectionRetry() {
        dataManager.removeFromConnectionRetry(this)
    }

    private fun setDeleted() {
        for (channelConfig in deviceConfig.channelConfigsById.values) {
            channelConfig.state = ChannelState.DELETED
            channelConfig.channel!!.setFlag(Flag.CHANNEL_DELETED)
            channelConfig.channel!!.handle = null
        }
        state = DeviceState.DELETED
    }

    private fun disableSampling() {
        for (channelConfig in deviceConfig.channelConfigsById.values) {
            if (channelConfig.state !== ChannelState.DISABLED) {
                if (channelConfig.state === ChannelState.SAMPLING) {
                    dataManager.removeFromSamplingCollections(channelConfig.channel!!)
                }
            }
        }
    }

    private fun handleEventQueueWhenConnected() {
        removeAllTasksOfThisDevice()
        setStates(DeviceState.DISCONNECTING, ChannelState.DISCONNECTING, Flag.DISCONNECTING)
        disconnect()
    }

    private fun removeAllTasksOfThisDevice() {
        val devTaskIter = taskList.iterator()
        while (devTaskIter.hasNext()) {
            val deviceTask = devTaskIter.next()
            if (deviceTask.device == this) {
                devTaskIter.remove()
            }
        }
        if (!taskList.isEmpty()) {
            val firstDevice = taskList.first
            if (!firstDevice.isAlive) {
                firstDevice.device.executeNextTask()
            }
        }
    }

    private fun handleEventQueueWhenDisconnected() {

        // DeviceEvent.DRIVER_DEREGISTERED will always be put at position 0
        if (eventList[0] == DeviceEvent.DRIVER_DEREGISTERED) {
            synchronized(dataManager.driverRemovedSignal!!) {
                dataManager.activeDeviceCountDown--
                if (dataManager.activeDeviceCountDown == 0) {
                    dataManager.driverRemovedSignal!!.countDown()
                }
            }
        }
        val lastEvent = eventList[eventList.size - 1]
        if (lastEvent == DeviceEvent.DRIVER_DEREGISTERED) {
            setStates(DeviceState.DRIVER_UNAVAILABLE, ChannelState.DRIVER_UNAVAILABLE, Flag.DRIVER_UNAVAILABLE)
        } else if (lastEvent == DeviceEvent.DISABLED) {
            setStates(DeviceState.DISABLED, ChannelState.DISABLED, Flag.DISABLED)
        } else if (lastEvent == DeviceEvent.DELETED) {
            setDeleted()
        }
        // TODO handle DeviceEvent.DRIVER_REGISTERED?
        eventList.clear()
    }

    private fun connect() {
        val connectTask = ConnectTask(
            deviceConfig.driverParent!!.activeDriver!!, deviceConfig.device!!,
            dataManager
        )
        taskList.add(connectTask)
        if (containsOneTask()) {
            dataManager.executor!!.execute(connectTask)
        }
    }

    private fun containsOneTask(): Boolean {
        return taskList.size == 1
    }

    private fun disconnect() {
        val disconnectTask = DisconnectTask(
            deviceConfig.driverParent!!.activeDriver!!, deviceConfig.device!!,
            dataManager
        )
        taskList.add(disconnectTask)
        if (containsOneTask()) {
            dataManager.executor!!.execute(disconnectTask)
        }
    }

    // only called by main thread
    fun addSamplingTask(samplingTask: SamplingTask, samplingInterval: Int): Boolean {
        return if (isConnected) {

            // new
            // if (deviceConfig.readTimeout == 0 || deviceConfig.readTimeout > samplingInterval) {
            // if (taskList.size() > 0) {
            // if (taskList.get(0).getType() == DeviceTaskType.READ) {
            // ((GenReadTask) taskList.get(0)).startedLate = true;
            // }
            // }
            // }
            // new
            taskList.add(samplingTask)
            if (containsOneTask()) {
                samplingTask.running = true
                state = DeviceState.READING
                dataManager.executor!!.execute(samplingTask)
            }
            true
        } else {
            samplingTask.deviceNotConnected()
            // TODO in the future change this to true
            true
        }
    }

    fun <T> addTask(deviceTask: T) where T : DeviceTask, T : ConnectedTask? {
        if (isConnected) {
            taskList.add(deviceTask)
            if (containsOneTask()) {
                state = deviceTask.type.resultingState
                dataManager.executor!!.execute(deviceTask)
            }
        } else {
            deviceTask!!.deviceNotConnected()
        }
    }

    fun taskFinished() {
        taskList.removeFirst()
        if (eventList.isEmpty()) {
            executeNextTask()
        } else {
            handleEventQueueWhenConnected()
        }
    }

    private fun executeNextTask() {
        if (!taskList.isEmpty()) {
            val firstTask = taskList.first
            if (firstTask.type == DeviceTaskType.SAMPLE) {
                (firstTask as SamplingTask).startedLate = true
            }
            state = firstTask.type.resultingState
            dataManager.executor!!.execute(firstTask)
        } else {
            state = DeviceState.CONNECTED
        }
    }

    fun removeTask(samplingTask: SamplingTask) {
        taskList.remove(samplingTask)
    }

    fun addStartListeningTask(startListenTask: StartListeningTask) {
        if (isConnected) {
            taskList.add(startListenTask)
            if (containsOneTask()) {
                state = DeviceState.STARTING_TO_LISTEN
                dataManager.executor!!.execute(startListenTask)
            }
        }
    }

    val isConnected: Boolean
        get() = state === DeviceState.CONNECTED || state === DeviceState.READING || state === DeviceState.SCANNING_FOR_CHANNELS || state === DeviceState.STARTING_TO_LISTEN || state === DeviceState.WRITING

    private fun setConnected(currentTime: Long) {
        var listeningChannels: MutableList<ChannelRecordContainerImpl>? = null
        for (channelConfig in deviceConfig.channelConfigsById.values) {
            if (channelConfig.state !== ChannelState.DISABLED) {
                if (channelConfig.isListening) {
                    if (listeningChannels == null) {
                        listeningChannels = LinkedList()
                    }
                    listeningChannels.add(channelConfig.channel!!.createChannelRecordContainer())
                    channelConfig.state = ChannelState.LISTENING
                    channelConfig.channel!!.setFlag(Flag.NO_VALUE_RECEIVED_YET)
                } else if (channelConfig.samplingInterval != ChannelConfig.SAMPLING_INTERVAL_DEFAULT) {
                    dataManager.addToSamplingCollections(channelConfig.channel!!, currentTime)
                    channelConfig.state = ChannelState.SAMPLING
                    channelConfig.channel!!.setFlag(Flag.NO_VALUE_RECEIVED_YET)
                } else {
                    channelConfig.state = ChannelState.CONNECTED
                }
            }
        }
        state = if (listeningChannels != null) {
            taskList.add(StartListeningTask(dataManager, this, listeningChannels))
            DeviceState.STARTING_TO_LISTEN
        } else {
            DeviceState.CONNECTED
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Device::class.java)
    }
}
