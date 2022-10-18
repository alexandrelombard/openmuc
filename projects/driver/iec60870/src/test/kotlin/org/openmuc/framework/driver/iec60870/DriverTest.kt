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
package org.openmuc.framework.driver.iec60870

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.driver.iec60870.settings.DeviceAddress
import org.openmuc.framework.driver.iec60870.settings.DeviceSettings

class DriverTest {
    private val SEP = ";"
    private val TS = "="
    @Test
    @Throws(ArgumentSyntaxException::class)
    fun testDeviceAddress_OK() {
        val expectedHost = "192.168.1.5"
        val expectedPort = 1265
        val expectedCa = 5
        val string = ("p " + TS + expectedPort + SEP + "h " + TS + "  " + expectedHost + SEP + "   ca    " + TS
                + expectedCa)
        println("testDeviceAddress_OK: $string")
        val testSetting = DeviceAddress(string)
        Assertions.assertEquals(expectedHost, testSetting.hostAddress()!!.hostAddress)
        Assertions.assertEquals(expectedPort, testSetting.port())
        Assertions.assertEquals(expectedCa, testSetting.commonAddress())
    }

    @Test
    @Throws(ArgumentSyntaxException::class)
    fun testDeviceAddress_OK_with_one_option() {
        val expectedHost = "192.168.1.5"
        val string = "h$TS$expectedHost"
        println("testDeviceAddress_OK: $string")
        val testSetting = DeviceAddress(string)
        Assertions.assertEquals(expectedHost, testSetting.hostAddress()!!.hostAddress)
    }

    @Test
    @Throws(ArgumentSyntaxException::class)
    fun testDeviceSettings_OK() {
        val expectedMFT = 123
        val expectedCFL = 321
        val string = "mft " + TS + expectedMFT + SEP + "cfl " + TS + expectedCFL
        println("testDeviceSettings_OK: $string")
        val testSetting = DeviceSettings(string)
        Assertions.assertEquals(expectedMFT, testSetting.messageFragmentTimeout())
        Assertions.assertEquals(expectedCFL, testSetting.cotFieldLength())
    }

    @Test
    @Throws(ArgumentSyntaxException::class)
    fun test_syntax() {
        // System.out.println(DeviceSettings.syntax(DeviceSettings.class));
        // System.out.println(DeviceAddress.syntax(DeviceAddress.class));
        // System.out.println(ChannelAddress.syntax(ChannelAddress.class));
    }
}
