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
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.*

class DeviceConfigImpl(private override var id: String, var driverParent: DriverConfigImpl?) : DeviceConfig {
    private override var description: String? = null
    private override var deviceAddress: String? = null
    private override var settings: String? = null
    private override var samplingTimeout: Int? = null
    private override var connectRetryInterval: Int? = null
    private var disabled: Boolean? = null
    var device: Device? = null
    val channelConfigsById: HashMap<String?, ChannelConfigImpl?> = LinkedHashMap()
    fun clone(clonedParentConfig: DriverConfigImpl?): DeviceConfigImpl {
        val configClone = DeviceConfigImpl(id, clonedParentConfig)
        configClone.description = description
        configClone.deviceAddress = deviceAddress
        configClone.settings = settings
        configClone.samplingTimeout = samplingTimeout
        configClone.connectRetryInterval = connectRetryInterval
        configClone.disabled = disabled
        for (channelConfig in channelConfigsById.values) {
            configClone.channelConfigsById[channelConfig!!.getId()] = channelConfig.clone(configClone)
        }
        return configClone
    }

    override fun getId(): String {
        return id
    }

    @Throws(IdCollisionException::class)
    override fun setId(id: String?) {
        requireNotNull(id) { "The device ID may not be null" }
        ChannelConfigImpl.Companion.checkIdSyntax(id)
        if (driverParent!!.rootConfigParent!!.deviceConfigsById.containsKey(id)) {
            throw IdCollisionException("Collision with device ID:$id")
        }
        driverParent!!.deviceConfigsById[id] = driverParent!!.deviceConfigsById.remove(this.id)
        driverParent!!.rootConfigParent!!.deviceConfigsById[id] =
            driverParent!!.rootConfigParent!!.deviceConfigsById.remove(this.id)
        this.id = id
    }

    override fun getDescription(): String? {
        return description
    }

    override fun setDescription(description: String?) {
        this.description = description
    }

    override fun getDeviceAddress(): String? {
        return deviceAddress
    }

    override fun setDeviceAddress(address: String?) {
        deviceAddress = address
    }

    override fun getSettings(): String? {
        return settings
    }

    override fun setSettings(settings: String?) {
        this.settings = settings
    }

    override fun getSamplingTimeout(): Int {
        return samplingTimeout!!
    }

    override fun setSamplingTimeout(timeout: Int?) {
        require(!(timeout != null && timeout < 0)) { "A negative sampling timeout is not allowed" }
        samplingTimeout = timeout
    }

    override fun getConnectRetryInterval(): Int? {
        return connectRetryInterval
    }

    override fun setConnectRetryInterval(interval: Int?) {
        require(!(interval != null && interval < 0)) { "A negative connect retry interval is not allowed" }
        connectRetryInterval = interval
    }

    override fun isDisabled(): Boolean? {
        return disabled
    }

    override fun setDisabled(disabled: Boolean?) {
        this.disabled = disabled
    }

    @Throws(IdCollisionException::class)
    override fun addChannel(channelId: String?): ChannelConfig? {
        requireNotNull(channelId) { "The channel ID may not be null" }
        ChannelConfigImpl.Companion.checkIdSyntax(channelId)
        if (driverParent!!.rootConfigParent!!.channelConfigsById.containsKey(channelId)) {
            throw IdCollisionException("Collision with channel ID: $channelId")
        }
        val newChannel = ChannelConfigImpl(channelId, this)
        driverParent!!.rootConfigParent!!.channelConfigsById[channelId] = newChannel
        channelConfigsById[channelId] = newChannel
        return newChannel
    }

    override fun getChannel(channelId: String?): ChannelConfig? {
        return channelConfigsById[channelId]
    }

    override val channels: Collection<ChannelConfig>?
        get() = Collections
            .unmodifiableCollection(channelConfigsById.values) as Collection<*> as Collection<ChannelConfig>

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
            childElement.textContent = ChannelConfigImpl.Companion.millisToTimeString(samplingTimeout!!)
            parentElement.appendChild(childElement)
        }
        if (connectRetryInterval != null) {
            childElement = document.createElement("connectRetryInterval")
            childElement.textContent = ChannelConfigImpl.Companion.millisToTimeString(connectRetryInterval!!)
            parentElement.appendChild(childElement)
        }
        if (disabled != null) {
            childElement = document.createElement("disabled")
            if (disabled) {
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
        if (disabled == null || clonedParentConfig.disabled!!) {
            configClone.disabled = clonedParentConfig.disabled
        } else {
            configClone.disabled = disabled
        }
        for (channelConfig in channelConfigsById.values) {
            configClone.channelConfigsById[channelConfig!!.getId()] = channelConfig.cloneWithDefaults(configClone)
        }
        return configClone
    }

    companion object {
        @Throws(ParseException::class)
        fun addDeviceFromDomNode(deviceConfigNode: Node, parentConfig: DriverConfig?) {
            val id: String = ChannelConfigImpl.Companion.getAttributeValue(deviceConfigNode, "id")
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
                        ChannelConfigImpl.Companion.addChannelFromDomNode(childNode, config)
                    } else if (childName == "description") {
                        config!!.setDescription(childNode.textContent)
                    } else if (childName == "deviceAddress") {
                        config!!.setDeviceAddress(childNode.textContent)
                    } else if (childName == "settings") {
                        config!!.setSettings(childNode.textContent)
                    } else if (childName == "samplingTimeout") {
                        config!!.setSamplingTimeout(ChannelConfigImpl.Companion.timeStringToMillis(childNode.textContent))
                    } else if (childName == "connectRetryInterval") {
                        config!!.setConnectRetryInterval(ChannelConfigImpl.Companion.timeStringToMillis(childNode.textContent))
                    } else if (childName == "disabled") {
                        config!!.disabled = java.lang.Boolean.parseBoolean(childNode.textContent)
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
