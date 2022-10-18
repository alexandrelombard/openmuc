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

import org.slf4j.LoggerFactory
import java.io.IOException
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ConnectServlet : GenericServlet() {
    @Throws(ServletException::class, IOException::class)
    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger) ?: return
        val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
        if (pathInfo == "/") {
            // do nothing only send 200 SC_OK
            sendJson(null, response)
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND)
        }
    }

    @Throws(ServletException::class, IOException::class)
    override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = "application/json"
        response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND)
    }

    @Throws(ServletException::class, IOException::class)
    override fun doPut(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = "application/json"
        response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND)
    }

    @Throws(ServletException::class, IOException::class)
    override fun doDelete(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = "application/json"
        response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND)
    }

    companion object {
        private const val NOT_FOUND = "Not found."
        private const val serialVersionUID = -2248093375930139043L
        private val logger = LoggerFactory.getLogger(DriverResourceServlet::class.java)
    }
}
