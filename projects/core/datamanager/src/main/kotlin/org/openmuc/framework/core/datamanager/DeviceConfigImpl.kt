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
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.*

class DeviceConfigImpl(id: String, var driverParent: DriverConfigImpl?) : DeviceConfig {
    @set:Throws(IdCollisionException::class)
    override var id: String = ""
        set(value) {
            ChannelConfigImpl.checkIdSyntax(value)
            if (driverParent!!.rootConfigParent!!.deviceConfigsById.containsKey(value)) {
                throw IdCollisionException("Collision with device ID:$value")
            }
            driverParent!!.deviceConfigsById[value] = driverParent!!.deviceConfigsById.remove(field)!!
            driverParent!!.rootConfigParent!!.deviceConfigsById[value] =
                driverParent!!.rootConfigParent!!.deviceConfigsById.remove(field)
            field = value
        }

    override var description: String? = null
    override var deviceAddress: String? = null
    override var settings: String? = null
    override var samplingTimeout: Int = 0
        set (value) {
            require(!(value < 0)) { "A negative sampling timeout is not allowed" }
            field = value
        }
    override var connectRetryInterval: Int = 0
        set(value) {
            require(!(value < 0)) { "A negative connect retry interval is not allowed" }
            field = value
        }
    override var isDisabled: Boolean = false
    var device: Device? = null
    val channelConfigsById: HashMap<String, ChannelConfigImpl> = LinkedHashMap()

    init {
        this.id = id
    }

    fun clone(clonedParentConfig: DriverConfigImpl?): DeviceConfigImpl {
        val configClone = DeviceConfigImpl(id, clonedParentConfig)
        configClone.description = description
        configClone.deviceAddress = deviceAddress
        configClone.settings = settings
        configClone.samplingTimeout = samplingTimeout
        configClone.connectRetryInterval = connectRetryInterval
        configClone.isDisabled = isDisabled
        for (channelConfig in channelConfigsById.values) {
            configClone.channelConfigsById[channelConfig!!.id] = channelConfig.clone(configClone)
        }
        return configClone
    }

    @Throws(IdCollisionException::class)
    override fun addChannel(channelId: String): ChannelConfig {
        ChannelConfigImpl.checkIdSyntax(channelId)
        if (driverParent!!.rootConfigParent!!.channelConfigsById.containsKey(channelId)) {
            throw IdCollisionException("Collision with channel ID: $channelId")
        }
        val newChannel = ChannelConfigImpl(channelId, this)
        driverParent!!.rootConfigParent!!.channelConfigsById[channelId] = newChannel
        channelConfigsById[channelId] = newChannel
        return newChannel
    }

    override fun getChannel(channelId: String): ChannelConfig? {
        return channelConfigsById[channelId]
    }

    override val channels: Collection<ChannelConfig>
        get() = Collections
            .unmodifiableCollection(channelConfigsById.values) as Collection<ChannelConfig>

    override fun delete() {
        driverParent!!.deviceConfigsById.remove(id)
        clear()
    }

    fun clear() {
        for (channelConfig in channelConfigsById.values) {
            channelConfig!!.clear()
        }
        channelConfigsById.clear()
        driverParent!!.rootConfigParent!!.deviceConfigsById.remove(id)
        driverParent = null
    }

    override val driver: DriverConfig?
        get() = driverParent

    fun getDomElement(document: Document): Element {
        val parentElement = document.createElement("device")
        parentElement.setAttribute("id", id)
        var childElement: Element
        if (description != null) {
            childElement = document.createElement("description")
            childElement.textContent = description
            parentElement.appendChild(childElement)
        }
        if (deviceAddress != null) {
            childElement = document.createElement("deviceAddress")
            childElement.textContent = deviceAddress
            parentElement.appendChild(childElement)
        }
        if (settings != null) {
            childElement = document.createElement("settings")
            childElement.textContent = settings
            parentElement.appendChild(childElement)
        }
        if (samplingTimeout != null) {
            childElement = document.createElement("samplingTimeout")
            childElement.textContent = ChannelConfigImpl.millisToTimeString(samplingTimeout)
            parentElement.appendChild(childElement)
        }
        if (connectRetryInterval != null) {
            childElement = document.createElement("connectRetryInterval")
            childElement.textContent = ChannelConfigImpl.millisToTimeString(connectRetryInterval)
            parentElement.appendChild(childElement)
        }
        if (isDisabled != null) {
            childElement = document.createElement("disabled")
            if (isDisabled) {
                childElement.textContent = "true"
            } else {
                childElement.textContent = "false"
            }
            parentElement.appendChild(childElement)
        }
        for (channelConfig in channelConfigsById.values) {
            parentElement.appendChild(channelConfig!!.getDomElement(document))
        }
        return parentElement
    }

    fun cloneWithDefaults(clonedParentConfig: DriverConfigImpl): DeviceConfigImpl {
        val configClone = DeviceConfigImpl(id, clonedParentConfig)
        if (description == null) {
            configClone.description = DeviceConfig.DESCRIPTION_DEFAULT
        } else {
            configClone.description = description
        }
        if (deviceAddress == null) {
            configClone.deviceAddress = DeviceConfig.DEVICE_ADDRESS_DEFAULT
        } else {
            configClone.deviceAddress = deviceAddress
        }
        if (settings == null) {
            configClone.settings = DeviceConfig.SETTINGS_DEFAULT
        } else {
            configClone.settings = settings
        }
        if (samplingTimeout == null) {
            configClone.samplingTimeout = clonedParentConfig.samplingTimeout
        } else {
            configClone.samplingTimeout = samplingTimeout
        }
        if (connectRetryInterval == null) {
            configClone.connectRetryInterval = clonedParentConfig.connectRetryInterval
        } else {
            configClone.connectRetryInterval = connectRetryInterval
        }
        if (isDisabled == null || clonedParentConfig.isDisabled) {
            configClone.isDisabled = clonedParentConfig.isDisabled
        } else {
            configClone.isDisabled = isDisabled
        }
        for (channelConfig in channelConfigsById.values) {
            configClone.channelConfigsById[channelConfig!!.id] = channelConfig.cloneWithDefaults(configClone)
        }
        return configClone
    }

    companion object {
        @Throws(ParseException::class)
        fun addDeviceFromDomNode(deviceConfigNode: Node, parentConfig: DriverConfig?) {
            val id: String = ChannelConfigImpl.getAttributeValue(deviceConfigNode, "id")
                ?: throw ParseException("device has no id attribute")
            val config: DeviceConfigImpl?
            config = try {
                parentConfig!!.addDevice(id) as DeviceConfigImpl?
            } catch (e: Exception) {
                throw ParseException(e)
            }
            val deviceChildren = deviceConfigNode.childNodes
            try {
                for (i in 0 until deviceChildren.length) {
                    val childNode = deviceChildren.item(i)
                    val childName = childNode.nodeName
                    if (childName == "#text") {
                        continue
                    } else if (childName == "channel") {
                        ChannelConfigImpl.addChannelFromDomNode(childNode, config)
                    } else if (childName == "description") {
                        config!!.description = childNode.textContent
                    } else if (childName == "deviceAddress") {
                        config!!.deviceAddress = childNode.textContent
                    } else if (childName == "settings") {
                        config!!.settings = childNode.textContent
                    } else if (childName == "samplingTimeout") {
                        config!!.samplingTimeout = (ChannelConfigImpl.timeStringToMillis(childNode.textContent))
                    } else if (childName == "connectRetryInterval") {
                        config!!.connectRetryInterval = (ChannelConfigImpl.timeStringToMillis(childNode.textContent))
                    } else if (childName == "disabled") {
                        config!!.isDisabled = childNode.textContent.toBoolean()
                    } else {
                        throw ParseException("found unknown tag:$childName")
                    }
                }
            } catch (e: IllegalArgumentException) {
                throw ParseException(e)
            }
        }
    }
}
