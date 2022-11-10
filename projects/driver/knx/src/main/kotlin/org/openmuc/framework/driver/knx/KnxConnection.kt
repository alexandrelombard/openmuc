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
package org.openmuc.framework.driver.knx

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.driver.spi.*
import org.slf4j.LoggerFactory
import tuwien.auto.calimero.DataUnitBuilder
import tuwien.auto.calimero.GroupAddress
import tuwien.auto.calimero.IndividualAddress
import tuwien.auto.calimero.exception.KNXException
import tuwien.auto.calimero.exception.KNXFormatException
import tuwien.auto.calimero.exception.KNXTimeoutException
import tuwien.auto.calimero.link.KNXLinkClosedException
import tuwien.auto.calimero.link.KNXNetworkLink
import tuwien.auto.calimero.link.KNXNetworkLinkIP
import tuwien.auto.calimero.link.medium.KNXMediumSettings
import tuwien.auto.calimero.link.medium.TPSettings
import tuwien.auto.calimero.process.ProcessCommunicator
import tuwien.auto.calimero.process.ProcessCommunicatorImpl
import java.net.InetSocketAddress
import java.net.URI
import java.net.URISyntaxException
import java.util.*

class KnxConnection internal constructor(deviceAddress: String, settings: String, timeout: Int) : Connection {
    private var knxNetworkLink: KNXNetworkLink? = null
    private var processCommunicator: ProcessCommunicator
    private var processListener: KnxProcessListener
    private var responseTimeout = 0
    private var name: String = ""

    init {
        var interfaceURI: URI? = null
        var deviceURI: URI? = null
        val isKNXIP: Boolean
        try {
            val deviceAddressSubStrings =
                deviceAddress.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (deviceAddressSubStrings.size == 2) {
                interfaceURI = URI(deviceAddressSubStrings[0])
                deviceURI = URI(deviceAddressSubStrings[1])
                isKNXIP = true
            } else {
                deviceURI = URI(deviceAddress)
                isKNXIP = false
            }
        } catch (e: URISyntaxException) {
            logger.error("wrong format of interface address in deviceAddress")
            throw ArgumentSyntaxException()
        }
        var address = IndividualAddress(0)
        val serialNumber = ByteArray(6)

        val settingsArray = settings.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (arg in settingsArray) {
            val p = arg.indexOf('=')
            if (p != -1) {
                val key = arg.substring(0, p).lowercase(Locale.getDefault()).trim { it <= ' ' }
                var value = arg.substring(p + 1).trim { it <= ' ' }
                if (key.equals("address", ignoreCase = true)) {
                    try {
                        address = IndividualAddress(value)
                        logger.debug("setting individual address to $address")
                    } catch (e: KNXFormatException) {
                        logger.warn("wrong format of individual address in settings")
                    }
                } else if (key.equals("serialnumber", ignoreCase = true)) {
                    if (value.length == 12) {
                        value = value.lowercase(Locale.getDefault())
                        for (i in 0..5) {
                            val hexValue = value.substring(i * 2, i * 2 + 2)
                            serialNumber[i] = hexValue.toInt(16).toByte()
                        }
                        logger.debug("setting serial number to " + DataUnitBuilder.toHex(serialNumber, ":"))
                    }
                }
            }
        }

        if (isKNXIP && isSchemeOk(deviceURI, KnxDriver.ADDRESS_SCHEME_KNXIP)
            && isSchemeOk(interfaceURI, KnxDriver.ADDRESS_SCHEME_KNXIP)
        ) {
            name = interfaceURI!!.host + " - " + deviceURI.host
            logger.debug("connecting over KNX/IP from " + name.replace("-", "to"))
            connectNetIP(interfaceURI, deviceURI, address)
        } else {
            logger.error("wrong format of device URI in deviceAddress")
            throw ArgumentSyntaxException()
        }
        try {
            processCommunicator = ProcessCommunicatorImpl(knxNetworkLink)
            processListener = KnxProcessListener()
            processCommunicator.addProcessListener(processListener)
            setResponseTimeout(timeout)
        } catch (e: KNXLinkClosedException) {
            throw ConnectionException(e)
        }
    }

    private fun isSchemeOk(uri: URI?, scheme: String): Boolean {
        val isSchemeOK = uri!!.scheme.equals(scheme, ignoreCase = true)
        if (!isSchemeOK) {
            logger.error("Scheme is not OK. Is " + uri.scheme + " should be ", scheme)
        }
        return isSchemeOK
    }

    @Throws(ConnectionException::class)
    private fun connectNetIP(localUri: URI?, remoteUri: URI, address: IndividualAddress) {
        try {
            val localIP = localUri!!.host
            val localPort = if (localUri.port < 0) DEFAULT_PORT else localUri.port
            val remoteIP = remoteUri.host
            val remotePort = if (remoteUri.port < 0) DEFAULT_PORT else remoteUri.port
            val serviceMode = KNXNetworkLinkIP.TUNNELING
            val localSocket = InetSocketAddress(localIP, localPort)
            val remoteSocket = InetSocketAddress(remoteIP, remotePort)
            val useNAT = true
            val settings: KNXMediumSettings = TPSettings()
            settings.deviceAddress = address
            knxNetworkLink = KNXNetworkLinkIP(serviceMode, localSocket, remoteSocket, useNAT, settings)
        } catch (e: KNXException) {
            logger.error("Connection failed: " + e.message)
            throw ConnectionException(e)
        } catch (e: InterruptedException) {
            throw ConnectionException(e)
        }
    }

    private fun listKnownChannels(): List<ChannelScanInfo> {
        val informations: MutableList<ChannelScanInfo> = ArrayList()
        val values = processListener.cachedValues
        val keys: Set<GroupAddress?> = values.keys
        for (groupAddress in keys) {
            val asdu = values[groupAddress]
            val channelAddress = StringBuilder()
            channelAddress.append(groupAddress.toString()).append(":1.001")
            val description = StringBuilder()
            description.append("Datapoint length: ").append(asdu!!.size)
            description.append("; Last datapoint ASDU: ").append(DataUnitBuilder.toHex(asdu, ":"))
            informations.add(ChannelScanInfo(channelAddress.toString(), description.toString(), ValueType.UNKNOWN, 0))
        }
        return informations
    }

    @Throws(ConnectionException::class)
    private fun ensureOpenConnection() {
        if (!knxNetworkLink!!.isOpen) {
            throw ConnectionException()
        }
    }

    @Throws(ConnectionException::class, KNXException::class)
    private fun read(groupDP: KnxGroupDP, timeout: Int): Record {
        ensureOpenConnection()
        val record: Record
        setResponseTimeout(timeout)
        try {
            groupDP.knxValue.dPTValue = processCommunicator.read(groupDP)
            record = Record(groupDP.knxValue.openMucValue, System.currentTimeMillis())
        } catch (e: InterruptedException) {
            throw ConnectionException("Read failed for group address " + groupDP.mainAddress, e)
        } catch (e: KNXLinkClosedException) {
            throw ConnectionException(e)
        }
        return record
    }

    @Throws(ConnectionException::class)
    fun write(groupDP: KnxGroupDP, timeout: Int): Boolean {
        ensureOpenConnection()
        setResponseTimeout(timeout)
        return try {
            val value = groupDP.knxValue
            processCommunicator.write(groupDP, value.dPTValue)
            true
        } catch (e: KNXLinkClosedException) {
            throw ConnectionException(e)
        } catch (e: KNXException) {
            logger.warn("write failed")
            false
        }
    }

    private fun setResponseTimeout(timeout: Int) {
        if (responseTimeout != timeout) {
            responseTimeout = timeout
            val timeoutSec = timeout / 1000
            if (timeoutSec > 0) {
                processCommunicator.responseTimeout = timeoutSec
            } else {
                processCommunicator.responseTimeout = DEFAULT_TIMEOUT
            }
        }
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun scanForChannels(settings: String): List<ChannelScanInfo> {
        return listKnownChannels()
    }

    override fun disconnect() {
        logger.debug("disconnecting from $name")
        processCommunicator.detach()
        knxNetworkLink!!.close()
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun read(
        containers: List<ChannelRecordContainer>,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        for (container in containers) {
            try {
                val groupDP: KnxGroupDP
                if (container.channelHandle == null) {
                    groupDP = createKnxGroupDP(container.channelAddress)
                    logger.debug("New datapoint: $groupDP")
                    container.channelHandle = groupDP
                } else {
                    groupDP = container.channelHandle as KnxGroupDP
                }
                val record = read(groupDP, KnxDriver.timeout)
                container.record = record
            } catch (e: ArgumentSyntaxException) {
                container.record = Record(Flag.DRIVER_ERROR_CHANNEL_ADDRESS_SYNTAX_INVALID)
                logger.error(e.message, "Channel-ID: " + container.channel!!.id)
            } catch (e1: KNXTimeoutException) {
                logger.debug(e1.message)
                container.record = Record(null, System.currentTimeMillis(), Flag.TIMEOUT)
            } catch (e: KNXException) {
                logger.warn(e.message)
            }
        }
        return null
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer>, listener: RecordsReceivedListener?) {
        for (container in containers) {
            if (container.channelHandle == null) {
                try {
                    container.channelHandle = createKnxGroupDP(container.channelAddress)
                } catch (e: ArgumentSyntaxException) {
                    container.record = Record(Flag.DRIVER_ERROR_CHANNEL_ADDRESS_SYNTAX_INVALID)
                    logger.error(e.message + "Channel-ID: " + container.channel!!.id)
                } catch (e: KNXException) {
                    logger.warn(e.message)
                }
            }
        }
        logger.info("Start listening for ", containers.size, " channels")
        processListener!!.registerOpenMucListener(containers, listener)
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer>, containerListHandle: Any?): Any? {
        for (container in containers) {
            var groupDP: KnxGroupDP
            try {
                if (container.channelHandle == null) {
                    groupDP = createKnxGroupDP(container.channelAddress)
                    logger.debug("New datapoint: $groupDP")
                    container.channelHandle = groupDP
                } else {
                    groupDP = container.channelHandle as KnxGroupDP
                }
                groupDP.knxValue.openMucValue = container.value!!
                val state = write(groupDP, KnxDriver.timeout)
                if (state) {
                    container.flag = Flag.VALID
                } else {
                    container.flag = Flag.UNKNOWN_ERROR
                }
            } catch (e: ArgumentSyntaxException) {
                container.flag = Flag.DRIVER_ERROR_CHANNEL_ADDRESS_SYNTAX_INVALID
                logger.error(e.message)
            } catch (e: KNXException) {
                logger.warn(e.message)
            }
        }
        return null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KnxConnection::class.java)
        private const val DEFAULT_PORT = 3671
        private const val DEFAULT_TIMEOUT = 2
        @Throws(KNXException::class, ArgumentSyntaxException::class)
        private fun createKnxGroupDP(channelAddress: String?): KnxGroupDP {
            val address = channelAddress!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var dp: KnxGroupDP? = null
            if (address.size != 2 && address.size != 4) {
                throw ArgumentSyntaxException("Channel address has a wrong format. ")
            } else {
                val main = GroupAddress(address[0])
                val dptID = address[1]
                dp = KnxGroupDP(main, channelAddress, dptID)
                if (address.size == 4) {
                    val AET = address[2] == "1"
                    val value = address[3]
                }
            }
            return dp
        }
    }
}
