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
package org.openmuc.framework.driver.knx.value

import org.openmuc.framework.data.Value
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import tuwien.auto.calimero.dptxlator.DPTXlator
import tuwien.auto.calimero.exception.KNXException
import tuwien.auto.calimero.exception.KNXFormatException

abstract class KnxValue {
    protected var dptXlator: DPTXlator? = null

    @set:Throws(KNXFormatException::class)
    var dPTValue: String?
        get() = dptXlator!!.value
        set(value) {
            dptXlator!!.value = value
        }

    fun setData(data: ByteArray?) {
        dptXlator!!.data = data
    }

    @set:Throws(KNXFormatException::class)
    abstract var openMucValue: Value?

    companion object {
        @Throws(KNXException::class)
        fun createKnxValue(dptID: String): KnxValue {
            val mainNumber = Integer.valueOf(dptID.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[0])
            return when (mainNumber) {
                1 -> KnxValueBoolean(dptID)
                2 -> KnxValue1BitControlled(dptID)
                3 -> KnxValue3BitControlled(dptID)
                5 -> KnxValue8BitUnsigned(dptID)
                7 -> KnxValue2ByteUnsigned(dptID)
                9 -> KnxValue2ByteFloat(dptID)
                10 -> KnxValueTime(dptID)
                11 -> KnxValueDate(dptID)
                12 -> KnxValue4ByteUnsigned(dptID)
                13 -> KnxValue4ByteSigned(dptID)
                14 -> KnxValue4ByteFloat(dptID)
                16 -> KnxValueString(dptID)
                19 -> KnxValueDateTime(dptID)
                else -> throw KNXException("unknown datapoint")
            }
        }
    }
}
