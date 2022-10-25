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

import org.apache.felix.service.command.CommandProcessor
import org.openmuc.framework.config.*
import org.openmuc.framework.data.Flag
import org.openmuc.framework.dataaccess.*
import org.openmuc.framework.datalogger.spi.DataLoggerService
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.driver.spi.*
import org.openmuc.framework.server.spi.ServerMappingContainer
import org.openmuc.framework.server.spi.ServerService
import org.osgi.framework.BundleException
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.*
import org.slf4j.LoggerFactory
import java.io.*
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.Collectors
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactoryConfigurationError

@Component(
    service = [DataAccessService::class, ConfigService::class],
    immediate = true,
    property = [CommandProcessor.COMMAND_SCOPE + ":String=openmuc", CommandProcessor.COMMAND_FUNCTION + ":String=reload"]
)
class DataManager : Thread(), DataAccessService, ConfigService, RecordsReceivedListener {
    val connectedDevices = LinkedList<Device>()
    val disconnectedDevices = LinkedList<Device>()
    val connectionFailures = LinkedList<Device>()
    val samplingTaskFinished = LinkedList<SamplingTask>()
    val newWriteTasks = LinkedList<WriteTask>()
    val newReadTasks = LinkedList<ReadTask>()
    val tasksFinished = LinkedList<DeviceTask>()
    private val newDrivers: HashMap<String, DriverService> = LinkedHashMap()
    private val serverServices = HashMap<String, ServerService>()
    private val activeDrivers: MutableMap<String, DriverService> = LinkedHashMap()
    private val actions = LinkedList<Action>()
    private val configChangeListeners: MutableList<ConfigChangeListener> = LinkedList()
    private val newDataLoggers: MutableList<DataLoggerService> = LinkedList()
    private val activeDataLoggers: Deque<DataLoggerService> = LinkedBlockingDeque()
    private val receivedRecordContainers = LinkedList<List<ChannelRecordContainer>>()
    private val configLock = ReentrantLock()
    var dataLoggerRemovedSignal: CountDownLatch? = null

    @Volatile
    var activeDeviceCountDown = 0
    var executor: ThreadPoolExecutor? = null
    var driverRemovedSignal: CountDownLatch? = null

    @Volatile
    private var stopFlag = false

    // does not need to be a list because RemovedService() for driver services
    // are never called in parallel:
    @Volatile
    private var driverToBeRemovedId: String? = null

    @Volatile
    private var dataLoggerToBeRemoved: DataLoggerService? = null

    @Volatile
    private var newRootConfigWithoutDefaults: RootConfigImpl? = null

    @Volatile
    private var rootConfig: RootConfigImpl? = null

    @Volatile
    private var rootConfigWithoutDefaults: RootConfigImpl? = null
    private var configFile: File? = null

    @Volatile
    private var dataManagerActivated = false
    private var newConfigSignal: CountDownLatch? = null
    @Activate
    @Throws(
        TransformerFactoryConfigurationError::class,
        IOException::class,
        ParserConfigurationException::class,
        TransformerException::class,
        ParseException::class
    )
    protected fun activate() {
        var configFileName = System.getProperty("org.openmuc.framework.channelconfig")
        if (configFileName == null) {
            configFileName = DEFAULT_CONF_FILE
        }
        activateWithConfig(File(configFileName))
    }

    @Throws(
        TransformerFactoryConfigurationError::class,
        IOException::class,
        ParserConfigurationException::class,
        TransformerException::class,
        ParseException::class
    )
    protected fun activateWithConfig(configFile: File) {
        logger.info("Activating Data Manager with config {}", configFile)
        val namedThreadFactory = NamedThreadFactory("OpenMUC Data Manager Pool - thread-")
        executor = Executors.newCachedThreadPool(namedThreadFactory) as ThreadPoolExecutor
        try {
            this.configFile = configFile
            try {
                rootConfigWithoutDefaults = RootConfigImpl.Companion.createFromFile(configFile)
            } catch (e: FileNotFoundException) {
                // create an empty configuration and store it in a file
                rootConfigWithoutDefaults = RootConfigImpl()
                rootConfigWithoutDefaults!!.writeToFile(configFile)
                logger.info(
                    "No configuration file found. Created an empty config file at: {}",
                    configFile.absolutePath
                )
            } catch (e: ParseException) {
                throw ParseException("Error parsing OpenMUC config file: " + e.message, e)
            }
            rootConfig = RootConfigImpl()
            applyConfiguration(rootConfigWithoutDefaults, System.currentTimeMillis())
            start()
            dataManagerActivated = true
        } catch (e: ParseException) {
            logger.error(e.message)
            logger.error("Stopping Framework.")
            val bundleContext = FrameworkUtil.getBundle(this.javaClass).bundleContext
            try {
                bundleContext.getBundle(0).stop()
                bundleContext.bundle.stop()
            } catch (ex: BundleException) {
                ex.printStackTrace()
            }
        }
    }

    fun reload() {
        logger.info("Reload config from file.")
        try {
            reloadConfigFromFile()
        } catch (e: FileNotFoundException) {
            logger.error(e.message)
        } catch (e: ParseException) {
            logger.error(e.message)
        }
    }

    @Deactivate
    private fun deactivate() {
        logger.info("Deactivating Data Manager")
        stopFlag = true
        interrupt()
        try {
            this.join()
            executor!!.shutdown()
        } catch (e: InterruptedException) {
        }
        dataManagerActivated = false
    }

    override fun run() {
        name = "OpenMUC Data Manager"
        handleInterruptEvent()
        while (!stopFlag) {
            if (interrupted()) {
                handleInterruptEvent()
                continue
            }
            if (actions.isEmpty()) {
                try {
                    while (true) {
                        sleep(Long.MAX_VALUE)
                    }
                } catch (e: InterruptedException) {
                    handleInterruptEvent()
                    continue
                }
            }
            val currentAction = actions.first
            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - currentAction.startTime
            if (elapsedTime > 1000L) {
                elapsedTimeTooBig(currentAction, currentTime)
                continue
            }
            val sleepTime = currentAction.startTime - currentTime
            if (sleepTime > 0) {
                try {
                    sleep(sleepTime)
                } catch (e: InterruptedException) {
                    handleInterruptEvent()
                    continue
                }
            }
            actions.removeFirst()
            currentAction.timeouts?.let { triggerTimeouts(it) }
            val loggingController = LoggingController(activeDataLoggers)
            if (loggingController.channelsHaveToBeLogged(currentAction)) {
                for (collection in loggingController.triggerLogging(currentAction)) {
                    handleStillFilledChannels(collection, currentAction)
                }
            }
            if (currentAction.connectionRetryDevices != null && currentAction.connectionRetryDevices!!.isNotEmpty()) {
                for (device in currentAction.connectionRetryDevices!!) {
                    device.connectRetrySignal()
                }
            }
            if (currentAction.samplingCollections != null && currentAction.samplingCollections!!.isNotEmpty()) {
                for (samplingCollection in currentAction.samplingCollections!!) {
                    val selectedChannels: MutableList<ChannelRecordContainerImpl> = ArrayList(
                        samplingCollection.channels!!.size
                    )
                    for (channel in samplingCollection.channels!!) {
                        selectedChannels.add(channel!!.createChannelRecordContainer())
                    }
                    val samplingTask = SamplingTask(
                        this, samplingCollection.device, selectedChannels,
                        samplingCollection.samplingGroup
                    )
                    val timeout: Int = samplingCollection.device!!.deviceConfig.samplingTimeout
                    val taskAddSuccessful = samplingCollection.device!!.addSamplingTask(
                        samplingTask,
                        samplingCollection.interval
                    )
                    if (taskAddSuccessful && timeout > 0) {
                        addSamplingWorkerTimeoutToActions(samplingTask, currentAction.startTime + timeout)
                    }
                    addSamplingCollectionToActions(
                        samplingCollection,
                        currentAction.startTime + samplingCollection.interval
                    )
                }
            }
        }
    }

    private fun handleStillFilledChannels(logCollectionOpt: Optional<ChannelCollection>, currentAction: Action) {
        if (!logCollectionOpt.isPresent) {
            return
        }
        val loggingCollection = logCollectionOpt.get()
        val startTimestamp = currentAction.startTime + loggingCollection.interval
        addLoggingCollectionToActions(loggingCollection, startTimestamp)
    }

    private fun triggerTimeouts(timeouts: List<SamplingTask>) {
        for (samplingTask in timeouts) {
            samplingTask.timeout()
        }
    }

    private fun elapsedTimeTooBig(currentAction: Action, currentTime: Long) {
        actions.removeFirst()
        logger.error(
            "Action was scheduled for UNIX time {}. But current time is already {}. Will calculate new action time because the action has timed out. Has the system clock jumped?",
            currentAction.startTime, currentTime
        )
        currentAction.timeouts?.let { triggerTimeouts(it) }
        if (currentAction.loggingCollections != null) {
            for (loggingCollection in currentAction.loggingCollections!!) {
                val startTimestamp = loggingCollection.calculateNextActionTime(currentTime)
                addLoggingCollectionToActions(loggingCollection, startTimestamp)
            }
        }
        if (currentAction.samplingCollections != null) {
            for (samplingCollection in currentAction.samplingCollections!!) {
                val startTimestamp = samplingCollection.calculateNextActionTime(currentTime)
                addSamplingCollectionToActions(samplingCollection, startTimestamp)
            }
        }
        if (currentAction.connectionRetryDevices != null) {
            for (device in currentAction.connectionRetryDevices!!) {
                val startTimestamp: Long = currentTime + device.deviceConfig.connectRetryInterval
                addReconnectDeviceToActions(device, startTimestamp)
            }
        }
    }

    private fun addSamplingCollectionToActions(channelCollection: ChannelCollection, startTimestamp: Long) {
        var fittingAction: Action? = null
        val actionIterator = actions.listIterator()
        while (actionIterator.hasNext()) {
            val currentAction = actionIterator.next()
            if (currentAction.startTime == startTimestamp) {
                fittingAction = currentAction
                if (fittingAction.samplingCollections == null) {
                    fittingAction.samplingCollections = LinkedList()
                }
                break
            } else if (currentAction.startTime > startTimestamp) {
                fittingAction = Action(startTimestamp)
                fittingAction.samplingCollections = LinkedList()
                actionIterator.previous()
                actionIterator.add(fittingAction)
                break
            }
        }
        if (fittingAction == null) {
            fittingAction = Action(startTimestamp)
            fittingAction.samplingCollections = LinkedList()
            actions.add(fittingAction)
        }
        fittingAction.samplingCollections!!.add(channelCollection)
        channelCollection.action = fittingAction
    }

    private fun addLoggingCollectionToActions(channelCollection: ChannelCollection, startTimestamp: Long) {
        var fittingAction: Action? = null
        val actionIterator = actions.listIterator()
        while (actionIterator.hasNext()) {
            val currentAction = actionIterator.next()
            if (currentAction.startTime == startTimestamp) {
                fittingAction = currentAction
                if (fittingAction.loggingCollections == null) {
                    fittingAction.loggingCollections = LinkedList()
                }
                break
            } else if (currentAction.startTime > startTimestamp) {
                fittingAction = Action(startTimestamp)
                fittingAction.loggingCollections = LinkedList()
                actionIterator.previous()
                actionIterator.add(fittingAction)
                break
            }
        }
        if (fittingAction == null) {
            fittingAction = Action(startTimestamp)
            fittingAction.loggingCollections = LinkedList()
            actions.add(fittingAction)
        }
        fittingAction.loggingCollections!!.add(channelCollection)
        channelCollection.action = fittingAction
    }

    fun addReconnectDeviceToActions(device: Device, startTimestamp: Long) {
        var fittingAction: Action? = null
        val actionIterator = actions.listIterator()
        while (actionIterator.hasNext()) {
            val currentAction = actionIterator.next()
            if (currentAction.startTime == startTimestamp) {
                fittingAction = currentAction
                if (fittingAction.connectionRetryDevices == null) {
                    fittingAction.connectionRetryDevices = LinkedList()
                }
                break
            }
            if (currentAction.startTime > startTimestamp) {
                fittingAction = Action(startTimestamp)
                fittingAction.connectionRetryDevices = LinkedList()
                actionIterator.previous()
                actionIterator.add(fittingAction)
                break
            }
        }
        if (fittingAction == null) {
            fittingAction = Action(startTimestamp)
            fittingAction.connectionRetryDevices = LinkedList()
            actions.add(fittingAction)
        }
        fittingAction.connectionRetryDevices!!.add(device)
    }

    private fun addSamplingWorkerTimeoutToActions(readWorker: SamplingTask, timeout: Long) {
        var fittingAction: Action? = null
        val actionIterator = actions.listIterator()
        while (actionIterator.hasNext()) {
            val currentAction = actionIterator.next()
            if (currentAction.startTime == timeout) {
                fittingAction = currentAction
                if (fittingAction.timeouts == null) {
                    fittingAction.timeouts = LinkedList()
                }
                break
            } else if (currentAction.startTime > timeout) {
                fittingAction = Action(timeout)
                fittingAction.timeouts = LinkedList()
                actionIterator.previous()
                actionIterator.add(fittingAction)
                break
            }
        }
        if (fittingAction == null) {
            fittingAction = Action(timeout)
            fittingAction.timeouts = LinkedList()
            actions.add(fittingAction)
        }
        fittingAction.timeouts!!.add(readWorker)
    }

    private fun handleInterruptEvent() {
        if (stopFlag) {
            prepareStop()
            return
        }
        var currentTime: Long = 0
        if (newRootConfigWithoutDefaults != null) {
            currentTime = System.currentTimeMillis()
            applyConfiguration(newRootConfigWithoutDefaults, currentTime)
            newRootConfigWithoutDefaults = null
            newConfigSignal!!.countDown()
        }
        synchronized(receivedRecordContainers) {
            var recordContainers: List<ChannelRecordContainer>
            val loggingController = LoggingController(activeDataLoggers)
            val channelRecordContainerList: MutableList<ChannelRecordContainerImpl> = ArrayList()
            while (receivedRecordContainers.poll().also { recordContainers = it } != null) {
                recordContainers.stream()
                    .map { recContainer: ChannelRecordContainer -> recContainer as ChannelRecordContainerImpl }
                    .filter { containerImpl: ChannelRecordContainerImpl ->
                        containerImpl.channel
                            .channelState === ChannelState.LISTENING || containerImpl.channel
                            .driverName == "virtual"
                    }
                    .forEach { containerImpl: ChannelRecordContainerImpl ->
                        containerImpl.channel.setNewRecord(containerImpl.record)
                        if (containerImpl.channel.isLoggingEvent) {
                            channelRecordContainerList.add(containerImpl)
                        }
                    }
            }
            loggingController.deliverLogsToEventBasedLogServices(channelRecordContainerList)
        }
        synchronized(samplingTaskFinished) {
            var samplingTask: SamplingTask
            while (samplingTaskFinished.poll().also { samplingTask = it!! } != null) {
                samplingTask.storeValues()
                samplingTask.device.taskFinished()
            }
        }
        synchronized(tasksFinished) {
            var deviceTask: DeviceTask
            while (tasksFinished.poll().also { deviceTask = it!! } != null) {
                deviceTask.device.taskFinished()
            }
        }
        synchronized(newDrivers) {

            // needed to synchronize with getRunningDrivers
            synchronized(activeDrivers) { activeDrivers.putAll(newDrivers) }
            for ((driverId, value) in newDrivers) {
                logger.info("Driver registered: $driverId")
                val driverConfig = rootConfig!!.driverConfigsById[driverId] ?: continue
                driverConfig.activeDriver = value
                for (deviceConfig in driverConfig.deviceConfigsById.values) {
                    deviceConfig.device!!.driverRegisteredSignal()
                }
            }
            newDrivers.clear()
        }
        synchronized(newDataLoggers) {
            if (newDataLoggers.isNotEmpty()) {
                activeDataLoggers.addAll(newDataLoggers)
                for (dataLogger in newDataLoggers) {
                    logger.info("Data logger registered: " + dataLogger.id)
                    dataLogger.setChannelsToLog(rootConfig!!.logChannels ?: listOf())
                }
                newDataLoggers.clear()
            }
        }
        if (driverToBeRemovedId != null) {
            var removedDriverService: DriverService?
            synchronized(activeDrivers) { removedDriverService = activeDrivers.remove(driverToBeRemovedId) }
            if (removedDriverService == null) {
                // drivers was removed before it was added to activeDrivers
                newDrivers.remove(driverToBeRemovedId)
                driverRemovedSignal!!.countDown()
            } else {
                val driverConfig = rootConfig!!.driverConfigsById[driverToBeRemovedId]
                if (driverConfig != null) {
                    activeDeviceCountDown = driverConfig.deviceConfigsById.size
                    if (activeDeviceCountDown > 0) {

                        // all devices have to be given a chance to finish their current task and disconnect:
                        for (deviceConfig in driverConfig.deviceConfigsById.values) {
                            deviceConfig.device!!.driverDeregisteredSignal()
                        }
                        synchronized(driverRemovedSignal!!) {
                            if (activeDeviceCountDown == 0) {
                                driverRemovedSignal!!.countDown()
                            }
                        }
                    } else {
                        driverRemovedSignal!!.countDown()
                    }
                } else {
                    driverRemovedSignal!!.countDown()
                }
            }
            driverToBeRemovedId = null
        }
        if (dataLoggerToBeRemoved != null) {
            if (!activeDataLoggers.remove(dataLoggerToBeRemoved)) {
                newDataLoggers.remove(dataLoggerToBeRemoved)
            }
            dataLoggerToBeRemoved = null
            dataLoggerRemovedSignal!!.countDown()
        }
        synchronized(connectionFailures) {
            if (currentTime == 0L) {
                currentTime = System.currentTimeMillis()
            }
            var connectionFailureDevice: Device
            while (connectionFailures.poll().also { connectionFailureDevice = it!! } != null) {
                connectionFailureDevice.connectFailureSignal(currentTime)
            }
        }
        synchronized(connectedDevices) {
            if (currentTime == 0L) {
                currentTime = System.currentTimeMillis()
            }
            var connectedDevice: Device
            while (connectedDevices.poll().also { connectedDevice = it!! } != null) {
                connectedDevice.connectedSignal(currentTime)
            }
        }
        synchronized(newWriteTasks) { addTasksAndClear(newWriteTasks) }
        synchronized(newReadTasks) { addTasksAndClear(newReadTasks) }
        synchronized(disconnectedDevices) {
            var connectedDevice: Device
            while (disconnectedDevices.poll().also { connectedDevice = it!! } != null) {
                connectedDevice.disconnectedSignal()
            }
        }
    }

    private fun <T> addTasksAndClear(newTasksList: Queue<T>) where T : DeviceTask?, T : ConnectedTask? {
        var nextTask: T
        while (newTasksList.poll().also { nextTask = it } != null) {
            nextTask!!.device.addTask(nextTask)
        }
    }

    private fun applyConfiguration(configWithoutDefaults: RootConfigImpl?, currentTime: Long) {
        val newRootConfig = configWithoutDefaults!!.cloneWithDefaults()
        val logChannels: MutableList<LogChannel> = LinkedList()
        for (oldDriverConfig in rootConfig!!.driverConfigsById.values) {
            val newDriverConfig = newRootConfig.driverConfigsById[oldDriverConfig!!.id]
            if (newDriverConfig != null) {
                newDriverConfig.activeDriver = oldDriverConfig.activeDriver
            }
            for (oldDeviceConfig in oldDriverConfig.deviceConfigsById.values) {
                var newDeviceConfig: DeviceConfigImpl? = null
                if (newDriverConfig != null) {
                    newDeviceConfig = newDriverConfig.deviceConfigsById[oldDeviceConfig.id]
                }
                if (newDeviceConfig == null) {
                    // Device was deleted in new config
                    oldDeviceConfig.device!!.deleteSignal()
                } else {
                    // Device exists in new and old config
                    oldDeviceConfig.device!!.configChangedSignal(newDeviceConfig, currentTime, logChannels)
                }
            }
        }
        for (newDriverConfig in newRootConfig.driverConfigsById.values) {
            val oldDriverConfig = rootConfig!!.driverConfigsById[newDriverConfig!!.id]
            if (oldDriverConfig == null) {
                newDriverConfig.activeDriver = activeDrivers[newDriverConfig.id]
            }
            for (newDeviceConfig in newDriverConfig.deviceConfigsById.values) {
                var oldDeviceConfig: DeviceConfigImpl? = null
                if (oldDriverConfig != null) {
                    oldDeviceConfig = oldDriverConfig.deviceConfigsById[newDeviceConfig.id]
                }
                if (oldDeviceConfig == null) {
                    // Device is new
                    newDeviceConfig.device = Device(this, newDeviceConfig, currentTime, logChannels)
                    if (newDeviceConfig.device.state === DeviceState.CONNECTING) {
                        newDeviceConfig.device!!.connectRetrySignal()
                    }
                }
            }
        }
        for (oldChannelConfig in rootConfig!!.channelConfigsById.values) {
            val newChannelConfig = newRootConfig.channelConfigsById[oldChannelConfig!!.id]
            if (newChannelConfig == null) {
                // oldChannelConfig does not exist in the new configuration
                if (oldChannelConfig.state === ChannelState.SAMPLING) {
                    removeFromSamplingCollections(oldChannelConfig.channel)
                }
                oldChannelConfig.state = ChannelState.DELETED
                oldChannelConfig.channel!!.setFlag(Flag.CHANNEL_DELETED)
                // note: disabling SampleTasks and such has to be done at the
                // Device level
            }
        }
        updateLogChannelsInDataLoggers(logChannels)
        newRootConfig.logChannels = logChannels
        synchronized(configChangeListeners) {
            rootConfig = newRootConfig
            rootConfigWithoutDefaults = configWithoutDefaults
            for (configChangeListener in configChangeListeners) {
                executor!!.execute { configChangeListener.configurationChanged() }
            }
        }
        notifyServers()
    }

    private fun updateLogChannelsInDataLoggers(logChannels: List<LogChannel>) {
        for (dataLogger in activeDataLoggers) {
            if (dataLogger.logSettingsRequired()) {
                setLoggerSpecific(dataLogger, logChannels)
            } else {
                setLoggerSpecificAndWithoutSettings(dataLogger, logChannels)
            }
        }
    }

    private fun setLoggerSpecific(dataLogger: DataLoggerService, logChannels: List<LogChannel>) {
        val specificLogChannels: List<LogChannel> = filterLogChannelsForSpecificLogger(dataLogger.id, logChannels)
        dataLogger.setChannelsToLog(specificLogChannels)
    }

    private fun setLoggerSpecificAndWithoutSettings(dataLogger: DataLoggerService, logChannels: List<LogChannel>) {
        val specificLogChannels = filterLogChannelsForSpecificLogger(dataLogger.id, logChannels)
        val logChannelsWithoutLoggingSettings = logChannels.stream()
            .filter { logChannel: LogChannel ->
                (logChannel.loggingSettings == null
                        || logChannel.loggingSettings!!.isEmpty())
            }
            .collect(Collectors.toList())
        specificLogChannels.addAll(logChannelsWithoutLoggingSettings)
        dataLogger.setChannelsToLog(logChannelsWithoutLoggingSettings)
    }

    private fun filterLogChannelsForSpecificLogger(
        loggerId: String,
        logChannels: List<LogChannel>
    ): MutableList<LogChannel> {
        return logChannels.stream()
            .filter { logChannel: LogChannel ->
                (logChannel.loggingSettings != null
                        && logChannel.loggingSettings!!.isNotEmpty())
            }
            .filter { logChannel: LogChannel -> parseDefinedLogger(logChannel.loggingSettings!!).contains(loggerId) }
            .collect(Collectors.toList())
    }

    private fun parseDefinedLogger(logSettings: String): List<String> {
        val loggerSegments =
            logSettings.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return Arrays.stream(loggerSegments)
            .map { seg: String -> seg.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0] }
            .collect(Collectors.toList())
    }

    fun addToSamplingCollections(channel: ChannelImpl?, time: Long) {
        var fittingSamplingCollection: ChannelCollection? = null
        for (action in actions) {
            if (action.samplingCollections != null) {
                for (samplingCollection in action.samplingCollections!!) {
                    if (samplingCollection.interval == channel.samplingInterval && samplingCollection.timeOffset == channel.samplingTimeOffset && samplingCollection.samplingGroup == channel!!.config.samplingGroup && samplingCollection.device == channel.config.deviceParent!!.device) {
                        fittingSamplingCollection = samplingCollection
                        break
                    }
                }
            }
        }
        if (fittingSamplingCollection == null) {
            fittingSamplingCollection = ChannelCollection(
                channel.samplingInterval,
                channel.samplingTimeOffset, channel!!.config.samplingGroup,
                channel.config.deviceParent!!.device
            )
            addSamplingCollectionToActions(
                fittingSamplingCollection,
                fittingSamplingCollection.calculateNextActionTime(time)
            )
        }
        if (channel!!.samplingCollection != null) {
            if (channel.samplingCollection != fittingSamplingCollection) {
                removeFromSamplingCollections(channel)
            } else {
                return
            }
        }
        fittingSamplingCollection.channels!!.add(channel)
        channel.samplingCollection = fittingSamplingCollection
    }

    fun addToLoggingCollections(channel: ChannelImpl?, time: Long) {
        var fittingLoggingCollection: ChannelCollection? = null
        for (action in actions) {
            if (action.loggingCollections != null) {
                for (loggingCollection in action.loggingCollections!!) {
                    if (loggingCollection.interval == channel.loggingInterval
                        && loggingCollection.timeOffset == channel.loggingTimeOffset
                    ) {
                        fittingLoggingCollection = loggingCollection
                        break
                    }
                }
            }
        }
        if (fittingLoggingCollection == null) {
            fittingLoggingCollection = ChannelCollection(
                channel.loggingInterval,
                channel.loggingTimeOffset, null, null
            )
            addLoggingCollectionToActions(
                fittingLoggingCollection,
                fittingLoggingCollection.calculateNextActionTime(time)
            )
        }
        if (channel!!.loggingCollection != null) {
            if (channel.loggingCollection != fittingLoggingCollection) {
                removeFromLoggingCollections(channel)
            } else {
                return
            }
        }
        fittingLoggingCollection.channels!!.add(channel)
        channel.loggingCollection = fittingLoggingCollection
    }

    fun removeFromLoggingCollections(channel: ChannelImpl) {
        channel.loggingCollection!!.channels!!.remove(channel)
        if (channel.loggingCollection!!.channels!!.isEmpty()) {
            channel.loggingCollection!!.action!!.loggingCollections!!.remove(channel.loggingCollection)
        }
        channel.loggingCollection = null
    }

    fun removeFromSamplingCollections(channel: ChannelImpl) {
        channel.samplingCollection!!.channels!!.remove(channel)
        if (channel.samplingCollection!!.channels!!.isEmpty()) {
            channel.samplingCollection!!.action!!.samplingCollections!!.remove(channel.samplingCollection)
        }
        channel.samplingCollection = null
    }

    fun removeFromConnectionRetry(device: Device) {
        for (action in actions) {
            if (action.connectionRetryDevices != null && action.connectionRetryDevices!!.remove(device)) {
                break
            }
        }
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    fun bindDriverService(driver: DriverService) {
        val driverId = driver.info.id
        synchronized(newDrivers) {
            if (activeDrivers[driverId] != null || newDrivers[driverId] != null) {
                logger.error("Unable to register driver: a driver with the ID {}  is already registered.", driverId)
                return
            }
            newDrivers[driverId] = driver
            interrupt()
        }
    }

    /**
     * Registers a new ServerService.
     *
     * @param serverService
     * ServerService object to register
     */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    private fun bindServerService(serverService: ServerService) {
        val serverId = serverService.id
        serverServices[serverId] = serverService
        if (dataManagerActivated) {
            notifyServer(serverService)
        }
        logger.info("Registered Server: {}.", serverId)
    }

    /**
     * Removes a registered ServerService.
     *
     * @param serverService
     * ServerService object to unset
     */
    private fun unbindServerService(serverService: ServerService) {
        serverServices.remove(serverService.id)
    }

    /**
     * Updates all ServerServices with mapped channels.
     */
    protected fun notifyServers() {
        for (serverService in serverServices.values) {
            notifyServer(serverService)
        }
    }

    /**
     * Updates a specified ServerService with mapped channels.
     *
     * @param serverService
     * ServerService object to updating
     */
    protected fun notifyServer(serverService: ServerService) {
        val relatedServerMappings: MutableList<ServerMappingContainer> = ArrayList()
        for (config in rootConfig!!.channelConfigsById.values) {
            for (serverMapping in config!!.serverMappings) {
                if (serverMapping.id == serverService.id) {
                    relatedServerMappings
                        .add(ServerMappingContainer(this.getChannel(config.id)!!, serverMapping))
                }
            }
        }
        serverService.serverMappings(relatedServerMappings)
    }

    private fun unbindDriverService(driver: DriverService) {
        val driverId = driver.info.id
        logger.info("Unregistering driver: {}.", driverId)

        // note: no synchronization needed here because this function and the
        // deactivate function are always called sequentially:
        if (dataManagerActivated) {
            driverToBeRemovedId = driverId
            driverRemovedSignal = CountDownLatch(1)
            interrupt()
            try {
                driverRemovedSignal!!.await()
            } catch (e: InterruptedException) {
                // TODO Log exception
            }
        } else {
            if (activeDrivers.remove(driverId) == null) {
                newDrivers.remove(driverId)
            }
        }
        logger.info("Driver unregistered: {}", driverId)
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    fun bindDataLoggerService(dataLogger: DataLoggerService) {
        synchronized(newDataLoggers) {
            newDataLoggers.add(dataLogger)
            interrupt()
        }
    }

    private fun unbindDataLoggerService(dataLogger: DataLoggerService) {
        val dataLoggerId = dataLogger.id
        logger.info("Unregistering data logger: {}.", dataLoggerId)
        if (dataManagerActivated) {
            dataLoggerRemovedSignal = CountDownLatch(1)
            dataLoggerToBeRemoved = dataLogger
            interrupt()
            try {
                dataLoggerRemovedSignal!!.await()
            } catch (e: InterruptedException) {
                // TODO Log exception
            }
        } else {
            if (!activeDataLoggers.remove(dataLogger)) {
                newDataLoggers.remove(dataLogger)
            }
        }
        logger.info("Data logger deregistered: $dataLoggerId")
    }

    private fun prepareStop() {
        // TODO tell all drivers to stop listening
        // Do I have to wait for all threads (such as SamplingTasks) to finish?
        executor!!.shutdown()
    }

    override fun getChannel(id: String): Channel? {
        if (rootConfig == null) {
            return null
        }
        val channelConfig = rootConfig!!.channelConfigsById[id] ?: return null
        return channelConfig.channel
    }

    override fun getChannel(id: String, channelChangeListener: ChannelChangeListener?): Channel? {
        // TODO Auto-generated method stub
        return null
    }

    override fun getLogicalDevices(type: String): List<LogicalDevice> {
        // TODO Auto-generated method stub
        return null
    }

    override fun getLogicalDevices(
        type: String?,
        logicalDeviceChangeListener: LogicalDeviceChangeListener?
    ): List<LogicalDevice>? {
        // TODO Auto-generated method stub
        return null
    }

    override fun newRecords(recordContainers: List<ChannelRecordContainer>) {
        val recordContainersCopy: MutableList<ChannelRecordContainer> = ArrayList(recordContainers.size)
        for (container in recordContainers) {
            recordContainersCopy.add(container.copy())
        }
        synchronized(receivedRecordContainers) { receivedRecordContainers.add(recordContainersCopy) }
        interrupt()
    }

    override fun connectionInterrupted(driverId: String, connection: Connection) {
        // TODO synchronize here
        val driverConfig = rootConfig!!.getDriver(driverId) ?: return
        for (deviceConfig in driverConfig.devices) {
            val deviceConfigImpl = deviceConfig as DeviceConfigImpl?
            if (deviceConfigImpl!!.device!!.connection !== connection) {
                continue
            }
            val device = deviceConfigImpl!!.device
            logger.info("Connection to device {} was interrupted.", device!!.deviceConfig.id)
            device.disconnectedSignal()
            return
        }
    }

    override fun lock() {
        configLock.lock()
    }

    override fun tryLock(): Boolean {
        return configLock.tryLock()
    }

    override fun unlock() {
        configLock.unlock()
    }

    override var config: RootConfig?
        get() = RootConfigImpl(rootConfigWithoutDefaults)
        set(config) {
            configLock.lock()
            try {
                val newConfigCopy = RootConfigImpl(config as RootConfigImpl?)
                setNewConfig(newConfigCopy)
            } finally {
                configLock.unlock()
            }
        }

    override fun getConfig(listener: ConfigChangeListener?): RootConfig? {
        synchronized(configChangeListeners) {
            for (configChangeListener in configChangeListeners) {
                if (configChangeListener === listener) {
                    configChangeListeners.remove(configChangeListener)
                }
            }
            if (listener != null) {
                configChangeListeners.add(listener)
            }
            return config
        }
    }

    override fun stopListeningForConfigChange(listener: ConfigChangeListener) {
        synchronized(configChangeListeners) { configChangeListeners.remove(listener) }
    }

    @Throws(FileNotFoundException::class, ParseException::class)
    override fun reloadConfigFromFile() {
        configLock.lock()
        try {
            val newConfigCopy: RootConfigImpl = RootConfigImpl.Companion.createFromFile(configFile)
            setNewConfig(newConfigCopy)
        } finally {
            configLock.unlock()
        }
    }

    private fun setNewConfig(newConfigCopy: RootConfigImpl) {
        synchronized(this) {
            newConfigSignal = CountDownLatch(1)
            newRootConfigWithoutDefaults = newConfigCopy
            interrupt()
        }
        while (true) {
            try {
                newConfigSignal!!.await()
                break
            } catch (e: InterruptedException) {
            }
        }
    }

    override val emptyConfig: RootConfig
        get() = RootConfigImpl()

    @Throws(ConfigWriteException::class)
    override fun writeConfigToFile() {
        try {
            rootConfigWithoutDefaults!!.writeToFile(configFile)
        } catch (e: Exception) {
            throw ConfigWriteException(e)
        }
    }

    @Throws(
        DriverNotAvailableException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ScanInterruptedException::class
    )
    override fun scanForDevices(driverId: String, settings: String): List<DeviceScanInfo> {
        val driver = activeDrivers[driverId] ?: throw DriverNotAvailableException()
        val blockingScanListener = BlockingScanListener()
        try {
            driver.scanForDevices(settings, blockingScanListener)
        } catch (e: RuntimeException) {
            if (e is UnsupportedOperationException) {
                throw e
            }
            throw ScanException(e)
        }
        return blockingScanListener.scanInfos
    }

    @Throws(DriverNotAvailableException::class)
    override fun scanForDevices(driverId: String, settings: String, scanListener: DeviceScanListener?) {
        val driver = activeDrivers[driverId] ?: throw DriverNotAvailableException()
        executor!!.execute(ScanForDevicesTask(driver, settings, scanListener))
    }

    @Throws(DriverNotAvailableException::class, UnsupportedOperationException::class)
    override fun interruptDeviceScan(driverId: String) {
        val driver = activeDrivers[driverId] ?: throw DriverNotAvailableException()
        driver.interruptDeviceScan()
    }

    @Throws(
        DriverNotAvailableException::class,
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class
    )
    override fun scanForChannels(deviceId: String, settings: String): List<ChannelScanInfo> {
        // TODO this function is probably not thread safe
        val config = rootConfig!!.getDevice(deviceId) as DeviceConfigImpl?
            ?: throw ScanException("No device with ID \"$deviceId\" found.")
        val activeDriver = activeDrivers[config.driverParent!!.id] ?: throw DriverNotAvailableException()
        waitTilDeviceIsConnected(config.device)
        return try {
            config.device!!.connection!!.scanForChannels(settings)
        } catch (e: ConnectionException) {
            config.device!!.disconnectedSignal()
            throw ScanException(e.message, e)
        }
    }

    @Throws(ScanException::class)
    private fun waitTilDeviceIsConnected(device: Device?) {
        var i = 0
        while (i < 10 && device.state === DeviceState.CONNECTING) {
            try {
                sleep(10L)
            } catch (e: InterruptedException) {
                // ignore
            }
            i++
        }
        if (device!!.connection == null) {
            throw ScanException("Connection of the device is not yet initialized.")
        }
    }

    override val idsOfRunningDrivers: List<String>
        get() {
            var availableDrivers: MutableList<String>
            synchronized(activeDrivers) {
                availableDrivers = ArrayList(activeDrivers.size)
                for (activeDriverName in activeDrivers.keys) {
                    availableDrivers.add(activeDriverName)
                }
            }
            return availableDrivers
        }

    override fun write(values: List<WriteValueContainer>) {
        val containersByDevice: HashMap<Device, MutableList<WriteValueContainerImpl>> = LinkedHashMap()
        for (value in values) {
            val valueContainerImpl = value as WriteValueContainerImpl
            if (valueContainerImpl.value == null) {
                valueContainerImpl.flag = Flag.CANNOT_WRITE_NULL_VALUE
                continue
            }
            val device = valueContainerImpl.channel.config.deviceParent?.device
            var writeValueContainers = containersByDevice[device]
            if (writeValueContainers == null) {
                writeValueContainers = LinkedList()
                containersByDevice[device] = writeValueContainers
            }
            writeValueContainers.add(valueContainerImpl)
        }
        val writeTasksFinishedSignal = CountDownLatch(containersByDevice.size)
        synchronized(newWriteTasks) {
            for ((key, value) in containersByDevice) {
                val writeTask = WriteTask(
                    this, key,
                    value, writeTasksFinishedSignal
                )
                newWriteTasks.add(writeTask)
            }
        }
        interrupt()
        try {
            writeTasksFinishedSignal.await()
        } catch (e: InterruptedException) {
            // TODO Log exception
        }
    }

    override fun read(values: List<ReadRecordContainer>) {
        val containersByDevice: MutableMap<Device?, MutableList<ChannelRecordContainerImpl>> = HashMap()
        for (container in values) {
            require(container is ChannelRecordContainerImpl) { "Only use ReadRecordContainer created by Channel.getReadContainer()" }
            val channel = container.channel as ChannelImpl?
            var containersOfDevice = containersByDevice[channel!!.config.deviceParent!!.device]
            if (containersOfDevice == null) {
                containersOfDevice = LinkedList()
                containersByDevice[channel.config.deviceParent!!.device] = containersOfDevice
            }
            containersOfDevice.add(container)
        }
        val readTasksFinishedSignal = CountDownLatch(containersByDevice.size)
        synchronized(newReadTasks) {
            for ((key, value) in containersByDevice) {
                val readTask = ReadTask(
                    this, key,
                    value, readTasksFinishedSignal
                )
                newReadTasks.add(readTask)
            }
        }
        interrupt()
        try {
            readTasksFinishedSignal.await()
        } catch (e: InterruptedException) {
            // TODO Log exception
        }
    }

    override val allIds: List<String>
        get() = rootConfig!!.channelConfigsById.keys.toList()

    @Throws(DataLoggerNotAvailableException::class)
    fun getDataLogger(loggerId: String): DataLoggerService {
        val dataLogger: DataLoggerService = if (loggerId.isNullOrEmpty()) {
            activeDataLoggers.peekFirst()
        } else {
            activeDataLoggers.stream()
                .filter { activeLogger: DataLoggerService -> activeLogger.id == loggerId }
                .findFirst()
                .orElseThrow {
                    logger.warn("DataLogger with id $loggerId not found for reading logs!")
                    DataLoggerNotAvailableException()
                }
        }
        logger.debug("Accessing logged values using {}", dataLogger.id)
        return dataLogger
    }

    @Throws(DriverNotAvailableException::class)
    override fun getDriverInfo(driverId: String): DriverInfo {
        val driver = activeDrivers[driverId] ?: throw DriverNotAvailableException()
        return driver.info
    }

    override fun getDeviceState(deviceId: String): DeviceState? {
        val deviceConfig = rootConfig!!.getDevice(deviceId) as DeviceConfigImpl? ?: return null
        return deviceConfig.device?.state
    }

    internal inner class BlockingScanListener : DriverDeviceScanListener {
        var scanInfos: MutableList<DeviceScanInfo> = ArrayList()
        override fun scanProgressUpdate(progress: Int) {}
        override fun deviceFound(scanInfo: DeviceScanInfo) {
            if (!scanInfos.contains(scanInfo)) {
                scanInfos.add(scanInfo)
            }
        }
    }

    companion object {
        private const val DEFAULT_CONF_FILE = "conf/channels.xml"
        private val logger = LoggerFactory.getLogger(DataManager::class.java)
    }
}
