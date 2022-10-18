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

import com.ghgande.j2mod.modbus.ModbusException
import com.ghgande.j2mod.modbus.ModbusIOException
import com.ghgande.j2mod.modbus.ModbusSlaveException
import com.ghgande.j2mod.modbus.io.ModbusSerialTransaction
import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction
import com.ghgande.j2mod.modbus.io.ModbusTransaction
import com.ghgande.j2mod.modbus.msg.*
import com.ghgande.j2mod.modbus.procimg.InputRegister
import com.ghgande.j2mod.modbus.procimg.Register
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.ghgande.j2mod.modbus.util.BitVector
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.Value
import org.openmuc.framework.driver.modbus.ModbusChannel.EAccess
import org.openmuc.framework.driver.spi.*
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import org.slf4j.LoggerFactory
import java.util.*

abstract class ModbusConnection : Connection {
    private var transaction: ModbusTransaction? = null

    // List do manage Channel Objects to avoid to check the syntax of each channel address for every read or write
    private val modbusChannels: Hashtable<String?, ModbusChannel>
    private var requestTransactionId = 0
    private val MAX_RETRIES_FOR_JAMOD = 0
    private val MAX_RETRIES_FOR_DRIVER = 3
    @Throws(ConnectionException::class)
    abstract fun connect()

    init {
        modbusChannels = Hashtable()
    }

    @Synchronized
    fun setTransaction(transaction: ModbusTransaction?) {
        this.transaction = transaction

        // WORKAROUND: The jamod ModbusTCPTransaction.execute() tries maximum 3 times (default) to sent request and read
        // response. Problematic is a "java.net.SocketTimeoutException: Read timed out" while trying to get the
        // response. This exception is swallowed up by the library since it tries 3 times to get the data. Since the
        // jamod doesn't check the transaction id of the response, we assume that this causes the mismatch between
        // request and response.
        // To fix this we set the retries to 0 so the SocketTimeoutException isn't swallowed by the lib and we can
        // handle it, according to our needs
        // TODO: We might need to implement our own retry mechanism within the driver so that the first timeout doesn't
        // directly causes a ConnectionException
        this.transaction!!.retries = MAX_RETRIES_FOR_JAMOD
    }

    @Throws(ModbusException::class)
    fun readChannel(channel: ModbusChannel): Value? {
        if (logger.isDebugEnabled) {
            logger.debug("read channel: " + channel.channelAddress)
        }
        var value: Value? = null
        value = when (channel.functionCode) {
            EFunctionCode.FC_01_READ_COILS -> ModbusDriverUtil.getBitVectorsValue(readCoils(channel))
            EFunctionCode.FC_02_READ_DISCRETE_INPUTS -> ModbusDriverUtil.getBitVectorsValue(readDiscreteInputs(channel))
            EFunctionCode.FC_03_READ_HOLDING_REGISTERS -> ModbusDriverUtil.getRegistersValue(
                readHoldingRegisters(channel),
                channel.datatype
            )

            EFunctionCode.FC_04_READ_INPUT_REGISTERS -> ModbusDriverUtil.getRegistersValue(
                readInputRegisters(channel),
                channel.datatype
            )

            else -> throw RuntimeException("FunctionCode " + channel.functionCode + " not supported yet")
        }
        return value
    }

    @Throws(ConnectionException::class)
    fun readChannelGroupHighLevel(
        containers: List<ChannelRecordContainer>, containerListHandle: Any?,
        samplingGroup: String
    ): Any {

        // NOTE: containerListHandle is null if something changed in configuration!!!
        var channelGroup: ModbusChannelGroup? = null

        // use existing channelGroup
        if (containerListHandle != null) {
            if (containerListHandle is ModbusChannelGroup) {
                channelGroup = containerListHandle
            }
        }

        // create new channelGroup
        if (channelGroup == null) {
            val channelList = ArrayList<ModbusChannel?>()
            for (container in containers) {
                channelList.add(getModbusChannel(container.channelAddress, EAccess.READ))
            }
            channelGroup = ModbusChannelGroup(samplingGroup, channelList)
        }

        // read all channels of the group
        try {
            readChannelGroup(channelGroup, containers)
        } catch (e: ModbusIOException) {
            logger.error("ModbusIOException while reading samplingGroup:$samplingGroup", e)
            disconnect()
            throw ConnectionException(e)
        } catch (e: ModbusException) {
            logger.error("Unable to read ChannelGroup $samplingGroup", e)

            // set channel values and flag, otherwise the datamanager will throw a null pointer exception
            // and the framework collapses.
            setChannelsWithErrorFlag(containers)
        }
        return channelGroup
    }

    @Throws(ModbusException::class)
    private fun readChannelGroup(channelGroup: ModbusChannelGroup, containers: List<ChannelRecordContainer>) {
        when (channelGroup.functionCode) {
            EFunctionCode.FC_01_READ_COILS -> {
                val coils = readCoils(channelGroup)
                channelGroup.setChannelValues(coils, containers)
            }

            EFunctionCode.FC_02_READ_DISCRETE_INPUTS -> {
                val discretInput = readDiscreteInputs(channelGroup)
                channelGroup.setChannelValues(discretInput, containers)
            }

            EFunctionCode.FC_03_READ_HOLDING_REGISTERS -> {
                val registers = readHoldingRegisters(channelGroup)
                channelGroup.setChannelValues(registers, containers)
            }

            EFunctionCode.FC_04_READ_INPUT_REGISTERS -> {
                val inputRegisters = readInputRegisters(channelGroup)
                channelGroup.setChannelValues(inputRegisters, containers)
            }

            else -> throw RuntimeException("FunctionCode " + channelGroup.functionCode + " not supported yet")
        }
    }

    @Throws(ModbusException::class, RuntimeException::class)
    fun writeChannel(channel: ModbusChannel, value: Value) {
        if (logger.isDebugEnabled) {
            logger.debug("write channel: {}", channel.channelAddress)
        }
        when (channel.functionCode) {
            EFunctionCode.FC_05_WRITE_SINGLE_COIL -> writeSingleCoil(channel, value.asBoolean())
            EFunctionCode.FC_15_WRITE_MULITPLE_COILS -> writeMultipleCoils(
                channel,
                ModbusDriverUtil.getBitVectorFromByteArray(value)
            )

            EFunctionCode.FC_06_WRITE_SINGLE_REGISTER -> writeSingleRegister(
                channel,
                SimpleRegister(value.asShort().toInt())
            )

            EFunctionCode.FC_16_WRITE_MULTIPLE_REGISTERS -> writeMultipleRegisters(
                channel,
                ModbusDriverUtil.valueToRegisters(value, channel.datatype)
            )

            else -> throw RuntimeException("FunctionCode " + channel.functionCode.toString() + " not supported yet")
        }
    }

    fun setChannelsWithErrorFlag(containers: List<ChannelRecordContainer>) {
        for (container in containers) {
            container.setRecord(Record(null, null, Flag.DRIVER_ERROR_CHANNEL_TEMPORARILY_NOT_ACCESSIBLE))
        }
    }

    protected fun getModbusChannel(channelAddress: String?, access: EAccess): ModbusChannel? {
        var modbusChannel: ModbusChannel? = null

        // check if the channel object already exists in the list
        if (modbusChannels.containsKey(channelAddress)) {
            modbusChannel = modbusChannels[channelAddress]

            // if the channel object exists the access flag might has to be updated
            // (this is case occurs when the channel is readable and writable)
            if (modbusChannel.getAccessFlag() != access) {
                modbusChannel!!.update(access)
            }
        } else {
            modbusChannel = ModbusChannel(channelAddress, access)
            modbusChannels[channelAddress] = modbusChannel
        }
        return modbusChannel
    }

    // TODO refactoring - to evaluate the transaction id the execution should be part of the modbus tcp connection and
    // not part of the common modbusConnection since RTU has no transaction id
    @Throws(ModbusException::class)
    private fun executeReadTransaction(): ModbusResponse {
        var response: ModbusResponse? = null

        // see: performModbusTCPReadTransactionWithRetry()
        response = performModbusReadTransaction()
        if (response == null) {
            throw ModbusException("received response object is null")
        } else {
            printResponseTraceMsg(response)
        }
        return response
    }

    @Throws(ModbusException::class)
    private fun performModbusReadTransaction(): ModbusResponse {
        printRequestTraceMsg()
        transaction!!.execute()
        return transaction!!.response
    }

    // FIXME concept with retry is not working after a java.net.SocketTimeoutException: Read timed out
    // Problem is that the Transaction.excecute() increments the Transaction ID with each execute. Example: Request is
    // sent with Transaction ID 30, then a timeout happens, when using the retry mechanism below it will resend the
    // request. the jamod increases the transaction id again to 31 but then it receives the response for id 30. From the
    // time a timeout happened the response id will be always smaller than the request id, since the jamod doesn't
    // provide a method to read a response without sending a request.
    @Throws(ModbusException::class)
    private fun performModbusTCPReadTransactionWithRetry(): ModbusResponse? {
        var response: ModbusResponse? = null

        // NOTE: see comments about max retries in setTransaction()
        var retries = 0
        while (retries < MAX_RETRIES_FOR_DRIVER) {
            // +1 because id is incremented within transaction execution

            // int requestId = transaction.getTransactionID() + 1;
            printRequestTraceMsg()
            try {
                transaction!!.execute()
            } catch (e: ModbusIOException) {
                // logger.trace("caught ModbusIOException, probably timeout, retry");
                retries++
                checkRetryCondition(retries)
                continue
            }
            if (isTransactionIdMatching) {
                response = transaction!!.response
                break
            } else {
                retries++
                checkRetryCondition(retries)
            }
        }
        return response
    }

    /**
     *
     * @param retries
     * @throws ModbusIOException
     * if max number of retries is reached, which indicates an IO problem.
     */
    @Throws(ModbusIOException::class)
    private fun checkRetryCondition(retries: Int) {
        logger.trace("Failed to get response. Retry {}/{}", retries, MAX_RETRIES_FOR_DRIVER)
        if (retries == MAX_RETRIES_FOR_DRIVER) {
            throw ModbusIOException("Unable to get response. Max number of retries reached")
        }
    }

    private val isTransactionIdMatching: Boolean
        private get() {
            var isMatching = false
            val requestId = transaction!!.request.transactionID
            val responseId = transaction!!.response.transactionID
            if (requestId == responseId) {
                isMatching = true
            } else {
                logger.warn(
                    "Mismatching transaction IDs: request ({}) / response ({}). Retrying transaction...", requestId,
                    responseId
                )
            }
            return isMatching
        }

    @Throws(ModbusException::class)
    private fun executeWriteTransaction() {
        printRequestTraceMsg()
        transaction!!.execute()
        // FIXME evaluate response
        val response = transaction!!.response
        printResponseTraceMsg(response)
    }

    @Synchronized
    @Throws(ModbusException::class)
    private fun readCoils(startAddress: Int, count: Int, unitID: Int): BitVector {
        val readCoilsRequest = ReadCoilsRequest()
        readCoilsRequest.reference = startAddress
        readCoilsRequest.bitCount = count
        readCoilsRequest.unitID = unitID
        if (transaction is ModbusSerialTransaction) {
            readCoilsRequest.setHeadless()
        }
        transaction!!.request = readCoilsRequest
        val response = executeReadTransaction()
        val bitvector = (response as ReadCoilsResponse).coils
        bitvector.forceSize(count)
        return bitvector
    }

    @Throws(ModbusException::class)
    fun readCoils(channel: ModbusChannel): BitVector {
        return readCoils(channel.startAddress, channel.count, channel.unitId)
    }

    @Throws(ModbusException::class)
    fun readCoils(channelGroup: ModbusChannelGroup): BitVector {
        return readCoils(channelGroup.startAddress, channelGroup.count, channelGroup.unitId)
    }

    @Synchronized
    @Throws(ModbusException::class)
    private fun readDiscreteInputs(startAddress: Int, count: Int, unitID: Int): BitVector {
        val readInputDiscretesRequest = ReadInputDiscretesRequest()
        readInputDiscretesRequest.reference = startAddress
        readInputDiscretesRequest.bitCount = count
        readInputDiscretesRequest.unitID = unitID
        if (transaction is ModbusSerialTransaction) {
            readInputDiscretesRequest.setHeadless()
        }
        transaction!!.request = readInputDiscretesRequest
        val response = executeReadTransaction()
        val bitvector = (response as ReadInputDiscretesResponse).discretes
        bitvector.forceSize(count)
        return bitvector
    }

    @Throws(ModbusException::class)
    fun readDiscreteInputs(channel: ModbusChannel): BitVector {
        return readDiscreteInputs(channel.startAddress, channel.count, channel.unitId)
    }

    @Throws(ModbusException::class)
    fun readDiscreteInputs(channelGroup: ModbusChannelGroup): BitVector {
        return readDiscreteInputs(channelGroup.startAddress, channelGroup.count, channelGroup.unitId)
    }

    @Synchronized
    @Throws(ModbusException::class)
    private fun readHoldingRegisters(startAddress: Int, count: Int, unitID: Int): Array<Register?> {
        val readHoldingRegisterRequest = ReadMultipleRegistersRequest()
        readHoldingRegisterRequest.reference = startAddress
        readHoldingRegisterRequest.wordCount = count
        readHoldingRegisterRequest.unitID = unitID
        if (transaction is ModbusSerialTransaction) {
            readHoldingRegisterRequest.setHeadless()
        }
        transaction!!.request = readHoldingRegisterRequest
        val response = executeReadTransaction()
        return (response as ReadMultipleRegistersResponse).registers
    }

    @Throws(ModbusException::class)
    fun readHoldingRegisters(channel: ModbusChannel): Array<Register?> {
        return readHoldingRegisters(channel.startAddress, channel.count, channel.unitId)
    }

    @Throws(ModbusException::class)
    fun readHoldingRegisters(channelGroup: ModbusChannelGroup): Array<Register?> {
        return readHoldingRegisters(channelGroup.startAddress, channelGroup.count, channelGroup.unitId)
    }

    /**
     * Read InputRegisters
     *
     */
    @Synchronized
    @Throws(ModbusIOException::class, ModbusSlaveException::class, ModbusException::class)
    private fun readInputRegisters(startAddress: Int, count: Int, unitID: Int): Array<InputRegister?> {
        val readInputRegistersRequest = ReadInputRegistersRequest()
        readInputRegistersRequest.reference = startAddress
        readInputRegistersRequest.wordCount = count
        readInputRegistersRequest.unitID = unitID
        if (transaction is ModbusSerialTransaction) {
            readInputRegistersRequest.setHeadless()
        }
        transaction!!.request = readInputRegistersRequest
        val response = executeReadTransaction()
        return (response as ReadInputRegistersResponse).registers
    }

    /**
     * Read InputRegisters for a channel
     *
     * @param channel
     * Modbus channel
     * @return input register array
     * @throws ModbusException
     * if an modbus error occurs
     */
    @Throws(ModbusException::class)
    fun readInputRegisters(channel: ModbusChannel): Array<InputRegister?> {
        return readInputRegisters(channel.startAddress, channel.count, channel.unitId)
    }

    /**
     * Read InputRegisters for a channelGroup
     *
     * @param channelGroup
     * modbus channel group
     * @return the input register array
     * @throws ModbusException
     * if an modbus error occurs
     */
    @Throws(ModbusException::class)
    fun readInputRegisters(channelGroup: ModbusChannelGroup): Array<InputRegister?> {
        return readInputRegisters(channelGroup.startAddress, channelGroup.count, channelGroup.unitId)
    }

    @Synchronized
    @Throws(ModbusException::class)
    fun writeSingleCoil(channel: ModbusChannel, state: Boolean) {
        val writeCoilRequest = WriteCoilRequest()
        writeCoilRequest.reference = channel.startAddress
        writeCoilRequest.coil = state
        writeCoilRequest.unitID = channel.unitId
        transaction!!.request = writeCoilRequest
        executeWriteTransaction()
    }

    @Synchronized
    @Throws(ModbusException::class)
    fun writeMultipleCoils(channel: ModbusChannel, coils: BitVector?) {
        val writeMultipleCoilsRequest = WriteMultipleCoilsRequest()
        writeMultipleCoilsRequest.reference = channel.startAddress
        writeMultipleCoilsRequest.coils = coils
        writeMultipleCoilsRequest.unitID = channel.unitId
        transaction!!.request = writeMultipleCoilsRequest
        executeWriteTransaction()
    }

    @Synchronized
    @Throws(ModbusException::class)
    fun writeSingleRegister(channel: ModbusChannel, register: Register?) {
        val writeSingleRegisterRequest = WriteSingleRegisterRequest()
        writeSingleRegisterRequest.reference = channel.startAddress
        writeSingleRegisterRequest.register = register
        writeSingleRegisterRequest.unitID = channel.unitId
        transaction!!.request = writeSingleRegisterRequest
        executeWriteTransaction()
    }

    @Synchronized
    @Throws(ModbusException::class)
    fun writeMultipleRegisters(channel: ModbusChannel, registers: Array<Register?>?) {
        val writeMultipleRegistersRequest = WriteMultipleRegistersRequest()
        writeMultipleRegistersRequest.reference = channel.startAddress
        writeMultipleRegistersRequest.registers = registers
        writeMultipleRegistersRequest.unitID = channel.unitId
        transaction!!.request = writeMultipleRegistersRequest
        executeWriteTransaction()
    }

    // FIXME transaction ID unsupported by RTU since it is headless... create own debug for RTU
    private fun printRequestTraceMsg() {
        if (logger.isTraceEnabled) {
            logger.trace(createRequestTraceMsg())
        }
    }

    // FIXME: This debug message should be inside the transaction.execute() of the jamod.
    // The problem is, that the hex message (especially the transaction ID) is set within the execute method. The hex
    // message here shows a wrong transaction id.
    private fun createRequestTraceMsg(): String {
        val request = transaction!!.request

        // Transaction ID is incremented within the transaction.execute command. To view correct transaction Id in debug
        // output the value is incremented by one
        requestTransactionId = transaction!!.transactionID + 1
        var traceMsg = ""
        try {
            val sb = StringBuilder()
            sb.append("REQUEST: ").append(request.hexMessage).append('\n')
            sb.append("- transaction ID: ").append(requestTransactionId).append('\n')
            sb.append("- protocol ID   : ").append(request.protocolID).append('\n')
            sb.append("- data length   : ").append(request.dataLength).append('\n')
            sb.append("- unit ID       : ").append(request.unitID).append('\n')
            sb.append("- function code : ").append(request.functionCode).append('\n')
            sb.append("- is headless   : ").append(request.isHeadless).append('\n')
            sb.append("- max retries   : ").append(transaction!!.retries)
            if (transaction is ModbusTCPTransaction) {
                sb.append("\n   (NOTE: incorrect transaction Id displayed in hex message due to issue with jamod)")
            }
            traceMsg = sb.toString()
        } catch (e: Exception) {
            logger.trace("Unable to create debug message from request", e)
        }
        return traceMsg
    }

    private fun printResponseTraceMsg(response: ModbusResponse) {
        if (logger.isTraceEnabled) {
            logger.trace(createResponseTraceMsg(response))
        }
    }

    private fun createResponseTraceMsg(response: ModbusResponse): String {
        val responseTransactionId = response.transactionID
        if (transaction is ModbusTCPTransaction) {
            if (responseTransactionId > requestTransactionId + MAX_RETRIES_FOR_DRIVER) {
                logger.warn("responseTransactionId > (lastRequestTransactionId + MAX_RETRIES)")
            }
        }
        var traceMsg = ""
        try {
            val sb = StringBuilder()
            sb.append(
                """
    RESPONSE: ${response.hexMessage}
    
    """.trimIndent()
            )
            sb.append("- transaction ID: $responseTransactionId\n")
            sb.append(
                """
    - protocol ID   : ${response.protocolID}
    
    """.trimIndent()
            )
            sb.append(
                """
    - unit ID       : ${response.unitID}
    
    """.trimIndent()
            )
            sb.append(
                """
    - function code : ${response.functionCode}
    
    """.trimIndent()
            )
            sb.append(
                """
    - length        : ${response.dataLength}
    
    """.trimIndent()
            )
            sb.append(
                """
    - is headless   : ${response.isHeadless}
    
    """.trimIndent()
            )
            sb.append("- max retries   : " + transaction!!.retries)
            traceMsg = sb.toString()
        } catch (e: Exception) {
            logger.trace("Unable to create debug message from received response", e)
        }
        return traceMsg
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ModbusConnection::class.java)
    }
}
