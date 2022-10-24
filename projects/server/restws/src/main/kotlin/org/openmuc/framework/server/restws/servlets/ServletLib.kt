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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.IOException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object ServletLib {
    private const val COULD_NOT_SEND_HTTP_ERROR_MESSAGE = "Could not send HTTP Error message."
    private val logger = LoggerFactory.getLogger(ServletLib::class.java)
    const val PATH_ARRAY_NR = 0
    internal const val QUERRY_ARRAY_NR = 1
    internal fun buildString(br: BufferedReader): String {
        val text = StringBuilder()
        try {
            var line: String?
            while (br.readLine().also { line = it } != null) {
                text.append(line)
            }
        } catch (e: IOException) {
            logger.error("", e)
        }
        return text.toString()
    }

    fun getFromJson(request: HttpServletRequest, logger: Logger, response: HttpServletResponse): FromJson? {
        var json: FromJson? = null
        try {
            json = FromJson(getJsonText(request))
        } catch (e: Exception) {
            sendHTTPErrorAndLogWarn(
                response, HttpServletResponse.SC_BAD_REQUEST, logger,
                "Malformed JSON message: ", e.message
            )
        }
        return json
    }

    /**
     * Send HTTP Error and log as warning. Only the first String will be sent over HTTP response.
     *
     * @param response
     * HttpServletResponse response
     * @param errorCode
     * error code
     * @param logger
     * logger
     * @param msg
     * message array
     */
    internal fun sendHTTPErrorAndLogWarn(
        response: HttpServletResponse, errorCode: Int, logger: Logger,
        vararg msg: String?
    ) {
        try {
            response.sendError(errorCode, msg[0])
        } catch (e: IOException) {
            logger.error(COULD_NOT_SEND_HTTP_ERROR_MESSAGE, e)
        }
        val warnMessage = StringBuilder()
        for (m in msg) {
            warnMessage.append(m)
        }
        if (logger.isWarnEnabled) {
            logger.warn(warnMessage.toString())
        }
    }

    /**
     * Send HTTP Error and log as debug. Only the first String will be sent over HTTP response.
     *
     * @param response
     * HttpServletResponse response
     * @param errorCode
     * error code
     * @param logger
     * logger
     * @param msg
     * message array
     */
    fun sendHTTPErrorAndLogDebug(
        response: HttpServletResponse, errorCode: Int, logger: Logger,
        vararg msg: String?
    ) {
        try {
            response.sendError(errorCode, msg[0])
        } catch (e: IOException) {
            logger.error(COULD_NOT_SEND_HTTP_ERROR_MESSAGE, e)
        }
        val warnMessage = StringBuilder()
        for (m in msg) {
            warnMessage.append(m)
        }
        if (logger.isDebugEnabled) {
            logger.debug(warnMessage.toString())
        }
    }

    /**
     * Send HTTP Error and log as error. Logger and HTTP response are the same message.
     *
     * @param response
     * HttpServletResponse response
     * @param errorCode
     * error code
     * @param logger
     * logger
     * @param msg
     * message array
     */
    fun sendHTTPErrorAndLogErr(
        response: HttpServletResponse, errorCode: Int, logger: Logger,
        vararg msg: String?
    ) {
        try {
            val sbErrMessage = StringBuilder()
            for (m in msg) {
                sbErrMessage.append(m)
            }
            val errMessage = sbErrMessage.toString()
            response.sendError(errorCode, errMessage)
            logger.error(errMessage)
        } catch (e: IOException) {
            logger.error(COULD_NOT_SEND_HTTP_ERROR_MESSAGE, e)
        }
    }

    @Throws(IOException::class)
    fun getJsonText(request: HttpServletRequest): String {
        return buildString(request.reader)
    }

    fun getPathInfoArray(pathInfo: String): Array<String> {
        return if (pathInfo.length > 1) {
            pathInfo.replaceFirst("/".toRegex(), "").split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        } else {
            arrayOf("/")
        }
    }
}
