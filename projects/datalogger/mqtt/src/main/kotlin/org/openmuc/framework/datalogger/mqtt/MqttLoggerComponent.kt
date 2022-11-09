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
package org.openmuc.framework.datalogger.mqtt

import org.openmuc.framework.datalogger.spi.DataLoggerService
import org.openmuc.framework.lib.osgi.deployment.RegistrationHandler
import org.openmuc.framework.parser.spi.ParserService
import org.openmuc.framework.security.SslManagerInterface
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceEvent
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeoutException

@Component
class MqttLoggerComponent {
    private var registrationHandler: RegistrationHandler? = null
    private var mqttLogger: MqttLogger? = null
    @Activate
    protected fun activate(context: BundleContext) {
        logger.info("Activating MQTT Logger")
        mqttLogger = MqttLogger()
        registrationHandler = RegistrationHandler(context)

        // subscribe for ParserService
        var serviceName = ParserService::class.java.name
        registrationHandler!!.subscribeForServiceServiceEvent(serviceName) { event: Any? ->
            handleServiceRegistrationEvent(event!!, context)
        }

        // subscribe for SSLManager
        serviceName = SslManagerInterface::class.java.name
        registrationHandler!!.subscribeForService(serviceName) { instance: Any? ->
            if (instance != null) {
                mqttLogger!!.setSslManager(instance as SslManagerInterface)
            }
        }

        // provide ManagedService
        val pid = MqttLogger::class.java.name
        registrationHandler!!.provideInFrameworkAsManagedService(mqttLogger, pid)
        registerDataLoggerService()
    }

    private fun registerDataLoggerService() {
        registrationHandler!!.provideInFrameworkWithoutConfiguration(DataLoggerService::class.java.name, mqttLogger)
    }

    private fun handleServiceRegistrationEvent(event: Any, context: BundleContext) {
        val serviceReference = (event as ServiceEvent).serviceReference
        val parserId = serviceReference.getProperty("parserID") as String
        val parserService = context.getService(serviceReference) as ParserService
        val parserServiceName = parserService.javaClass.name
        if (event.type == ServiceEvent.UNREGISTERING) {
            logger.info("{} unregistering, removing Parser", parserServiceName)
            mqttLogger!!.removeParser(parserId)
        } else {
            logger.info("{} changed, updating Parser", parserServiceName)
            mqttLogger!!.addParser(parserId, parserService)
        }
    }

    @Deactivate
    @Throws(IOException::class, TimeoutException::class)
    protected fun deactivate(context: ComponentContext?) {
        logger.info("Deactivating MQTT logger")
        mqttLogger!!.shutdown()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MqttLoggerComponent::class.java)
    }
}
