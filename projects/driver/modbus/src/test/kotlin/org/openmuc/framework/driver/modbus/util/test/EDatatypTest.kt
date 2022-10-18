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
package org.openmuc.framework.driver.modbus.util.test

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.openmuc.framework.driver.modbus.EDatatype.Companion.isValid
import org.openmuc.framework.driver.modbus.EDatatype.Companion.supportedDatatypes
import org.slf4j.LoggerFactory

class EDatatypTest {
    @get:Test
    val supportedDatatypesTest: Unit
        get() {
            logger.info("Supported Datatyps: " + supportedDatatypes)
            Assertions.assertTrue(true)
        }
    // valid

    // invalid
    // @Test
    @get:Test
    val isValidDatatypTest: Unit
        get() {

            // valid
            Assertions.assertTrue(isValid("int32"))
            Assertions.assertTrue(isValid("INT32"))

            // invalid
            Assertions.assertFalse(isValid("INT30"))
            Assertions.assertFalse(isValid("shorts"))
        }

    // public void modbusRegisterToValue() {
    // ModbusDriverUtil util = new ModbusDriverUtil();
    //
    // // One modbus register has the size of two Byte
    //
    // byte[] registers = new byte[] { (byte) 0xFF, (byte) 0xFF };
    //
    // Value value;
    //
    // value = util.getValueFromByteArray(registers, EDatatype.INT8);
    // logger.info(registers.toString() + " : " + value.toString());
    //
    // value = util.getValueFromByteArray(registers, EDatatype.UINT8);
    // logger.info(registers.toString() + " : " + value.toString());
    //
    // }
    companion object {
        private val logger = LoggerFactory.getLogger(EDatatypTest::class.java)

        // INT8, Byte
        private const val INT8_MIN = -128
        private const val INT8_MAX = -127
        private const val UINT8_MIN = 0
        private const val UINT8_MAX = 255
    }
}
