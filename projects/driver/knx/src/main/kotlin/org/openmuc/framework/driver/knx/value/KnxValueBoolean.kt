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

import org.openmuc.framework.data.BooleanValue
import org.openmuc.framework.data.Value
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import tuwien.auto.calimero.dptxlator.DPTXlatorBoolean
import tuwien.auto.calimero.exception.KNXFormatException

class KnxValueBoolean(dptID: String?) : KnxValue() {
    init {
        dptXlator = DPTXlatorBoolean(dptID)
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.openmuc.framework.driver.knx.value.KnxValue#getOpenMucValue()
     *//*
     * (non-Javadoc)
     * 
     * @see org.openmuc.framework.driver.knx.value.KnxValue#setOpenMucValue(org.openmuc.framework.data.Value)
     */
    @set:Throws(KNXFormatException::class)
    override var openMucValue: Value?
        get() = BooleanValue((dptXlator as DPTXlatorBoolean).valueBoolean)
        set(value) {
            (dptXlator as DPTXlatorBoolean).setValue(value!!.asBoolean())
        }
}
