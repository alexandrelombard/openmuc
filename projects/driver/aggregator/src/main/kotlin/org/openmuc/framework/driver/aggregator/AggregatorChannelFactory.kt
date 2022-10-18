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

import org.openmuc.framework.data.Record.value
import org.openmuc.framework.dataaccess.DataAccessService
import org.openmuc.framework.driver.aggregator.types.AverageAggregation
import org.openmuc.framework.driver.aggregator.types.DiffAggregation
import org.openmuc.framework.driver.aggregator.types.LastAggregation
import org.openmuc.framework.driver.aggregator.types.PulseEnergyAggregation
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import java.util.*

/**
 * Creates a AggregatorChannel instance according to the aggregationType
 */
object AggregatorChannelFactory {
    @Throws(AggregationException::class)
    fun createAggregatorChannel(
        container: ChannelRecordContainer?,
        dataAccessService: DataAccessService?
    ): AggregatorChannel? {
        var aggregatorChannel: AggregatorChannel? = null
        val simpleAddress = createAddressFrom(container)
        aggregatorChannel = createByAddress(simpleAddress, dataAccessService)
        return aggregatorChannel
    }

    /**
     * Creates a AggregatorChannel instance according to the aggregationType
     *
     * Note: Add new types here if necessary
     *
     * @throws AggregationException
     */
    @Throws(AggregationException::class)
    private fun createByAddress(
        channelAddress: ChannelAddress,
        dataAccessService: DataAccessService?
    ): AggregatorChannel {
        val aggregationType = channelAddress.aggregationType
        return when (aggregationType) {
            AggregatorConstants.AGGREGATION_TYPE_AVG -> AverageAggregation(channelAddress, dataAccessService)
            AggregatorConstants.AGGREGATION_TYPE_LAST -> LastAggregation(channelAddress, dataAccessService)
            AggregatorConstants.AGGREGATION_TYPE_DIFF -> DiffAggregation(channelAddress, dataAccessService)
            AggregatorConstants.AGGREGATION_TYPE_PULS_ENERGY -> PulseEnergyAggregation(
                channelAddress,
                dataAccessService
            )

            else -> throw AggregationException(
                "Unsupported aggregationType: " + aggregationType + " in channel "
                        + channelAddress.container.channelAddress
            )
        }
    }

    /**
     * Returns the "type" parameter from address
     *
     * @throws WrongChannelAddressFormatException
     */
    @Throws(AggregationException::class)
    private fun createAddressFrom(container: ChannelRecordContainer?): ChannelAddress {
        val address = container!!.channelAddress
        val addressParts =
            address!!.split(AggregatorConstants.ADDRESS_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        val addressPartsLength = addressParts.size
        if (addressPartsLength > AggregatorConstants.MAX_ADDRESS_PARTS_LENGTH || addressPartsLength < AggregatorConstants.MIN_ADDRESS_PARTS_LENGTH) {
            throw AggregationException("Invalid number of channel address parameters.")
        }
        val sourceChannelId = addressParts[AggregatorConstants.ADDRESS_SOURCE_CHANNEL_ID_INDEX]
        val aggregationType =
            addressParts[AggregatorConstants.ADDRESS_AGGREGATION_TYPE_INDEX].uppercase(Locale.getDefault())
        val quality = extractQuality(addressPartsLength, addressParts)
        return ChannelAddress(container, sourceChannelId, aggregationType, quality)
    }

    private fun extractQuality(addressPartsLength: Int, addressParts: Array<String>): Double {
        var quality = -1.0
        if (addressPartsLength == AggregatorConstants.MAX_ADDRESS_PARTS_LENGTH) {
            quality = java.lang.Double.valueOf(addressParts[AggregatorConstants.ADDRESS_QUALITY_INDEX])
        }

        // use the default value if the previous parsing failed or the parsed quality value is invalid
        if (quality < 0.0 || quality > 1.0) {
            quality = AggregatorConstants.DEFAULT_QUALITY
        }
        return quality
    }
}
