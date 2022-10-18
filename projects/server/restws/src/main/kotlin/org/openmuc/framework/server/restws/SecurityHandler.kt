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

import org.apache.commons.codec.binary.Base64
import org.openmuc.framework.authentication.AuthenticationService
import org.openmuc.framework.data.Record.value
import org.osgi.framework.Bundle
import org.osgi.service.http.HttpContext
import java.io.IOException
import java.net.URL
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class SecurityHandler(var contextBundle: Bundle, var authService: AuthenticationService?) : HttpContext {
    @Throws(IOException::class)
    override fun handleSecurity(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        if (!authenticated(request)) {
            response.setHeader("WWW-Authenticate", "BASIC realm=\"private area\"")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
            return false
        }
        return true
    }

    private fun authenticated(request: HttpServletRequest): Boolean {
        if (request.method == "OPTIONS") {
            return true
        }
        val authzHeader = request.getHeader("Authorization") ?: return false
        val usernameAndPassword: String
        usernameAndPassword = try {
            String(Base64.decodeBase64(authzHeader.substring(6)))
        } catch (e: ArrayIndexOutOfBoundsException) {
            return false
        }
        val userNameIndex = usernameAndPassword.indexOf(':')
        val username = usernameAndPassword.substring(0, userNameIndex)
        val password = usernameAndPassword.substring(userNameIndex + 1)
        return authService!!.login(username, password)
    }

    override fun getResource(name: String): URL {
        return contextBundle.getResource(name)
    }

    override fun getMimeType(name: String): String {
        return null
    }
}
