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
package org.openmuc.framework.driver.aggregator

import org.openmuc.framework.config.*
import org.openmuc.framework.data.DoubleValue
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.dataaccess.DataAccessService
import org.openmuc.framework.driver.spi.*
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

/**
 * Driver which performs aggregation of logged values from a channel. It uses the DriverService and the
 * DataAccessService. It is therefore a kind of OpenMUC driver/application mix. The aggregator is fully configurable
 * through the channel config file.
 *
 * **Synopsis**<br></br>
 *
 *  * **driver id** = aggregator
 *  * **channelAddress** = &lt;sourceChannelId&gt;:&lt;aggregationType&gt;[:&lt;quality&gt;]
 *
 *  * **sourceChannelId** = id of channel to be aggregated
 *  * **aggregationType**
 *
 *  * AVG: calculates the average of all values of interval (e.g. for average power)
 *  * LAST: takes the last value of interval (e.g. for energy)
 *  * DIFF: calculates difference of first and last value of interval
 *  * PULS_ENERGY,&lt;pulses per Wh&gt;,&lt;max counter&gt;: calculates energy from pulses of interval (e.g. for pulse
 * counter/meter)
 *
 *  * Example: PULSE_ENERGY,10,65535
 *
 *
 *
 *
 *
 *
 *  * **quality** = Range 0.0 - 1.0. Percentage of the expected valid/available logged records for aggregation.
 * Default value is 1.0. Example: Aggregation of 5s values to 15min. The 15min interval consists of 180 5s values. If
 * quality is 0.9 then at least 162 of 180 values must be valid/available for aggregation. NOTE: The missing/invalid
 * values could appear as block at the beginning or end of the interval, which might be problematic for some aggregation
 * types
 *
 *
 * Example: <br></br>
 * Channel A (channelA) is sampled and logged every 10 seconds.
 *
 * <pre>
 * &lt;channelid="channelA"&gt;
 * &lt;samplingInterval&gt;10s&lt;/samplingInterval&gt;
 * &lt;loggingInterval&gt;10s&lt;/loggingInterval&gt;
 * &lt;/channel&gt;
</pre> *
 *
 *
 * Now you want a channel B (channelB) which contains the same values as channel A but in a 1 minute resolution by using
 * the 'average' as aggregation type. You can achieve this by simply adding the aggregator driver to your channel config
 * file and define a the channel B as follows:
 *
 * <pre>
 * &lt;driver id="aggregator"&gt;
 * &lt;device id="aggregatordevice"&gt;
 * &lt;channelid="channelB"&gt;
 * &lt;channelAddress&gt;channelA:avg&lt;/channelAddress&gt;
 * &lt;samplingInterval&gt;60s&lt;/samplingInterval&gt;
 * &lt;loggingInterval&gt;60s&lt;/loggingInterval&gt;
 * &lt;/channel&gt;
 * &lt;/device&gt;
 * &lt;/driver&gt;
</pre> *
 *
 * The new (aggregated) channel has the id channelB. The channel address consists of the channel id of the original
 * channel and the aggregation type which is channelA:avg in this example. OpenMUC calls the read method of the
 * aggregator every minute. The aggregator then gets all logged records from channelA of the last minute, calculates the
 * average and sets this value for the record of channelB.
 *
 *
 *
 *
 * NOTE: It's recommended to specify the samplingTimeOffset for channelB. It should be between samplingIntervalB -
 * samplingIntervalA and samplingIntervalB. In this example: 50 &lt; offset &lt; 60. This constraint ensures that values
 * are AGGREGATED CORRECTLY. At hh:mm:55 the aggregator gets the logged values of channelA and at hh:mm:60 respectively
 * hh:mm:00 the aggregated value is logged.
 *
 * <pre>
 * &lt;driver id="aggregator"&gt;
 * &lt;device id="aggregatordevice"&gt;
 * &lt;channelid="channelB"&gt;
 * &lt;channelAddress&gt;channelA:avg&lt;/channelAddress&gt;
 * &lt;samplingInterval&gt;60s&lt;/samplingInterval&gt;
 * &lt;samplingTimeOffset&gt;55s&lt;/samplingTimeOffset&gt;
 * &lt;loggingInterval&gt;60s&lt;/loggingInterval&gt;
 * &lt;/channel&gt;
 * &lt;/device&gt;
 * &lt;/driver&gt;
</pre> *
 *
 *
 */
// TODO: Performance: Some checks of aggregatorUtil.getDoubleRecordValue() could be removed since
// AggregatorChannel.removeErrorRecords() removes all invalid records.
@Component(service = [DriverService::class])
class Aggregator : DriverService, Connection {
    private var dataAccessService: DataAccessService? = null

    // <id><type,param1,param2><quality>
    //
    // PULSES_ENERGY
    // - register size // needed vor overflow
    // - impulse pro wh
    //
    // [:<optionalLongSetting1>][:<optionalLongSetting2>]
    override val info: DriverInfo
        get() {
            val driverId = "aggregator"
            val description =
                "Is able to aggregate logged values of a channel and writes the aggregated value into a new channel. Different aggregation types supported."
            val deviceAddressSyntax = "not needed"
            val parametersSyntax = "not needed"
            val channelAddressSyntax = "<id of channel which should be aggregated>:<type>[:<quality>]"
            val deviceScanParametersSyntax = "not supported"
            return DriverInfo(
                driverId, description, deviceAddressSyntax, parametersSyntax, channelAddressSyntax,
                deviceScanParametersSyntax
            )
        }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun read(
        containers: List<ChannelRecordContainer?>?,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        val currentTimestamp = currentTimestamp()
        val endTimestamp = endTimestampFrom(currentTimestamp)
        for (container in containers!!) {
            val record = aggrgateRecordFor(currentTimestamp, endTimestamp, container)
            container!!.setRecord(record)
        }
        return null
    }

    private fun aggrgateRecordFor(
        currentTimestamp: Long,
        endTimestamp: Long,
        container: ChannelRecordContainer?
    ): Record {
        return try {
            val aggregatorChannel = AggregatorChannelFactory.createAggregatorChannel(
                container,
                dataAccessService
            )
            val aggregatedValue = aggregatorChannel!!.aggregate(currentTimestamp, endTimestamp)
            Record(DoubleValue(aggregatedValue), currentTimestamp)
        } catch (e: AggregationException) {
            logger.debug(
                "Unable to perform aggregation for channel {}. {}", container!!.channel!!.id,
                e.message
            )
            newRecordWithErrorFlag(currentTimestamp)
        } catch (e: Exception) {
            logger.error(
                "Unexpected Exception: Unable to perform aggregation for channel " + container!!.channel!!.id,
                e
            )
            newRecordWithErrorFlag(currentTimestamp)
        }
    }

    @Throws(ArgumentSyntaxException::class, ConnectionException::class)
    override fun connect(deviceAddress: String?, settings: String?): Connection? {

        // no connection needed so far
        return this
    }

    override fun disconnect() {

        // no disconnect needed so far
    }

    @Reference
    protected fun setDataAccessService(dataAccessService: DataAccessService?) {
        this.dataAccessService = dataAccessService
    }

    protected fun unsetDataAccessService(dataAccessService: DataAccessService?) {
        this.dataAccessService = null
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer?>?, listener: RecordsReceivedListener?) {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer?>?, containerListHandle: Any?): Any? {
        throw UnsupportedOperationException()
    }

    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ScanInterruptedException::class
    )
    override fun scanForDevices(settings: String?, listener: DriverDeviceScanListener?) {
        throw UnsupportedOperationException()
    }

    @Throws(UnsupportedOperationException::class)
    override fun interruptDeviceScan() {
        throw UnsupportedOperationException()
    }

    @Throws(
        UnsupportedOperationException::class,
        ArgumentSyntaxException::class,
        ScanException::class,
        ConnectionException::class
    )
    override fun scanForChannels(settings: String?): List<ChannelScanInfo?>? {
        throw UnsupportedOperationException()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Aggregator::class.java)

        /**
         * @return the current timestamp where milliseconds are set to 000: e.g. 10:45:00.015 --> 10:45:00.000
         */
        private fun currentTimestamp(): Long {
            return System.currentTimeMillis() / 1000 * 1000
        }

        /**
         * endTimestamp must be slightly before the currentTimestamp Example: Aggregate a channel from 10:30:00 to 10:45:00
         * to 15 min values. 10:45:00 should be the timestamp of the aggregated value therefore the aggregator has to get
         * logged values from 10:30:00,000 till 10:44:59,999. 10:45:00 is part of the next 15 min interval.
         *
         * @param currentTimestamp
         * @return endTimestamp
         */
        private fun endTimestampFrom(currentTimestamp: Long): Long {
            return currentTimestamp - 1
        }

        private fun newRecordWithErrorFlag(endTimestamp: Long): Record {
            return Record(null, endTimestamp, Flag.DRIVER_ERROR_READ_FAILURE)
        }
    }
}
