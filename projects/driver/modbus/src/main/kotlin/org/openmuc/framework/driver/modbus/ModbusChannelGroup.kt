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
package org.openmuc.framework.driver.modbus

import com.ghgande.j2mod.modbus.procimg.InputRegister
import com.ghgande.j2mod.modbus.util.BitVector
import org.openmuc.framework.data.BooleanValue
import org.openmuc.framework.data.Record
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.slf4j.LoggerFactory

/**
 * Represents a group of channels which is used for a multiple read request
 */
class ModbusChannelGroup(val samplingGroup: String, val channels: ArrayList<ModbusChannel?>) {
    var primaryTable: EPrimaryTable? = null
        private set

    /** Start address to read from  */
    var startAddress = 0
        private set

    /** Number of Registers/Coils to be read from startAddress  */
    var count = 0
        private set
    var unitId = 0
        private set
    var functionCode: EFunctionCode? = null
        private set

    init {
        setPrimaryTable()
        setUnitId()
        setStartAddress()
        setCount()
        setFunctionCode()
    }

    val info: String
        get() {
            var info = "SamplingGroup: '$samplingGroup' Channels: "
            for (channel in channels) {
                info += channel.getStartAddress().toString() + ":" + channel.getDatatype() + ", "
            }
            return info
        }

    private fun setFunctionCode() {
        var init = false
        var tempFunctionCode: EFunctionCode? = null
        for (channel in channels) {
            if (!init) {
                tempFunctionCode = channel.getFunctionCode()
                init = true
            } else {
                if (tempFunctionCode != channel.getFunctionCode()) {
                    throw RuntimeException(
                        "FunctionCodes of all channels within the samplingGroup '"
                                + samplingGroup + "' are not equal! Change your openmuc config."
                    )
                }
            }
        }
        functionCode = tempFunctionCode
    }

    /**
     * Checks if the primary table of all channels of the sampling group is equal and sets the value for the channel
     * group.
     */
    private fun setPrimaryTable() {
        var init = false
        var tempPrimaryTable: EPrimaryTable? = null
        for (channel in channels) {
            if (!init) {
                tempPrimaryTable = channel.getPrimaryTable()
                init = true
            } else {
                if (tempPrimaryTable != channel.getPrimaryTable()) {
                    throw RuntimeException(
                        "Primary tables of all channels within the samplingGroup '"
                                + samplingGroup + "' are not equal! Change your openmuc config."
                    )
                }
            }
        }
        primaryTable = tempPrimaryTable
    }

    private fun setUnitId() {
        var idOfFirstChannel = INVALID
        for (channel in channels) {
            if (idOfFirstChannel == INVALID) {
                idOfFirstChannel = channel.getUnitId()
            } else {
                if (channel.getUnitId() != idOfFirstChannel) {

                    // TODO ???
                    // channel 1 device 1 = unitId 1
                    // channel 1 device 2 = unitId 2
                    // Does openmuc calls the read method for channels of different devices?
                    // If so, then the check for UnitID has to be modified. Only channels of the same device
                    // need to have the same unitId...
                    throw RuntimeException(
                        "UnitIds of all channels within the samplingGroup '" + samplingGroup
                                + "' are not equal! Change your openmuc config."
                    )
                }
            }
        }
        unitId = idOfFirstChannel
    }

    /**
     * StartAddress is the smallest channel address of the group
     */
    private fun setStartAddress() {
        startAddress = INVALID
        for (channel in channels) {
            startAddress = if (startAddress == INVALID) {
                channel.getStartAddress()
            } else {
                Math.min(startAddress, channel.getStartAddress())
            }
        }
    }

    /**
     *
     */
    private fun setCount() {
        var maximumAddress = startAddress
        for (channel in channels) {
            maximumAddress = Math.max(maximumAddress, channel.getStartAddress() + channel.getCount())
        }
        count = maximumAddress - startAddress
    }

    fun setChannelValues(inputRegisters: Array<InputRegister?>?, containers: List<ChannelRecordContainer>) {
        for (channel in channels) {
            // determine start index of the registers which contain the values of the channel
            val registerIndex = channel.getStartAddress() - startAddress
            // create a temporary register array
            val registers = arrayOfNulls<InputRegister>(channel.getCount())
            // copy relevant registers for the channel
            System.arraycopy(inputRegisters, registerIndex, registers, 0, channel.getCount())

            // now we have a register array which contains the value of the channel
            val container = searchContainer(channel.getChannelAddress(), containers)
            val receiveTime = System.currentTimeMillis()
            val value = ModbusDriverUtil.getRegistersValue(registers, channel.getDatatype())
            if (logger.isTraceEnabled) {
                logger.trace("response value channel " + channel.getChannelAddress() + ": " + value.toString())
            }
            container.setRecord(Record(value, receiveTime))
        }
    }

    fun setChannelValues(bitVector: BitVector, containers: List<ChannelRecordContainer>) {
        for (channel in channels) {
            val receiveTime = System.currentTimeMillis()

            // determine start index of the registers which contain the values of the channel
            val index = channel.getStartAddress() - startAddress
            val value = BooleanValue(bitVector.getBit(index))
            val container = searchContainer(channel.getChannelAddress(), containers)
            container.setRecord(Record(value, receiveTime))
        }
    }

    private fun searchContainer(
        channelAddress: String?,
        containers: List<ChannelRecordContainer>
    ): ChannelRecordContainer {
        for (container in containers) {
            if (container.channelAddress.equals(channelAddress, ignoreCase = true)) {
                return container
            }
        }
        throw RuntimeException("No ChannelRecordContainer found for channelAddress $channelAddress")
    }

    val isEmpty: Boolean
        get() {
            var result = true
            if (channels.size != 0) {
                result = false
            }
            return result
        }

    companion object {
        private val logger = LoggerFactory.getLogger(ModbusChannelGroup::class.java)
        private const val INVALID = -1
    }
}
