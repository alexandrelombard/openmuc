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
package org.openmuc.framework.lib.ssl

import org.openmuc.framework.lib.osgi.config.*
import org.openmuc.framework.security.SslConfigChangeListener
import org.openmuc.framework.security.SslManagerInterface
import org.osgi.service.cm.ConfigurationException
import org.osgi.service.cm.ManagedService
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.util.*
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class SslManager internal constructor() : ManagedService, SslManagerInterface {
    private val listeners: MutableList<SslConfigChangeListener?> = ArrayList()
    private val propertyHandler: PropertyHandler
    override var keyManagerFactory: KeyManagerFactory? = null
        private set
    override var trustManagerFactory: TrustManagerFactory? = null
        private set
    override var sslContext: SSLContext? = null
        private set
    override var isLoaded = false
        private set

    init {
        try {
            keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
            trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
            sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, null, null)
        } catch (e: NoSuchAlgorithmException) {
            logger.error("Factory could not be loaded: {}", e.message)
        } catch (e: KeyManagementException) {
            logger.error("Factory could not be loaded: {}", e.message)
        }
        propertyHandler = PropertyHandler(Settings(), SslManager::class.java.name)
    }

    override fun listenForConfigChange(listener: SslConfigChangeListener?) {
        synchronized(listeners) { listeners.add(listener) }
    }

    private fun load() {
        isLoaded = true
        val keyStorePassword = propertyHandler.getString(Settings.Companion.KEYSTORE_PASSWORD)!!
            .toCharArray()
        val trustStorePassword = propertyHandler.getString(Settings.Companion.TRUSTSTORE_PASSWORD)!!
            .toCharArray()
        try {
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(FileInputStream(propertyHandler.getString(Settings.Companion.KEYSTORE)), keyStorePassword)
            val trustStore = KeyStore.getInstance("PKCS12")
            trustStore.load(
                FileInputStream(propertyHandler.getString(Settings.Companion.TRUSTSTORE)),
                trustStorePassword
            )

            // get factories
            keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
            keyManagerFactory.init(keyStore, keyStorePassword)
            trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
            trustManagerFactory.init(trustStore)
            sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null)
            logger.info("Successfully loaded")
        } catch (e: Exception) {
            logger.error("Could not load key/trust store: {}", e.message)
        }
    }

    private fun notifyListeners() {
        synchronized(listeners) {
            for (listener in listeners) {
                listener!!.configChanged()
            }
        }
    }

    fun tryProcessConfig(newConfig: DictionaryPreprocessor?) {
        try {
            propertyHandler.processConfig(newConfig!!)
            if (!isLoaded || !propertyHandler.isDefaultConfig && propertyHandler.configChanged()) {
                load()
                notifyListeners()
            }
        } catch (e: ServicePropertyException) {
            logger.error("update properties failed", e)
        }
    }

    @Throws(ConfigurationException::class)
    override fun updated(properties: Dictionary<String?, *>?) {
        val dict = DictionaryPreprocessor(properties)
        if (!dict.wasIntermediateOsgiInitCall()) {
            tryProcessConfig(dict)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SslManager::class.java)
    }
}
