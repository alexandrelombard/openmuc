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
package org.openmuc.framework.driver.knx

import org.openmuc.framework.config.DeviceScanInfo
import org.openmuc.framework.driver.spi.DriverDeviceScanListener
import org.slf4j.LoggerFactory
import tuwien.auto.calimero.knxnetip.Discoverer
import tuwien.auto.calimero.knxnetip.servicetype.SearchResponse
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress

class KnxIpDiscover(interfaceAddress: String, natAware: Boolean, mcastResponse: Boolean) {
    private var discoverer: Discoverer
    private var searchResponses: Array<SearchResponse>? = null

    init {
        discoverer = try {
            val localHost = InetAddress.getByName(interfaceAddress)
            Discoverer(localHost, 0, natAware, mcastResponse)
        } catch (e: Exception) {
            logger.warn("can not create discoverer: " + e.message)
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    fun startSearch(timeout: Int, listener: DriverDeviceScanListener?) {
        var timeout = timeout
        timeout /= 1000
        if (timeout < 1) {
            timeout = DEFALUT_TIMEOUT
        }
        searchResponses = try {
            logger.debug("Starting search (timeout: " + timeout + "s)")
            discoverer.startSearch(timeout, true)
            discoverer.searchResponses
        } catch (e: Exception) {
            logger.warn("A network I/O error occurred")
            throw IOException(e)
        }
        if (searchResponses != null) {
            notifyListener(listener)
        }
    }

    private fun notifyListener(listener: DriverDeviceScanListener?) {
        for (response in searchResponses!!) {
            val deviceAddress = StringBuilder()
            deviceAddress.append(KnxDriver.Companion.ADDRESS_SCHEME_KNXIP).append("://")
            val address = response.controlEndpoint.address
            val ipAddress = address.hostAddress
            if (address is Inet6Address) {
                deviceAddress.append("[").append(ipAddress).append("]")
            } else {
                deviceAddress.append(ipAddress)
            }
            deviceAddress.append(":").append(response.controlEndpoint.port)
            val deviceDIB = response.device
            val name = deviceDIB.serialNumberString
            val description = deviceDIB.toString()
            logger.debug("Found $deviceAddress - $name - $description")
            listener!!.deviceFound(DeviceScanInfo(deviceAddress.toString(), "", description))
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KnxIpDiscover::class.java)
        private const val DEFALUT_TIMEOUT = 5
    }
}
