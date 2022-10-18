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

import org.osgi.framework.Bundle
import org.osgi.service.http.HttpContext
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class BundleHttpContext(private val contextBundle: Bundle) : HttpContext {
    @Throws(IOException::class)
    override fun handleSecurity(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        // TODO: change this for some files..
        return true
    }

    override fun getResource(name: String): URL {
        val pathname = System.getProperty("user.dir") + name
        if (name.startsWith("/media/")) {
            return findUrl(pathname, "*")!!
        } else if (name.startsWith("/conf/webui/")) {
            return findUrl(pathname, ".conf")!!
        }
        return contextBundle.getResource(name)
    }

    private fun findUrl(pathname: String, fileEnding: String): URL? {
        var path = pathname
        if (fileEnding != "*") {
            path += fileEnding
        }
        val file = File(path)
        if (!file.canRead()) {
            logger.warn("Can not read requested file at {}.", path)
            return null
        }
        return try {
            file.toURI().toURL()
        } catch (e: MalformedURLException) {
            logger.warn("Can not read requested file at {}. {}", path, e)
            null
        }
    }

    override fun getMimeType(file: String): String {
        return if (file.endsWith(".jpg")) {
            "image/jpeg"
        } else if (file.endsWith(".png")) {
            "image/png"
        } else if (file.endsWith(".js")) {
            "text/javascript"
        } else if (file.endsWith(".css")) {
            "text/css"
        } else if (file.endsWith(".html")) {
            "text/html"
        } else if (file.endsWith(".pdf")) {
            "application/pdf"
        } else if (file.startsWith("/conf/webui")) {
            "application/json"
        } else {
            "text/html"
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BundleHttpContext::class.java)
    }
}
