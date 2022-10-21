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
package org.openmuc.framework.driver.csv

import org.openmuc.framework.config.*
import org.openmuc.framework.driver.csv.settings.DeviceScanSettings
import org.openmuc.framework.driver.csv.settings.DeviceSettings
import org.openmuc.framework.driver.csv.settings.GenericSetting
import org.openmuc.framework.driver.spi.*
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

/**
 * Driver to read data from CSV file.
 *
 *
 * Three sampling modes are available:
 *
 *  * LINE: starts from begin of file. With every sampling it reads the next line. Timestamps ignored
 *  * UNIXTIMESTAMP: With every sampling it reads the line with the closest unix timestamp regarding to sampling
 * timestamp
 *  * HHMMSS: With every sampling it reads the line with the closest time HHMMSS regarding to sampling timestamp
 *
 */
@Component
class CsvDriver : DriverService {
    private var isDeviceScanInterrupted = false
    override val info: DriverInfo
        get() {
            val ID = "csv"
            val DESCRIPTION = "Driver to read out csv files."
            val DEVICE_ADDRESS = "csv file path e.g. /home/usr/bin/openmuc/csv/meter.csv"
            val DEVICE_SETTINGS: String = (GenericSetting.Companion.syntax(
                DeviceSettings::class.java
            ) + "\n samplingmode: "
                    + Arrays.toString(ESamplingMode.values()).lowercase(Locale.getDefault())
                    + " Example: samplingmode=line;rewind=true Default: " + DEFAULT_DEVICE_SETTINGS.lowercase(Locale.getDefault()))
            val CHANNEL_ADDRESS = "column header"
            val DEVICE_SCAN_SETTINGS: String = (GenericSetting.Companion.syntax(
                DeviceScanSettings::class.java
            )
                    + " path of directory containing csv files e.g: path=/home/usr/openmuc/framework/csv-driver/")
            return DriverInfo(ID, DESCRIPTION, DEVICE_ADDRESS, DEVICE_SETTINGS, CHANNEL_ADDRESS, DEVICE_SCAN_SETTINGS)
        }

    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ScanInterruptedException::class
    )
    override fun scanForDevices(settings: String, listener: DriverDeviceScanListener?) {
        logger.info("Scan for CSV files. Settings: {}", settings)
        resetDeviceScanInterrupted()
        val deviceScanSettings = DeviceScanSettings(settings)
        val listOfFiles = deviceScanSettings.path()!!.listFiles()
        if (listOfFiles != null) {
            val numberOfFiles = listOfFiles.size.toDouble()
            var fileCounter = 0.0
            var idCounter = 0
            for (file in listOfFiles) {

                // check if device scan was interrupted
                if (isDeviceScanInterrupted) {
                    break
                }
                if (file.isFile) {
                    if (file.name.endsWith("csv")) {
                        val deviceId = "csv_device_$idCounter"
                        listener!!.deviceFound(
                            DeviceScanInfo(
                                deviceId, file.absolutePath,
                                DEFAULT_DEVICE_SETTINGS.lowercase(Locale.getDefault()), file.name
                            )
                        )
                    } // else: do nothing, non csv files are ignored
                } // else: do nothing, folders are ignored
                fileCounter++
                listener!!.scanProgressUpdate((fileCounter / numberOfFiles * 100.0).toInt())
                idCounter++
            }
        }
    }

    private fun resetDeviceScanInterrupted() {
        isDeviceScanInterrupted = false
    }

    @Throws(UnsupportedOperationException::class)
    override fun interruptDeviceScan() {
        isDeviceScanInterrupted = true
    }

    @Throws(ArgumentSyntaxException::class, ConnectionException::class)
    override fun connect(deviceAddress: String, settings: String): Connection {
        val csvFile = File(deviceAddress)
        if (!csvFile.exists()) {
            throw ArgumentSyntaxException("CSV driver - file not found: $deviceAddress")
        }
        val csvConnection = CsvDeviceConnection(deviceAddress, settings)
        logger.info("Device connected: {}", deviceAddress)
        return csvConnection
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CsvDriver::class.java)
        private val DEFAULT_DEVICE_SETTINGS = (DeviceSettings.Option.SAMPLINGMODE.name + "="
                + ESamplingMode.LINE.toString())
    }
}
