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
package org.openmuc.framework.driver.rest

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.config.DriverInfo
import org.openmuc.framework.config.ScanException
import org.openmuc.framework.dataaccess.DataAccessService
import org.openmuc.framework.driver.spi.*
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [DriverService::class])
class RestDriverImpl : DriverService {
    private var dataAccessService: DataAccessService? = null
    @Throws(ArgumentSyntaxException::class, ConnectionException::class)
    override fun connect(deviceAddress: String, settings: String): Connection {
        var settings = settings
        val connection: RestConnection
        if (settings.isEmpty() || settings.trim { it <= ' ' }
                .isEmpty() || !settings.contains(":")) {
            throw ArgumentSyntaxException(
                "Invalid User Credentials provided in settings: " + settings
                        + ". Expected Format: username:password"
            )
        }
        return if (deviceAddress.isEmpty() || deviceAddress.trim { it <= ' ' }.isEmpty() || !deviceAddress.contains(":")
        ) {
            throw ArgumentSyntaxException(
                "Invalid address or port: " + deviceAddress
                        + ". Expected Format: https://adress:port or http://adress:port"
            )
        } else if (deviceAddress.startsWith(HTTP) || deviceAddress.startsWith(HTTPS)) {
            var checkTimestamp = false
            if (settings.startsWith("ct;")) {
                settings = settings.replaceFirst("ct;".toRegex(), "")
                checkTimestamp = true
            }
            connection =
                RestConnection(deviceAddress, settings, timeout, checkTimestamp, dataAccessService)
            connection.connect()
            connection
        } else {
            throw ConnectionException(
                "Invalid address or port: " + deviceAddress
                        + ". Expected Format: https://adress:port or http://adress:port"
            )
        }
    }

    override val info: DriverInfo
        get() = Companion.info

    @Throws(UnsupportedOperationException::class, ArgumentSyntaxException::class, ScanException::class)
    override fun scanForDevices(settings: String, listener: DriverDeviceScanListener?) {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class)
    override fun interruptDeviceScan() {
        throw UnsupportedOperationException()
    }

    @Reference
    protected fun setDataAccessService(dataAccessService: DataAccessService?) {
        this.dataAccessService = dataAccessService
    }

    protected fun unsetDataAccessService(dataAccessService: DataAccessService?) {
        this.dataAccessService = null
    }

    companion object {
        private const val timeout = 10000
        private const val ID = "rest"
        private const val DESCRIPTION =
            "Driver to connect this OpenMUC instance with another, remote OpenMUC instance with rest."
        private const val DEVICE_ADDRESS = "https://adress:port or http://adress:port"
        private const val SETTINGS =
            "<username>:<password> or ct;<username>:<password>  ct means check timestamp, only load record if timestamp changed"
        private const val CHANNEL_ADDRESS = "channelId"
        private const val DEVICE_SCAN_SETTINGS = "N.A."
        private val info = DriverInfo(
            ID, DESCRIPTION, DEVICE_ADDRESS, SETTINGS, CHANNEL_ADDRESS,
            DEVICE_SCAN_SETTINGS
        )
        private const val HTTP = "http://"
        private const val HTTPS = "https://"
    }
}
