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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.openmuc.framework.authentication.AuthenticationService
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class WebUiBaseServlet(private val webUiBase: WebUiBase) : HttpServlet() {
    private var isSensitiveMode = true
    private var authService: AuthenticationService? = null
    @Throws(ServletException::class, IOException::class)
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val servletPath = req.servletPath
        if (servletPath == null) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Path is null.")
        } else if ("/applications" == servletPath) {
            if (req.session.isNew) {
                req.session.invalidate()
                resp.sendError(401)
                return
            }
            val jApplications = JsonArray()
            for (webUiApp in webUiBase.pluginsByAlias.values) {
                val app = JsonObject()
                app.addProperty("alias", webUiApp.alias)
                app.addProperty("name", webUiApp.name)
                jApplications.add(app)
            }
            val applicationsStr = jApplications.toString()
            if (logger.isDebugEnabled) {
                logger.debug(applicationsStr)
            }
            resp.contentType = "application/json"
            resp.writer.println(applicationsStr)
            return
        }
        val inputStream = servletContext.getResourceAsStream("page.html")
        val outputStream: OutputStream = resp.outputStream
        resp.contentType = "text/html"
        copyStream(inputStream, outputStream)
        outputStream.close()
        inputStream.close()
    }

    @Throws(ServletException::class, IOException::class)
    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val servletPath = req.servletPath
        if (logger.isInfoEnabled) {
            logger.info(servletPath)
        }
        if (servletPath != "/login") {
            doGet(req, resp)
            return
        }
        val user = req.getParameter("user")
        val pwd = req.getParameter("pwd")
        if (authService!!.login(user, pwd)) {
            updateView(user)
            val session = req.getSession(true) // create a new session
            session.maxInactiveInterval = SESSION_TIMEOUT // set session timeout
            session.setAttribute("user", user)
            resp.status = HttpServletResponse.SC_ACCEPTED
        } else {
            if (logger.isInfoEnabled) {
                logger.info("login failed!")
            }
            req.session.invalidate() // invalidate the session
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED)
        }
    }

    private fun updateView(user: String) {
        if (!authService!!.isUserAdmin(user) && isSensitiveMode) {
            hideSensitiveContent()
            isSensitiveMode = false
        } else if (authService!!.isUserAdmin(user) && !isSensitiveMode) {
            showSensitiveContent()
            isSensitiveMode = true
        }
    }

    private fun hideSensitiveContent() {
        webUiBase.unsetWebUiPluginServiceByAlias("channelaccesstool")
        webUiBase.unsetWebUiPluginServiceByAlias("channelconfigurator")
        webUiBase.unsetWebUiPluginServiceByAlias("userconfigurator")
        webUiBase.unsetWebUiPluginServiceByAlias("mediaviewer")
        webUiBase.unsetWebUiPluginServiceByAlias("dataplotter")
        webUiBase.unsetWebUiPluginServiceByAlias("dataexporter")
    }

    private fun showSensitiveContent() {
        webUiBase.restoreWebUiPlugin("channelaccesstool")
        webUiBase.restoreWebUiPlugin("channelconfigurator")
        webUiBase.restoreWebUiPlugin("userconfigurator")
        webUiBase.restoreWebUiPlugin("mediaviewer")
        webUiBase.restoreWebUiPlugin("dataplotter")
        webUiBase.restoreWebUiPlugin("dataexporter")
    }

    fun setAuthentification(authService: AuthenticationService?) {
        this.authService = authService
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WebUiBaseServlet::class.java)

        /**
         * 10 minutes.
         */
        private const val SESSION_TIMEOUT = 600
        @Throws(IOException::class)
        fun copyStream(input: InputStream, output: OutputStream) {
            val buffer = ByteArray(1024) // Adjust if you want
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
        }
    }
}
