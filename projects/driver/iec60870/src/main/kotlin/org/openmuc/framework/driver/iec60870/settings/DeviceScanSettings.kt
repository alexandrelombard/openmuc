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
package org.openmuc.framework.driver.iec60870.settings

import org.openmuc.framework.config.ArgumentSyntaxException
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.UnknownHostException
import java.text.MessageFormat

class DeviceScanSettings(deviceScanSettings: String) : GenericSetting() {
    protected var host_address: InetAddress? = null
    protected var port = 2404
    protected var common_address = 1

    protected enum class Option(
        private val prefix: String,
        private val type: Class<*>,
        private val mandatory: Boolean
    ) : OptionI {
        PORT("p", Int::class.java, false),
        HOST_ADDRESS("h", InetAddress::class.java, false),
        COMMON_ADDRESS("ca", Int::class.java, false);

        override fun prefix(): String {
            return prefix
        }

        override fun type(): Class<*> {
            return type
        }

        override fun mandatory(): Boolean {
            return mandatory
        }
    }

    init {
        val addressLength = parseFields(deviceScanSettings!!, Option::class.java)
        if (addressLength == 0) {
            logger.info(
                MessageFormat.format(
                    "No device address set in configuration, default values will be used: host address = localhost; port = {0}",
                    port
                )
            )
        }
        if (host_address == null) {
            host_address = try {
                InetAddress.getLocalHost()
            } catch (e: UnknownHostException) {
                throw ArgumentSyntaxException("Could not set default host address: localhost")
            }
        }
    }

    fun hostAddress(): InetAddress? {
        return host_address
    }

    fun port(): Int {
        return port
    }

    fun commonAddress(): Int {
        return common_address
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeviceScanSettings::class.java)
    }
}
