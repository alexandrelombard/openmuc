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
package org.openmuc.framework.server.modbus

import org.openmuc.framework.data.Record
import org.openmuc.framework.lib.osgi.deployment.RegistrationHandler
import org.openmuc.framework.server.spi.ServerService
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.slf4j.LoggerFactory
import java.io.IOException

@Component
class ModbusComponent {
    private var modbusServer: ModbusServer? = null
    private var registrationHandler: RegistrationHandler? = null
    @Activate
    @Throws(IOException::class)
    protected fun activate(context: BundleContext?) {
        logger.info("Activating Modbus Server")
        modbusServer = ModbusServer()
        registrationHandler = RegistrationHandler(context!!)
        val pid = ModbusServer::class.java.name
        registrationHandler!!.provideInFramework(ServerService::class.java.name, modbusServer, pid)
    }

    @Deactivate
    protected fun deactivate(context: BundleContext?) {
        logger.info("Deactivating Modbus Server")
        modbusServer!!.shutdown()
        registrationHandler!!.removeAllProvidedServices()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ModbusComponent::class.java)
    }
}
