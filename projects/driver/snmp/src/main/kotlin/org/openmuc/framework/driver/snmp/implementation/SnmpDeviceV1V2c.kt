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
import org.openmuc.framework.driver.snmp.SnmpDriver.SnmpDriverSettingVariableNames
import org.openmuc.framework.driver.snmp.implementation.SnmpDeviceV1V2c
import org.openmuc.framework.driver.spi.ConnectionException
import org.slf4j.LoggerFactory
import org.snmp4j.CommunityTarget
import org.snmp4j.PDU
import org.snmp4j.Snmp
import org.snmp4j.event.ResponseEvent
import org.snmp4j.event.ResponseListener
import org.snmp4j.mp.MPv3
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.security.SecurityModels
import org.snmp4j.security.SecurityProtocols
import org.snmp4j.security.USM
import org.snmp4j.smi.GenericAddress
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.VariableBinding
import org.snmp4j.transport.DefaultUdpTransportMapping
import java.io.IOException

class SnmpDeviceV1V2c : SnmpDevice {
    private var snmpVersion = 0

    override var authenticationPassphrase: String = ""

    /**
     * snmp constructor takes primary parameters in order to create snmp object. this implementation uses UDP protocol
     *
     * @param version
     * Can be V1 or V2c corresponding to snmp v1 or v2c
     * @param address
     * Contains ip and port. accepted string "X.X.X.X/portNo" or "udp:X.X.X.X/portNo"
     * @param authenticationPassphrase
     * the authentication pass phrase. If not `null`, `authenticationProtocol` must
     * also be not `null`. RFC3414 11.2 requires pass phrases to have a minimum length of 8
     * bytes. If the length of `authenticationPassphrase` is less than 8 bytes an
     * `IllegalArgumentException` is thrown. [required by snmp4j library]
     *
     * @throws ConnectionException
     * thrown if SNMP listen or initialization failed
     * @throws ArgumentSyntaxException
     * thrown if Given snmp version is not correct or supported
     */
    constructor(version: SNMPVersion, address: String, authenticationPassphrase: String) : super(
        address,
        authenticationPassphrase
    ) {
        setVersion(version)
        setTarget()
    }

    /**
     * scanner constructor
     *
     * @param version
     * SNMP version
     * @throws ArgumentSyntaxException
     * thrown if Given snmp version is not correct or supported
     * @throws ConnectionException
     * thrown if SNMP listen failed
     */
    constructor(version: SNMPVersion) {
        setVersion(version)
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
    }

    /**
     * set SNMP version
     *
     * @param version
     * SNMP version
     * @throws ArgumentSyntaxException
     * thrown if Given snmp version is not correct or supported
     */
    @Throws(ArgumentSyntaxException::class)
    private fun setVersion(version: SNMPVersion) {
        snmpVersion = when (version) {
            SNMPVersion.V1 -> SnmpConstants.version1
            SNMPVersion.V2c -> SnmpConstants.version2c
            else -> throw ArgumentSyntaxException(
                "Given snmp version is not correct or supported! Expected values are [V1,V2c]."
            )
        }
    }

    public override fun setTarget() {
        val target = CommunityTarget()
        target.community = OctetString(authenticationPassphrase)
        target.address = targetAddress
        target.retries = retries
        target.timeout = timeout.toLong()
        target.version = snmpVersion
        this.target = target
    }

    val interfaceAddress: String?
        get() = null
    override val deviceAddress: String
        get() = targetAddress.toString()
    val settings: String
        get() = (SnmpDriverSettingVariableNames.SNMP_VERSION.toString() + "="
                + getSnmpVersionFromSnmpConstantsValue(snmpVersion) + ":COMMUNITY=" + authenticationPassphrase)
    val connectionHandle: Any
        get() = this

    /**
     * Search for SNMP V2c enabled devices in network by sending proper SNMP GET request to given range of IP addresses.
     *
     * For network and process efficiency, requests are sent to broadcast addresses (IP addresses ending with .255).
     *
     * startIPRange can be greater than endIPRange. In this case, it will reach the last available address and start
     * from the first IP address again
     *
     * @param startIPRange
     * start of IP range
     * @param endIPRange
     * en of IP range
     * @param communityWords
     * community words
     * @throws ArgumentSyntaxException
     * thrown if Given start ip address is not a valid IPV4 address
     */
    @Throws(ArgumentSyntaxException::class)
    fun scanSnmpV2cEnabledDevices(startIPRange: String?, endIPRange: String?, communityWords: Array<String>) {

        // create PDU
        var startIPRange = startIPRange
        var endIPRange = endIPRange
        val pdu = PDU()
        for (oid in ScanOIDs.values) {
            pdu.add(VariableBinding(OID(oid)))
        }
        pdu.type = PDU.GET

        // make sure the start/end IP is broadcast (eg. X.Y.Z.255)
        try {
            var ip = startIPRange!!.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            startIPRange = ip[0] + "." + ip[1] + "." + ip[2] + ".255"
            ip = endIPRange!!.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            endIPRange = ip[0] + "." + ip[1] + "." + ip[2] + ".255"
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw ArgumentSyntaxException("Given start ip address is not a valid IPV4 address.")
        }
        var nextIp: String = startIPRange
        // in order to check also the EndIPRange
        endIPRange = SnmpDevice.Companion.getNextBroadcastIPV4Address(endIPRange)
        try {

            // loop through all IP addresses
            while (endIPRange.compareTo(nextIp) != 0) {
                // TODO scan progress can be implemented here

                // define broadcast address
                targetAddress = try {
                    GenericAddress.parse("udp:$nextIp/161")
                } catch (e: IllegalArgumentException) {
                    throw ArgumentSyntaxException("Device address format is wrong! (eg. 1.1.1.255)")
                }

                // loop through all community words
                for (community in communityWords) {
                    // set target V2c
                    authenticationPassphrase = community
                    setTarget()
                    class ScanResponseListener : ResponseListener {
                        override fun onResponse(event: ResponseEvent) {
                            /**
                             * Since we are sending broadcast request we have to keep async request alive. Otherwise
                             * async request must be cancel by blew code in order to prevent memory leak
                             *
                             * ((Snmp)event.getSource()).cancel(event.getRequest (), this);
                             *
                             */
                            if (event.response != null) {
                                val vbs: List<VariableBinding> = event.response.variableBindings
                                // check if sent and received OIDs are the same
                                // or else snmp version may not compatible
                                if (!SnmpDevice.ScanOIDs.containsValue(vbs[0].oid.toString())) {
                                    // wrong version or not correct response!
                                    return
                                }
                                NotifyForNewDevice(
                                    event.peerAddress, SNMPVersion.V2c,
                                    SnmpDevice.scannerMakeDescriptionString(
                                        SnmpDevice.parseResponseVectorToHashMap(
                                            vbs
                                        )
                                    )
                                )
                            }
                        }
                    }

                    val listener = ScanResponseListener()
                    snmp!!.send(pdu, target, null, listener)
                } // end of community loop
                nextIp = SnmpDevice.Companion.getNextBroadcastIPV4Address(nextIp)
            } // end of IP loop
        } catch (e1: IOException) {
            logger.error("", e1)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SnmpDeviceV1V2c::class.java)
    }
}
