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
import org.openmuc.framework.core.datamanager.ChannelConfigImpl.Companion.timeStringToMillis
import org.openmuc.framework.driver.spi.DriverService
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.*

class DriverConfigImpl internal constructor(id: String, var rootConfigParent: RootConfigImpl?) :
    DriverConfig {

    @set:kotlin.jvm.Throws(IdCollisionException::class)
    override var id: String = ""
        set(value) {
            ChannelConfigImpl.checkIdSyntax(value)
            if (rootConfigParent!!.driverConfigsById.containsKey(value)) {
                throw IdCollisionException("Collision with the driver ID:$value")
            }
            rootConfigParent!!.driverConfigsById.remove(field)
            rootConfigParent!!.driverConfigsById[value] = this
            field = value
        }
    override var samplingTimeout: Int = 0
        set(value) {
            require(value >= 0) { "A negative sampling timeout is not allowed" }
            field = value
        }
    override var connectRetryInterval: Int = 0
        set(value) {
            require(value >= 0) { "A negative connect retry interval is not allowed" }
            field = value
        }
    override var isDisabled: Boolean = false
    val deviceConfigsById: HashMap<String, DeviceConfigImpl> = LinkedHashMap()
    var activeDriver: DriverService? = null

    init {
        this.id = id
    }

    @Throws(IdCollisionException::class)
    override fun addDevice(deviceId: String): DeviceConfig {
        ChannelConfigImpl.checkIdSyntax(deviceId)
        if (rootConfigParent!!.deviceConfigsById.containsKey(deviceId)) {
            throw IdCollisionException("Collision with device ID: $deviceId")
        }
        val newDevice = DeviceConfigImpl(deviceId, this)
        rootConfigParent!!.deviceConfigsById[deviceId] = newDevice
        deviceConfigsById[deviceId] = newDevice
        return newDevice
    }

    override fun getDevice(deviceId: String): DeviceConfig? {
        return deviceConfigsById[deviceId]
    }

    override val devices: Collection<DeviceConfig>
        get() = Collections
            .unmodifiableCollection(deviceConfigsById.values) as Collection<DeviceConfig>

    override fun delete() {
        rootConfigParent!!.driverConfigsById.remove(id)
        for (deviceConfig in deviceConfigsById.values) {
            deviceConfig.clear()
        }
        deviceConfigsById.clear()
        rootConfigParent = null
    }

    fun getDomElement(document: Document): Element {
        val parentElement = document.createElement("driver")
        parentElement.setAttribute("id", id)
        var childElement: Element
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
            childElement.textContent = isDisabled.toString()
            parentElement.appendChild(childElement)
        }
        for (deviceConfig in deviceConfigsById.values) {
            parentElement.appendChild(deviceConfig.getDomElement(document))
        }
        return parentElement
    }

    fun clone(clonedParentConfig: RootConfigImpl?): DriverConfigImpl {
        val configClone = DriverConfigImpl(id, clonedParentConfig)
        configClone.samplingTimeout = samplingTimeout
        configClone.connectRetryInterval = connectRetryInterval
        configClone.isDisabled = isDisabled
        for (deviceConfig in deviceConfigsById.values) {
            configClone.deviceConfigsById[deviceConfig.id] = deviceConfig.clone(configClone)
        }
        return configClone
    }

    fun cloneWithDefaults(clonedParentConfig: RootConfigImpl?): DriverConfigImpl {
        val configClone = DriverConfigImpl(id, clonedParentConfig)
        if (samplingTimeout == null) {
            configClone.samplingTimeout = DriverConfig.SAMPLING_TIMEOUT_DEFAULT
        } else {
            configClone.samplingTimeout = samplingTimeout
        }
        if (connectRetryInterval == null) {
            configClone.connectRetryInterval = DriverConfig.CONNECT_RETRY_INTERVAL_DEFAULT
        } else {
            configClone.connectRetryInterval = connectRetryInterval
        }
        if (isDisabled == null) {
            configClone.isDisabled = DriverConfig.DISABLED_DEFAULT
        } else {
            configClone.isDisabled = isDisabled
        }
        for (deviceConfig in deviceConfigsById.values) {
            configClone.deviceConfigsById[deviceConfig.id] = deviceConfig.cloneWithDefaults(configClone)
        }
        return configClone
    }

    companion object {
        @Throws(ParseException::class)
        fun addDriverFromDomNode(driverConfigNode: Node, parentConfig: RootConfigImpl) {
            val id: String = ChannelConfigImpl.getAttributeValue(driverConfigNode, "id")
                ?: throw ParseException("driver has no id attribute")
            val config = try {
                parentConfig.addDriver(id)
            } catch (e: IdCollisionException) {
                throw ParseException(e)
            }
            parseDiverNode(driverConfigNode, config!!)
        }

        @Throws(ParseException::class)
        private fun parseDiverNode(driverConfigNode: Node, config: DriverConfig) {
            val driverChildren = driverConfigNode.childNodes
            try {
                for (j in 0 until driverChildren.length) {
                    val childNode = driverChildren.item(j)
                    val childName = childNode.nodeName
                    when (childName) {
                        "#text" -> continue
                        "device" -> DeviceConfigImpl.addDeviceFromDomNode(childNode, config)
                        "samplingTimeout" -> config.samplingTimeout = timeStringToMillis(childNode.textContent)
                        "connectRetryInterval" -> config.connectRetryInterval = timeStringToMillis(childNode.textContent)
                        "disabled" -> {
                            val disabledString = childNode.textContent
                            config.isDisabled = disabledString.toBoolean()
                        }

                        else -> throw ParseException("found unknown tag:$childName")
                    }
                }
            } catch (e: IllegalArgumentException) {
                throw ParseException(e)
            }
        }
    }
}
