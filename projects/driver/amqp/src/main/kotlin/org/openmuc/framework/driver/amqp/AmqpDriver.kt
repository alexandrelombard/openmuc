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
package org.openmuc.framework.driver.amqp

import org.openmuc.framework.config.*
import org.openmuc.framework.driver.spi.*
import org.openmuc.framework.driver.spi.ChannelValueContainer
import org.openmuc.framework.lib.osgi.deployment.RegistrationHandler
import org.openmuc.framework.parser.spi.ParserService
import org.openmuc.framework.security.SslManagerInterface
import org.osgi.framework.*
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.slf4j.LoggerFactory
import java.util.*

@Component
class AmqpDriver : DriverService {
    private lateinit var context: BundleContext
    private var connection: AmqpDriverConnection? = null
    @Activate
    fun activate(context: BundleContext) {
        this.context = context
    }

    @Deactivate
    fun deactivate() {
        if (connection != null) {
            connection!!.disconnect()
        }
    }

    override val info: DriverInfo
        get() {
            val ID = "amqp"
            val DESCRIPTION = "Driver to read out of amqp queue"
            val DEVICE_ADDRESS = "amqp host e.g. localhost, 192.168.8.4, ..."
            val DEVICE_SETTINGS =
                ("port=<port>;vhost=<vhost>;user=<user>;password=<pw>;framework=<framework_name>;parser=<parser_name>"
                        + "[;buffersize=<buffersize>][;ssl=<true/false>][;separator=<separator_sign>][;exchange=<exchange_name>]")
            val CHANNEL_ADDRESS = "<channel>"
            val DEVICE_SCAN_SETTINGS = "device scan not supported"
            return DriverInfo(ID, DESCRIPTION, DEVICE_ADDRESS, DEVICE_SETTINGS, CHANNEL_ADDRESS, DEVICE_SCAN_SETTINGS)
        }

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
        synchronized(this) {
            connection = AmqpDriverConnection(deviceAddress, settings)
            sslManager
            checkForExistingParserService()
            addParserServiceListenerToServiceRegistry()
            return connection
        }
    }

    private val sslManager: Unit
        get() {
            val registrationHandler = RegistrationHandler(context)
            registrationHandler.subscribeForService(SslManagerInterface::class.java.name) { instance: Any? ->
                if (instance != null) {
                    connection!!.setSslManager(instance as SslManagerInterface?)
                }
            }
        }

    private fun checkForExistingParserService() {
        val serviceReferences = serviceReferences
        for (serviceReference in serviceReferences) {
            val parserIdInit = serviceReference.getProperty("parserID") as String
            val parserInit = context!!.getService(serviceReference) as ParserService
            logger.info("{} registered, updating Parser in AmqpDriver", parserInit.javaClass.name)
            connection!!.setParser(parserIdInit, parserInit)
        }
    }

    private val serviceReferences: List<ServiceReference<*>>
        get() = try {
            var serviceReferences = context.getAllServiceReferences(
                ParserService::class.java.name,
                null
            )
            if (serviceReferences == null) {
                serviceReferences = arrayOf()
            }
            Arrays.asList(*serviceReferences)
        } catch (e: InvalidSyntaxException) {
            ArrayList()
        }

    private fun addParserServiceListenerToServiceRegistry() {
        val filter = '('.toString() + Constants.OBJECTCLASS + '=' + ParserService::class.java.name + ')'
        try {
            context.addServiceListener(
                { event: ServiceEvent -> getNewParserImplementationFromServiceRegistry(event) },
                filter
            )
        } catch (e: InvalidSyntaxException) {
            logger.error("Service listener can't be added to framework", e)
        }
    }

    private fun getNewParserImplementationFromServiceRegistry(event: ServiceEvent) {
        val serviceReference = event.serviceReference
        val parser = context.getService(serviceReference) as ParserService
        val parserId = serviceReference.getProperty("parserID") as String
        if (event.type == ServiceEvent.UNREGISTERING) {
            logger.info("{} unregistering, removing Parser from AmqpDriver", parser.javaClass.name)
            connection!!.setParser(parserId, null)
        } else {
            logger.info("{} changed, updating Parser in AmqpDriver", parser.javaClass.name)
            connection!!.setParser(parserId, parser)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AmqpDriver::class.java)
    }
}
