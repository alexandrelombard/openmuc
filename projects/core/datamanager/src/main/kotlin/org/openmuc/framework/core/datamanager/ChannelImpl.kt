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
import org.openmuc.framework.data.*
import org.openmuc.framework.dataaccess.*
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.stream.Collectors

class ChannelImpl(
    private val dataManager: DataManager,
    @field:Volatile var config: ChannelConfigImpl,
    initState: ChannelState,
    initFlag: Flag,
    currentTime: Long,
    logChannels: MutableList<LogChannel>
) : Channel {
    private val listeners: MutableSet<RecordListener> = LinkedHashSet()
    var samplingCollection: ChannelCollection? = null
    var loggingCollection: ChannelCollection? = null

    @Volatile
    var handle: Any? = null

    @Volatile
    override var latestRecord: Record? = null
        set(value) {
            requireNotNull(value)
            setNewRecord(value)
        }
    private var timer: Timer? = null
    private var futureValues: List<FutureValue?>

    init {
        futureValues = ArrayList()
        if (config.isDisabled) {
            config.state = ChannelState.DISABLED
            latestRecord = Record(Flag.DISABLED)
        } else if (!config.isListening && config.samplingInterval < 0) {
            config.state = initState
            latestRecord = Record(Flag.SAMPLING_AND_LISTENING_DISABLED)
        } else {
            config.state = initState
            latestRecord = Record(null, null, initFlag)
        }
        if (config.loggingInterval != ChannelConfig.LOGGING_INTERVAL_DEFAULT) {
            dataManager.addToLoggingCollections(this, currentTime)
            logChannels.add(config)
        } else if (config.loggingInterval == ChannelConfig.LOGGING_INTERVAL_DEFAULT && config.isLoggingEvent
            && config.isListening
        ) {
            logChannels.add(config)
        }
    }

    override val id: String
        get() = config.id
    override val channelAddress: String?
        get() = config.channelAddress
    override val description: String?
        get() = config.description
    override val settings: String?
        get() = config.settings
    override val loggingSettings: String?
        get() = config.loggingSettings
    override val unit: String?
        get() = config.unit
    override val valueType: ValueType
        get() = config.valueType
    override val scalingFactor: Double
        get() = config.scalingFactor ?: 1.0
    override val samplingInterval: Int
        get() = config.samplingInterval
    override val samplingTimeOffset: Int
        get() = config.samplingTimeOffset
    override val samplingTimeout: Int
        get() = config.deviceParent!!.samplingTimeout
    override val loggingInterval: Int
        get() = config.loggingInterval
    override val loggingTimeOffset: Int
        get() = config.loggingTimeOffset
    override val driverName: String
        get() = config.deviceParent!!.driverParent!!.id
    override val deviceAddress: String?
        get() = config.deviceParent!!.deviceAddress
    override val deviceName: String
        get() = config.deviceParent!!.id
    override val deviceDescription: String?
        get() = config.deviceParent!!.description
    override val channelState: ChannelState?
        get() = config.state
    override val deviceState: DeviceState?
        get() = config.deviceParent?.device?.state

    override fun addListener(listener: RecordListener) {
        synchronized(listeners) { listeners.add(listener) }
    }

    override fun removeListener(listener: RecordListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    @Throws(DataLoggerNotAvailableException::class, IOException::class)
    override fun getLoggedRecord(time: Long): Record? {
        val reader = validReaderIdFromConfig
        val records = dataManager.getDataLogger(reader).getRecords(
            config.id, time, time
        )
        return if (records.isNotEmpty()) {
            records[0]
        } else {
            null
        }
    }

    @Throws(DataLoggerNotAvailableException::class, IOException::class)
    override fun getLoggedRecords(startTime: Long): List<Record> {
        val reader = validReaderIdFromConfig
        return dataManager.getDataLogger(reader).getRecords(config.id, startTime, System.currentTimeMillis())
    }

    @Throws(DataLoggerNotAvailableException::class, IOException::class)
    override fun getLoggedRecords(startTime: Long, endTime: Long): List<Record> {
        val reader = validReaderIdFromConfig
        val toReturn = dataManager.getDataLogger(reader).getRecords(config.id, startTime, endTime).toMutableList()

        // values in the future values list are sorted.
        val currentTime = System.currentTimeMillis()
        for (futureValue in futureValues) {
            if (futureValue!!.writeTime >= currentTime) {
                if (futureValue.writeTime <= endTime) {
                    val futureValAsRec = Record(
                        futureValue.value, futureValue.writeTime
                    )
                    toReturn.add(futureValAsRec)
                } else {
                    break
                }
            }
        }
        return toReturn
    }

    private val validReaderIdFromConfig: String
        get() = if (config.reader == null || config.reader!!.isEmpty()) {
            firstLoggerFromLogSettings()
        } else {
            config.reader!!
        }

    private fun firstLoggerFromLogSettings(): String {
        val loggerSegments =
            (config.loggingSettings ?: "").split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val definedLogger = Arrays.stream(loggerSegments)
            .map { seg: String -> seg.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0] }
            .collect(Collectors.toList())
        return definedLogger[0]
    }

    fun setNewRecord(record: Record): Record {
        val convertedRecord = if (record.flag === Flag.VALID) {
            convertValidRecord(record)
        } else {
            val latestRecord = latestRecord
            requireNotNull(latestRecord)
            Record(latestRecord.value, latestRecord.timestamp, record.flag)
        }
        latestRecord = convertedRecord
        notifyListeners()
        return convertedRecord
    }

    private fun convertValidRecord(record: Record): Record {
        var record = record
        val scalingFactor = config.scalingFactor
        val scalingOffset = config.valueOffset
        if (scalingFactor != null) {
            try {
                record = Record(
                    DoubleValue(record.value!!.asDouble() * scalingFactor),
                    record.timestamp, record.flag
                )
            } catch (e: TypeConversionException) {
                val msg = ("Unable to apply scaling factor to channel " + config.id
                        + " because a TypeConversionError occurred.")
                logger.error(msg, e)
            }
        }
        if (scalingOffset != null) {
            try {
                record = Record(
                    DoubleValue(record.value!!.asDouble() + scalingOffset),
                    record.timestamp, record.flag
                )
            } catch (e: TypeConversionException) {
                val msg = ("Unable to apply scaling offset to channel " + config.id
                        + " because a TypeConversionError occurred.")
                logger.error(msg, e)
            }
        }
        return try {
            when (config.valueType) {
                ValueType.BOOLEAN -> Record(
                    BooleanValue(
                        record.value!!.asBoolean()
                    ), record.timestamp,
                    record.flag
                )

                ValueType.BYTE -> Record(
                    ByteValue(
                        record.value!!.asByte()
                    ), record.timestamp, record.flag
                )

                ValueType.SHORT -> Record(
                    ShortValue(
                        record.value!!.asShort()
                    ), record.timestamp, record.flag
                )

                ValueType.INTEGER -> Record(
                    IntValue(
                        record.value!!.asInt()
                    ), record.timestamp, record.flag
                )

                ValueType.LONG -> Record(
                    LongValue(
                        record.value!!.asLong()
                    ), record.timestamp, record.flag
                )

                ValueType.FLOAT -> Record(
                    FloatValue(
                        record.value!!.asFloat()
                    ), record.timestamp, record.flag
                )

                ValueType.DOUBLE -> Record(
                    DoubleValue(
                        record.value!!.asDouble()
                    ), record.timestamp,
                    record.flag
                )

                ValueType.BYTE_ARRAY -> Record(
                    ByteArrayValue(
                        record.value!!.asByteArray()
                    ), record.timestamp,
                    record.flag
                )

                ValueType.STRING -> Record(
                    StringValue(record.value.toString()), record.timestamp,
                    record.flag
                )

                else -> Record(
                    StringValue(record.value.toString()), record.timestamp,
                    record.flag
                )
            }
        } catch (e: TypeConversionException) {
            logger.error("Unable to convert value to configured value type because a TypeConversionError occured.", e)
            Record(Flag.DRIVER_ERROR_CHANNEL_VALUE_TYPE_CONVERSION_EXCEPTION)
        }
    }

    private fun notifyListeners() {
        if (listeners.isEmpty()) {
            return
        }
        synchronized(listeners) {
            for (listener in listeners) {
                config.deviceParent!!.device!!.dataManager.executor!!.execute(
                    ListenerNotifier(
                        listener,
                        latestRecord!!
                    )
                )
            }
        }
    }

    fun createChannelRecordContainer(): ChannelRecordContainerImpl {
        return ChannelRecordContainerImpl(this)
    }

    fun setFlag(flag: Flag) {
        if (flag !== latestRecord?.flag) {
            this.latestRecord = Record(latestRecord?.value, latestRecord?.timestamp, flag)
            notifyListeners()
        }
    }

    fun setNewDeviceState(state: ChannelState?, flag: Flag) {
        if (config.isDisabled) {
            config.state = ChannelState.DISABLED
            setFlag(Flag.DISABLED)
        } else if (!config.isListening && config.samplingInterval < 0) {
            config.state = state
            setFlag(Flag.SAMPLING_AND_LISTENING_DISABLED)
        } else {
            config.state = state
            setFlag(flag)
        }
    }

    override fun write(value: Value): Flag {
        if (config.deviceParent!!.driverParent!!.id == "virtual") {
            val record = Record(value, System.currentTimeMillis())
            this.latestRecord = record
            val recordContainers: MutableList<ChannelRecordContainer> = ArrayList()
            val recordContainer: ChannelRecordContainer = ChannelRecordContainerImpl(this)
            recordContainer.record = record
            recordContainers.add(recordContainer)
            dataManager.newRecords(recordContainers)
            dataManager.interrupt()
            return record.flag
        }
        val writeTaskFinishedSignal = CountDownLatch(1)
        val writeValueContainer = WriteValueContainerImpl(this)
        var adjustedValue = value
        val valueOffset = config.valueOffset
        val scalingFactor = config.scalingFactor
        if (valueOffset != null) {
            adjustedValue = DoubleValue(adjustedValue.asDouble() - valueOffset)
        }
        if (scalingFactor != null) {
            adjustedValue = DoubleValue(adjustedValue.asDouble() / scalingFactor)
        }
        writeValueContainer.value = adjustedValue
        val writeValueContainerList = listOf(writeValueContainer)
        val writeTask = WriteTask(
            dataManager, config.deviceParent!!.device!!, writeValueContainerList,
            writeTaskFinishedSignal
        )
        synchronized(dataManager.newWriteTasks) { dataManager.newWriteTasks.add(writeTask) }
        dataManager.interrupt()
        try {
            writeTaskFinishedSignal.await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        val timestamp = System.currentTimeMillis()
        latestRecord = Record(value, timestamp, writeValueContainer.flag!!)
        notifyListeners()
        return writeValueContainer.flag!!
    }

    override fun writeFuture(values: MutableList<FutureValue>) {
        futureValues = values
        values.sortWith { o1, o2 -> o1.writeTime.compareTo(o2.writeTime) }
        if (timer != null) {
            timer!!.cancel()
        }
        timer = Timer("Timer ChannelImpl " + config.id)
        val currentTimestamp = System.currentTimeMillis()
        for (value in futureValues) {
            if (currentTimestamp - value!!.writeTime >= 1000L) {
                continue
            }
            val timerTask: TimerTask = object : TimerTask() {
                override fun run() {
                    write(value.value)
                }
            }
            val scheduleTime = Date(value.writeTime)
            timer!!.schedule(timerTask, scheduleTime)
        }
    }

    override fun read(): Record {
        val readTaskFinishedSignal = CountDownLatch(1)
        val readValueContainer = ChannelRecordContainerImpl(this)
        val readValueContainerList = listOf(readValueContainer)
        val readTask = ReadTask(
            dataManager, config.deviceParent!!.device!!, readValueContainerList,
            readTaskFinishedSignal
        )
        synchronized(dataManager.newReadTasks) { dataManager.newReadTasks.add(readTask) }
        dataManager.interrupt()
        try {
            readTaskFinishedSignal.await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return setNewRecord(readValueContainer.record!!)
    }

    override val isConnected: Boolean
        get() = config.state === ChannelState.CONNECTED || config.state === ChannelState.SAMPLING || config.state === ChannelState.LISTENING
    override val writeContainer: WriteValueContainer
        get() = WriteValueContainerImpl(this)
    override val readContainer: ReadRecordContainer
        get() = ChannelRecordContainerImpl(this)
    val isLoggingEvent: Boolean
        get() = config.isLoggingEvent && config.isListening && config.loggingInterval == -1

    companion object {
        private val logger = LoggerFactory.getLogger(ChannelImpl::class.java)
    }
}
