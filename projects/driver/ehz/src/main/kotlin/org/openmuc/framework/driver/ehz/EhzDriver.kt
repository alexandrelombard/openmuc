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
package org.openmuc.framework.driver.ehz

import org.openmuc.framework.config.*
import org.openmuc.framework.driver.spi.*
import org.openmuc.jrxtx.SerialPortBuilder
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URISyntaxException

@Component
class EhzDriver : DriverService {
    private var interruptScan = false
    override val info: DriverInfo
        get() = Companion.info

    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ScanInterruptedException::class
    )
    override fun scanForDevices(settings: String?, listener: DriverDeviceScanListener?) {
        val serialPortNames = SerialPortBuilder.getSerialPortNames()
        var i = 0.0
        val progress = 0
        val numberOfPorts = serialPortNames.size
        interruptScan = false
        listener!!.scanProgressUpdate(progress)
        for (spName in serialPortNames) {
            logger.trace("Searching for device at {}", spName)
            var deviceAddress: URI?
            deviceAddress = try {
                checkForIEC(spName)
            } catch (e: ConnectionException) {
                logger.trace("{} is no IEC 62056-21 device", spName)
                continue
            } catch (e: URISyntaxException) {
                logger.trace("{} is no IEC 62056-21 device", spName)
                continue
            }
            addDevice(listener, spName, deviceAddress)
            if (interruptScan) {
                return
            }
            if (deviceAddress == null) {
                updateProgress(listener, i + 0.5, numberOfPorts)
                deviceAddress = checkForSML(spName, deviceAddress)
            }
            addDevice(listener, spName, deviceAddress)
            if (interruptScan) {
                return
            }
            updateProgress(listener, ++i, numberOfPorts)
        }
    }

    private fun updateProgress(listener: DriverDeviceScanListener?, i: Double, numberOfPorts: Int) {
        val progress = (i * 100).toInt() / numberOfPorts
        listener!!.scanProgressUpdate(progress)
    }

    private fun addDevice(listener: DriverDeviceScanListener?, spName: String, deviceAddress: URI?) {
        if (deviceAddress != null) {
            listener!!.deviceFound(DeviceScanInfo(deviceAddress.toString(), "", ""))
        } else {
            logger.info("No ehz device found at {}", spName)
        }
    }

    private fun checkForSML(spName: String, deviceAddress: URI?): URI? {
        var deviceAddress = deviceAddress
        val connection: GeneralConnection
        try {
            connection = SmlConnection(spName)
            if (connection.works()) {
                logger.info("Found sml device at {}", spName)
                deviceAddress = URI(ADDR_SML + "://" + spName)
            }
            connection.disconnect()
        } catch (e: ConnectionException) {
            logger.trace("{} is no SML device", spName)
        } catch (e: URISyntaxException) {
            logger.trace("{} is no SML device", spName)
        }
        return deviceAddress
    }

    @Throws(ConnectionException::class, URISyntaxException::class)
    private fun checkForIEC(spName: String): URI? {
        var deviceAddress: URI? = null
        val connection: GeneralConnection = IecConnection(spName, 2000)
        if (connection.works()) {
            logger.info("Found iec device at {}", spName)
            deviceAddress = URI(ADDR_IEC + "://" + spName)
        }
        connection.disconnect()
        return deviceAddress
    }

    @Throws(UnsupportedOperationException::class)
    override fun interruptDeviceScan() {
        interruptScan = true
    }

    @Throws(ArgumentSyntaxException::class, ConnectionException::class)
    override fun connect(deviceAddress: String?, settings: String?): Connection? {
        logger.trace("Trying to connect to {}", deviceAddress)
        try {
            val device = URI(deviceAddress)
            if (device.scheme == ADDR_IEC) {
                logger.trace("Connecting to iec device")
                return IecConnection(device.path, GeneralConnection.Companion.TIMEOUT)
            } else if (device.scheme == ADDR_SML) {
                logger.trace("Connecting to sml device")
                return SmlConnection(device.path)
            }
            throw ConnectionException("Unrecognized address scheme " + device.scheme)
        } catch (e: URISyntaxException) {
            throw ConnectionException(e)
        }
    }

    companion object {
        const val ID = "ehz"
        private val logger = LoggerFactory.getLogger(EhzDriver::class.java)
        private const val ADDR_IEC = "iec"
        private const val ADDR_SML = "sml"
        private val info = DriverInfo(
            ID,  // description
            "Driver for IEC 62056-21 and SML.",  // device address
            "iec://<serial_device> or sml://<serial_device> e.g.: sml:///dev/ttyUSB0 or sml://COM3",  // parameters
            "N.A.",  // channel address
            "e.g.: 0100010800FF",  // device scan settings
            "N.A."
        )
    }
}
