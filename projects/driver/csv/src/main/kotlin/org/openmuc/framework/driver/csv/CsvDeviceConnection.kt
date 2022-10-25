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
import org.openmuc.framework.data.*
import org.openmuc.framework.driver.csv.CsvDeviceConnection
import org.openmuc.framework.driver.csv.channel.*
import org.openmuc.framework.driver.csv.exceptions.CsvException
import org.openmuc.framework.driver.csv.exceptions.EmptyChannelAddressException
import org.openmuc.framework.driver.csv.exceptions.NoValueReceivedYetException
import org.openmuc.framework.driver.csv.exceptions.TimeTravelException
import org.openmuc.framework.driver.csv.settings.DeviceSettings
import org.openmuc.framework.driver.spi.*
import org.slf4j.LoggerFactory
import java.util.function.Supplier

class CsvDeviceConnection private constructor(
    deviceAddress: String,
    deviceSettings: String,
    currentMillisSupplier: Supplier<Long>
) : Connection {
    /**
     * Map holds all data of the csv file
     */
    private var channelMap = hashMapOf<String, CsvChannel>()

    /**
     * Map containing 'column name' as key and 'list of all column data' as value
     */
    private val data: Map<String, List<String>>
    private val settings: DeviceSettings
    private val currentMillisSupplier: Supplier<Long>

    constructor(deviceAddress: String, deviceSettings: String) : this(
        deviceAddress,
        deviceSettings,
        Supplier<Long> { System.currentTimeMillis() }) {
    }

    init {
        settings = DeviceSettings(deviceSettings)
        data = CsvFileReader.readCsvFile(deviceAddress)
        channelMap = ChannelFactory.createChannelMap(data, settings)
        this.currentMillisSupplier = currentMillisSupplier
    }

    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ConnectionException::class
    )
    override fun scanForChannels(settings: String): List<ChannelScanInfo> {
        logger.info("Scan for channels called. Settings: $settings")
        val channels: MutableList<ChannelScanInfo> = ArrayList()
        var channelId: String
        val keys = data.keys.iterator()
        while (keys.hasNext()) {
            channelId = keys.next()
            val channel = ChannelScanInfo(channelId, channelId, ValueType.DOUBLE, 0)
            channels.add(channel)
        }
        return channels
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun read(
        containers: List<ChannelRecordContainer>,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        val samplingTime = currentMillisSupplier.get()
        for (container in containers) {
            try {
                val channel = getCsvChannel(container)
                val valueAsString = channel!!.readValue(samplingTime)
                if (container.channel?.valueType == ValueType.STRING) {
                    container.record = Record(StringValue(valueAsString), samplingTime, Flag.VALID)
                } else {
                    // in all other cases try parsing as double
                    val value = valueAsString.toDouble()
                    container.record = Record(DoubleValue(value), samplingTime, Flag.VALID)
                }
            } catch (e: EmptyChannelAddressException) {
                logger.warn("EmptyChannelAddressException: {}", e.message)
                container.record = Record(DoubleValue(Double.NaN), samplingTime, Flag.DRIVER_ERROR_CHANNEL_NOT_ACCESSIBLE)
            } catch (e: NoValueReceivedYetException) {
                logger.warn("NoValueReceivedYetException: {}", e.message)
                container.record = Record(DoubleValue(Double.NaN), samplingTime, Flag.NO_VALUE_RECEIVED_YET)
            } catch (e: TimeTravelException) {
                logger.warn("TimeTravelException: {}", e.message)
                container.record = Record(DoubleValue(Double.NaN), samplingTime, Flag.DRIVER_ERROR_READ_FAILURE)
            } catch (e: CsvException) {
                logger.error("CsvException: {}", e.message)
                container.record = Record(DoubleValue(Double.NaN), samplingTime, Flag.DRIVER_THREW_UNKNOWN_EXCEPTION)
            }
        }
        return null
    }

    @Throws(EmptyChannelAddressException::class)
    private fun getCsvChannel(container: ChannelRecordContainer?): CsvChannel? {
        val channelAddress = container!!.channelAddress
        if (channelAddress.isEmpty()) {
            throw EmptyChannelAddressException("No ChannelAddress for channel " + container.channel?.id)
        }
        return channelMap[channelAddress]
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer>, listener: RecordsReceivedListener?) {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer>, containerListHandle: Any?): Any {
        throw UnsupportedOperationException()
    }

    override fun disconnect() {
        // nothing to do here, no open file stream since complete file is read during connection.
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CsvDeviceConnection::class.java)

        /**
         * FOR TESTING ONLY (unless timeprovider is [System.currentTimeMillis] )
         */
        @JvmStatic
        @Deprecated("")
        @Throws(ConnectionException::class, ArgumentSyntaxException::class)
        fun forTesting(
            deviceAddress: String,
            deviceSettings: String,
            currentMillisSupplier: Supplier<Long>
        ): CsvDeviceConnection {
            logger.warn("USING {} IN TESTING MODE", CsvDeviceConnection::class.java.name)
            return CsvDeviceConnection(deviceAddress, deviceSettings, currentMillisSupplier)
        }
    }
}
