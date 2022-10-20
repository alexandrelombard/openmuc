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
package org.openmuc.framework.driver.aggregator.types

import org.openmuc.framework.data.Record
import org.openmuc.framework.dataaccess.DataAccessService
import org.openmuc.framework.driver.aggregator.*

class AverageAggregation(simpleAddress: ChannelAddress, dataAccessService: DataAccessService) :
    AggregatorChannel(simpleAddress, dataAccessService) {
    @Throws(AggregationException::class)
    override fun aggregate(currentTimestamp: Long, endTimestamp: Long): Double {
        return try {
            val recordList = getLoggedRecords(currentTimestamp, endTimestamp)
            calcAvgOf(recordList)
        } catch (e: AggregationException) {
            throw e
        } catch (e: Exception) {
            throw AggregationException(e.message ?: "")
        }
    }

    companion object {
        /**
         * Calculates the average of the all records
         */
        @Throws(AggregationException::class)
        private fun calcAvgOf(recordList: List<Record?>?): Double {
            val sum = calcSumOf(recordList)
            return sum / recordList!!.size
        }

        private fun calcSumOf(recordList: List<Record?>?): Double {
            var sum = 0.0
            for (record in recordList!!) {
                sum += record!!.value!!.asDouble()
            }
            return sum
        }
    }
}
