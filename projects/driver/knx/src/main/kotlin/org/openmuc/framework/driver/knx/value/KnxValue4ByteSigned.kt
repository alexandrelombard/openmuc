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

import org.openmuc.framework.data.IntValue
import org.openmuc.framework.data.Value
import tuwien.auto.calimero.dptxlator.DPTXlator
import tuwien.auto.calimero.dptxlator.DPTXlator4ByteSigned
import tuwien.auto.calimero.exception.KNXFormatException

class KnxValue4ByteSigned(dptID: String?) : KnxValue() {
    override var dptXlator: DPTXlator = DPTXlator4ByteSigned(dptID)

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
    override var openMucValue: Value
        get() = IntValue((dptXlator as DPTXlator4ByteSigned).valueSigned)
        set(value) {
            (dptXlator as DPTXlator4ByteSigned).setValue(value!!.asInt())
        }
}
