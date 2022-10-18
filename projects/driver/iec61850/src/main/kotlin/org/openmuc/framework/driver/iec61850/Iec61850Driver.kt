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
package org.openmuc.framework.driver.iec61850

import com.beanit.iec61850bean.*
import org.openmuc.framework.config.*
import org.openmuc.framework.driver.spi.*
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.io.IOException

@Component
class Iec61850Driver : DriverService {
    override val info: DriverInfo
        get() = Companion.info

    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ScanInterruptedException::class
    )
    override fun scanForDevices(settings: String?, listener: DriverDeviceScanListener?) {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class)
    override fun interruptDeviceScan() {
        throw UnsupportedOperationException()
    }

    @Throws(ArgumentSyntaxException::class, ConnectionException::class)
    override fun connect(deviceAddress: String?, settings: String?): Connection? {
        val deviceAdress = DeviceAddress(deviceAddress)
        val deviceSettings = DeviceSettings(settings)
        val clientSap = ClientSap()
        clientSap.setTSelLocal(deviceSettings.tSelLocal)
        clientSap.setTSelLocal(deviceSettings.tSelRemote)
        val clientAssociation: ClientAssociation
        clientAssociation = try {
            clientSap.associate(
                deviceAdress.adress, deviceAdress.remotePort,
                deviceSettings.authentication, null
            )
        } catch (e: IOException) {
            throw ConnectionException(e)
        }
        val serverModel: ServerModel
        serverModel = try {
            clientAssociation.retrieveModel()
        } catch (e: ServiceError) {
            clientAssociation.close()
            throw ConnectionException("Service error retrieving server model" + e.message, e)
        } catch (e: IOException) {
            clientAssociation.close()
            throw ConnectionException("IOException retrieving server model: " + e.message, e)
        }
        return Iec61850Connection(clientAssociation, serverModel)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Iec61850Driver::class.java)
        private val info = DriverInfo(
            "iec61850",  // id
            // description
            "This driver can be used to access IEC 61850 MMS devices",  // device address
            "Synopsis: <host>[:<port>]\nThe default port is 102.",  // parameters
            "Synopsis: [-a <authentication_parameter>] [-lt <local_t-selector>] [-rt <remote_t-selector>]",  // channel address
            "Synopsis: <bda_reference>:<fc>",  // device scan settings
            "N.A."
        )
    }
}
