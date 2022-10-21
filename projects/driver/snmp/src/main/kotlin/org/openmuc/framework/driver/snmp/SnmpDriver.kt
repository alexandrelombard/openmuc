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
package org.openmuc.framework.driver.snmp

import org.openmuc.framework.config.*
import org.openmuc.framework.driver.snmp.implementation.SnmpDevice
import org.openmuc.framework.driver.snmp.implementation.SnmpDevice.SNMPVersion
import org.openmuc.framework.driver.snmp.implementation.SnmpDeviceV1V2c
import org.openmuc.framework.driver.snmp.implementation.SnmpDeviceV3
import org.openmuc.framework.driver.spi.*
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

@Component
class SnmpDriver : DriverService {
    // AUTHENTICATIONPASSPHRASE is the same COMMUNITY word in SNMP V2c
    enum class SnmpDriverSettingVariableNames {
        SNMP_VERSION, USERNAME, SECURITYNAME, AUTHENTICATIONPASSPHRASE, PRIVACYPASSPHRASE
    }

    // AUTHENTICATIONPASSPHRASE is the same COMMUNITY word in SNMP V2c
    enum class SnmpDriverScanSettingVariableNames {
        SNMP_VERSION, USERNAME, SECURITYNAME, AUTHENTICATIONPASSPHRASE, PRIVACYPASSPHRASE, STARTIP, ENDIP
    }

    override val info: DriverInfo
        get() = Companion.info

    /**
     * Currently only supports SNMP V2c
     *
     * Default port number 161 is used
     *
     * @param settings
     * at least must contain<br></br>
     *
     * <br></br>
     * SnmpDriverSettingVariableNames.AUTHENTICATIONPASSPHRASE: (community word) in case of more than on
     * value, they should be separated by ",". No community word is allowed to contain "," <br></br>
     * SnmpDriverScanSettingVariableNames.STARTIP: Start of IP range <br></br>
     * SnmpDriverScanSettingVariableNames.ENDIP: End of IP range <br></br>
     * eg. "AUTHENTICATIONPASSPHRASE=community,public:STARTIP=1.1.1.1:ENDIP=1.10.1.1"
     */
    @Throws(ArgumentSyntaxException::class, ScanException::class, ScanInterruptedException::class)
    override fun scanForDevices(settings: String, listener: DriverDeviceScanListener?) {
        val settingMapper = settingParser(settings)

        // Current implementation is only for SNMP version 2c
        val snmpScanner: SnmpDeviceV1V2c
        snmpScanner = try {
            SnmpDeviceV1V2c(SNMPVersion.V2c)
        } catch (e: ConnectionException) {
            throw ScanException(e.message)
        }
        val discoveryListener = SnmpDriverDiscoveryListener(listener)
        snmpScanner.addEventListener(discoveryListener)
        val communityWords = settingMapper[SnmpDriverScanSettingVariableNames.AUTHENTICATIONPASSPHRASE.toString()]
            ?.split(",".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray() ?: arrayOf()
        snmpScanner.scanSnmpV2cEnabledDevices(
            settingMapper[SnmpDriverScanSettingVariableNames.STARTIP.toString()],
            settingMapper[SnmpDriverScanSettingVariableNames.ENDIP.toString()], communityWords
        )
    }

    @Throws(UnsupportedOperationException::class)
    override fun interruptDeviceScan() {
        throw UnsupportedOperationException()
    }

    /**
     *
     * @param settings
     * SNMPVersion=V2c:COMMUNITY=value:SECURITYNAME=value:AUTHENTICATIONPASSPHRASE=value:PRIVACYPASSPHRASE=
     * value
     *
     * @throws ConnectionException
     * thrown if SNMP listen or initialization failed
     * @throws ArgumentSyntaxException
     * thrown if Device address foramt is wrong
     */
    @Throws(ConnectionException::class, ArgumentSyntaxException::class)
    override fun connect(deviceAddress: String, settings: String): Connection {
        var device: SnmpDevice? = null
        var snmpVersion: SNMPVersion? = null

        // check arguments
        if (deviceAddress == "") {
            throw ArgumentSyntaxException(NULL_DEVICE_ADDRESS_EXCEPTION)
        } else if (settings == "") {
            throw ArgumentSyntaxException(NULL_SETTINGS_EXCEPTION)
        } else {
            val mappedSettings = settingParser(settings)
            snmpVersion = try {
                SNMPVersion.valueOf(mappedSettings[SnmpDriverSettingVariableNames.SNMP_VERSION.toString()]!!)
            } catch (e: IllegalArgumentException) {
                throw ArgumentSyntaxException(INCORRECT_SNMP_VERSION_EXCEPTION)
            } catch (e: NullPointerException) {
                throw ArgumentSyntaxException(NULL_SNMP_VERSION_EXCEPTION)
            }
            device = when (snmpVersion) {
                SNMPVersion.V1, SNMPVersion.V2c -> SnmpDeviceV1V2c(
                    snmpVersion, deviceAddress,
                    mappedSettings[SnmpDriverSettingVariableNames.AUTHENTICATIONPASSPHRASE.toString()]!!
                )

                SNMPVersion.V3 -> SnmpDeviceV3(
                    deviceAddress,
                    mappedSettings[SnmpDriverSettingVariableNames.USERNAME.toString()]!!,
                    mappedSettings[SnmpDriverSettingVariableNames.SECURITYNAME.toString()],
                    mappedSettings[SnmpDriverSettingVariableNames.AUTHENTICATIONPASSPHRASE.toString()]!!,
                    mappedSettings[SnmpDriverSettingVariableNames.PRIVACYPASSPHRASE.toString()]!!
                )

                else -> throw ArgumentSyntaxException(INCORRECT_SNMP_VERSION_EXCEPTION)
            }
        }
        return device
    }

    /**
     * Read settings string and put them in a Key,Value HashMap Each setting consists of a pair of key/value and is
     * separated by ":" from other settings Inside the setting string, key and value are separated by "=" e.g.
     * "key1=value1:key2=value3" Be careful! "=" and ":" are not allowed in keys and values
     *
     * if your key contains more than one value, you can separate values by ",". in this case "," is not allowed in
     * values.
     *
     * @param settings
     * @return Map<String></String>,String>
     * @throws ArgumentSyntaxException
     */
    @Throws(ArgumentSyntaxException::class)
    private fun settingParser(settings: String?): Map<String, String> {
        val settingsMaper: MutableMap<String, String> = HashMap()
        try {
            val settingsArray = settings!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (setting in settingsArray) {
                val keyValue = setting.split("=".toRegex(), limit = 2).toTypedArray()
                settingsMaper[keyValue[0]] = keyValue[1]
            }
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw ArgumentSyntaxException(INCORRECT_SETTINGS_FORMAT_EXCEPTION)
        }
        return settingsMaper
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SnmpDriver::class.java)
        private val info = DriverInfo("snmp", "snmp v1/v2c/v3 are supported.", "?", "?", "?", "?")

        // exception messages
        private const val NULL_DEVICE_ADDRESS_EXCEPTION =
            "No device address found in config. Please specify one [eg. \"1.1.1.1/161\"]."
        private const val NULL_SETTINGS_EXCEPTION = "No device settings found in config. Please specify settings."
        private const val INCORRECT_SETTINGS_FORMAT_EXCEPTION =
            ("Format of setting string is invalid! \n Please use this format: "
                    + "USERNAME=username:SECURITYNAME=securityname:AUTHENTICATIONPASSPHRASE=password:PRIVACYPASSPHRASE=privacy")
        private const val INCORRECT_SNMP_VERSION_EXCEPTION = ("Incorrect snmp version value. "
                + "Please choose proper version. Possible values are defined in SNMPVersion enum")
        private const val NULL_SNMP_VERSION_EXCEPTION = ("Snmp version is not defined. "
                + "Please choose proper version. Possible values are defined in SNMPVersion enum")
    }
}
