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
import org.openmuc.framework.data.Record.value
import org.openmuc.framework.dataaccess.DataAccessService
import org.openmuc.framework.driver.aggregator.*

class DiffAggregation(simpleAddress: ChannelAddress, dataAccessService: DataAccessService?) :
    AggregatorChannel(simpleAddress, dataAccessService) {
    @Throws(AggregationException::class)
    override fun aggregate(currentTimestamp: Long, endTimestamp: Long): Double {
        return try {
            val recordList = getLoggedRecords(currentTimestamp, endTimestamp)
            calcDiffBetweenLastAndFirstRecord(recordList)
        } catch (e: AggregationException) {
            throw e
        } catch (e: Exception) {
            throw AggregationException(e.message)
        }
    }

    companion object {
        /**
         * Calculates the difference between the last and first value of the list. <br></br>
         * Can be used to determine the energy per interval
         */
        @Throws(AggregationException::class)
        private fun calcDiffBetweenLastAndFirstRecord(recordList: List<Record?>?): Double {
            if (recordList!!.size < 2) {
                throw AggregationException("List holds less than 2 records, calculation of difference not possible.")
            }
            val end = AggregatorUtil.findLastRecordIn(recordList)!!.value!!.asDouble()
            val start = recordList[0]!!.value!!.asDouble()
            return end - start
        }
    }
}
