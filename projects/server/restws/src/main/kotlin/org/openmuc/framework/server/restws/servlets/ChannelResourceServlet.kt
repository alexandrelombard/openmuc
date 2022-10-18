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

import com.google.gson.JsonElement
import org.openmuc.framework.config.ChannelConfig
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.Value
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.dataaccess.Channel
import org.openmuc.framework.lib.rest1.Const
import org.openmuc.framework.lib.rest1.FromJson
import org.slf4j.LoggerFactory
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ChannelResourceServlet : GenericServlet() {
    private var dataAccess: DataAccessService? = null
    private var configService: ConfigService? = null
    private var rootConfig: RootConfig? = null
    @Throws(ServletException::class, IOException::class)
    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger)
            ?: return
        setConfigAccess()
        val pathInfo = pathAndQueryString[0]
        val pathInfoArray = ServletLib.getPathInfoArray(pathInfo)
        response.status = HttpServletResponse.SC_OK
        val json = ToJson()
        if (pathInfo == "/") {
            doGetAllChannels(json)
        } else {
            readSpecificChannels(request, response, pathInfoArray, json)
        }
        sendJson(json, response)
    }

    @Throws(IOException::class)
    private fun readSpecificChannels(
        request: HttpServletRequest, response: HttpServletResponse, pathInfoArray: Array<String?>?,
        json: ToJson
    ) {
        val channelId = pathInfoArray!![0]!!.replace("/", "")
        if (pathInfoArray.size == 1) {
            doGetSpecificChannel(json, channelId, response)
        } else if (pathInfoArray.size == 2) {
            if (pathInfoArray[1].equals(Const.TIMESTAMP, ignoreCase = true)) {
                doGetSpecificChannelField(json, channelId, Const.TIMESTAMP, response)
            } else if (pathInfoArray[1].equals(Const.FLAG, ignoreCase = true)) {
                doGetSpecificChannelField(json, channelId, Const.FLAG, response)
            } else if (pathInfoArray[1].equals(Const.VALUE_STRING, ignoreCase = true)) {
                doGetSpecificChannelField(json, channelId, Const.VALUE_STRING, response)
            } else if (pathInfoArray[1].equals(Const.CONFIGS, ignoreCase = true)) {
                doGetConfigs(json, channelId, response)
            } else if (pathInfoArray[1]!!.startsWith(Const.HISTORY)) {
                val fromParameter = request.getParameter("from")
                val untilParameter = request.getParameter("until")
                doGetHistory(json, channelId, fromParameter, untilParameter, response)
            } else if (pathInfoArray[1].equals(Const.DRIVER_ID, ignoreCase = true)) {
                doGetDriverId(json, channelId, response)
            } else if (pathInfoArray[1].equals(Const.DEVICE_ID, ignoreCase = true)) {
                doGetDeviceId(json, channelId, response)
            } else {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
                )
            }
        } else if (pathInfoArray.size == 3 && pathInfoArray[1].equals(Const.CONFIGS, ignoreCase = true)) {
            val configField = pathInfoArray[2]
            doGetConfigField(json, channelId, configField, response)
        } else {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_FOUND, logger,
                REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
            )
        }
    }

    @Throws(ServletException::class, IOException::class)
    override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger)
            ?: return
        setConfigAccess()
        val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
        val pathInfoArray = ServletLib.getPathInfoArray(pathInfo)
        val channelId = pathInfoArray!![0]!!.replace("/", "")
        val json = ServletLib.getFromJson(request, logger, response) ?: return
        if (pathInfoArray.size == 1) {
            setAndWriteChannelConfig(channelId, response, json, false)
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
            setConfigAccess()
            val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
            val pathInfoArray = ServletLib.getPathInfoArray(pathInfo)
            val channelId = pathInfoArray!![0]!!.replace("/", "")
            val json = ServletLib.getFromJson(request, logger, response) ?: return
            if (pathInfoArray.size < 1) {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
                )
            } else {
                val channelConfig = getChannelConfig(channelId, response)
                if (channelConfig != null) {
                    if (pathInfoArray.size == 2 && pathInfoArray[1].equals(Const.CONFIGS, ignoreCase = true)) {
                        setAndWriteChannelConfig(channelId, response, json, true)
                    } else if (pathInfoArray.size == 2 && pathInfoArray[1].equals(
                            Const.LATESTRECORD,
                            ignoreCase = true
                        )
                    ) {
                        doSetRecord(channelId, response, json)
                    } else if (pathInfoArray.size == 1) {
                        doWriteChannel(channelId, response, json)
                    } else {
                        ServletLib.sendHTTPErrorAndLogDebug(
                            response, HttpServletResponse.SC_NOT_FOUND, logger,
                            REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
                        )
                    }
                }
            }
        }
    }

    @Synchronized
    @Throws(ServletException::class, IOException::class)
    override fun doDelete(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger)
        if (pathAndQueryString != null) {
            setConfigAccess()
            val pathInfo = pathAndQueryString[0]
            val channelId: String
            val pathInfoArray = ServletLib.getPathInfoArray(pathInfo)
            val channelConfig: ChannelConfig?
            channelId = pathInfoArray!![0]!!.replace("/", "")
            channelConfig = getChannelConfig(channelId, response)
            if (channelConfig == null) {
                return
            }
            if (pathInfoArray.size != 1) {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    REQUESTED_REST_PATH_IS_NOT_AVAILABLE, " Path Info = ", request.pathInfo
                )
            } else {
                try {
                    channelConfig.delete()
                    configService.config = rootConfig
                    configService.writeConfigToFile()
                    if (rootConfig.getDriver(channelId) == null) {
                        response.status = HttpServletResponse.SC_OK
                    } else {
                        ServletLib.sendHTTPErrorAndLogErr(
                            response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            logger, "Not able to delete channel ", channelId
                        )
                    }
                } catch (e: ConfigWriteException) {
                    ServletLib.sendHTTPErrorAndLogErr(
                        response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, logger,
                        "Not able to write into config."
                    )
                    logger.warn("Failed to write config.", e)
                }
            }
        }
    }

    private fun getChannelConfig(channelId: String, response: HttpServletResponse): ChannelConfig? {
        val channelConfig: ChannelConfig = rootConfig.getChannel(channelId)
        if (channelConfig == null) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_FOUND, logger,
                "Requested rest channel is not available.", " ChannelID = ", channelId
            )
        }
        return channelConfig
    }

    @Throws(IOException::class)
    private fun doGetConfigField(json: ToJson, channelId: String, configField: String?, response: HttpServletResponse) {
        val channelConfig = getChannelConfig(channelId, response)
        if (channelConfig != null) {
            val jsoConfigAll: JsonObject = ToJson.getChannelConfigAsJsonObject(channelConfig)
            if (jsoConfigAll == null) {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    "Could not find JSON object \"configs\""
                )
            } else {
                val jseConfigField: JsonElement = jsoConfigAll.get(configField)
                if (jseConfigField == null) {
                    ServletLib.sendHTTPErrorAndLogDebug(
                        response, HttpServletResponse.SC_NOT_FOUND, logger,
                        "Requested rest config field is not available.", " configField = ", configField
                    )
                } else {
                    val jso = JsonObject()
                    jso.add(configField, jseConfigField)
                    json.addJsonObject(Const.CONFIGS, jso)
                }
            }
        }
    }

    private fun doGetDriverId(json: ToJson, channelId: String, response: HttpServletResponse) {
        val channelConfig = getChannelConfig(channelId, response)
        if (channelConfig != null) {
            val driverId = channelConfig.device!!.driver!!.id
            json.addString(Const.DRIVER_ID, driverId)
        }
    }

    private fun doGetDeviceId(json: ToJson, channelId: String, response: HttpServletResponse) {
        val channelConfig = getChannelConfig(channelId, response)
        if (channelConfig != null) {
            val deviceId = channelConfig.device!!.id
            json.addString(Const.DEVICE_ID, deviceId)
        }
    }

    private fun doGetConfigs(json: ToJson, channelId: String, response: HttpServletResponse) {
        val channelConfig = getChannelConfig(channelId, response)
        if (channelConfig != null) {
            json.addChannelConfig(channelConfig)
        }
    }

    private fun doGetHistory(
        json: ToJson, channelId: String, fromParameter: String, untilParameter: String,
        response: HttpServletResponse
    ) {
        var fromTimeStamp: Long = 0
        var untilTimeStamp: Long = 0
        val channelIds: List<String> = dataAccess.allIds
        var records: List<Record?>? = null
        if (channelIds.contains(channelId)) {
            val channel: Channel = dataAccess.getChannel(channelId)
            try {
                fromTimeStamp = fromParameter.toLong()
                untilTimeStamp = untilParameter.toLong()
            } catch (ex: NumberFormatException) {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_BAD_REQUEST, logger,
                    "From/To value is not a long number."
                )
            }
            try {
                records = channel.getLoggedRecords(fromTimeStamp, untilTimeStamp)
            } catch (e: DataLoggerNotAvailableException) {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, logger,
                    e.message
                )
            } catch (e: IOException) {
                ServletLib.sendHTTPErrorAndLogDebug(response, HttpServletResponse.SC_NOT_FOUND, logger, e.message)
            }
            json.addRecordList(records, channel.valueType)
        }
    }

    private fun setAndWriteChannelConfig(
        channelId: String, response: HttpServletResponse, json: FromJson?,
        isHTTPPut: Boolean
    ): Boolean {
        var ok = false
        try {
            ok = if (isHTTPPut) {
                setAndWriteHttpPutChannelConfig(channelId, response, json)
            } else {
                setAndWriteHttpPostChannelConfig(channelId, response, json)
            }
        } catch (e: JsonSyntaxException) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_CONFLICT, logger,
                "JSON syntax is wrong."
            )
        } catch (e: ConfigWriteException) {
            ServletLib.sendHTTPErrorAndLogErr(
                response, HttpServletResponse.SC_CONFLICT, logger,
                "Could not write channel \"", channelId, "\"."
            )
            e.printStackTrace()
        } catch (e: RestConfigIsNotCorrectException) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_ACCEPTABLE, logger,
                "Not correct formed channel config json.", " JSON = ", json.jsonObject.toString()
            )
        } catch (e: Error) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_ACCEPTABLE, logger,
                e.message
            )
        } catch (e: MissingJsonObjectException) {
            ServletLib.sendHTTPErrorAndLogDebug(response, HttpServletResponse.SC_NOT_FOUND, logger, e.message)
        } catch (e: IllegalStateException) {
            ServletLib.sendHTTPErrorAndLogDebug(response, HttpServletResponse.SC_CONFLICT, logger, e.message)
        }
        return ok
    }

    @Synchronized
    @Throws(
        JsonSyntaxException::class,
        ConfigWriteException::class,
        RestConfigIsNotCorrectException::class,
        MissingJsonObjectException::class,
        IllegalStateException::class
    )
    private fun setAndWriteHttpPutChannelConfig(
        channelId: String,
        response: HttpServletResponse,
        json: FromJson?
    ): Boolean {
        var ok = false
        val channelConfig = getChannelConfig(channelId, response)
        if (channelConfig != null) {
            try {
                json.setChannelConfig(channelConfig, channelId)
                configService.config = rootConfig
                configService.writeConfigToFile()
            } catch (e: IdCollisionException) {
            }
            response.status = HttpServletResponse.SC_OK
            ok = true
        }
        return ok
    }

    @Synchronized
    @Throws(
        JsonSyntaxException::class,
        ConfigWriteException::class,
        RestConfigIsNotCorrectException::class,
        Error::class,
        MissingJsonObjectException::class,
        IllegalStateException::class
    )
    private fun setAndWriteHttpPostChannelConfig(
        channelId: String,
        response: HttpServletResponse,
        json: FromJson?
    ): Boolean {
        var ok = false
        val deviceConfig: DeviceConfig
        var channelConfig: ChannelConfig = rootConfig.getChannel(channelId)
        val jso: JsonObject = json.jsonObject
        val jsonElement: JsonElement = jso.get(Const.DEVICE)
        if (jsonElement == null) {
            ServletLib.sendHTTPErrorAndLogErr(
                response, HttpServletResponse.SC_BAD_REQUEST, logger,
                "Wrong json message syntax. Device statement is missing."
            )
        }
        val deviceID: String = jsonElement.getAsString()
        deviceConfig = if (deviceID != null) {
            rootConfig.getDevice(deviceID)
        } else {
            throw Error("No device ID in JSON")
        }
        if (deviceConfig == null) {
            ServletLib.sendHTTPErrorAndLogErr(
                response, HttpServletResponse.SC_CONFLICT, logger,
                "Device does not exists: ", deviceID
            )
        } else if (channelConfig != null) {
            ServletLib.sendHTTPErrorAndLogErr(
                response, HttpServletResponse.SC_CONFLICT, logger,
                "Channel already exists: ", channelId
            )
        } else {
            try {
                channelConfig = deviceConfig.addChannel(channelId)
                json.setChannelConfig(channelConfig, channelId)
                if ((channelConfig.valueType === ValueType.STRING
                            || channelConfig.valueType === ValueType.BYTE_ARRAY)
                    && channelConfig.valueTypeLength == null
                ) {
                    ServletLib.sendHTTPErrorAndLogErr(
                        response, HttpServletResponse.SC_NOT_ACCEPTABLE, logger,
                        "Channel ", channelId, " with value type ", channelConfig.valueType.toString(),
                        ", missing valueTypeLength."
                    )
                    channelConfig.delete()
                } else {
                    configService.config = rootConfig
                    configService.writeConfigToFile()
                }
            } catch (e: IdCollisionException) {
            }
            response.status = HttpServletResponse.SC_OK
            ok = true
        }
        return ok
    }

    @Throws(IOException::class)
    private fun doGetSpecificChannel(json: ToJson, chId: String, response: HttpServletResponse) {
        val channel: Channel = dataAccess.getChannel(chId)
        if (channel != null) {
            val record = channel.latestRecord
            json.addRecord(record, channel.valueType)
        } else {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_FOUND, logger,
                "Requested rest channel is not available, ChannelID = $chId"
            )
        }
    }

    @Throws(IOException::class)
    private fun doGetSpecificChannelField(json: ToJson, chId: String, field: String, response: HttpServletResponse) {
        val channel: Channel = dataAccess.getChannel(chId)
        if (channel != null) {
            val record = channel.latestRecord
            when (field) {
                Const.TIMESTAMP -> json.addNumber(Const.TIMESTAMP, record!!.timestamp)
                Const.FLAG -> json.addString(Const.FLAG, record!!.flag.toString())
                Const.VALUE_STRING -> json.addValue(record!!.value, channel.valueType)
                else -> ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    "Requested rest channel field is not available, ChannelID = $chId Field: $field"
                )
            }
        } else {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_FOUND, logger,
                "Requested rest channel is not available, ChannelID = $chId"
            )
        }
    }

    private fun doGetAllChannels(json: ToJson) {
        val ids: List<String> = dataAccess.allIds
        val channels: MutableList<Channel> = ArrayList(ids.size)
        for (id in ids) {
            channels.add(dataAccess.getChannel(id))
        }
        json.addChannelRecordList(channels)
    }

    @Throws(ClassCastException::class)
    private fun doSetRecord(channelId: String, response: HttpServletResponse, json: FromJson?) {
        val channel: Channel = dataAccess.getChannel(channelId)
        val record: Record = json.getRecord(channel.valueType)
        if (record.flag == null) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_ACCEPTABLE, logger,
                "No flag set."
            )
        } else if (record.value == null) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_ACCEPTABLE, logger,
                "No value set."
            )
        } else {
            var timestamp = record.timestamp
            if (timestamp == null) {
                timestamp = System.currentTimeMillis()
            }
            val rec = Record(record.value, timestamp, record.flag)
            channel.latestRecord = rec
        }
    }

    private fun doWriteChannel(channelId: String, response: HttpServletResponse, json: FromJson?) {
        val channel: Channel = dataAccess.getChannel(channelId)
        val value: Value = json.getValue(channel.valueType)
        val flag = writeToChannel(channel, value)
        if (flag !== Flag.VALID) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_CONFLICT, logger,
                "Problems by writing to channel. Flag = " + flag.toString()
            )
        }
    }

    private fun setConfigAccess() {
        this.dataAccess = handleDataAccessService(null)
        this.configService = handleConfigService(null)
        this.rootConfig = handleRootConfig(null)
    }

    companion object {
        private const val REQUESTED_REST_PATH_IS_NOT_AVAILABLE = "Requested rest path is not available"
        private const val serialVersionUID = -702876016040151438L
        private val logger = LoggerFactory.getLogger(ChannelResourceServlet::class.java)
        fun writeToChannel(channel: Channel, value: Value?): Flag? {
            return channel.write(value)
        }
    }
}
