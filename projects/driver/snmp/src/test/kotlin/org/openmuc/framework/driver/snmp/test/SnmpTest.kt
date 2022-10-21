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
package org.openmuc.framework.driver.snmp.test

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.driver.snmp.SnmpDriver
import org.openmuc.framework.driver.snmp.SnmpDriver.SnmpDriverSettingVariableNames
import org.openmuc.framework.driver.spi.ConnectionException

class SnmpTest {
    @Test
    @Throws(ConnectionException::class, ArgumentSyntaxException::class)
    fun testInvalidSettingStringNumber() {
        val settings = (SnmpDriverSettingVariableNames.SECURITYNAME.toString() + "=security:"
                + SnmpDriverSettingVariableNames.AUTHENTICATIONPASSPHRASE + "=pass:"
                + SnmpDriverSettingVariableNames.PRIVACYPASSPHRASE + "=pass")
        Assertions.assertThrows(ArgumentSyntaxException::class.java) { snmpDriver.connect("1.1.1.1/1", settings) }
        Assertions.assertThrows(ArgumentSyntaxException::class.java) { snmpDriver.connect("1.1.1.1/1", settings) }
        Assertions.assertThrows(ArgumentSyntaxException::class.java) { snmpDriver.connect("1.1.1.1/1", settings) }
    }

    @Test
    @Throws(ConnectionException::class, ArgumentSyntaxException::class)
    fun testNullSettingStringNumber() {
        Assertions.assertThrows(ArgumentSyntaxException::class.java) { snmpDriver.connect("1.1.1.1/1", "") }
        Assertions.assertThrows(ArgumentSyntaxException::class.java) { snmpDriver.connect("1.1.1.1/1", "") }
        Assertions.assertThrows(ArgumentSyntaxException::class.java) { snmpDriver.connect("1.1.1.1/1", "") }
    }

    @Test
    @Throws(ConnectionException::class, ArgumentSyntaxException::class)
    fun testEmptySettingStringNumber() {
        Assertions.assertThrows(ArgumentSyntaxException::class.java) { snmpDriver.connect("1.1.1.1/1", "") }
        Assertions.assertThrows(ArgumentSyntaxException::class.java) { snmpDriver.connect("1.1.1.1/1", "") }
        Assertions.assertThrows(ArgumentSyntaxException::class.java) { snmpDriver.connect("1.1.1.1/1", "") }
    }

    @Test
    @Throws(ConnectionException::class, ArgumentSyntaxException::class)
    fun testInvalidSettingStringFormat() {
        var settings = (SnmpDriverSettingVariableNames.USERNAME.toString() + "=username&"
                + SnmpDriverSettingVariableNames.SECURITYNAME + "=username:"
                + SnmpDriverSettingVariableNames.AUTHENTICATIONPASSPHRASE + "=pass:"
                + SnmpDriverSettingVariableNames.PRIVACYPASSPHRASE + "=pass")
        val finalSettings = settings
        Assertions.assertThrows(ArgumentSyntaxException::class.java) {
            snmpDriver.connect(
                "1.1.1.1/1",
                finalSettings
            )
        }
        Assertions.assertThrows(ArgumentSyntaxException::class.java) {
            snmpDriver.connect(
                "1.1.1.1/1",
                finalSettings
            )
        }
        Assertions.assertThrows(ArgumentSyntaxException::class.java) {
            snmpDriver.connect(
                "1.1.1.1/1",
                finalSettings
            )
        }
        settings =
            (SnmpDriverSettingVariableNames.USERNAME.toString() + ":username&" + SnmpDriverSettingVariableNames.SECURITYNAME
                    + "=username:" + SnmpDriverSettingVariableNames.AUTHENTICATIONPASSPHRASE + "=pass:"
                    + SnmpDriverSettingVariableNames.PRIVACYPASSPHRASE + "=pass")
        val finalSettings1 = settings
        Assertions.assertThrows(ArgumentSyntaxException::class.java) {
            snmpDriver.connect(
                "1.1.1.1/1",
                finalSettings1
            )
        }
        Assertions.assertThrows(ArgumentSyntaxException::class.java) {
            snmpDriver.connect(
                "1.1.1.1/1",
                finalSettings1
            )
        }
        Assertions.assertThrows(ArgumentSyntaxException::class.java) {
            snmpDriver.connect(
                "1.1.1.1/1",
                finalSettings1
            )
        }
    }

    @Test
    @Throws(ConnectionException::class, ArgumentSyntaxException::class)
    fun testInvalidDeviceAddress() {
        Assertions.assertThrows(ArgumentSyntaxException::class.java) {
            snmpDriver.connect(
                "1.1.1.1:1",
                correctSetting
            )
        }
    }

    @Test
    @Throws(ConnectionException::class, ArgumentSyntaxException::class)
    fun testEmptyDeviceAddress() {
        Assertions.assertThrows(ArgumentSyntaxException::class.java) { snmpDriver.connect("", "") }
    }

    @Test
    @Throws(ConnectionException::class, ArgumentSyntaxException::class)
    fun testIncorrectSnmpVersoin() {
        Assertions.assertThrows(ArgumentSyntaxException::class.java) {
            snmpDriver.connect(
                "1.1.1.1/1",
                correctSetting
            )
        }
    }

    @Test
    @Throws(ConnectionException::class, ArgumentSyntaxException::class)
    fun testNullSnmpVersoin() {
        Assertions.assertThrows(ArgumentSyntaxException::class.java) {
            snmpDriver.connect(
                "1.1.1.1/1",
                correctSetting
            )
        }
    }

    @Test
    @Throws(ConnectionException::class, ArgumentSyntaxException::class)
    fun testEmptySnmpVersoin() {
        Assertions.assertThrows(ArgumentSyntaxException::class.java) {
            snmpDriver.connect(
                "1.1.1.1/1",
                correctSetting
            )
        }
    }

    companion object {
        private lateinit var snmpDriver: SnmpDriver
        private lateinit var correctSetting: String

        @JvmStatic
        @BeforeAll
        fun beforeClass() {
            snmpDriver = SnmpDriver()
            correctSetting = (SnmpDriverSettingVariableNames.USERNAME.toString() + "=username:"
                    + SnmpDriverSettingVariableNames.SECURITYNAME + "=securityname:"
                    + SnmpDriverSettingVariableNames.AUTHENTICATIONPASSPHRASE + "=password:"
                    + SnmpDriverSettingVariableNames.PRIVACYPASSPHRASE + "=privacy")
        }
    }
}
