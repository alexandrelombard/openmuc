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
import org.openmuc.framework.lib.rest1.Const
import org.openmuc.framework.lib.rest1.ToJson
import org.openmuc.framework.lib.rest1.rest.objects.RestUserConfig
import org.slf4j.LoggerFactory
import java.io.IOException
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class UserServlet : GenericServlet() {
    private var authenticationService: AuthenticationService? = null
    @Throws(ServletException::class, IOException::class)
    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger)
        if (pathAndQueryString != null) {
            setServices()
            val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
            val json = ToJson()
            if (pathInfo == "/") {
                val userSet = authenticationService!!.allUsers
                val userList: MutableList<String?> = ArrayList()
                userList.addAll(userSet!!)
                json.addStringList(Const.USERS, userList)
            } else {
                val pathInfoArray = ServletLib.getPathInfoArray(pathInfo)
                if (pathInfoArray!!.size == 1) {
                    val userId = pathInfoArray[0]!!.replace("/", "")
                    if (userId.equals(Const.GROUPS, ignoreCase = true)) {
                        val groupList: MutableList<String?> = ArrayList()
                        groupList.add("") // TODO: add real groups, if groups exists in OpenMUC
                        json.addStringList(Const.GROUPS, groupList)
                    } else if (authenticationService!!.contains(userId)) {
                        json.addRestUserConfig(RestUserConfig(userId))
                    } else {
                        ServletLib.sendHTTPErrorAndLogDebug(
                            response, HttpServletResponse.SC_NOT_FOUND, logger,
                            "User does not exist.", " User = ", userId
                        )
                    }
                } else {
                    ServletLib.sendHTTPErrorAndLogDebug(
                        response, HttpServletResponse.SC_NOT_FOUND, logger,
                        REQUESTED_REST_PATH_IS_NOT_AVAILABLE, " Path Info = ", request.pathInfo
                    )
                }
            }
            sendJson(json, response)
        }
    }

    @Throws(ServletException::class, IOException::class)
    override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger) ?: return
        setServices()
        val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
        val json = ServletLib.getFromJson(request, logger, response) ?: return
        if (pathInfo == "/") {
            val userConfig = json.restUserConfig
            if (authenticationService!!.contains(userConfig.id)) {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    "User already exists.", " User = ", userConfig.id
                )
            } else if (userConfig.password == null || userConfig.password!!.isEmpty()) {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_PRECONDITION_FAILED, logger,
                    "Password is mandatory."
                )
            } else {
                authenticationService!!.registerNewUser(userConfig.id, userConfig.password)
            }
        } else {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_FOUND, logger,
                REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
            )
        }
    }

    @Throws(ServletException::class, IOException::class)
    override fun doPut(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger)
        if (pathAndQueryString != null) {
            setServices()
            val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
            val json = ServletLib.getFromJson(request, logger, response) ?: return
            if (pathInfo == "/") {
                val userConfig = json.restUserConfig
                if (userConfig.password == null || userConfig.password!!.isEmpty()) {
                    ServletLib.sendHTTPErrorAndLogDebug(
                        response, HttpServletResponse.SC_PRECONDITION_FAILED, logger,
                        "Password is mandatory."
                    )
                } else if (userConfig.oldPassword == null || userConfig.oldPassword!!.isEmpty()) {
                    ServletLib.sendHTTPErrorAndLogDebug(
                        response, HttpServletResponse.SC_PRECONDITION_FAILED, logger,
                        "Old password is mandatory."
                    )
                } else if (authenticationService!!.contains(userConfig.id)) {
                    val id = userConfig.id
                    if (authenticationService!!.login(id, userConfig.oldPassword)) {
                        authenticationService!!.registerNewUser(id, userConfig.password)
                    } else {
                        ServletLib.sendHTTPErrorAndLogDebug(
                            response, HttpServletResponse.SC_UNAUTHORIZED, logger,
                            "Old password is wrong."
                        )
                    }
                } else {
                    ServletLib.sendHTTPErrorAndLogDebug(
                        response, HttpServletResponse.SC_NOT_FOUND, logger,
                        "User does not exist.", " User = ", userConfig.id
                    )
                }
            } else {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
                )
            }
        }
    }

    @Throws(ServletException::class, IOException::class)
    override fun doDelete(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger)
        if (pathAndQueryString != null) {
            setServices()
            val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
            val json = ServletLib.getFromJson(request, logger, response) ?: return
            if (pathInfo == "/") {
                val userConfig = json.restUserConfig
                val userID = userConfig.id
                if (authenticationService!!.contains(userID)) {
                    authenticationService!!.delete(userID)
                } else {
                    ServletLib.sendHTTPErrorAndLogDebug(
                        response, HttpServletResponse.SC_NOT_FOUND, logger,
                        "Requested user does not exist.", " User = ", userID
                    )
                }
            } else {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
                )
            }
        } else {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_FOUND, logger,
                REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
            )
        }
    }

    private fun setServices() {
        this.authenticationService = handleAuthenticationService(null)
    }

    companion object {
        private const val REQUESTED_REST_PATH_IS_NOT_AVAILABLE = "Requested rest path is not available."
        private const val serialVersionUID = -5635380730045771853L
        private val logger = LoggerFactory.getLogger(DriverResourceServlet::class.java)
    }
}
