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
import org.openmuc.framework.dataaccess.Channel
import org.openmuc.framework.dataaccess.DataAccessService
import org.openmuc.framework.driver.aggregator.*

class PulseEnergyAggregation(simpleAddress: ChannelAddress, dataAccessService: DataAccessService) :
    AggregatorChannel(simpleAddress, dataAccessService) {
    @Throws(AggregationException::class)
    override fun aggregate(currentTimestamp: Long, endTimestamp: Long): Double {
        return try {
            val recordList = getLoggedRecords(currentTimestamp, endTimestamp)
            getPulsesEnergy(channelAddress, sourceChannel!!, recordList, aggregatedChannel!!)
        } catch (e: AggregationException) {
            throw e
        } catch (e: Exception) {
            throw AggregationException(e.message ?: "")
        }
    }

    companion object {
        private const val SHORT_MAX = 65535.0
        private const val INDEX_PULSES_WH = 1
        private const val INDEX_MAX_COUNTER = 2
        @Throws(AggregationException::class, AggregationException::class)
        private fun getPulsesEnergy(
            simpleAdress: ChannelAddress,
            sourceChannel: Channel,
            recordList: List<Record?>?,
            aggregatedChannel: Channel
        ): Double {

            // parse type address params. length = 3: <type,pulsePerWh,maxCounterValue>
            val typeParams = simpleAdress.aggregationType.split(AggregatorConstants.TYPE_PARAM_SEPARATOR.toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            if (typeParams.size != 3) {
                throw AggregationException("Wrong parameters for PULSE_ENERGY.")
            }
            val pulsesPerWh = java.lang.Double.valueOf(typeParams[INDEX_PULSES_WH])
            var maxCounterValue = java.lang.Double.valueOf(typeParams[INDEX_MAX_COUNTER])
            if (pulsesPerWh <= 0) {
                throw AggregationException("Parameter pulses per Wh has to be greater then 0.")
            }
            if (maxCounterValue <= 0) {
                maxCounterValue = SHORT_MAX // if negative or null then set default value
            }
            return calcImpulsValue(
                sourceChannel, recordList, aggregatedChannel.samplingInterval.toLong(), pulsesPerWh,
                maxCounterValue
            )
        }

        @Throws(AggregationException::class)
        private fun calcImpulsValue(
            sourceChannel: Channel, recordList: List<Record?>?, samplingInterval: Long,
            pulsesPerX: Double, maxCounterValue: Double
        ): Double {
            if (recordList!!.isEmpty()) {
                throw AggregationException("List holds less than 1 records, calculation of pulses not possible.")
            }
            val lastRecord = AggregatorUtil.findLastRecordIn(recordList)
            val past = lastRecord!!.value!!.asDouble()
            val actual = retrieveLatestRecordValueWithTs(sourceChannel, lastRecord)
            return calcPulsesValue(actual, past, pulsesPerX, samplingInterval, maxCounterValue)
        }

        private fun calcPulsesValue(
            actualPulses: Double, pulsesHist: Double, pulsesPerX: Double,
            loggingInterval: Long, maxCounterValue: Double
        ): Double {
            var pulses = actualPulses - pulsesHist
            pulses = if (pulses >= 0.0) {
                actualPulses - pulsesHist
            } else {
                maxCounterValue - pulsesHist + actualPulses
            }
            return pulses / pulsesPerX * (loggingInterval / 1000.0)
        }

        private fun retrieveLatestRecordValueWithTs(srcChannel: Channel, lastRecord: Record?): Double {
            val timestamp = lastRecord!!.timestamp!!
            do {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            } while (srcChannel.latestRecord!!.timestamp == timestamp)
            return srcChannel.latestRecord!!.value!!.asDouble()
        }
    }
}
