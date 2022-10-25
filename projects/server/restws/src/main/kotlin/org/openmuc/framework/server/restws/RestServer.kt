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
package org.openmuc.framework.server.restws

import org.openmuc.framework.authentication.AuthenticationService
import org.openmuc.framework.config.ConfigService
import org.openmuc.framework.dataaccess.DataAccessService
import org.openmuc.framework.lib.rest1.Const
import org.openmuc.framework.server.restws.servlets.*
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.http.HttpService
import org.slf4j.LoggerFactory

@Component
class RestServer {
    private val chRServlet = ChannelResourceServlet()
    private val devRServlet = DeviceResourceServlet()
    private val drvRServlet = DriverResourceServlet()
    private val connectServlet = ConnectServlet()
    private val userServlet = UserServlet()
    @Reference
    protected fun setDataAccessService(dataAccessService: DataAccessService?) {
        Companion.dataAccessService = dataAccessService
    }

    @Reference
    protected fun setConfigService(configService: ConfigService?) {
        Companion.configService = configService
    }

    @Reference
    protected fun setAuthenticationService(authenticationService: AuthenticationService?) {
        Companion.authenticationService = authenticationService
    }

    @Activate
    @Throws(Exception::class)
    protected fun activate(context: ComponentContext) {
        logger.info("Activating REST Server")
        val securityHandler = SecurityHandler(
            context.bundleContext.bundle,
            authenticationService
        )
        httpService!!.registerServlet(Const.ALIAS_CHANNELS, chRServlet, null, securityHandler)
        httpService!!.registerServlet(Const.ALIAS_DEVICES, devRServlet, null, securityHandler)
        httpService!!.registerServlet(Const.ALIAS_DRIVERS, drvRServlet, null, securityHandler)
        httpService!!.registerServlet(Const.ALIAS_USERS, userServlet, null, securityHandler)
        httpService!!.registerServlet(Const.ALIAS_CONNECT, connectServlet, null, securityHandler)
        // httpService.registerServlet(Const.ALIAS_CONTROLS, controlsServlet, null, securityHandler);
    }

    @Deactivate
    protected fun deactivate(context: ComponentContext?) {
        logger.info("Deactivating REST Server")
        httpService!!.unregister(Const.ALIAS_CHANNELS)
        httpService!!.unregister(Const.ALIAS_DEVICES)
        httpService!!.unregister(Const.ALIAS_DRIVERS)
        httpService!!.unregister(Const.ALIAS_USERS)
        httpService!!.unregister(Const.ALIAS_CONNECT)
        // httpService.unregister(Const.ALIAS_CONTROLS);
    }

    protected fun unsetConfigService(configService: ConfigService?) {
        Companion.configService = null
    }

    protected fun unsetAuthenticationService(authenticationService: AuthenticationService?) {
        Companion.authenticationService = null
    }

    @Reference
    protected fun setHttpService(httpService: HttpService?) {
        Companion.httpService = httpService
    }

    protected fun unsetHttpService(httpService: HttpService?) {
        Companion.httpService = null
    }

    protected fun unsetDataAccessService(dataAccessService: DataAccessService?) {
        Companion.dataAccessService = null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RestServer::class.java)

        // private final ControlsServlet controlsServlet = new ControlsServlet();
        var dataAccessService: DataAccessService? = null
            private set
        var authenticationService: AuthenticationService? = null
            private set
        var configService: ConfigService? = null
            private set
        private var httpService: HttpService? = null
    }
}
