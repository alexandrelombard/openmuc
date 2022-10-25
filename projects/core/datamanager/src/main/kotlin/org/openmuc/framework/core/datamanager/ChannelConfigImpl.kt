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

class ChannelConfigImpl constructor(id: String, var deviceParent: DeviceConfigImpl?) :
    ChannelConfig, LogChannel {
    @set:Throws(IdCollisionException::class)
    override var id: String = ""
        set(value) {
            checkIdSyntax(value)
            if (deviceParent?.driverParent!!.rootConfigParent!!.channelConfigsById.containsKey(value)) {
                throw IdCollisionException("Collision with channel ID:$value")
            }
            val deviceParent = deviceParent
            if(deviceParent != null) {
                deviceParent.channelConfigsById[value] = deviceParent.channelConfigsById.remove(field)!!
                deviceParent.driverParent!!.rootConfigParent!!.channelConfigsById[value] =
                    deviceParent.driverParent!!.rootConfigParent!!.channelConfigsById.remove(field)
            }
            field = value
        }

    var channel: ChannelImpl? = null
    var state: ChannelState? = null
    override var channelAddress: String = ""
    override var description: String = ""
    override var unit: String? = null
    override var valueType: ValueType = ValueType.UNKNOWN
    override var valueTypeLength: Int = 0
    override var scalingFactor: Double? = null
    override var valueOffset: Double? = null
    override var isListening: Boolean = false
        set(value) {
            check(value && samplingInterval <= 0) { "Listening may not be enabled while sampling is enabled." }
            field = value
        }
    override var samplingInterval: Int = 0
        set(value) {
            check(isListening && value <= 0) { "Sampling may not be enabled while listening is enabled." }
            field = value
        }
    override var samplingTimeOffset: Int = 0
        set(value) {
            require(!(value < 0)) { "The sampling time offset may not be negative." }
            field = value
        }
    override var samplingGroup: String? = null
    override var settings: String? = null
    override var isLoggingEvent: Boolean = false
    override var loggingInterval: Int = 0
    override var loggingTimeOffset: Int = 0
        set(value) {
            require(!(value < 0)) { "The logging time offset may not be negative." }
            field = value
        }
    override var loggingSettings: String? = null
    override var isDisabled: Boolean = false
    override var serverMappings = arrayListOf<ServerMapping>()
    override var reader: String? = null

    init {
        this.id = id
    }

    override fun delete() {
        deviceParent?.channelConfigsById?.remove(id)
        clear()
    }

    fun clear() {
        deviceParent?.driverParent!!.rootConfigParent!!.channelConfigsById.remove(id)
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
            for (serverMapping in serverMappings) {
                childElement = document.createElement("serverMapping")
                childElement.setAttribute("id", serverMapping.id)
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
        if (isListening != null) {
            childElement = document.createElement("listening")
            childElement.textContent = isListening.toString()
            parentElement.appendChild(childElement)
        }
        if (samplingInterval != null) {
            childElement = document.createElement("samplingInterval")
            childElement.textContent = millisToTimeString(samplingInterval)
            parentElement.appendChild(childElement)
        }
        if (samplingTimeOffset != null) {
            childElement = document.createElement("samplingTimeOffset")
            childElement.textContent = millisToTimeString(samplingTimeOffset)
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
            childElement.textContent = millisToTimeString(loggingInterval)
            parentElement.appendChild(childElement)
        }
        if (loggingTimeOffset != null) {
            childElement = document.createElement("loggingTimeOffset")
            childElement.textContent = millisToTimeString(loggingTimeOffset)
            parentElement.appendChild(childElement)
        }
        if (isLoggingEvent != null) {
            childElement = document.createElement("loggingEvent")
            childElement.textContent = isLoggingEvent.toString()
            parentElement.appendChild(childElement)
        }
        if (loggingSettings != null) {
            childElement = document.createElement("loggingSettings")
            childElement.textContent = loggingSettings
            parentElement.appendChild(childElement)
        }
        if (isDisabled != null) {
            childElement = document.createElement("disabled")
            childElement.textContent = isDisabled.toString()
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
        configClone.isListening = isListening
        configClone.samplingInterval = samplingInterval
        configClone.samplingTimeOffset = samplingTimeOffset
        configClone.samplingGroup = samplingGroup
        configClone.settings = settings
        configClone.loggingInterval = loggingInterval
        configClone.loggingTimeOffset = loggingTimeOffset
        configClone.isDisabled = isDisabled
        configClone.isLoggingEvent = isLoggingEvent
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
        if (isListening == null) {
            configClone.isListening = ChannelConfig.LISTENING_DEFAULT
        } else {
            configClone.isListening = isListening
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
        if (isLoggingEvent == null) {
            configClone.isLoggingEvent = ChannelConfig.LOGGING_EVENT_DEFAULT
        } else {
            configClone.isLoggingEvent = isLoggingEvent
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
        if (isDisabled == null) {
            configClone.isDisabled = clonedParentConfig.isDisabled ?: false
        } else {
            if (clonedParentConfig.isDisabled) {
                configClone.isDisabled = false
            } else {
                configClone.isDisabled = isDisabled
            }
        }
        return configClone
    }

    val isSampling: Boolean
        get() = !isDisabled && samplingInterval != null && samplingInterval > 0

    override fun addServerMapping(serverMapping: ServerMapping) {
        if (serverMappings == null) {
            serverMappings = ArrayList()
        }
        serverMappings.add(serverMapping)
    }

    override fun deleteServerMappings(id: String) {
        if (serverMappings != null) {
            val newMappings = ArrayList<ServerMapping>()
            for (serverMapping in serverMappings) {
                if (serverMapping.id != id) {
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
                        config!!.description = childNode.textContent
                    } else if (childName == "channelAddress") {
                        config!!.channelAddress = childNode.textContent
                    } else if (childName == "loggingSettings") {
                        config!!.loggingSettings = childNode.textContent
                        config.reader = getAttributeValue(childNode, "reader")
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
                        config!!.unit = childNode.textContent
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
                        config!!.scalingFactor = childNode.textContent.toDouble()
                    } else if (childName == "valueOffset") {
                        config!!.valueOffset = childNode.textContent.toDouble()
                    } else if (childName == "listening") {
                        config!!.isListening = childNode.textContent.toBoolean()
                    } else if (childName == "samplingInterval") {
                        config!!.samplingInterval = timeStringToMillis(childNode.textContent)
                    } else if (childName == "samplingTimeOffset") {
                        config!!.samplingTimeOffset = timeStringToMillis(childNode.textContent)
                    } else if (childName == "samplingGroup") {
                        config!!.samplingGroup = childNode.textContent
                    } else if (childName == "settings") {
                        config!!.settings = childNode.textContent
                    } else if (childName == "loggingInterval") {
                        config!!.loggingInterval = timeStringToMillis(childNode.textContent)
                    } else if (childName == "loggingTimeOffset") {
                        config!!.loggingTimeOffset = timeStringToMillis(childNode.textContent)
                    } else if (childName == "loggingEvent") {
                        config!!.isLoggingEvent = childNode.textContent.toBoolean()
                    } else if (childName == "disabled") {
                        config!!.isDisabled = childNode.textContent.toBoolean()
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
        fun timeStringToMillis(timeString: String): Int {
            if (timeString.isEmpty()) {
                throw ParseException("Unable to parse empty string")
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
            if (id.matches("[a-zA-Z0-9_-]+".toRegex())) {
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
