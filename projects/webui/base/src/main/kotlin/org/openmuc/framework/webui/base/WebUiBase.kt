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
package org.openmuc.framework.webui.base

import org.openmuc.framework.authentication.AuthenticationService
import org.openmuc.framework.webui.spi.WebUiPluginService
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.*
import org.osgi.service.http.HttpService
import org.osgi.service.http.NamespaceException
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Component
open class WebUiBase {
    val pluginsByAlias: MutableMap<String, WebUiPluginService> = ConcurrentHashMap()
    val pluginsByAliasDeactivated: MutableMap<String, WebUiPluginService> = ConcurrentHashMap()

    @Reference
    protected lateinit var httpService: HttpService

    @Reference
    protected lateinit var authService: AuthenticationService

    @Volatile
    private lateinit var servlet: WebUiBaseServlet
    @Activate
    protected fun activate(context: BundleContext) {
        logger.info("Activating WebUI Base")
        servlet = WebUiBaseServlet(this)
        servlet.setAuthentification(authService)
        val bundleHttpContext = BundleHttpContext(context.bundle)
        try {
            httpService.registerResources("/app", "/app", bundleHttpContext)
            httpService.registerResources("/assets", "/assets", bundleHttpContext)
            httpService.registerResources("/openmuc/css", "/css", bundleHttpContext)
            httpService.registerResources("/openmuc/images", "/images", bundleHttpContext)
            httpService.registerResources("/openmuc/html", "/html", bundleHttpContext)
            httpService.registerResources("/openmuc/js", "/js", bundleHttpContext)
            httpService.registerResources("/media", "/media", bundleHttpContext)
            httpService.registerResources("/conf/webui", "/conf/webui", bundleHttpContext)
            httpService.registerServlet("/", servlet, null, bundleHttpContext)
        } catch (e: Exception) {
            //
        }
        synchronized(pluginsByAlias) {
            for (plugin in pluginsByAlias.values) {
                registerResources(plugin)
            }
        }
    }

    @Deactivate
    protected fun deactivate() {
        logger.info("Deactivating WebUI Base")
        httpService.unregister("/app")
        httpService.unregister("/assets")
        httpService.unregister("/openmuc/css")
        httpService.unregister("/openmuc/images")
        httpService.unregister("/openmuc/html")
        httpService.unregister("/openmuc/js")
        httpService.unregister("/media")
        httpService.unregister("/conf/webui")
        httpService.unregister("/")
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    protected fun setWebUiPluginService(uiPlugin: WebUiPluginService) {
        synchronized(pluginsByAlias) {
            if (!pluginsByAlias.containsValue(uiPlugin)) {
                pluginsByAlias[uiPlugin.alias] = uiPlugin
                registerResources(uiPlugin)
            }
        }
    }

    protected fun unsetWebUiPluginService(uiPlugin: WebUiPluginService) {
        unregisterResources(uiPlugin)
        pluginsByAlias.remove(uiPlugin.alias)
        logger.info("WebUI plugin deregistered: {}.", uiPlugin.name)
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected fun setAuthentificationService(authService: AuthenticationService) {
        this.authService = authService
    }

    protected fun unsetAuthentificationService(authService: AuthenticationService) {}
    fun unsetWebUiPluginServiceByAlias(alias: String) {
        val toRemovePlugin = pluginsByAlias.remove(alias)
        if(toRemovePlugin != null) {
            pluginsByAliasDeactivated[alias] = toRemovePlugin
        }
    }

    fun restoreWebUiPlugin(alias: String) {
        val toAddPlugin = pluginsByAliasDeactivated.remove(alias)
        if (toAddPlugin != null) {
            pluginsByAlias[alias] = toAddPlugin
        }
    }

    private fun registerResources(plugin: WebUiPluginService) {
        if (servlet == null || httpService == null) {
            logger.warn("Can't register WebUI plugin {}.", plugin.name)
            return
        }
        val bundleHttpContext = BundleHttpContext(plugin.contextBundle)
        val aliases: Set<String> = plugin.resources.keys
        for (alias in aliases) {
            try {
                httpService.registerResources(
                    "/" + plugin.alias + "/" + alias, plugin.resources[alias],
                    bundleHttpContext
                )
            } catch (e: NamespaceException) {
                logger.error("Servlet with alias \"/{}/{}\" already registered.", plugin.alias, alias)
            }
        }
        logger.info("WebUI plugin registered: " + plugin.name)
    }

    private fun unregisterResources(plugin: WebUiPluginService) {
        val aliases: Set<String> = plugin.resources.keys
        for (alias in aliases) {
            httpService.unregister("/" + plugin.alias + "/" + alias)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WebUiBase::class.java)
    }
}
