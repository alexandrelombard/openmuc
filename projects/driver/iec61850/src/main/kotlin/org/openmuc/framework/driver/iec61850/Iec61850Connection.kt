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
package org.openmuc.framework.driver.iec61850

import com.beanit.iec61850bean.*
import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.data.*
import org.openmuc.framework.driver.spi.*
import org.openmuc.framework.driver.spi.ChannelValueContainer
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Instant
import java.util.*

class Iec61850Connection(private val clientAssociation: ClientAssociation, private val serverModel: ServerModel) :
    Connection {
    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun scanForChannels(settings: String?): List<ChannelScanInfo?>? {
        val bdas = serverModel.basicDataAttributes
        val scanInfos: MutableList<ChannelScanInfo?> = ArrayList(bdas.size)
        for (bda in bdas) {
            scanInfos.add(createScanInfo(bda))
        }
        return scanInfos
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun read(
        containers: List<ChannelRecordContainer?>?,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        // Check if record container objects exist -> check if basic data attribute exists in server model for channel
        // adress
        // -> model node exists but is no BDA
        for (container in containers!!) {
            setChannelHandleWithFcModelNode(container)
        }
        return if (samplingGroup!!.isNotEmpty()) {
            setRecordContainerWithSamplingGroup(containers, containerListHandle, samplingGroup)
        } else {
            for (container in containers) {
                setRecordContainer(container)
            }
            null
        }
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer?>?, containerListHandle: Any?): Any? {
        val modelNodesToBeWritten: MutableList<FcModelNode?> = ArrayList(
            containers!!.size
        )
        for (container in containers) {
            if (container!!.channelHandle != null) {
                modelNodesToBeWritten.add(container.channelHandle as FcModelNode?)
                setFcModelNode(container, container.channelHandle as FcModelNode?)
            } else {
                val args = container.channelAddress!!.split(":".toRegex(), limit = 3).toTypedArray()
                if (args.size != 2) {
                    logger.debug("Wrong channel address syntax: {}", container.channelAddress)
                    container.flag = Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND
                    continue
                }
                val modelNode = serverModel.findModelNode(args[0], Fc.fromString(args[1]))
                if (modelNode == null) {
                    logger.debug(
                        "No Basic Data Attribute for the channel address {} was found in the server model.",
                        container.channelAddress
                    )
                    container.flag = Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND
                    continue
                }
                var fcModelNode: FcModelNode
                try {
                    fcModelNode = modelNode as FcModelNode
                } catch (e: ClassCastException) {
                    logger.debug(
                        "ModelNode with object reference {} was found in the server model but is not a Basic Data Attribute.",
                        container.channelAddress
                    )
                    container.flag = Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND
                    continue
                }
                container.channelHandle = fcModelNode
                modelNodesToBeWritten.add(fcModelNode)
                setFcModelNode(container, fcModelNode)
            }
        }

        // TODO
        // first check all datasets if the are some that contain only requested channels
        // then check all remaining model nodes
        val fcNodesToBeRequested: MutableList<FcModelNode?> = ArrayList()
        while (modelNodesToBeWritten.size > 0) {
            fillRequestedNodes(fcNodesToBeRequested, modelNodesToBeWritten, serverModel)
        }
        for (fcModelNode in fcNodesToBeRequested) {
            try {
                if (fcModelNode!!.fc.toString() == "CO") {
                    logger.info("writing CO model node")
                    fcModelNode = fcModelNode.parent.parent as FcModelNode
                    clientAssociation.operate(fcModelNode)
                } else {
                    clientAssociation.setDataValues(fcModelNode)
                }
            } catch (e: ServiceError) {
                logger.error(
                    "Error writing to channel: service error calling setDataValues on {}: {}",
                    fcModelNode!!.reference, e
                )
                for (bda in fcModelNode.basicDataAttributes) {
                    for (valueContainer in containers) {
                        if (valueContainer!!.channelHandle === bda) {
                            valueContainer!!.flag = Flag.DRIVER_ERROR_CHANNEL_NOT_ACCESSIBLE
                        }
                    }
                }
                return null
            } catch (e: IOException) {
                throw ConnectionException(e)
            }
            for (bda in fcModelNode.basicDataAttributes) {
                for (valueContainer in containers) {
                    if (valueContainer!!.channelHandle === bda) {
                        valueContainer!!.flag = Flag.VALID
                    }
                }
            }
        }
        return null
    }

    @Throws(ConnectionException::class)
    private fun setRecordContainer(container: ChannelRecordContainer?): FcModelNode? {
        if (container!!.channelHandle == null) {
            return null
        }
        val fcModelNode = container.channelHandle as FcModelNode?
        try {
            clientAssociation.getDataValues(fcModelNode)
        } catch (e: ServiceError) {
            logger.debug(
                "Error reading channel: service error calling getDataValues on {}: {}",
                container.channelAddress, e
            )
            container.record = Record(Flag.DRIVER_ERROR_CHANNEL_NOT_ACCESSIBLE)
            return fcModelNode
        } catch (e: IOException) {
            throw ConnectionException(e)
        }
        if (fcModelNode is BasicDataAttribute) {
            val receiveTime = System.currentTimeMillis()
            setRecord(container, fcModelNode as BasicDataAttribute?, receiveTime)
        } else {
            val sb = StringBuilder("")
            for (bda in fcModelNode!!.basicDataAttributes) {
                sb.append(bda2String(bda) + STRING_SEPARATOR)
            }
            sb.delete(sb.length - 1, sb.length) // remove last separator
            val receiveTime = System.currentTimeMillis()
            setRecord(container, sb.toString(), receiveTime)
        }
        return null
    }

    @Throws(ConnectionException::class)
    private fun setRecordContainerWithSamplingGroup(
        containers: List<ChannelRecordContainer?>?,
        containerListHandle: Any?, samplingGroup: String?
    ): Any? {
        val fcModelNode: FcModelNode
        fcModelNode = if (containerListHandle != null) {
            containerListHandle as FcModelNode
        } else {
            val args = samplingGroup!!.split(":".toRegex(), limit = 3).toTypedArray()
            if (args.size != 2) {
                logger.debug("Wrong sampling group syntax: {}", samplingGroup)
                for (container in containers!!) {
                    container!!.record = Record(Flag.DRIVER_ERROR_SAMPLING_GROUP_NOT_FOUND)
                }
                return null
            }
            val modelNode = serverModel.findModelNode(args[0], Fc.fromString(args[1]))
            if (modelNode == null) {
                logger.debug(
                    "Error reading sampling group: no FCDO/DA or DataSet with object reference {} was not found in the server model.",
                    samplingGroup
                )
                for (container in containers!!) {
                    container!!.record = Record(Flag.DRIVER_ERROR_SAMPLING_GROUP_NOT_FOUND)
                }
                return null
            }
            try {
                modelNode as FcModelNode
            } catch (e: ClassCastException) {
                logger.debug(
                    "Error reading channel: ModelNode with sampling group reference {} was found in the server model but is not a FcModelNode.",
                    samplingGroup
                )
                for (container in containers!!) {
                    container!!.record = Record(Flag.DRIVER_ERROR_SAMPLING_GROUP_NOT_FOUND)
                }
                return null
            }
        }
        try {
            clientAssociation.getDataValues(fcModelNode)
        } catch (e: ServiceError) {
            logger.debug(
                "Error reading sampling group: service error calling getDataValues on {}: {}", samplingGroup,
                e
            )
            for (container in containers!!) {
                container!!.record = Record(Flag.DRIVER_ERROR_SAMPLING_GROUP_NOT_ACCESSIBLE)
            }
            return fcModelNode
        } catch (e: IOException) {
            throw ConnectionException(e)
        }
        val receiveTime = System.currentTimeMillis()
        for (container in containers!!) {
            if (container!!.channelHandle != null) {
                setRecord(container, container.channelHandle as BasicDataAttribute?, receiveTime)
            } else {
                container.record = Record(Flag.DRIVER_ERROR_CHANNEL_NOT_PART_OF_SAMPLING_GROUP)
            }
        }
        return fcModelNode
    }

    private fun setChannelHandleWithFcModelNode(container: ChannelRecordContainer?) {
        if (container!!.channelHandle == null) {
            val args = container.channelAddress!!.split(":".toRegex(), limit = 3).toTypedArray()
            if (args.size != 2) {
                logger.debug("Wrong channel address syntax: {}", container.channelAddress)
                container.record = Record(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND)
                return
            }
            val modelNode = serverModel.findModelNode(args[0], Fc.fromString(args[1]))
            if (modelNode == null) {
                logger.debug(
                    "No Basic Data Attribute for the channel address {} was found in the server model.",
                    container.channelAddress
                )
                container.record = Record(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND)
                return
            }
            val fcModelNode: FcModelNode
            fcModelNode = try {
                modelNode as FcModelNode
            } catch (e: ClassCastException) {
                logger.debug(
                    "ModelNode with object reference {} was found in the server model but is not a Basic Data Attribute.",
                    container.channelAddress
                )
                container.record = Record(Flag.DRIVER_ERROR_CHANNEL_WITH_THIS_ADDRESS_NOT_FOUND)
                return
            }
            container.channelHandle = fcModelNode
        }
    }

    @Throws(UnsupportedOperationException::class)
    override fun startListening(containers: List<ChannelRecordContainer?>?, listener: RecordsReceivedListener?) {
        throw UnsupportedOperationException()
    }

    fun fillRequestedNodes(
        fcNodesToBeRequested: MutableList<FcModelNode?>, remainingFcModelNodes: MutableList<FcModelNode?>,
        serverModel: ServerModel
    ) {
        val currentFcModelNode = remainingFcModelNodes[0]
        if (!checkParent(currentFcModelNode, fcNodesToBeRequested, remainingFcModelNodes, serverModel)) {
            remainingFcModelNodes.remove(currentFcModelNode)
            fcNodesToBeRequested.add(currentFcModelNode)
        }
    }

    fun checkParent(
        modelNode: ModelNode?, fcNodesToBeRequested: MutableList<FcModelNode?>,
        remainingModelNodes: MutableList<FcModelNode?>, serverModel: ServerModel
    ): Boolean {
        if (modelNode !is FcModelNode) {
            return false
        }
        val fcModelNode = modelNode
        var parentNode: ModelNode = serverModel
        for (i in 0 until fcModelNode.reference.size() - 1) {
            parentNode = parentNode.getChild(fcModelNode.reference[i], fcModelNode.fc)
        }
        val basicDataAttributes = parentNode.basicDataAttributes
        for (bda in basicDataAttributes) {
            if (!remainingModelNodes.contains(bda)) {
                return false
            }
        }
        if (!checkParent(parentNode, fcNodesToBeRequested, remainingModelNodes, serverModel)) {
            for (bda in basicDataAttributes) {
                remainingModelNodes.remove(bda)
            }
            fcNodesToBeRequested.add(parentNode as FcModelNode)
        }
        return true
    }

    private fun setFcModelNode(container: ChannelValueContainer?, fcModelNode: FcModelNode?) {
        if (fcModelNode is BasicDataAttribute) {
            setBda(container, fcModelNode)
        } else {
            val bdas = fcModelNode!!.basicDataAttributes
            val valueString = container!!.value.toString()
            val bdaValues = valueString.split(STRING_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            check(bdaValues.size == bdas.size) {
                ("attempt to write array " + valueString + " into fcModelNode "
                        + fcModelNode.name + " failed as the dimensions don't fit.")
            }
            for (i in bdaValues.indices) {
                setBda(bdaValues[i], bdas[i])
            }
        }
    }

    enum class BdaTypes {
        BOOLEAN {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(channelAddress, "", ValueType.BOOLEAN, 0)
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaBoolean).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(BooleanValue((bda as BdaBoolean?)!!.value), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaBoolean).value = java.lang.Boolean.parseBoolean(bdaValueString)
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaBoolean).value = container!!.value!!.asBoolean()
            }
        },
        INT8 {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(channelAddress, "", ValueType.BYTE, 0)
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaInt8).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(DoubleValue((bda as BdaInt8?)!!.value.toDouble()), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaInt8).value = bdaValueString.toByte()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaInt8).value = container!!.value!!.asByte()
            }
        },
        INT16 {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(channelAddress, "", ValueType.SHORT, 0)
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return "" + (bda as BdaInt16).value
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(DoubleValue((bda as BdaInt16?)!!.value.toDouble()), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaInt16).value = bdaValueString.toShort()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaInt16).value = container!!.value!!.asShort()
            }
        },
        INT32 {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(channelAddress, "", ValueType.INTEGER, 0)
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaInt32).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(DoubleValue((bda as BdaInt32?)!!.value.toDouble()), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaInt32).value = bdaValueString.toInt()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaInt32).value = container!!.value!!.asInt()
            }
        },
        INT64 {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(channelAddress, "", ValueType.LONG, 0)
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return "" + (bda as BdaInt64).value
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(DoubleValue((bda as BdaInt64?)!!.value.toDouble()), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaInt64).value = bdaValueString.toLong()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaInt64).value = container!!.value!!.asLong()
            }
        },
        INT128 {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo? {
                return null
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaInt128).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(DoubleValue((bda as BdaInt128?)!!.value.toDouble()), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaInt128).value = bdaValueString.toLong()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaInt128).value = container!!.value!!.asLong()
            }
        },
        INT8U {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(channelAddress, "", ValueType.SHORT, 0)
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return "" + (bda as BdaInt8U).value
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(DoubleValue((bda as BdaInt8U?)!!.value.toDouble()), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaInt8U).value = bdaValueString.toShort()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaInt8U).value = container!!.value!!.asShort()
            }
        },
        INT16U {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(channelAddress, "", ValueType.INTEGER, 0)
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return "" + (bda as BdaInt16U).value
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(DoubleValue((bda as BdaInt16U?)!!.value.toDouble()), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaInt16U).value = bdaValueString.toInt()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaInt16U).value = container!!.value!!.asInt()
            }
        },
        INT32U {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(channelAddress, "", ValueType.LONG, 0)
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaInt32U).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(DoubleValue((bda as BdaInt32U?)!!.value.toDouble()), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaInt32U).value = bdaValueString.toLong()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaInt32U).value = container!!.value!!.asLong()
            }
        },
        FLOAT32 {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(channelAddress, "", ValueType.FLOAT, 0)
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaFloat32).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(FloatValue((bda as BdaFloat32?)!!.float), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaFloat32).float = bdaValueString.toFloat()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaFloat32).float = container!!.value!!.asFloat()
            }
        },
        FLOAT64 {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(channelAddress, "", ValueType.DOUBLE, 0)
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaFloat64).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(DoubleValue((bda as BdaFloat64?)!!.double), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaFloat64).double = bdaValueString.toDouble()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaFloat64).double = container!!.value!!.asDouble()
            }
        },
        OCTET_STRING {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(
                    channelAddress, "", ValueType.BYTE_ARRAY,
                    (bda as BdaOctetString).maxLength
                )
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return Arrays.toString((bda as BdaOctetString).value)
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(ByteArrayValue((bda as BdaOctetString?)!!.value, true), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaOctetString).value = bdaValueString.toByteArray()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaOctetString).value = container!!.value!!.asByteArray()
            }
        },
        VISIBLE_STRING {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(
                    channelAddress, "", ValueType.BYTE_ARRAY,
                    (bda as BdaVisibleString).maxLength
                )
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaVisibleString).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(StringValue((bda as BdaVisibleString?)!!.stringValue), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaVisibleString).setValue(bdaValueString)
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaVisibleString).setValue(container!!.value!!.asString())
            }
        },
        UNICODE_STRING {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                // TODO Auto- method stub
                return ChannelScanInfo(
                    channelAddress, "", ValueType.BYTE_ARRAY,
                    (bda as BdaUnicodeString).maxLength
                )
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                val byteValue = (bda as BdaUnicodeString).value
                return byteValue?.let { String(it) } ?: "null"
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(ByteArrayValue((bda as BdaUnicodeString?)!!.value, true), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaUnicodeString).value = bdaValueString.toByteArray()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaUnicodeString).value = container!!.value!!.asByteArray()
            }
        },
        TIMESTAMP {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(channelAddress, "", ValueType.LONG, 0)
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                val date = (bda as BdaTimestamp).instant
                return if (date == null) "<invalid date>" else "" + date.toEpochMilli()
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                val date = (bda as BdaTimestamp?)!!.instant
                return if (date == null) {
                    Record(LongValue(-1L), receiveTime)
                } else {
                    Record(LongValue(date.toEpochMilli()), receiveTime)
                }
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaTimestamp).instant = Instant.ofEpochMilli(bdaValueString.toLong())
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaTimestamp).instant = Instant.ofEpochMilli(container!!.value!!.asLong())
            }
        },
        ENTRY_TIME {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(
                    channelAddress, "", ValueType.BYTE_ARRAY,
                    (bda as BdaEntryTime).value.size
                )
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaEntryTime).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(ByteArrayValue((bda as BdaEntryTime?)!!.value, true), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaEntryTime).value = bdaValueString.toByteArray()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaEntryTime).value = container!!.value!!.asByteArray()
            }
        },
        CHECK {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(
                    channelAddress, "", ValueType.BYTE_ARRAY,
                    (bda as BdaBitString).value.size
                )
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaCheck).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(ByteArrayValue((bda as BdaCheck?)!!.value, true), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = bdaValueString.toByteArray()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = container!!.value!!.asByteArray()
            }
        },
        QUALITY {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(
                    channelAddress, "", ValueType.BYTE_ARRAY,
                    (bda as BdaBitString).value.size
                )
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaQuality).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(ByteArrayValue((bda as BdaQuality?)!!.value, true), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = bdaValueString.toByteArray()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = container!!.value!!.asByteArray()
            }
        },
        DOUBLE_BIT_POS {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(
                    channelAddress, "", ValueType.BYTE_ARRAY,
                    (bda as BdaBitString).value.size
                )
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaDoubleBitPos).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(ByteArrayValue((bda as BdaDoubleBitPos?)!!.value, true), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = bdaValueString.toByteArray()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = container!!.value!!.asByteArray()
            }
        },
        TAP_COMMAND {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(
                    channelAddress, "", ValueType.BYTE_ARRAY,
                    (bda as BdaBitString).value.size
                )
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return "" + (bda as BdaTapCommand).tapCommand.intValue
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(IntValue((bda as BdaTapCommand?)!!.tapCommand.intValue), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = bdaValueString.toByteArray()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = container!!.value!!.asByteArray()
            }
        },
        TRIGGER_CONDITIONS {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(
                    channelAddress, "", ValueType.BYTE_ARRAY,
                    (bda as BdaBitString).value.size
                )
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaTriggerConditions).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(ByteArrayValue((bda as BdaBitString?)!!.value, true), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = bdaValueString.toByteArray()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = container!!.value!!.asByteArray()
            }
        },
        OPTFLDS {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(
                    channelAddress, "", ValueType.BYTE_ARRAY,
                    (bda as BdaBitString).value.size
                )
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaOptFlds).toString()
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(ByteArrayValue((bda as BdaOptFlds?)!!.value, true), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = bdaValueString.toByteArray()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = container!!.value!!.asByteArray()
            }
        },
        REASON_FOR_INCLUSION {
            override fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo {
                return ChannelScanInfo(
                    channelAddress, "", ValueType.BYTE_ARRAY,
                    (bda as BdaBitString).value.size
                )
            }

            override fun bda2String(bda: BasicDataAttribute): String {
                return (bda as BdaReasonForInclusion).valueString
            }

            override fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record {
                return Record(ByteArrayValue((bda as BdaReasonForInclusion?)!!.value, true), receiveTime)
            }

            override fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = bdaValueString.toByteArray()
            }

            override fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
                (bda as BdaBitString).value = container!!.value!!.asByteArray()
            }
        };

        abstract fun getScanInfo(channelAddress: String?, bda: BasicDataAttribute): ChannelScanInfo?
        abstract fun bda2String(bda: BasicDataAttribute): String
        abstract fun setRecord(bda: BasicDataAttribute?, receiveTime: Long): Record
        abstract fun setBda(bdaValueString: String, bda: BasicDataAttribute)
        abstract fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute)
    }

    fun createScanInfo(bda: BasicDataAttribute): ChannelScanInfo? {
        return try {
            BdaTypes.valueOf(bda.basicType.toString())
                .getScanInfo(bda.reference.toString() + ":" + bda.fc, bda)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("unknown BasicType received: " + bda.basicType)
        }
    }

    fun bda2String(bda: BasicDataAttribute): String {
        return try {
            BdaTypes.valueOf(bda.basicType.toString()).bda2String(bda)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("unknown BasicType received: " + bda.basicType)
        }
    }

    private fun setRecord(container: ChannelRecordContainer?, bda: BasicDataAttribute?, receiveTime: Long) {
        try {
            container!!.record = BdaTypes.valueOf(bda!!.basicType.toString()).setRecord(bda, receiveTime)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("unknown BasicType received: " + bda!!.basicType)
        }
    }

    private fun setRecord(container: ChannelRecordContainer?, stringValue: String, receiveTime: Long) {
        container!!.record = Record(ByteArrayValue(stringValue.toByteArray(), true), receiveTime)
    }

    private fun setBda(bdaValueString: String, bda: BasicDataAttribute) {
        try {
            BdaTypes.valueOf(bda.basicType.toString()).setBda(bdaValueString, bda)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("unknown BasicType received: " + bda.basicType)
        }
    }

    private fun setBda(container: ChannelValueContainer?, bda: BasicDataAttribute) {
        try {
            BdaTypes.valueOf(bda.basicType.toString()).setBda(container, bda)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("unknown BasicType received: " + bda.basicType)
        }
    }

    override fun disconnect() {
        clientAssociation.disconnect()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Iec61850Connection::class.java)
        private const val STRING_SEPARATOR = ","
    }
}
