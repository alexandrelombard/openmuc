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
import org.openmuc.framework.config.ScanException
import org.openmuc.framework.dataaccess.Channel
import org.openmuc.framework.lib.rest1.Const
import org.openmuc.framework.lib.rest1.FromJson
import org.slf4j.LoggerFactory
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class DeviceResourceServlet : GenericServlet() {
    private var dataAccess: DataAccessService? = null
    private var configService: ConfigService? = null
    private var rootConfig: RootConfig? = null
    @Throws(ServletException::class, IOException::class)
    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger)
        if (pathAndQueryString != null) {
            setConfigAccess()
            val deviceID: String
            val configField: String
            val pathInfo = pathAndQueryString[0]
            val pathInfoArray = ServletLib.getPathInfoArray(pathInfo)
            val deviceList = doGetDeviceList()
            response.status = HttpServletResponse.SC_OK
            val json = ToJson()
            if (pathInfo == "/") {
                json.addStringList(Const.DEVICES, deviceList)
            } else {
                deviceID = pathInfoArray!![0]!!.replace("/", "")
                if (deviceList.contains(deviceID)) {
                    val deviceChannelList = doGetDeviceChannelList(deviceID)
                    val deviceState: DeviceState = configService.getDeviceState(deviceID)
                    if (pathInfoArray.size == 1) {
                        json.addChannelRecordList(deviceChannelList)
                        json.addDeviceState(deviceState)
                    } else if (pathInfoArray[1].equals(Const.STATE, ignoreCase = true)) {
                        json.addDeviceState(deviceState)
                    } else if (pathInfoArray.size > 1 && pathInfoArray[1] == Const.CHANNELS) {
                        json.addChannelList(deviceChannelList)
                        json.addDeviceState(deviceState)
                    } else if (pathInfoArray.size == 2 && pathInfoArray[1].equals(Const.CONFIGS, ignoreCase = true)) {
                        doGetConfigs(json, deviceID, response)
                    } else if (pathInfoArray.size == 3 && pathInfoArray[1].equals(Const.CONFIGS, ignoreCase = true)) {
                        configField = pathInfoArray[2]
                        doGetConfigField(json, deviceID, configField, response)
                    } else if (pathInfoArray[1].equals(Const.SCAN, ignoreCase = true)) {
                        val settings = request.getParameter(Const.SETTINGS)
                        val channelScanInfoList: List<ChannelScanInfo> =
                            scanForAllChannels(deviceID, settings, response)
                        json.addChannelScanInfoList(channelScanInfoList)
                    } else {
                        ServletLib.sendHTTPErrorAndLogDebug(
                            response, HttpServletResponse.SC_NOT_FOUND, logger,
                            "Requested rest device is not available or unknown option.", " DeviceID = ", deviceID
                        )
                    }
                } else {
                    ServletLib.sendHTTPErrorAndLogDebug(
                        response, HttpServletResponse.SC_NOT_FOUND, logger,
                        REQUESTED_REST_DEVICE_IS_NOT_AVAILABLE, " DeviceID = ", deviceID
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
            setConfigAccess()
            val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
            val pathInfoArray = ServletLib.getPathInfoArray(pathInfo)
            val deviceID = pathInfoArray!![0]!!.replace("/", "")
            val json = ServletLib.getFromJson(request, logger, response) ?: return
            if (pathInfoArray.size == 1) {
                setAndWriteDeviceConfig(deviceID, response, json, false)
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
            setConfigAccess()
            val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
            val pathInfoArray = ServletLib.getPathInfoArray(pathInfo)
            val deviceID = pathInfoArray!![0]!!.replace("/", "")
            val json = ServletLib.getFromJson(request, logger, response) ?: return
            if (pathInfoArray.size < 1) {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
                )
            } else {
                val deviceConfig: DeviceConfig = rootConfig.getDevice(deviceID)
                if (deviceConfig != null && pathInfoArray.size == 2 && pathInfoArray[1].equals(
                        Const.CONFIGS,
                        ignoreCase = true
                    )
                ) {
                    setAndWriteDeviceConfig(deviceID, response, json, true)
                } else {
                    ServletLib.sendHTTPErrorAndLogDebug(
                        response, HttpServletResponse.SC_NOT_FOUND, logger,
                        REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
                    )
                }
            }
        }
    }

    @Throws(ServletException::class, IOException::class)
    override fun doDelete(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger)
        if (pathAndQueryString != null) {
            setConfigAccess()
            val pathInfo = pathAndQueryString[0]
            var deviceID: String? = null
            val pathInfoArray = ServletLib.getPathInfoArray(pathInfo)
            deviceID = pathInfoArray!![0]!!.replace("/", "")
            val deviceConfig: DeviceConfig = rootConfig.getDevice(deviceID)
            if (pathInfoArray.size != 1) {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    "Requested rest path is not available", " Path Info = ", request.pathInfo
                )
            } else if (deviceConfig == null) {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    "Device \"$deviceID\" does not exist."
                )
            } else {
                try {
                    deviceConfig.delete()
                    configService.config = rootConfig
                    configService.writeConfigToFile()
                    if (rootConfig.getDriver(deviceID) == null) {
                        response.status = HttpServletResponse.SC_OK
                    } else {
                        ServletLib.sendHTTPErrorAndLogErr(
                            response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            logger, "Not able to delete driver ", deviceID
                        )
                    }
                } catch (e: ConfigWriteException) {
                    ServletLib.sendHTTPErrorAndLogErr(
                        response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, logger,
                        "Not able to write into config."
                    )
                }
            }
        }
    }

    private fun scanForAllChannels(
        deviceID: String,
        settings: String,
        response: HttpServletResponse
    ): List<ChannelScanInfo> {
        val channelList: MutableList<ChannelScanInfo> = ArrayList<ChannelScanInfo>()
        val scannedDevicesList: List<ChannelScanInfo>
        val deviceIDString = " deviceId = "
        try {
            scannedDevicesList = configService.scanForChannels(deviceID, settings)
            for (scannedDevice in scannedDevicesList) {
                channelList.add(scannedDevice)
            }
        } catch (e: UnsupportedOperationException) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, logger,
                "Device does not support scanning.", deviceIDString, deviceID
            )
        } catch (e: DriverNotAvailableException) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_FOUND, logger,
                REQUESTED_REST_DEVICE_IS_NOT_AVAILABLE, deviceIDString, deviceID
            )
        } catch (e: ArgumentSyntaxException) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_ACCEPTABLE, logger,
                "Argument syntax was wrong.", deviceIDString, deviceID, " Settings = ", settings
            )
        } catch (e: ScanException) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, logger,
                "Error while scan device channels", deviceIDString, deviceID, " Settings = ", settings
            )
        }
        return channelList
    }

    private fun doGetDeviceChannelList(deviceID: String): List<Channel> {
        val deviceChannelList: MutableList<Channel> = ArrayList()
        val channelConfig: Collection<ChannelConfig>
        channelConfig = rootConfig.getDevice(deviceID).channels
        for (chCf in channelConfig) {
            deviceChannelList.add(dataAccess.getChannel(chCf.id))
        }
        return deviceChannelList
    }

    private fun doGetDeviceList(): List<String> {
        val deviceList: MutableList<String> = ArrayList()
        val driverConfig: Collection<DriverConfig>
        driverConfig = rootConfig.drivers
        val deviceConfig: MutableCollection<DeviceConfig> = ArrayList<DeviceConfig>()
        for (drvCfg in driverConfig) {
            val driverId: String = drvCfg.id
            deviceConfig.addAll(rootConfig.getDriver(driverId).devices)
        }
        for (devCfg in deviceConfig) {
            deviceList.add(devCfg.id)
        }
        return deviceList
    }

    @Throws(IOException::class)
    private fun doGetConfigField(json: ToJson, deviceID: String, configField: String?, response: HttpServletResponse) {
        val deviceConfig: DeviceConfig = rootConfig.getDevice(deviceID)
        if (deviceConfig != null) {
            val jsoConfigAll: JsonObject = ToJson.getDeviceConfigAsJsonObject(deviceConfig)
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
        } else {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_FOUND, logger,
                "Requested rest channel is not available.", " ChannelID = ", deviceID
            )
        }
    }

    @Throws(IOException::class)
    private fun doGetConfigs(json: ToJson, deviceID: String, response: HttpServletResponse) {
        val deviceConfig: DeviceConfig
        deviceConfig = rootConfig.getDevice(deviceID)
        if (deviceConfig != null) {
            json.addDeviceConfig(deviceConfig)
        } else {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_FOUND, logger,
                REQUESTED_REST_DEVICE_IS_NOT_AVAILABLE, " DeviceID = ", deviceID
            )
        }
    }

    private fun setAndWriteDeviceConfig(
        deviceID: String, response: HttpServletResponse, json: FromJson?,
        isHTTPPut: Boolean
    ): Boolean {
        try {
            return if (isHTTPPut) {
                setAndWriteHttpPutDeviceConfig(deviceID, response, json)
            } else {
                setAndWriteHttpPostDeviceConfig(deviceID, response, json)
            }
        } catch (e: JsonSyntaxException) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_CONFLICT, logger,
                "JSON syntax is wrong."
            )
        } catch (e: ConfigWriteException) {
            ServletLib.sendHTTPErrorAndLogErr(
                response, HttpServletResponse.SC_CONFLICT, logger,
                "Could not write device \"", deviceID, "\"."
            )
        } catch (e: RestConfigIsNotCorrectException) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_ACCEPTABLE, logger,
                "Not correct formed device config json.", " JSON = ", json.jsonObject.toString()
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
        return false
    }

    @Throws(
        JsonSyntaxException::class,
        ConfigWriteException::class,
        RestConfigIsNotCorrectException::class,
        MissingJsonObjectException::class,
        IllegalStateException::class
    )
    private fun setAndWriteHttpPutDeviceConfig(
        deviceID: String,
        response: HttpServletResponse,
        json: FromJson?
    ): Boolean {
        var ok = false
        val deviceConfig: DeviceConfig = rootConfig.getDevice(deviceID)
        if (deviceConfig != null) {
            try {
                json.setDeviceConfig(deviceConfig, deviceID)
            } catch (e: IdCollisionException) {
            }
            configService.config = rootConfig
            configService.writeConfigToFile()
            response.status = HttpServletResponse.SC_OK
            ok = true
        } else {
            ServletLib.sendHTTPErrorAndLogErr(
                response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, logger,
                "Not able to access to device ", deviceID
            )
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
    private fun setAndWriteHttpPostDeviceConfig(
        deviceID: String,
        response: HttpServletResponse,
        json: FromJson?
    ): Boolean {
        var ok = false
        val driverConfig: DriverConfig
        var deviceConfig: DeviceConfig = rootConfig.getDevice(deviceID)
        val jso: JsonObject = json.jsonObject
        val driverID: String = jso.get(Const.DRIVER).getAsString()
        driverConfig = if (driverID != null) {
            rootConfig.getDriver(driverID)
        } else {
            throw Error("No driver ID in JSON")
        }
        if (driverConfig == null) {
            ServletLib.sendHTTPErrorAndLogErr(
                response, HttpServletResponse.SC_CONFLICT, logger,
                "Driver does not exists: ", driverID
            )
        } else if (deviceConfig != null) {
            ServletLib.sendHTTPErrorAndLogErr(
                response, HttpServletResponse.SC_CONFLICT, logger,
                "Device already exists: ", deviceID
            )
        } else {
            try {
                deviceConfig = driverConfig.addDevice(deviceID)
                json.setDeviceConfig(deviceConfig, deviceID)
            } catch (e: IdCollisionException) {
            }
            configService.config = rootConfig
            configService.writeConfigToFile()
            response.status = HttpServletResponse.SC_OK
            ok = true
        }
        return ok
    }

    private fun setConfigAccess() {
        this.dataAccess = handleDataAccessService(null)
        this.configService = handleConfigService(null)
        this.rootConfig = handleRootConfig(null)
    }

    companion object {
        private const val REQUESTED_REST_PATH_IS_NOT_AVAILABLE = "Requested rest path is not available."
        private const val REQUESTED_REST_DEVICE_IS_NOT_AVAILABLE = "Requested rest device is not available."
        private const val serialVersionUID = 4619892734239871891L
        private val logger = LoggerFactory.getLogger(DeviceResourceServlet::class.java)
    }
}
