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

import org.slf4j.LoggerFactory
import java.util.*

class ModbusChannel(channelAddress: String, accessFlag: EAccess) {
    /** Contains values to define the access method of the channel  */
    enum class EAccess {
        READ, WRITE
    }

    /** Start address to read or write from  */
    var startAddress = 0
        private set

    /** Number of registers/coils to be read or written  */
    var count = 0
        private set

    /** Used to determine the register/coil count  */
    var datatype: EDatatype = EDatatype.BOOLEAN
        private set

    /** Used to determine the appropriate transaction method  */
    var functionCode: EFunctionCode? = null
        private set

    /** Specifies whether the channel should be read or written  */
    var accessFlag: EAccess? = null
        private set

    /**  */
    var primaryTable: EPrimaryTable? = null
        private set
    val channelAddress: String

    /**
     * Is needed when the target device is behind a gateway/bridge which connects Modbus TCP with Modbus+ or Modbus
     * Serial. Note: Some devices requires the unitId even if they are in a Modbus TCP Network and have their own IP.
     * "Like when a device ties its Ethernet and RS-485 ports together and broadcasts whatever shows up on one side,
     * onto the other if the packet isn't for themselves, but isn't "just a bridge"."
     */
    var unitId = 0
        private set

    init {
        var channelAddress = channelAddress
        channelAddress = channelAddress.lowercase(Locale.getDefault())
        val addressParams = decomposeAddress(channelAddress)
        if (checkAddressParams(addressParams)) {
            this.channelAddress = channelAddress
            setUnitId(addressParams[UNITID])
            setPrimaryTable(addressParams[PRIMARYTABLE])
            setStartAddress(addressParams[ADDRESS])
            setDatatype(addressParams[DATATYPE])
            setCount(addressParams[DATATYPE])
            setAccessFlag(accessFlag)
            setFunctionCode()
        } else {
            throw RuntimeException("Address initialization faild! Invalid parameters used: $channelAddress")
        }
    }

    fun update(access: EAccess) {
        setAccessFlag(access)
        setFunctionCode()
    }

    private fun decomposeAddress(channelAddress: String): Array<String> {
        val addressParams =
            channelAddress.lowercase(Locale.getDefault()).split(":".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        return if (addressParams.size == 3) {
            arrayOf("", addressParams[0], addressParams[1], addressParams[2])
        } else if (addressParams.size == 4) {
            arrayOf(addressParams[0], addressParams[1], addressParams[2], addressParams[3])
        } else {
            arrayOf()
        }
    }

    private fun checkAddressParams(params: Array<String>): Boolean {
        var returnValue = false
        if (params[UNITID].matches("\\d+?".toRegex()) || params[UNITID] == ""
            && EPrimaryTable.Companion.isValidValue(params[PRIMARYTABLE]) && params[ADDRESS]
                .matches("\\d+?".toRegex())
            && EDatatype.Companion.isValid(params[DATATYPE])
        ) {
            returnValue = true
        }
        return returnValue
    }

    private fun setFunctionCode() {
        if (accessFlag == EAccess.READ) {
            setFunctionCodeForReading()
        } else {
            setFunctionCodeForWriting()
        }
    }

    /**
     * Matches data type with function code
     *
     * @throws Exception
     */
    private fun setFunctionCodeForReading() {
        when (datatype) {
            EDatatype.BOOLEAN -> if (primaryTable == EPrimaryTable.COILS) {
                functionCode = EFunctionCode.FC_01_READ_COILS
            } else if (primaryTable == EPrimaryTable.DISCRETE_INPUTS) {
                functionCode = EFunctionCode.FC_02_READ_DISCRETE_INPUTS
            } else {
                invalidReadAddressParameterCombination()
            }

            EDatatype.SHORT, EDatatype.INT16, EDatatype.INT32, EDatatype.UINT16, EDatatype.UINT32, EDatatype.FLOAT, EDatatype.DOUBLE, EDatatype.LONG, EDatatype.BYTEARRAY -> if (primaryTable == EPrimaryTable.HOLDING_REGISTERS) {
                functionCode = EFunctionCode.FC_03_READ_HOLDING_REGISTERS
            } else if (primaryTable == EPrimaryTable.INPUT_REGISTERS) {
                functionCode = EFunctionCode.FC_04_READ_INPUT_REGISTERS
            } else {
                invalidReadAddressParameterCombination()
            }

            else -> throw RuntimeException("read: Datatype " + datatype.toString() + " not supported yet!")
        }
    }

    private fun setFunctionCodeForWriting() {
        when (datatype) {
            EDatatype.BOOLEAN -> if (primaryTable == EPrimaryTable.COILS) {
                functionCode = EFunctionCode.FC_05_WRITE_SINGLE_COIL
            } else {
                invalidWriteAddressParameterCombination()
            }

            EDatatype.SHORT, EDatatype.INT16, EDatatype.INT32, EDatatype.UINT16, EDatatype.UINT32, EDatatype.FLOAT, EDatatype.DOUBLE, EDatatype.LONG, EDatatype.BYTEARRAY -> if (primaryTable == EPrimaryTable.HOLDING_REGISTERS) {
                functionCode = EFunctionCode.FC_16_WRITE_MULTIPLE_REGISTERS
            } else {
                invalidWriteAddressParameterCombination()
            }

            else -> throw RuntimeException("write: Datatype " + datatype.toString() + " not supported yet!")
        }
    }

    private fun invalidWriteAddressParameterCombination() {
        throw RuntimeException(
            """Invalid channel address parameter combination for writing. 
 Datatype: ${datatype.toString().uppercase(Locale.getDefault())} PrimaryTable: ${
                primaryTable.toString().uppercase(Locale.getDefault())
            }"""
        )
    }

    private fun invalidReadAddressParameterCombination() {
        throw RuntimeException(
            """Invalid channel address parameter combination for reading. 
 Datatype: ${datatype.toString().uppercase(Locale.getDefault())} PrimaryTable: ${
                primaryTable.toString().uppercase(Locale.getDefault())
            }"""
        )
    }

    private fun setStartAddress(startAddress: String) {
        this.startAddress = startAddress.toInt()
    }

    private fun setDatatype(datatype: String) {
        this.datatype = EDatatype.getEnum(datatype)
    }

    private fun setUnitId(unitId: String) {
        if (unitId == "") {
            this.unitId = IGNORE_UNIT_ID
        } else {
            this.unitId = unitId.toInt()
        }
    }

    private fun setPrimaryTable(primaryTable: String) {
        this.primaryTable = EPrimaryTable.getEnumfromString(primaryTable)
    }

    private fun setCount(addressParamDatatyp: String) {
        if (datatype == EDatatype.BYTEARRAY) {
            // TODO check syntax first? bytearray[n]

            // special handling of the BYTEARRAY datatyp
            val datatypParts = addressParamDatatyp.split("\\[|\\]".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray() // split string either at [ or ]
            if (datatypParts.size == 2) {
                count = datatypParts[1].toInt()
            }
        } else {
            // all other datatyps
            count = datatype.registerSize
        }
    }

    private fun setAccessFlag(accessFlag: EAccess) {
        this.accessFlag = accessFlag
    }

    override fun toString(): String {
        return ("channeladdress: " + unitId + ":" + primaryTable.toString() + ":" + startAddress + ":"
                + datatype.toString())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ModbusDriver::class.java)

        /** A Parameter of the channel address  */
        const val IGNORE_UNIT_ID = -1

        /** A Parameter of the channel address  */
        private const val UNITID = 0

        /** A Parameter of the channel address  */
        private const val PRIMARYTABLE = 1

        /** A Parameter of the channel address  */
        private const val ADDRESS = 2

        /** A Parameter of the channel address  */
        private const val DATATYPE = 3
    }
}
