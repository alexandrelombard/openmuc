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

import org.openmuc.framework.config.*
import org.openmuc.framework.driver.spi.*
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.slf4j.LoggerFactory
import tuwien.auto.calimero.log.LogManager
import java.io.IOException

@Component
class KnxDriver : DriverService {
    @Activate
    protected fun activate(context: ComponentContext?) {
        if (logger.isDebugEnabled) {
            LogManager.getManager().addWriter("", KnxLogWriter()) // Add calimero logger
        }
    }

    @Deactivate
    protected fun deactivate(context: ComponentContext?) {
        logger.debug("Deactivated KNX Driver")
    }

    override val info: DriverInfo
        get() = Companion.info

    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ScanInterruptedException::class
    )
    override fun scanForDevices(settings: String, listener: DriverDeviceScanListener?) {
        var args: Array<String>? = null
        logger.debug("settings = $settings")
        if (settings.isNotEmpty()) {
            args = settings.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (settings.length == 2) {
                logger.debug("args[0] = " + args[0])
                logger.debug("args[1] = " + args[1])
            }
        }
        if (args != null && args.size > 0) {
            var natAware = false
            var mcastResponse = false
            if (args.size > 1) {
                logger.debug("Applying settings: " + args[1])
                natAware = args[1].contains("nat")
                mcastResponse = args[1].contains("mcast")
            }
            val discover: KnxIpDiscover
            try {
                discover = KnxIpDiscover(args[0], natAware, mcastResponse)
                discover.startSearch(0, listener)
            } catch (e: IOException) {
                throw ScanException(e)
            }
        }
    }

    @Throws(UnsupportedOperationException::class)
    override fun interruptDeviceScan() {
        throw UnsupportedOperationException()
    }

    @Throws(ArgumentSyntaxException::class, ConnectionException::class)
    override fun connect(deviceAddress: String, settings: String): Connection {
        return KnxConnection(deviceAddress, settings, timeout)
    }

    companion object {
        const val ADDRESS_SCHEME_KNXIP = "knxip"
        private val logger = LoggerFactory.getLogger(KnxDriver::class.java)
        const val timeout = 10000
        private val info = DriverInfo( // id*/
            "knx",  // description
            "Driver to read and write KNX groupaddresses.",  // device address
            ADDRESS_SCHEME_KNXIP + "://<host_ip>[:<port>];" + ADDRESS_SCHEME_KNXIP + "://<device_ip>[:<port>]",  // settings
            "[Address=<Individual KNX address (e. g. 2.6.52)>];[SerialNumber=<Serial number>]",  // channel address
            "<Group_Adress>:<DPT_ID>",  // device scan settings
            "<host_ip>;<mcast> for multicast scan or <host_ip>;<nat> for NAT scan"
        )
    }
}
