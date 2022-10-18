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

import org.openmuc.framework.lib.rest1.FromJson
import org.openmuc.framework.lib.rest1.ToJson
import org.slf4j.LoggerFactory
import java.io.IOException
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ControlsServlet : GenericServlet() {
    @Throws(ServletException::class, IOException::class)
    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger)
        if (pathAndQueryString != null) {
            val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
            val json = ToJson()
            if (pathInfo == "/") {
            } else {
                val pathInfoArray = ServletLib.getPathInfoArray(pathInfo)
                if (pathInfoArray!!.size == 1) {
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
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger)
        if (pathAndQueryString != null) {
            val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
            FromJson(ServletLib.getJsonText(request))
            if (pathInfo == "/") {
            } else {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
                )
            }
        }
    }

    @Throws(ServletException::class, IOException::class)
    override fun doPut(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger)
        if (pathAndQueryString != null) {
            val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
            FromJson(ServletLib.getJsonText(request))
            if (pathInfo == "/") {
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
            val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
            FromJson(ServletLib.getJsonText(request))
            if (pathInfo == "/") {
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

    companion object {
        private const val REQUESTED_REST_PATH_IS_NOT_AVAILABLE = "Requested rest path is not available."
        private const val serialVersionUID = -5635380730045771853L
        private val logger = LoggerFactory.getLogger(DriverResourceServlet::class.java)
    }
}
