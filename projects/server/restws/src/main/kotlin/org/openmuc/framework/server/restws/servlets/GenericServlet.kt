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
package org.openmuc.framework.server.restws.servlets

import org.openmuc.framework.authentication.AuthenticationService
import org.openmuc.framework.config.ConfigChangeListener
import org.openmuc.framework.config.ConfigService
import org.openmuc.framework.config.RootConfig
import org.openmuc.framework.data.Record
import org.openmuc.framework.dataaccess.DataAccessService
import org.openmuc.framework.lib.rest1.ToJson
import org.openmuc.framework.server.restws.RestServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

abstract class GenericServlet : HttpServlet(), ConfigChangeListener {
    @Throws(ServletException::class)
    override fun init() {
        handleDataAccessService(RestServer.dataAccessService)
        handleConfigService(RestServer.configService)
        handleRootConfig(configService!!.getConfig(this))
        handleAuthenticationService(RestServer.authenticationService)
        corsProperty
    }

    @Throws(ServletException::class, IOException::class)
    public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    }

    @Throws(ServletException::class, IOException::class)
    public override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    }

    @Throws(ServletException::class, IOException::class)
    public override fun doPut(request: HttpServletRequest, response: HttpServletResponse) {
    }

    @Throws(ServletException::class, IOException::class)
    public override fun doDelete(request: HttpServletRequest, response: HttpServletResponse) {
    }

    // Handels an CORS(Cross-Origin Resource Sharing) request
    @Throws(ServletException::class, IOException::class)
    public override fun doOptions(request: HttpServletRequest, response: HttpServletResponse) {
        if (corsEnabled) {
            val iterator = propertyMap!!.entries.iterator()
            var flagOriginUnknown = true
            while (iterator.hasNext()) {
                val (key, value) = iterator.next()
                val header = request.getHeader("Origin")
                if (key == "*" || header.equals(key, ignoreCase = true)) {
                    flagOriginUnknown = false
                    response.setHeader("Access-Control-Allow-Origin", key)
                    response.setHeader("Access-Control-Allow-Methods", value!![0])
                    response.setHeader("Access-Control-Allow-Headers", value[1])
                    response.status = HttpServletResponse.SC_OK
                }
                if (flagOriginUnknown) {
                    ServletLib.sendHTTPErrorAndLogDebug(
                        response, HttpServletResponse.SC_FORBIDDEN, logger,
                        "Options request received, but origin is not known -> access denied"
                    )
                }
            }
        } else {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_IMPLEMENTED, logger,
                "The CORS functionality is deactivated"
            )
        }
    }

    override fun destroy() {}
    override fun configurationChanged() {
        rootConfig = configService!!.config
    }

    @Throws(ServletException::class, IOException::class)
    fun sendJson(json: ToJson?, response: HttpServletResponse) {
        if (!response.isCommitted) {
            val outStream: OutputStream = response.outputStream
            if (json != null) {
                val jsonString = json.toString()
                outStream.write(jsonString.toByteArray(CHARSET))
            }
            outStream.flush()
            outStream.close()
        }
    }

    fun checkIfItIsACorrectRest(
        request: HttpServletRequest,
        response: HttpServletResponse,
        logger: Logger
    ): Array<String>? {
        var pathInfo = request.pathInfo
        var queryStr = request.queryString
        if (pathInfo == null) {
            pathInfo = "/"
        }
        if (queryStr == null) {
            queryStr = ""
        }

        /* Accept only "application/json" and null. Null is a browser request. */if (request.contentType != null && !request.contentType.startsWith(
                "application/json"
            )
        ) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, logger,
                "Requested rest was not a json media type. Requested media type is: " + request.contentType
            )
            return null
        } else {
            return arrayOf(pathInfo, queryStr)
        }
    }

    @Synchronized
    fun handleDataAccessService(dataAccessService: DataAccessService?): DataAccessService? {
        if (dataAccessService != null) {
            dataAccess = dataAccessService
        }
        return dataAccess
    }

    @Synchronized
    fun handleConfigService(configServ: ConfigService?): ConfigService? {
        if (configServ != null) {
            configService = configServ
        }
        return configService
    }

    @Synchronized
    fun handleAuthenticationService(authServ: AuthenticationService?): AuthenticationService? {
        if (authServ != null) {
            authenticationService = authServ
        }
        return authenticationService
    }

    @Synchronized
    fun handleRootConfig(rootConf: RootConfig?): RootConfig? {
        if (rootConf != null) {
            rootConfig = rootConf
        }
        return rootConfig
    }

    companion object {
        private const val serialVersionUID = 4041357804530863512L
        private val CHARSET = StandardCharsets.UTF_8
        const val APPLICATION_JSON = "application/json"
        const val REST_PATH = " Rest Path = "
        private var dataAccess: DataAccessService? = null
        private var configService: ConfigService? = null
        private var authenticationService: AuthenticationService? = null
        private var rootConfig: RootConfig? = null
        private val logger = LoggerFactory.getLogger(GenericServlet::class.java)
        private var propertyMap: MutableMap<String, ArrayList<String>> = hashMapOf()
        private var corsEnabled = false
        private val corsProperty: Unit
            private get() {
                val propertyReader: PropertyReader = PropertyReader.instance
                corsEnabled = propertyReader.isCorsEnabled
                propertyMap = propertyReader.propertyMap
            }
    }
}
