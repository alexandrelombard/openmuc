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
import org.openmuc.framework.data.FutureValue.value
import org.openmuc.framework.data.Record.value
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.dataaccess.*
import org.openmuc.framework.datalogger.spi.LogChannel
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.text.MessageFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class ChannelConfigImpl internal constructor(private override var id: String, var deviceParent: DeviceConfigImpl?) :
    ChannelConfig, LogChannel {
    var channel: ChannelImpl? = null
    var state: ChannelState? = null
    private override var channelAddress: String? = null
    private override var description: String? = null
    private override var unit: String? = null
    private override var valueType: ValueType? = null
    private override var valueTypeLength: Int? = null
    private override var scalingFactor: Double? = null
    private override var valueOffset: Double? = null
    private var listening: Boolean? = null
    private override var samplingInterval: Int? = null
    private override var samplingTimeOffset: Int? = null
    private override var samplingGroup: String? = null
    private override var settings: String? = null
    private var loggingEvent: Boolean? = null
    private override var loggingInterval: Int? = null
    private override var loggingTimeOffset: Int? = null
    private override var loggingSettings: String? = null
    private var disabled: Boolean? = null
    private override var serverMappings: MutableList<ServerMapping?>? = null
    private override var reader: String? = null
    override fun getId(): String {
        return id
    }

    @Throws(IdCollisionException::class)
    override fun setId(id: String?) {
        requireNotNull(id) { "The channel ID may not be null" }
        checkIdSyntax(id)
        if (deviceParent!!.driverParent!!.rootConfigParent!!.channelConfigsById.containsKey(id)) {
            throw IdCollisionException("Collision with channel ID:$id")
        }
        deviceParent!!.channelConfigsById[id] = deviceParent!!.channelConfigsById.remove(this.id)
        deviceParent!!.driverParent!!.rootConfigParent!!.channelConfigsById[id] =
            deviceParent!!.driverParent!!.rootConfigParent!!.channelConfigsById.remove(this.id)
        this.id = id
    }

    override fun getDescription(): String {
        return description!!
    }

    override fun setDescription(description: String?) {
        this.description = description
    }

    override fun getChannelAddress(): String {
        return channelAddress!!
    }

    override fun setChannelAddress(address: String?) {
        channelAddress = address
    }

    override fun getUnit(): String {
        return unit!!
    }

    override fun setUnit(unit: String?) {
        this.unit = unit
    }

    override fun getValueType(): ValueType {
        return valueType!!
    }

    override fun setValueType(valueType: ValueType?) {
        this.valueType = valueType
    }

    override fun getValueTypeLength(): Int {
        return valueTypeLength!!
    }

    override fun setValueTypeLength(length: Int?) {
        valueTypeLength = length
    }

    override fun getScalingFactor(): Double {
        return scalingFactor!!
    }

    override fun setScalingFactor(factor: Double?) {
        scalingFactor = factor
    }

    override fun getValueOffset(): Double {
        return valueOffset!!
    }

    override fun setValueOffset(offset: Double?) {
        valueOffset = offset
    }

    override fun isListening(): Boolean {
        return listening!!
    }

    override fun setListening(listening: Boolean?) {
        check((samplingInterval != null && listening != null && listening && samplingInterval!!) <= 0) { "Listening may not be enabled while sampling is enabled." }
        this.listening = listening
    }

    override fun getSamplingInterval(): Int {
        return samplingInterval!!
    }

    override fun setSamplingInterval(samplingInterval: Int?) {
        check((listening != null && samplingInterval != null && isListening() && samplingInterval) <= 0) { "Sampling may not be enabled while listening is enabled." }
        this.samplingInterval = samplingInterval
    }

    override fun getSamplingTimeOffset(): Int {
        return samplingTimeOffset!!
    }

    override fun setSamplingTimeOffset(samplingTimeOffset: Int?) {
        require(!(samplingTimeOffset != null && samplingTimeOffset < 0)) { "The sampling time offset may not be negative." }
        this.samplingTimeOffset = samplingTimeOffset
    }

    override fun getSamplingGroup(): String {
        return samplingGroup!!
    }

    override fun setSamplingGroup(group: String?) {
        samplingGroup = group
    }

    override fun getSettings(): String? {
        return settings
    }

    override fun setSettings(settings: String?) {
        this.settings = settings
    }

    override fun getLoggingInterval(): Int {
        return loggingInterval!!
    }

    override fun setLoggingInterval(loggingInterval: Int?) {
        this.loggingInterval = loggingInterval
    }

    override fun setLoggingEvent(loggingEvent: Boolean?) {
        this.loggingEvent = loggingEvent
    }

    override fun isLoggingEvent(): Boolean {
        return loggingEvent!!
    }

    override fun getLoggingSettings(): String {
        return loggingSettings!!
    }

    override fun setLoggingSettings(loggingSettings: String?) {
        this.loggingSettings = loggingSettings
    }

    override fun getReader(): String? {
        return reader
    }

    override fun setReader(reader: String?) {
        this.reader = reader
    }

    override fun getLoggingTimeOffset(): Int {
        return loggingTimeOffset!!
    }

    override fun setLoggingTimeOffset(loggingTimeOffset: Int?) {
        require(!(loggingTimeOffset != null && loggingTimeOffset < 0)) { "The logging time offset may not be negative." }
        this.loggingTimeOffset = loggingTimeOffset
    }

    override fun isDisabled(): Boolean {
        return disabled!!
    }

    override fun setDisabled(disabled: Boolean?) {
        this.disabled = disabled
    }

    override fun delete() {
        deviceParent!!.channelConfigsById.remove(id)
        clear()
    }

    override fun getServerMappings(): List<ServerMapping?>? {
        return if (serverMappings != null) {
            serverMappings
        } else {
            ArrayList()
        }
    }

    fun clear() {
        deviceParent!!.driverParent!!.rootConfigParent!!.channelConfigsById.remove(id)
        deviceParent = null
    }

    override val device: DeviceConfig?
        get() = deviceParent

    fun getDomElement(document: Document): Element {
        val parentElement = document.createElement("channel")
        parentElement.setAttribute("id", id)
        var childElement: Element
        if (description != null) {
            childElement = document.createElement("description")
            childElement.textContent = description
            parentElement.appendChild(childElement)
        }
        if (channelAddress != null) {
            childElement = document.createElement("channelAddress")
            childElement.textContent = channelAddress
            parentElement.appendChild(childElement)
        }
        if (serverMappings != null) {
            for (serverMapping in serverMappings!!) {
                childElement = document.createElement("serverMapping")
                childElement.setAttribute("id", serverMapping!!.id)
                childElement.textContent = serverMapping.serverAddress
                parentElement.appendChild(childElement)
            }
        }
        if (unit != null) {
            childElement = document.createElement("unit")
            childElement.textContent = unit
            parentElement.appendChild(childElement)
        }
        if (valueType != null) {
            childElement = document.createElement("valueType")
            childElement.textContent = valueType.toString()
            if (valueTypeLength != null) {
                if (valueType === ValueType.BYTE_ARRAY || valueType === ValueType.STRING) {
                    childElement.setAttribute("length", valueTypeLength.toString())
                }
            }
            parentElement.appendChild(childElement)
        }
        if (scalingFactor != null) {
            childElement = document.createElement("scalingFactor")
            childElement.textContent = java.lang.Double.toString(scalingFactor!!)
            parentElement.appendChild(childElement)
        }
        if (valueOffset != null) {
            childElement = document.createElement("valueOffset")
            childElement.textContent = java.lang.Double.toString(valueOffset!!)
            parentElement.appendChild(childElement)
        }
        if (listening != null) {
            childElement = document.createElement("listening")
            childElement.textContent = listening.toString()
            parentElement.appendChild(childElement)
        }
        if (samplingInterval != null) {
            childElement = document.createElement("samplingInterval")
            childElement.textContent = millisToTimeString(samplingInterval!!)
            parentElement.appendChild(childElement)
        }
        if (samplingTimeOffset != null) {
            childElement = document.createElement("samplingTimeOffset")
            childElement.textContent = millisToTimeString(samplingTimeOffset!!)
            parentElement.appendChild(childElement)
        }
        if (samplingGroup != null) {
            childElement = document.createElement("samplingGroup")
            childElement.textContent = samplingGroup
            parentElement.appendChild(childElement)
        }
        if (settings != null) {
            childElement = document.createElement("settings")
            childElement.textContent = settings
            parentElement.appendChild(childElement)
        }
        if (loggingInterval != null) {
            childElement = document.createElement("loggingInterval")
            childElement.textContent = millisToTimeString(loggingInterval!!)
            parentElement.appendChild(childElement)
        }
        if (loggingTimeOffset != null) {
            childElement = document.createElement("loggingTimeOffset")
            childElement.textContent = millisToTimeString(loggingTimeOffset!!)
            parentElement.appendChild(childElement)
        }
        if (loggingEvent != null) {
            childElement = document.createElement("loggingEvent")
            childElement.textContent = loggingEvent.toString()
            parentElement.appendChild(childElement)
        }
        if (loggingSettings != null) {
            childElement = document.createElement("loggingSettings")
            childElement.textContent = loggingSettings
            parentElement.appendChild(childElement)
        }
        if (disabled != null) {
            childElement = document.createElement("disabled")
            childElement.textContent = disabled.toString()
            parentElement.appendChild(childElement)
        }
        return parentElement
    }

    fun clone(clonedParentConfig: DeviceConfigImpl?): ChannelConfigImpl {
        val configClone = ChannelConfigImpl(id, clonedParentConfig)
        configClone.description = description
        configClone.channelAddress = channelAddress
        configClone.serverMappings = serverMappings
        configClone.unit = unit
        configClone.valueType = valueType
        configClone.valueTypeLength = valueTypeLength
        configClone.scalingFactor = scalingFactor
        configClone.valueOffset = valueOffset
        configClone.listening = listening
        configClone.samplingInterval = samplingInterval
        configClone.samplingTimeOffset = samplingTimeOffset
        configClone.samplingGroup = samplingGroup
        configClone.settings = settings
        configClone.loggingInterval = loggingInterval
        configClone.loggingTimeOffset = loggingTimeOffset
        configClone.disabled = disabled
        configClone.loggingEvent = loggingEvent
        configClone.loggingSettings = loggingSettings
        configClone.reader = reader
        return configClone
    }

    fun cloneWithDefaults(clonedParentConfig: DeviceConfigImpl): ChannelConfigImpl {
        val configClone = ChannelConfigImpl(id, clonedParentConfig)
        if (description == null) {
            configClone.description = ChannelConfig.DESCRIPTION_DEFAULT
        } else {
            configClone.description = description
        }
        if (channelAddress == null) {
            configClone.channelAddress = ChannelConfig.CHANNEL_ADDRESS_DEFAULT
        } else {
            configClone.channelAddress = channelAddress
        }
        if (serverMappings == null) {
            configClone.serverMappings = ArrayList()
        } else {
            configClone.serverMappings = serverMappings
        }
        if (unit == null) {
            configClone.unit = ChannelConfig.UNIT_DEFAULT
        } else {
            configClone.unit = unit
        }
        if (valueType == null) {
            configClone.valueType = ChannelConfig.VALUE_TYPE_DEFAULT
        } else {
            configClone.valueType = valueType
        }
        if (valueTypeLength == null) {
            if (valueType === ValueType.DOUBLE) {
                configClone.valueTypeLength = 8
            } else if (valueType === ValueType.BYTE_ARRAY) {
                configClone.valueTypeLength = ChannelConfig.BYTE_ARRAY_SIZE_DEFAULT
            } else if (valueType === ValueType.STRING) {
                configClone.valueTypeLength = ChannelConfig.STRING_SIZE_DEFAULT
            } else if (valueType === ValueType.BYTE) {
                configClone.valueTypeLength = 1
            } else if (valueType === ValueType.FLOAT) {
                configClone.valueTypeLength = 4
            } else if (valueType === ValueType.SHORT) {
                configClone.valueTypeLength = 2
            } else if (valueType === ValueType.INTEGER) {
                configClone.valueTypeLength = 4
            } else if (valueType === ValueType.LONG) {
                configClone.valueTypeLength = 8
            } else if (valueType === ValueType.BOOLEAN) {
                configClone.valueTypeLength = 1
            }
        } else {
            configClone.valueTypeLength = valueTypeLength
        }
        configClone.scalingFactor = scalingFactor
        configClone.valueOffset = valueOffset
        if (listening == null) {
            configClone.listening = ChannelConfig.LISTENING_DEFAULT
        } else {
            configClone.listening = listening
        }
        if (samplingInterval == null) {
            configClone.samplingInterval = ChannelConfig.SAMPLING_INTERVAL_DEFAULT
        } else {
            configClone.samplingInterval = samplingInterval
        }
        if (samplingTimeOffset == null) {
            configClone.samplingTimeOffset = ChannelConfig.SAMPLING_TIME_OFFSET_DEFAULT
        } else {
            configClone.samplingTimeOffset = samplingTimeOffset
        }
        if (samplingGroup == null) {
            configClone.samplingGroup = ChannelConfig.SAMPLING_GROUP_DEFAULT
        } else {
            configClone.samplingGroup = samplingGroup
        }
        if (settings == null) {
            configClone.settings = ChannelConfig.SETTINGS_DEFAULT
        } else {
            configClone.settings = settings
        }
        if (loggingInterval == null) {
            configClone.loggingInterval = ChannelConfig.LOGGING_INTERVAL_DEFAULT
        } else {
            configClone.loggingInterval = loggingInterval
        }
        if (loggingEvent == null) {
            configClone.loggingEvent = ChannelConfig.LOGGING_EVENT_DEFAULT
        } else {
            configClone.loggingEvent = loggingEvent
        }
        if (loggingSettings == null) {
            configClone.loggingSettings = ChannelConfig.LOGGING_SETTINGS_DEFAULT
        } else {
            configClone.loggingSettings = loggingSettings
        }
        if (reader == null) {
            configClone.reader = ChannelConfig.LOGGING_READER_DEFAULT
        } else {
            configClone.reader = reader
        }
        if (loggingTimeOffset == null) {
            configClone.loggingTimeOffset = ChannelConfig.LOGGING_TIME_OFFSET_DEFAULT
        } else {
            configClone.loggingTimeOffset = loggingTimeOffset
        }
        if (disabled == null) {
            configClone.disabled = clonedParentConfig.isDisabled()
        } else {
            if (clonedParentConfig.isDisabled()!!) {
                configClone.disabled = false
            } else {
                configClone.disabled = disabled
            }
        }
        return configClone
    }

    val isSampling: Boolean
        get() = !disabled!! && samplingInterval != null && samplingInterval!! > 0

    override fun addServerMapping(serverMapping: ServerMapping?) {
        if (serverMappings == null) {
            serverMappings = ArrayList()
        }
        serverMappings!!.add(serverMapping)
    }

    override fun deleteServerMappings(id: String?) {
        if (serverMappings != null) {
            val newMappings: MutableList<ServerMapping?> = ArrayList()
            for (serverMapping in serverMappings!!) {
                if (serverMapping!!.id != id) {
                    newMappings.add(serverMapping)
                }
            }
            serverMappings = newMappings
        }
    }

    companion object {
        private val timePattern = Pattern.compile("^([0-9]+)(ms|s|m|h)?$")
        @Throws(ParseException::class)
        fun addChannelFromDomNode(channelConfigNode: Node, parentConfig: DeviceConfig?) {
            val id = getAttributeValue(channelConfigNode, "id")
                ?: throw ParseException("channel has no id attribute")
            val config: ChannelConfigImpl?
            config = try {
                parentConfig!!.addChannel(id) as ChannelConfigImpl?
            } catch (e: Exception) {
                throw ParseException(e)
            }
            val channelChildren = channelConfigNode.childNodes
            try {
                for (i in 0 until channelChildren.length) {
                    val childNode = channelChildren.item(i)
                    val childName = childNode.nodeName
                    if (childName == "#text") {
                        continue
                    } else if (childName == "description") {
                        config!!.setDescription(childNode.textContent)
                    } else if (childName == "channelAddress") {
                        config!!.setChannelAddress(childNode.textContent)
                    } else if (childName == "loggingSettings") {
                        config!!.setLoggingSettings(childNode.textContent)
                        config.setReader(getAttributeValue(childNode, "reader"))
                    } else if (childName == "serverMapping") {
                        val attributes = childNode.attributes
                        val nameAttribute = attributes.getNamedItem("id")
                        if (nameAttribute != null) {
                            config!!.addServerMapping(
                                ServerMapping(nameAttribute.textContent, childNode.textContent)
                            )
                        } else {
                            throw ParseException("No id attribute specified for serverMapping.")
                        }
                    } else if (childName == "unit") {
                        config!!.setUnit(childNode.textContent)
                    } else if (childName == "valueType") {
                        val valueTypeString = childNode.textContent.uppercase(Locale.getDefault())
                        try {
                            config!!.valueType = ValueType.valueOf(valueTypeString)
                        } catch (e: IllegalArgumentException) {
                            throw ParseException("found unknown channel value type:$valueTypeString")
                        }
                        if (config.valueType === ValueType.BYTE_ARRAY || config.valueType === ValueType.STRING) {
                            val valueTypeLengthString = getAttributeValue(childNode, "length")
                                ?: throw ParseException(
                                    "length of " + config.valueType.toString() + " value type was not specified"
                                )
                            config.valueTypeLength = timeStringToMillis(valueTypeLengthString)
                        }
                    } else if (childName == "scalingFactor") {
                        config!!.setScalingFactor(childNode.textContent.toDouble())
                    } else if (childName == "valueOffset") {
                        config!!.setValueOffset(childNode.textContent.toDouble())
                    } else if (childName == "listening") {
                        config!!.setListening(java.lang.Boolean.parseBoolean(childNode.textContent))
                    } else if (childName == "samplingInterval") {
                        config!!.setSamplingInterval(timeStringToMillis(childNode.textContent))
                    } else if (childName == "samplingTimeOffset") {
                        config!!.setSamplingTimeOffset(timeStringToMillis(childNode.textContent))
                    } else if (childName == "samplingGroup") {
                        config!!.setSamplingGroup(childNode.textContent)
                    } else if (childName == "settings") {
                        config!!.setSettings(childNode.textContent)
                    } else if (childName == "loggingInterval") {
                        config!!.setLoggingInterval(timeStringToMillis(childNode.textContent))
                    } else if (childName == "loggingTimeOffset") {
                        config!!.setLoggingTimeOffset(timeStringToMillis(childNode.textContent))
                    } else if (childName == "loggingEvent") {
                        config!!.setLoggingEvent(java.lang.Boolean.parseBoolean(childNode.textContent))
                    } else if (childName == "disabled") {
                        config!!.setDisabled(java.lang.Boolean.parseBoolean(childNode.textContent))
                    } else {
                        throw ParseException("found unknown tag:$childName")
                    }
                }
            } catch (e: IllegalArgumentException) {
                throw ParseException(e)
            } catch (e: IllegalStateException) {
                throw ParseException(e)
            }
        }

        fun getAttributeValue(element: Node, attributeName: String?): String? {
            val attributes = element.attributes
            val nameAttribute = attributes.getNamedItem(attributeName) ?: return null
            return nameAttribute.textContent
        }

        @kotlin.jvm.JvmStatic
        fun millisToTimeString(timeInMillis: Int): String {
            if (timeInMillis <= 0) {
                return "0"
            }
            if (timeInMillis % 1000 != 0) {
                return timeToString("ms", timeInMillis)
            }
            val timeInS = timeInMillis / 1000
            if (timeInS % 60 == 0) {
                val timeInM = timeInS / 60
                if (timeInM % 60 == 0) {
                    val timeInH = timeInM / 60
                    return timeToString("h", timeInH)
                }
                return timeToString("m", timeInM)
            }
            return timeToString("s", timeInS)
        }

        private fun timeToString(timeUnit: String, time: Int): String {
            return MessageFormat.format("{0,number,#}{1}", time, timeUnit)
        }

        @kotlin.jvm.JvmStatic
        @Throws(ParseException::class)
        fun timeStringToMillis(timeString: String?): Int? {
            if (timeString == null || timeString.isEmpty()) {
                return null
            }
            val timeMatcher = timePattern.matcher(timeString)
            if (!timeMatcher.matches()) {
                throw ParseException(MessageFormat.format("Unknown time string: ''{0}''.", timeString))
            }
            val timeNumStr = timeMatcher.group(1)
            val timeNum = parseTimeNumFrom(timeNumStr)
            val timeUnit = timeMatcher.group(2)
            val milliseconds = TimeUnit.MILLISECONDS
            return if (timeUnit == null) {
                timeNum.toInt()
            } else when (timeUnit) {
                "s" -> milliseconds.convert(timeNum, TimeUnit.SECONDS).toInt()
                "m" -> milliseconds.convert(timeNum, TimeUnit.MINUTES).toInt()
                "h" -> milliseconds.convert(timeNum, TimeUnit.HOURS).toInt()
                "ms" -> timeNum.toInt()
                else -> throw ParseException("Unknown time unit: $timeUnit")
            }
        }

        @Throws(ParseException::class)
        private fun parseTimeNumFrom(timeNumStr: String): Long {
            return try {
                timeNumStr.toLong()
            } catch (e: NumberFormatException) {
                throw ParseException(e)
            }
        }

        fun checkIdSyntax(id: String) {
            if (id.matches("[a-zA-Z0-9_-]+")) {
                return
            }
            val msg = MessageFormat.format(
                "Invalid ID: \"{0}\". An ID may not be the empty string and must contain only ASCII letters, digits, hyphens and underscores.",
                id
            )
            throw IllegalArgumentException(msg)
        }
    }
}
