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
package org.openmuc.framework.driver.modbustcp.test

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openmuc.framework.driver.modbus.EDatatype
import org.openmuc.framework.driver.modbus.EPrimaryTable
import org.openmuc.framework.driver.modbus.ModbusChannel
import org.openmuc.framework.driver.modbus.ModbusChannel.EAccess
import java.util.*

/**
 * This test class tests various parameter combination of the channel address
 *
 * @author Marco Mittelsdorf
 */
class ModbusTcpChannelTest {
    private var validAddressCombinations: ArrayList<String>? = null
    @BeforeEach
    fun setUp() {
        validAddressCombinations = ArrayList()
        validAddressCombinations!!.add("READ:COILS:BOOLEAN")
        validAddressCombinations!!.add("READ:DISCRETE_INPUTS:BOOLEAN")
        validAddressCombinations!!.add("READ:HOLDING_REGISTERS:SHORT")
        validAddressCombinations!!.add("READ:HOLDING_REGISTERS:INT16")
        validAddressCombinations!!.add("READ:HOLDING_REGISTERS:FLOAT")
        validAddressCombinations!!.add("READ:HOLDING_REGISTERS:DOUBLE")
        validAddressCombinations!!.add("READ:HOLDING_REGISTERS:LONG")
        validAddressCombinations!!.add("READ:INPUT_REGISTERS:SHORT")
        validAddressCombinations!!.add("READ:INPUT_REGISTERS:INT16")
        validAddressCombinations!!.add("READ:INPUT_REGISTERS:FLOAT")
        validAddressCombinations!!.add("READ:INPUT_REGISTERS:DOUBLE")
        validAddressCombinations!!.add("READ:INPUT_REGISTERS:LONG")
        validAddressCombinations!!.add("WRITE:COILS:BOOLEAN")
        validAddressCombinations!!.add("WRITE:HOLDING_REGISTERS:SHORT")
        validAddressCombinations!!.add("WRITE:HOLDING_REGISTERS:INT16")
        validAddressCombinations!!.add("WRITE:HOLDING_REGISTERS:FLOAT")
        validAddressCombinations!!.add("WRITE:HOLDING_REGISTERS:DOUBLE")
        validAddressCombinations!!.add("WRITE:HOLDING_REGISTERS:LONG")
    }

    @Test
    fun testValidReadAddresses() {
        val validAddresses = ArrayList<String>()
        validAddresses.add("0:DISCRETE_INPUTS:0:BOOLEAN")
        validAddresses.add("0:COILS:0:BOOLEAN")
        validAddresses.add("0:INPUT_REGISTERS:0:SHORT")
        validAddresses.add("0:INPUT_REGISTERS:0:INT16")
        validAddresses.add("0:INPUT_REGISTERS:0:FLOAT")
        validAddresses.add("0:INPUT_REGISTERS:0:DOUBLE")
        validAddresses.add("0:INPUT_REGISTERS:0:LONG")
        validAddresses.add("0:HOLDING_REGISTERS:0:SHORT")
        validAddresses.add("0:HOLDING_REGISTERS:0:INT16")
        validAddresses.add("0:HOLDING_REGISTERS:0:FLOAT")
        validAddresses.add("0:HOLDING_REGISTERS:0:DOUBLE")
        validAddresses.add("0:HOLDING_REGISTERS:0:LONG")
        for (channelAddress in validAddresses) {
            try {
                val channel = ModbusChannel(channelAddress, EAccess.READ)
                val testString = concatenate(
                    channel.accessFlag, channel.primaryTable,
                    channel.datatype
                )
                if (!validAddressCombinations!!.contains(testString.uppercase(Locale.getDefault()))) {
                    Assertions.fail<Any>(testString + "is not a valid paramaeter combination")
                } else {
                    println(channelAddress + " and resulting " + testString.uppercase(Locale.getDefault()) + " are valid.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Assertions.fail<Any>("unexpected exception")
            }
        }
    }

    @Test
    fun testValidWriteAddresses() {
        val validAddresses = ArrayList<String>()
        validAddresses.add("0:COILS:0:BOOLEAN")
        validAddresses.add("0:HOLDING_REGISTERS:0:SHORT")
        validAddresses.add("0:HOLDING_REGISTERS:0:INT16")
        validAddresses.add("0:HOLDING_REGISTERS:0:FLOAT")
        validAddresses.add("0:HOLDING_REGISTERS:0:DOUBLE")
        validAddresses.add("0:HOLDING_REGISTERS:0:LONG")
        for (channelAddress in validAddresses) {
            try {
                val channel = ModbusChannel(channelAddress, EAccess.WRITE)
                val testString = concatenate(
                    channel.accessFlag, channel.primaryTable,
                    channel.datatype
                )
                if (!validAddressCombinations!!.contains(testString.uppercase(Locale.getDefault()))) {
                    Assertions.fail<Any>(testString + "is not a valid paramaeter combination")
                } else {
                    println(channelAddress + " and resulting " + testString.uppercase(Locale.getDefault()) + " are valid.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Assertions.fail<Any>("unexpected exception")
            }
        }
    }

    private fun concatenate(a: EAccess?, p: EPrimaryTable?, d: EDatatype?): String {
        return a.toString() + ":" + p.toString() + ":" + d.toString()
    }
}
