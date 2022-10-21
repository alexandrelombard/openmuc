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
import org.openmuc.framework.driver.spi.ConnectionException
import org.snmp4j.UserTarget
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.security.AuthMD5
import org.snmp4j.security.PrivDES
import org.snmp4j.security.SecurityLevel
import org.snmp4j.security.UsmUser
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString

class SnmpDeviceV3 : SnmpDevice {
    // private UserTarget target; // snmp v3 target
    private val username: String?
    private val securityName: String?
    private val authenticationProtocol: OID
    override val authenticationPassphrase: String
    private val privacyProtocol: OID
    private val privacyPassphrase: String?

    /**
     * snmp constructor takes primary parameters in order to create snmp object. this implementation uses UDP protocol
     *
     * @param address
     * Contains ip and port. accepted string "X.X.X.X/portNo" or "udp:X.X.X.X/portNo"
     * @param username
     * String containing username
     * @param securityName
     * the security name of the user (typically the user name). [required by snmp4j library]
     * @param authenticationProtocol
     * the authentication protocol ID to be associated with this user. If set to `null`, this user
     * only supports unauthenticated messages. [required by snmp4j library] eg. AuthMD5.ID
     * @param authenticationPassphrase
     * the authentication pass phrase. If not `null`, `authenticationProtocol` must
     * also be not `null`. RFC3414 11.2 requires pass phrases to have a minimum length of 8
     * bytes. If the length of `authenticationPassphrase` is less than 8 bytes an
     * `IllegalArgumentException` is thrown. [required by snmp4j library]
     * @param privacyProtocol
     * the privacy protocol ID to be associated with this user. If set to `null`, this user only
     * supports not encrypted messages. [required by snmp4j library] eg. PrivDES.ID
     * @param privacyPassphrase
     * the privacy pass phrase. If not `null`, `privacyProtocol` must also be not
     * `null`. RFC3414 11.2 requires pass phrases to have a minimum length of 8 bytes. If
     * the length of `authenticationPassphrase` is less than 8 bytes an
     * `IllegalArgumentException` is thrown. [required by snmp4j library]
     *
     * @throws ConnectionException
     * thrown if SNMP listen or initialization failed
     * @throws ArgumentSyntaxException
     * thrown if Device address format is wrong
     */
    constructor(
        address: String, username: String, securityName: String, authenticationProtocol: OID,
        authenticationPassphrase: String, privacyProtocol: OID, privacyPassphrase: String
    ) : super(address, authenticationPassphrase) {
        this.username = username
        this.securityName = securityName
        this.authenticationProtocol = authenticationProtocol
        this.authenticationPassphrase = authenticationPassphrase
        this.privacyProtocol = privacyProtocol
        this.privacyPassphrase = privacyPassphrase
    }

    /**
     * snmp constructor takes primary parameters in order to create snmp object. this implementation uses UDP protocol
     * Default values: authenticationProtocol = AuthMD5.ID; privacyProtocol = PrivDES.ID;
     *
     * @param address
     * Contains ip and port. accepted string "X.X.X.X/portNo" or "udp:X.X.X.X/portNo"
     * @param username
     * String containing username
     * @param securityName
     * the security name of the user (typically the user name). [required by snmp4j library]
     * @param authenticationPassphrase
     * the authentication pass phrase. If not `null`, `authenticationProtocol` must
     * also be not `null`. RFC3414 11.2 requires pass phrases to have a minimum length of 8
     * bytes. If the length of `authenticationPassphrase` is less than 8 bytes an
     * `IllegalArgumentException` is thrown. [required by snmp4j library]
     * @param privacyPassphrase
     * the privacy pass phrase. If not `null`, `privacyProtocol` must also be not
     * `null`. RFC3414 11.2 requires pass phrases to have a minimum length of 8 bytes. If
     * the length of `authenticationPassphrase` is less than 8 bytes an
     * `IllegalArgumentException` is thrown. [required by snmp4j library]
     *
     * @throws ConnectionException
     * thrown if SNMP listen or initialization failed
     * @throws ArgumentSyntaxException
     * thrown if Device address foramt is wrong
     */
    constructor(
        address: String, username: String, securityName: String?, authenticationPassphrase: String,
        privacyPassphrase: String
    ) : super(address, authenticationPassphrase) {
        this.username = username
        this.securityName = securityName
        authenticationProtocol = AuthMD5.ID
        this.authenticationPassphrase = authenticationPassphrase
        privacyProtocol = PrivDES.ID
        this.privacyPassphrase = privacyPassphrase
    }

    public override fun setTarget() {
        var securityLevel = -1
        securityLevel = if (authenticationPassphrase.trim { it <= ' ' } == "") {
            // No Authentication and no Privacy
            SecurityLevel.NOAUTH_NOPRIV
        } else {
            // With Authentication
            if (privacyPassphrase == null || privacyPassphrase.trim { it <= ' ' } == "") {
                // No Privacy
                SecurityLevel.AUTH_NOPRIV
            } else {
                // With Privacy
                SecurityLevel.AUTH_PRIV
            }
        }
        snmp!!.usm
            .addUser(
                OctetString(username),
                UsmUser(
                    OctetString(securityName), authenticationProtocol,
                    OctetString(authenticationPassphrase), privacyProtocol,
                    OctetString(privacyPassphrase)
                )
            )
        // create the target
        val target = UserTarget()
        target.address = targetAddress
        target.retries = retries
        target.timeout = timeout.toLong()
        target.version = SnmpConstants.version3
        target.securityLevel = securityLevel
        target.securityName = OctetString(securityName)
        this.target = target
    }

    val interfaceAddress: String?
        get() = null
    override val deviceAddress: String
        get() = targetAddress.toString()
    val settings: String
        get() = (SnmpDriverSettingVariableNames.SNMP_VERSION.toString() + "=" + SNMPVersion.V3 + ":"
                + SnmpDriverSettingVariableNames.USERNAME + "=" + username + ":"
                + SnmpDriverSettingVariableNames.SECURITYNAME + "=" + securityName + ":"
                + SnmpDriverSettingVariableNames.AUTHENTICATIONPASSPHRASE + "=" + authenticationPassphrase + ":"
                + SnmpDriverSettingVariableNames.PRIVACYPASSPHRASE + "=" + privacyPassphrase)
    val connectionHandle: Any
        get() = this
}
