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
package org.openmuc.framework.lib.osgi.deployment

import ch.qos.logback.classic.Logger
import org.osgi.framework.*
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.cm.ManagedService
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

/**
 * This class provides some methods to ease the dynamic handling of OSGi services. It capsules routines of the OSGi
 * service management to make the handling more convenient for the developer. This class provides methods to register
 * own services to the OSGi environment and methods to subscribe to existing services.
 */
class RegistrationHandler(private val context: BundleContext) : ServiceListener {
    private val registrations: MutableList<ServiceRegistration<*>>
    private val logger = LoggerFactory.getLogger(this.javaClass) as Logger
    private val subscribedServices: MutableMap<String, ServiceAccess>
    private val subscribedServiceEvents: MutableMap<String, ServiceAccess>
    private val filterEntries: MutableList<String>
    private val FELIX_FILE_INSTALL_KEY = "felix.fileinstall.filename"
    private var configurationAdmin: ConfigurationAdmin? = null

    /**
     * Constructor
     *
     * @param context
     * BundleContext of your OSGi component which is typically provided in the activate method of your
     * component.
     */
    init {
        registrations = ArrayList()
        subscribedServices = HashMap()
        subscribedServiceEvents = HashMap()
        filterEntries = ArrayList()
        subscribeForService(
            ConfigurationAdmin::class.java.name
        ) { instance: Any? -> configurationAdmin = instance as ConfigurationAdmin? }
    }

    /**
     * Provides the given service within the OSGi environment.
     *
     * @param serviceName
     * Name of the service to provide. Typically "MyService.class.getName()" This class must implement the
     * interface org.osgi.service.cm.ManagedService.
     * @param serviceInstance
     * Instance of the service
     * @param pid
     * Persistence Id. Typically package path + class name e.g. "my.package.path.myClass"
     */
    fun provideInFramework(serviceName: String?, serviceInstance: Any?, pid: String) {
        val properties = buildProperties(pid)
        val newRegistration = context.registerService(serviceName, serviceInstance, properties)
        val newManagedService = context.registerService(
            ManagedService::class.java.name,
            serviceInstance, properties
        )
        updateConfigDatabaseWithGivenDictionary(properties)
        registrations.add(newRegistration)
        registrations.add(newManagedService)
    }

    fun provideInFrameworkWithoutManagedService(serviceName: String?, serviceInstance: Any?, pid: String) {
        val properties = buildProperties(pid)
        val newRegistration = context.registerService(serviceName, serviceInstance, properties)
        updateConfigDatabaseWithGivenDictionary(properties)
        registrations.add(newRegistration)
    }

    fun provideInFrameworkAsManagedService(serviceInstance: Any?, pid: String) {
        val properties = buildProperties(pid)
        val newManagedService = context.registerService(
            ManagedService::class.java.name,
            serviceInstance, properties
        )
        updateConfigDatabaseWithGivenDictionary(properties)
        registrations.add(newManagedService)
    }

    fun provideInFrameworkWithoutConfiguration(serviceName: String?, serviceInstance: Any?) {
        val newRegistration = context.registerService(serviceName, serviceInstance, null)
        registrations.add(newRegistration)
    }

    /**
     * Provides the given service within the OSGi environment with initial properties. <br></br>
     * **NOTE:** This method can be used at early development stage when no deployment package exists. Later on the
     * service would get the properties via the ManagedService interface.
     *
     * @param serviceName
     * Name of the service to provide. Typically "MyService.class.getName()"
     * @param serviceInstance
     * Instance of the service
     * @param properties
     * The properties for this service.
     */
    fun provideWithInitProperties(
        serviceName: String?, serviceInstance: Any?,
        properties: Dictionary<String, Any>
    ) {
        val newRegistration = context.registerService(serviceName, serviceInstance, properties)
        updateConfigDatabaseWithGivenDictionary(properties)
        registrations.add(newRegistration)
    }

    /**
     * Updates configuration entry in framework database for given dictionary. Dictionary must contain a property with
     * "Constants.SERVICE_PID" as key and service pid as value.
     *
     * @param properties
     * dictionary with updated properties and service pid
     */
    fun updateConfigDatabaseWithGivenDictionary(properties: Dictionary<String, Any>) {
        var pid: String? = null
        try {
            pid = properties[Constants.SERVICE_PID] as String
            val newConfig = configurationAdmin!!.getConfiguration(pid)
            val existingProperties: Dictionary<String, *>? = newConfig.properties
            if (existingProperties != null) {
                val fileName = existingProperties[FELIX_FILE_INSTALL_KEY] as String
                if (fileName != null) {
                    properties.put(FELIX_FILE_INSTALL_KEY, fileName)
                }
            }
            newConfig.update(properties)
        } catch (e: IOException) {
            logger.error("Config for {} can not been built\n{}", pid, e.message)
        }
    }

    /**
     * Unregisters all provided services
     */
    fun removeAllProvidedServices() {
        for (registration in registrations) {
            registration.unregister()
        }
        context.removeServiceListener(this)
    }

    /**
     * Subscribe for a service.
     *
     * @param serviceName
     * Name of the service. Typically "MyService.class.getName(). This class must implement the interface
     * org.osgi.service.cm.ManagedService.
     * @param access
     * ServicAccess instance
     */
    fun subscribeForService(serviceName: String, access: ServiceAccess) {
        subscribedServices[serviceName] = access
        filterEntries.add(serviceName)
        updateServiceListener()
        updateNow()
    }

    fun subscribeForServiceServiceEvent(serviceName: String, access: ServiceAccess) {
        subscribedServiceEvents[serviceName] = access
        filterEntries.add(serviceName)
        updateServiceListener()
        updateNow()
    }

    private fun buildProperties(pid: String): Dictionary<String, Any> {
        val properties: Dictionary<String, Any> = Hashtable()
        properties.put(Constants.SERVICE_PID, pid)
        val felixFileDir = System.getProperty("felix.fileinstall.dir")
        properties.put(FELIX_FILE_INSTALL_KEY, felixFileDir)
        return properties
    }

    private fun updateServiceListener() {
        context.removeServiceListener(this)
        val serviceFilter = createFilter()
        try {
            context.addServiceListener(this, serviceFilter)
        } catch (e: InvalidSyntaxException) {
            logger.error("Service listener can't be added to framework", e)
        }
    }

    private fun createFilter(): String {
        val builder = StringBuilder()
        builder.append("( |")
        for (serviceName in filterEntries) {
            builder.append("(" + Constants.OBJECTCLASS + "=" + serviceName + ") ")
        }
        builder.append(" ) ")
        return builder.toString()
    }

    private fun updateNow() {
        for (serviceName in subscribedServices.keys) {
            val serviceRef = context.getServiceReference(serviceName)
            val access = subscribedServices[serviceName]
            if (serviceRef == null) {
                access!!.setService(null)
            } else {
                access!!.setService(context.getService(serviceRef))
            }
        }
    }

    /**
     * Internal method! Must not be used by the user of RegistrationHandler class. (Note: Due to the implementation of
     * ServiceListener this method must be public)
     */
    override fun serviceChanged(event: ServiceEvent) {
        serviceServiceEventSubscribers(event)
        serveServiceSubscribers(event)
    }

    private fun serviceServiceEventSubscribers(event: ServiceEvent) {
        val changedServiceClass = (event.serviceReference.getProperty("objectClass") as Array<String>)[0]
        val access = subscribedServiceEvents[changedServiceClass]
        access?.setService(event)
    }

    private fun serveServiceSubscribers(event: ServiceEvent) {
        val changedServiceClass = (event.serviceReference.getProperty("objectClass") as Array<String>)[0]
        logger.debug("service changed for class $changedServiceClass")
        val access = subscribedServices[changedServiceClass] ?: return
        if (event.type == ServiceEvent.UNREGISTERING) {
            access.setService(null)
            return
        }
        val serviceRef = context.getServiceReference(changedServiceClass)
        access.setService(context.getService(serviceRef))
    }
}
