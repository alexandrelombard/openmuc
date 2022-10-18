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
package org.openmuc.framework.driver.snmp.implementation

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.data.ByteArrayValue
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.driver.spi.*
import org.slf4j.LoggerFactory
import org.snmp4j.AbstractTarget
import org.snmp4j.PDU
import org.snmp4j.Snmp
import org.snmp4j.event.ResponseEvent
import org.snmp4j.mp.MPv3
import org.snmp4j.security.SecurityModels
import org.snmp4j.security.SecurityProtocols
import org.snmp4j.security.USM
import org.snmp4j.smi.*
import org.snmp4j.transport.DefaultUdpTransportMapping
import java.io.IOException
import java.util.*

/**
 *
 * Super class for defining SNMP enabled devices.
 */
abstract class SnmpDevice : Connection {
    enum class SNMPVersion {
        V1, V2c, V3
    }

    protected var targetAddress: Address? = null
    protected var snmp: Snmp? = null
    protected var usm: USM? = null
    protected var timeout = 3000 // in milliseconds
    protected var retries = 3
    protected var authenticationPassphrase: String? = null
    protected var target: AbstractTarget? = null
    protected var listeners: MutableList<SnmpDiscoveryListener> = ArrayList()

    /**
     * snmp constructor takes primary parameters in order to create snmp object. this implementation uses UDP protocol
     *
     * @param address
     * Contains ip and port. accepted string "X.X.X.X/portNo"
     * @param authenticationPassphrase
     * the authentication pass phrase. If not `null`, `authenticationProtocol` must
     * also be not `null`. RFC3414 11.2 requires pass phrases to have a minimum length of 8
     * bytes. If the length of `authenticationPassphrase` is less than 8 bytes an
     * `IllegalArgumentException` is thrown. [required by snmp4j library]
     *
     * @throws ConnectionException
     * thrown if SNMP listen or initialization failed
     * @throws ArgumentSyntaxException
     * thrown if Device address foramt is wrong
     */
    constructor(address: String?, authenticationPassphrase: String?) {

        // start snmp compatible with all versions
        snmp = try {
            Snmp(DefaultUdpTransportMapping())
        } catch (e: IOException) {
            throw ConnectionException(
                """
    SNMP initialization failed! 
    ${e.message}
    """.trimIndent()
            )
        }
        usm = USM(SecurityProtocols.getInstance(), OctetString(MPv3.createLocalEngineID()), 0)
        SecurityModels.getInstance().addSecurityModel(usm)
        try {
            snmp!!.listen()
        } catch (e: IOException) {
            throw ConnectionException(
                """
    SNMP listen failed! 
    ${e.message}
    """.trimIndent()
            )
        }

        // set address
        targetAddress = try {
            GenericAddress.parse(address)
        } catch (e: IllegalArgumentException) {
            throw ArgumentSyntaxException("Device address foramt is wrong! (eg. 1.1.1.1/1)")
        }
        this.authenticationPassphrase = authenticationPassphrase
    }

    /**
     * Default constructor useful for scanner
     */
    constructor() {}

    /**
     * set target parameters. Implementations are different in SNMP v1, v2c and v3
     */
    abstract fun setTarget()

    /**
     * Receives a list of all OIDs in string format, creates PDU and sends GET request to defined target. This method is
     * a blocking method. It waits for response.
     *
     * @param OIDs
     * list of OIDs that should be read from target
     * @return Map&lt;String, String&gt; returns a Map of OID as Key and received value corresponding to that OID from
     * the target as Value
     *
     * @throws SnmpTimeoutException
     * thrown if Target doesn't responses
     * @throws ConnectionException
     * thrown if SNMP get request fails
     */
    @Throws(SnmpTimeoutException::class, ConnectionException::class)
    fun getRequestsList(OIDs: List<String?>): Map<String?, String?> {
        val result: MutableMap<String?, String?> = HashMap()

        // set PDU
        val pdu = PDU()
        pdu.type = PDU.GET
        for (oid in OIDs) {
            pdu.add(VariableBinding(OID(oid)))
        }

        // send GET request
        val response: ResponseEvent
        try {
            response = snmp!!.send(pdu, target)
            val responsePDU = response.response
            val vbs: List<VariableBinding> = responsePDU.variableBindings
            for (vb in vbs) {
                result[vb.oid.toString()] = vb.variable.toString()
            }
        } catch (e: IOException) {
            throw ConnectionException("SNMP get request failed! " + e.message)
        } catch (e: NullPointerException) {
            throw SnmpTimeoutException("Timeout: Target doesn't respond!")
        }
        return result
    }

    /**
     * Receives one single OID in string format, creates PDU and sends GET request to defined target. This method is a
     * blocking method. It waits for response.
     *
     * @param OID
     * OID that should be read from target
     * @return String containing read value
     *
     * @throws SnmpTimeoutException
     * thrown if Target doesn't responses
     * @throws ConnectionException
     * thrown if SNMP get request failsn
     */
    @Throws(SnmpTimeoutException::class, ConnectionException::class)
    fun getSingleRequests(OID: String?): String {
        var result: String? = null

        // set PDU
        val pdu = PDU()
        pdu.type = PDU.GET
        pdu.add(VariableBinding(OID(OID)))

        // send GET request
        val response: ResponseEvent
        try {
            response = snmp!!.send(pdu, target)
            val responsePDU = response.response
            val vbs: List<VariableBinding> = responsePDU.variableBindings
            result = vbs[0].variable.toString()
        } catch (e: IOException) {
            throw ConnectionException("SNMP get request failed! " + e.message)
        } catch (e: NullPointerException) {
            throw SnmpTimeoutException("Timeout: Target doesn't respond!")
        }
        return result
    }

    open val deviceAddress: String
        get() = targetAddress.toString()

    @Synchronized
    fun addEventListener(listener: SnmpDiscoveryListener) {
        listeners.add(listener)
    }

    @Synchronized
    fun removeEventListener(listener: SnmpDiscoveryListener) {
        listeners.remove(listener)
    }

    /**
     * This method will call all listeners for given new device
     *
     * @param address
     * address of device
     * @param version
     * version of snmp that this device support
     * @param description
     * other extra information which can be useful
     */
    @Synchronized
    protected fun NotifyForNewDevice(address: Address, version: SNMPVersion?, description: String?) {
        val event = SnmpDiscoveryEvent(this, address, version, description)
        val i: Iterator<*> = listeners.iterator()
        while (i.hasNext()) {
            (i.next() as SnmpDiscoveryListener).onNewDeviceFound(event)
        }
    }

    override fun disconnect() {}

    /**
     * At least device address and channel address must be specified in the container.<br></br>
     * <br></br>
     * containers.deviceAddress = device address (eg. 1.1.1.1/161) <br></br>
     * containers.channelAddress = OID (eg. 1.3.6.1.2.1.1.0)
     *
     */
    @Throws(ConnectionException::class)
    override fun read(
        containers: List<ChannelRecordContainer?>?,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        return readChannelGroup(containers, timeout)
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer?>?, containerListHandle: Any?): Any? {
        // TODO snmp set request will be implemented here
        throw UnsupportedOperationException()
    }

    /**
     * Read all the channels of the device at once.
     *
     * @param device
     * @param containers
     * @param timeout
     * @return Object
     * @throws ConnectionException
     */
    @Throws(ConnectionException::class)
    private fun readChannelGroup(containers: List<ChannelRecordContainer?>?, timeout: Int): Any? {
        Date().time
        val oids: MutableList<String?> = ArrayList()
        for (container in containers!!) {
            if (deviceAddress.equals(container!!.channel!!.deviceAddress, ignoreCase = true)) {
                oids.add(container.channelAddress)
            }
        }
        val values: Map<String?, String?>
        try {
            values = getRequestsList(oids)
            val receiveTime = System.currentTimeMillis()
            for (container in containers) {
                // make sure the value exists for corresponding channel
                if (values[container!!.channelAddress] != null) {
                    logger.debug(
                        "{}: value = '{}'", container.channelAddress,
                        values[container.channelAddress]
                    )
                    container.setRecord(
                        Record(
                            ByteArrayValue(values[container.channelAddress]!!.toByteArray()), receiveTime
                        )
                    )
                }
            }
        } catch (e: SnmpTimeoutException) {
            for (container in containers) {
                container!!.setRecord(Record(Flag.TIMEOUT))
            }
        }
        return null
    }

    @Throws(UnsupportedOperationException::class)
    override fun startListening(containers: List<ChannelRecordContainer?>?, listener: RecordsReceivedListener?) {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun scanForChannels(settings: String?): List<ChannelScanInfo?>? {
        throw UnsupportedOperationException()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SnmpDevice::class.java)
        protected val ScanOIDs: MutableMap<String, String> = HashMap()

        init {
            // some general OIDs that are valid in almost every MIB
            ScanOIDs["Device name: "] = "1.3.6.1.2.1.1.5.0"
            ScanOIDs["Description: "] = "1.3.6.1.2.1.1.1.0"
            ScanOIDs["Location: "] = "1.3.6.1.2.1.1.6.0"
        }

        /**
         * Calculate and return next broadcast address. (eg. if ip=1.2.3.x, returns 1.2.4.255)
         *
         * @param ip
         * IP
         * @return String the next broadcast address as String
         */
        fun getNextBroadcastIPV4Address(ip: String): String {
            val nums = ip.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val i = (nums[0].toInt() shl 24 or (nums[2].toInt() shl 8) or (nums[1].toInt() shl 16
                    ) or nums[3].toInt()) + 256
            return String.format("%d.%d.%d.%d", i ushr 24 and 0xFF, i shr 16 and 0xFF, i shr 8 and 0xFF, 255)
        }

        /**
         * Helper function in order to parse response vector to map structure
         *
         * @param responseVector
         * response vector
         * @return HashMap&lt;String, String&gt;
         */
        fun parseResponseVectorToHashMap(responseVector: List<VariableBinding>): HashMap<String?, String> {
            val map = HashMap<String?, String>()
            for (elem in responseVector) {
                map[elem.oid.toString()] = elem.variable.toString()
            }
            return map
        }

        protected fun scannerMakeDescriptionString(scannerResult: HashMap<String?, String>): String {
            val desc = StringBuilder()
            for (key in ScanOIDs.keys) {
                desc.append('[')
                    .append(key)
                    .append('(')
                    .append(ScanOIDs[key])
                    .append(")=")
                    .append(scannerResult[ScanOIDs[key]])
                    .append("] ")
            }
            return desc.toString()
        }

        /**
         * Returns respective SNMPVersion enum value based on given SnmpConstant version value
         *
         * @param version
         * the version as int
         * @return SNMPVersion or null if given value is not valid
         */
        protected fun getSnmpVersionFromSnmpConstantsValue(version: Int): SNMPVersion? {
            when (version) {
                0 -> return SNMPVersion.V1
                1 -> return SNMPVersion.V2c
                3 -> return SNMPVersion.V3
            }
            return null
        }
    }
}
