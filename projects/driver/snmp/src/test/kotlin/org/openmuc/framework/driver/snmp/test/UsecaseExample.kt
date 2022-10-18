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

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.driver.snmp.SnmpDriver
import org.openmuc.framework.driver.snmp.SnmpDriver.SnmpDriverSettingVariableNames
import org.openmuc.framework.driver.snmp.implementation.SnmpDevice
import org.openmuc.framework.driver.snmp.implementation.SnmpDevice.SNMPVersion
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.framework.driver.spi.ConnectionException

object UsecaseExample {
    /**
     * @param args
     */
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val snmpDriver = SnmpDriver()
            // SNMPVersion=V2c:COMMUNITY=root:SECURITYNAME=root:AUTHENTICATIONPASSPHRASE=adminadmin:PRIVACYPASSPHRASE=adminadmin
            val settings = (SnmpDriverSettingVariableNames.SNMP_VERSION.toString() + "=" + SNMPVersion.V2c + ":"
                    + SnmpDriverSettingVariableNames.USERNAME + "=root:" + SnmpDriverSettingVariableNames.SECURITYNAME
                    + "=root:" + SnmpDriverSettingVariableNames.AUTHENTICATIONPASSPHRASE + "=adminadmin:"
                    + SnmpDriverSettingVariableNames.PRIVACYPASSPHRASE + "=adminadmin")
            println(settings)
            val myDevice = snmpDriver.connect("192.168.1.1/161", settings) as SnmpDevice?
            val containers: MutableList<ChannelRecordContainer?> = ArrayList()
            val ch1 = SnmpChannel("192.168.1.1/161", "1.3.6.1.2.1.1.1.0")
            val ch2 = SnmpChannel("192.168.1.1/161", "1.3.6.1.2.1.25.1.1.0")
            val ch3 = SnmpChannel("192.168.1.1/161", "1.3.6.1.2.1.1.5.0")
            containers.add(SnmpChannelRecordContainer(ch1))
            containers.add(SnmpChannelRecordContainer(ch2))
            containers.add(SnmpChannelRecordContainer(ch3))
            myDevice!!.read(containers, null, null)
            for (container in containers) {
                if (container!!.record != null) {
                    println(container.record!!.value)
                }
            }
        } catch (e: ConnectionException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        } catch (e: ArgumentSyntaxException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
    }
}
