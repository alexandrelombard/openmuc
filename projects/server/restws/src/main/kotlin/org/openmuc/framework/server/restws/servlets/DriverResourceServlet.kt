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
import org.openmuc.framework.config.DriverInfo
import org.openmuc.framework.dataaccess.Channel
import org.openmuc.framework.lib.rest1.Const
import org.slf4j.LoggerFactory
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class DriverResourceServlet : GenericServlet() {
    private var dataAccess: DataAccessService? = null
    private var configService: ConfigService? = null
    private var rootConfig: RootConfig? = null
    private var scanListener = DeviceScanListenerImplementation()
    @Throws(ServletException::class, IOException::class)
    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger)
        if (pathAndQueryString != null) {
            setConfigAccess()
            val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
            val driversList: MutableList<String> = ArrayList()
            val driverConfigList: Collection<DriverConfig> = rootConfig.drivers
            for (drv in driverConfigList) {
                driversList.add(drv.id)
            }
            val json = ToJson()
            if (pathInfo == "/") {
                json.addStringList(Const.DRIVERS, driversList)
            } else {
                val pathInfoArray = ServletLib.getPathInfoArray(pathInfo)
                val driverID = pathInfoArray!![0]!!.replace("/", "")
                val driverChannelsList: List<Channel>
                val driverDevicesList: MutableList<String> = ArrayList()
                if (driversList.contains(driverID)) {
                    val channelConfigList: MutableCollection<ChannelConfig> = ArrayList()
                    val deviceConfigList: Collection<DeviceConfig>
                    val drv: DriverConfig = rootConfig.getDriver(driverID)
                    deviceConfigList = drv.devices
                    setDriverDevicesListAndChannelConfigList(driverDevicesList, channelConfigList, deviceConfigList)
                    driverChannelsList = getDriverChannelList(channelConfigList)
                    response.status = HttpServletResponse.SC_OK
                    val driverIsRunning: Boolean = configService.idsOfRunningDrivers.contains(driverID)
                    if (pathInfoArray.size > 1) {
                        if (pathInfoArray[1].equals(Const.CHANNELS, ignoreCase = true)) {
                            json.addChannelList(driverChannelsList)
                            json.addBoolean(Const.RUNNING, driverIsRunning)
                        } else if (pathInfoArray[1].equals(Const.RUNNING, ignoreCase = true)) {
                            json.addBoolean(Const.RUNNING, driverIsRunning)
                        } else if (pathInfoArray[1].equals(Const.INFOS, ignoreCase = true)) {
                            val driverInfo: DriverInfo
                            try {
                                driverInfo = configService.getDriverInfo(driverID)
                                json.addDriverInfo(driverInfo)
                            } catch (e: DriverNotAvailableException) {
                                logger.error("Driver info not available, because driver {} doesn't exist.", driverID)
                            }
                        } else if (pathInfoArray[1].equals(Const.DEVICES, ignoreCase = true)) {
                            json.addStringList(Const.DEVICES, driverDevicesList)
                            json.addBoolean(Const.RUNNING, driverIsRunning)
                        } else if (pathInfoArray[1].equals(Const.SCAN, ignoreCase = true)) {
                            var deviceScanInfoList: MutableList<DeviceScanInfo?>? = ArrayList<DeviceScanInfo?>()
                            scanListener = DeviceScanListenerImplementation(deviceScanInfoList)
                            val settings = request.getParameter(Const.SETTINGS)
                            deviceScanInfoList = scanForAllDrivers(driverID, settings, scanListener, response)
                            json.addDeviceScanInfoList(deviceScanInfoList)
                        } else if (pathInfoArray[1].equals(Const.SCAN_PROGRESS_INFO, ignoreCase = true)) {
                            json.addDeviceScanProgressInfo(scanListener.restScanProgressInfo)
                        } else if (pathInfoArray[1].equals(
                                Const.CONFIGS,
                                ignoreCase = true
                            ) && pathInfoArray.size == 2
                        ) {
                            doGetConfigs(json, driverID, response)
                        } else if (pathInfoArray[1].equals(
                                Const.CONFIGS,
                                ignoreCase = true
                            ) && pathInfoArray.size == 3
                        ) {
                            doGetConfigField(json, driverID, pathInfoArray[2], response)
                        } else {
                            ServletLib.sendHTTPErrorAndLogDebug(
                                response, HttpServletResponse.SC_NOT_FOUND, logger,
                                REQUESTED_REST_PATH_IS_NOT_AVAILABLE, PATH_INFO, request.pathInfo
                            )
                        }
                    } else if (pathInfoArray.size == 1) {
                        json.addChannelRecordList(driverChannelsList)
                        json.addBoolean(Const.RUNNING, driverIsRunning)
                    } else {
                        ServletLib.sendHTTPErrorAndLogDebug(
                            response, HttpServletResponse.SC_NOT_FOUND, logger,
                            REQUESTED_REST_PATH_IS_NOT_AVAILABLE, PATH_INFO, request.pathInfo
                        )
                    }
                } else {
                    driverNotAvailable(response, driverID)
                }
            }
            sendJson(json, response)
        }
    }

    @Throws(ServletException::class, IOException::class)
    override fun doPut(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = GenericServlet.Companion.APPLICATION_JSON
        val pathAndQueryString = checkIfItIsACorrectRest(request, response, logger) ?: return
        setConfigAccess()
        val pathInfo = pathAndQueryString[ServletLib.PATH_ARRAY_NR]
        val pathInfoArray = ServletLib.getPathInfoArray(pathInfo)
        val driverID = pathInfoArray!![0]!!.replace("/", "")
        val json = ServletLib.getJsonText(request)
        if (pathInfoArray.size < 1) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_FOUND, logger,
                REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
            )
        } else {
            val driverConfig: DriverConfig = rootConfig.getDriver(driverID)
            if (driverConfig != null && pathInfoArray.size == 2 && pathInfoArray[1].equals(
                    Const.CONFIGS,
                    ignoreCase = true
                )
            ) {
                setAndWriteDriverConfig(driverID, response, json)
            } else if (driverConfig != null && pathInfoArray.size == 2 && pathInfoArray[1].equals(
                    Const.SCAN_INTERRUPT,
                    ignoreCase = true
                )
            ) {
                interruptScanProcess(driverID, response, json)
            } else {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
                )
            }
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
            val driverID = pathInfoArray!![0]!!.replace("/", "")
            val json = ServletLib.getJsonText(request)
            if (pathInfoArray.size != 1) {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    REQUESTED_REST_PATH_IS_NOT_AVAILABLE, GenericServlet.Companion.REST_PATH, request.pathInfo
                )
            } else {
                try {
                    rootConfig.addDriver(driverID)
                    configService.config = rootConfig
                    configService.writeConfigToFile()
                    setAndWriteDriverConfig(driverID, response, json)
                } catch (e: IdCollisionException) {
                    ServletLib.sendHTTPErrorAndLogDebug(
                        response, HttpServletResponse.SC_CONFLICT, logger,
                        "Driver \"$driverID\" already exist"
                    )
                } catch (e: ConfigWriteException) {
                    ServletLib.sendHTTPErrorAndLogErr(
                        response, HttpServletResponse.SC_CONFLICT, logger,
                        "Could not write driver \"", driverID, "\"."
                    )
                    e.printStackTrace()
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
            var driverID: String? = null
            val pathInfoArray = ServletLib.getPathInfoArray(pathInfo)
            driverID = pathInfoArray!![0]!!.replace("/", "")
            val driverConfig: DriverConfig = rootConfig.getDriver(driverID)
            if (pathInfoArray.size != 1) {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    REQUESTED_REST_PATH_IS_NOT_AVAILABLE, PATH_INFO, request.pathInfo
                )
            } else if (driverConfig == null) {
                ServletLib.sendHTTPErrorAndLogDebug(
                    response, HttpServletResponse.SC_NOT_FOUND, logger,
                    "Driver \"$driverID\" does not exist."
                )
            } else {
                try {
                    driverConfig.delete()
                    configService.config = rootConfig
                    configService.writeConfigToFile()
                    if (rootConfig.getDriver(driverID) == null) {
                        response.status = HttpServletResponse.SC_OK
                    } else {
                        ServletLib.sendHTTPErrorAndLogErr(
                            response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            logger, "Not able to delete driver ", driverID
                        )
                    }
                } catch (e: ConfigWriteException) {
                    ServletLib.sendHTTPErrorAndLogErr(
                        response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, logger,
                        "Not able to write into config."
                    )
                    e.printStackTrace()
                }
            }
        }
    }

    private fun setAndWriteDriverConfig(driverID: String, response: HttpServletResponse, json: String?): Boolean {
        var ok = false
        try {
            val driverConfig: DriverConfig = rootConfig.getDriver(driverID)
            if (driverConfig != null) {
                try {
                    val fromJson = FromJson(json)
                    fromJson.setDriverConfig(driverConfig, driverID)
                } catch (e: IdCollisionException) {
                }
                configService.config = rootConfig
                configService.writeConfigToFile()
                response.status = HttpServletResponse.SC_OK
                ok = true
            } else {
                ServletLib.sendHTTPErrorAndLogErr(
                    response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, logger,
                    "Not able to access to driver ", driverID
                )
            }
        } catch (e: JsonSyntaxException) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_CONFLICT, logger,
                "JSON syntax is wrong."
            )
        } catch (e: MissingJsonObjectException) {
            ServletLib.sendHTTPErrorAndLogDebug(response, HttpServletResponse.SC_NOT_FOUND, logger, e.message)
        } catch (e: ConfigWriteException) {
            ServletLib.sendHTTPErrorAndLogErr(
                response, HttpServletResponse.SC_CONFLICT, logger,
                "Could not write driver \"", driverID, "\"."
            )
            logger.debug(e.message)
        } catch (e: RestConfigIsNotCorrectException) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_ACCEPTABLE, logger,
                "Not correct formed driver config json.", " JSON = ", json
            )
        } catch (e: IllegalStateException) {
            ServletLib.sendHTTPErrorAndLogDebug(response, HttpServletResponse.SC_CONFLICT, logger, e.message)
        }
        return ok
    }

    private fun doGetConfigs(json: ToJson, drvId: String, response: HttpServletResponse) {
        val driverConfig: DriverConfig = rootConfig.getDriver(drvId)
        if (driverConfig != null) {
            json.addDriverConfig(driverConfig)
        } else {
            driverNotAvailable(response, drvId)
        }
    }

    @Throws(IOException::class)
    private fun doGetConfigField(json: ToJson, drvId: String, configField: String?, response: HttpServletResponse) {
        val driverConfig: DriverConfig = rootConfig.getDriver(drvId)
        if (driverConfig != null) {
            val jsoConfigAll: JsonObject = ToJson.getDriverConfigAsJsonObject(driverConfig)
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
            driverNotAvailable(response, drvId)
        }
    }

    private fun interruptScanProcess(driverID: String, response: HttpServletResponse, json: String?) {
        try {
            configService.interruptDeviceScan(driverID)
            response.status = HttpServletResponse.SC_OK
        } catch (e: UnsupportedOperationException) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, logger,
                "Driver does not support scan interrupting.", DRIVER_ID, driverID
            )
        } catch (e: DriverNotAvailableException) {
            driverNotAvailable(response, driverID)
        }
    }

    private fun scanForAllDrivers(
        driverID: String, settings: String,
        scanListener: DeviceScanListenerImplementation, response: HttpServletResponse
    ): MutableList<DeviceScanInfo?>? {
        var scannedDevicesList: MutableList<DeviceScanInfo?>? = ArrayList<DeviceScanInfo?>()
        try {
            configService.scanForDevices(driverID, settings, scanListener)
            scannedDevicesList = scanListener.scannedDevicesList
        } catch (e: UnsupportedOperationException) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, logger,
                "Driver does not support scanning.", DRIVER_ID, driverID
            )
        } catch (e: DriverNotAvailableException) {
            driverNotAvailable(response, driverID)
        }
        return scannedDevicesList
    }

    private fun getDriverChannelList(channelConfig: Collection<ChannelConfig>): List<Channel> {
        val driverChannels: MutableList<Channel> = ArrayList()
        for (chCf in channelConfig) {
            driverChannels.add(dataAccess.getChannel(chCf.id))
        }
        return driverChannels
    }

    private fun setDriverDevicesListAndChannelConfigList(
        driverDevices: MutableList<String>,
        channelConfig: MutableCollection<ChannelConfig>, deviceConfig: Collection<DeviceConfig>
    ) {
        for (dvCf in deviceConfig) {
            driverDevices.add(dvCf.id)
            channelConfig.addAll(dvCf.channels)
        }
    }

    private fun setConfigAccess() {
        this.dataAccess = handleDataAccessService(null)
        this.configService = handleConfigService(null)
        this.rootConfig = handleRootConfig(null)
    }

    companion object {
        private const val REQUESTED_REST_PATH_IS_NOT_AVAILABLE = "Requested rest path is not available."
        private const val DRIVER_ID = " driverID = "
        private const val PATH_INFO = " Path Info = "
        private const val serialVersionUID = -2223282905555493215L
        private val logger = LoggerFactory.getLogger(DriverResourceServlet::class.java)
        private fun driverNotAvailable(response: HttpServletResponse, driverID: String) {
            ServletLib.sendHTTPErrorAndLogDebug(
                response, HttpServletResponse.SC_NOT_FOUND, logger,
                "Requested rest driver is not available.", DRIVER_ID, driverID
            )
        }
    }
}
