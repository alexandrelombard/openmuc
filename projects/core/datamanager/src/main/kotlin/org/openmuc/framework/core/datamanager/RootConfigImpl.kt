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
import org.openmuc.framework.datalogger.spi.LogChannel
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.*
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.TransformerFactoryConfigurationError
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class RootConfigImpl : RootConfig {
    override var dataLogSource: String? = null
    val driverConfigsById: HashMap<String, DriverConfigImpl?> = LinkedHashMap()
    val deviceConfigsById = HashMap<String, DeviceConfigImpl?>()
    val channelConfigsById = HashMap<String, ChannelConfigImpl?>()

    // TODO really needed?:
    var logChannels: List<LogChannel>? = null


    @Throws(
        TransformerFactoryConfigurationError::class,
        IOException::class,
        ParserConfigurationException::class,
        TransformerException::class
    )
    fun writeToFile(configFile: File?) {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        val result = StreamResult(FileWriter(configFile))
        val docBuild = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuild.newDocument()
        doc.appendChild(getDomElement(doc))
        val source = DOMSource(doc)
        transformer.transform(source, result)
    }

    private fun getDomElement(document: Document): Element {
        val rootConfigElement = document.createElement("configuration")
        if (dataLogSource != null) {
            val loggerChild: Node = document.createElement("dataLogSource")
            loggerChild.textContent = dataLogSource
            rootConfigElement.appendChild(loggerChild)
        }
        for (driverConfig in driverConfigsById.values) {
            rootConfigElement.appendChild(driverConfig!!.getDomElement(document))
        }
        return rootConfigElement
    }

    override fun getOrAddDriver(id: String): DriverConfig? {
        return try {
            addDriver(id)
        } catch (e: IdCollisionException) {
            driverConfigsById[id]
        }
    }

    @Throws(IdCollisionException::class)
    override fun addDriver(id: String): DriverConfigImpl {
        ChannelConfigImpl.checkIdSyntax(id)
        if (driverConfigsById.containsKey(id)) {
            throw IdCollisionException("Collision with the driver ID: $id")
        }
        val driverConfig = DriverConfigImpl(id, this)
        driverConfigsById[id] = driverConfig
        return driverConfig
    }

    override fun getDriver(id: String): DriverConfig? {
        return driverConfigsById[id]
    }

    override fun getDevice(id: String): DeviceConfig? {
        return deviceConfigsById[id]
    }

    override fun getChannel(id: String): ChannelConfig? {
        return channelConfigsById[id]
    }

    override val drivers: Collection<DriverConfig>
        get() = Collections
            .unmodifiableCollection(driverConfigsById.values) as Collection<DriverConfig>

    constructor() {}
    constructor(other: RootConfigImpl?) {
        dataLogSource = other!!.dataLogSource
        for (driverConfig in other.driverConfigsById.values) {
            addDriver(driverConfig!!.clone(this))
        }
    }

    fun cloneWithDefaults(): RootConfigImpl {
        val configClone = RootConfigImpl()
        if (dataLogSource != null) {
            configClone.dataLogSource = dataLogSource
        } else {
            configClone.dataLogSource = ""
        }
        for (driverConfig in driverConfigsById.values) {
            configClone.addDriver(driverConfig!!.cloneWithDefaults(configClone))
        }
        return configClone
    }

    private fun addDriver(driverConfig: DriverConfigImpl) {
        driverConfigsById[driverConfig.id] = driverConfig
        for (deviceConfig in driverConfig.deviceConfigsById.values) {
            deviceConfigsById[deviceConfig.id] = deviceConfig
            for (channelConfig in deviceConfig.channelConfigsById.values) {
                channelConfigsById[channelConfig!!.id] = channelConfig
            }
        }
    }

    companion object {
        @Throws(ParseException::class, FileNotFoundException::class)
        fun createFromFile(configFile: File?): RootConfigImpl {
            if (configFile == null) {
                throw NullPointerException("configFileName is null or the empty string.")
            }
            if (!configFile.exists()) {
                throw FileNotFoundException("Config file not found.")
            }
            val docBFac = DocumentBuilderFactory.newInstance()
            docBFac.isIgnoringComments = true
            val doc = parseDocument(configFile, docBFac)
            val rootNode: Node = doc.documentElement
            if (rootNode.nodeName != "configuration") {
                throw ParseException("root node in configuration is not of type \"configuration\"")
            }
            return loadRootConfigFrom(rootNode)
        }

        @Throws(ParseException::class)
        private fun parseDocument(configFile: File, docBFac: DocumentBuilderFactory): Document {
            return try {
                docBFac.newDocumentBuilder().parse(configFile)
            } catch (e: ParserConfigurationException) {
                throw ParseException(e)
            } catch (e: SAXException) {
                throw ParseException(e)
            } catch (e: IOException) {
                throw ParseException(e)
            }
        }

        @Throws(ParseException::class)
        private fun loadRootConfigFrom(domNode: Node): RootConfigImpl {
            val rootConfig = RootConfigImpl()
            val rootConfigChildren = domNode.childNodes
            for (i in 0 until rootConfigChildren.length) {
                val childNode = rootConfigChildren.item(i)
                when (val childName = childNode.nodeName) {
                    "#text" -> continue
                    "driver" -> DriverConfigImpl.Companion.addDriverFromDomNode(childNode, rootConfig)
                    "dataLogSource" -> rootConfig.dataLogSource = childNode.textContent
                    else -> throw ParseException("found unknown tag:$childName")
                }
            }
            return rootConfig
        }
    }
}
