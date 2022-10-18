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
package org.openmuc.framework.driver.iec60870

import org.openmuc.framework.data.*
import org.openmuc.framework.data.Record.value
import org.openmuc.framework.driver.iec60870.settings.ChannelAddress
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import org.openmuc.j60870.*
import org.openmuc.j60870.ie.*
import org.openmuc.j60870.ie.IeDoubleCommand.DoubleCommandState
import org.openmuc.j60870.ie.IeRegulatingStepCommand.StepCommandState
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import javax.naming.ConfigurationException

object Iec60870DataHandling {
    private const val ONLY_BYTE_ARRAY_WITH_LENGTH = "): Only byte array with length "
    private val logger = LoggerFactory.getLogger(Iec60870DataHandling::class.java)
    private const val INT32_BYTE_LENGTH = 4
    @Throws(IOException::class, UnsupportedOperationException::class, TypeConversionException::class)
    fun writeSingleCommand(record: Record, channelAddress: ChannelAddress, clientConnection: Connection?) {
        val commonAddress = channelAddress.commonAddress()
        val qualifierSelect = channelAddress.select()
        val informationObjectAddress = channelAddress.ioa()
        val typeId = ASduType.typeFor(channelAddress.typeId())
        val flag = record.flag
        val value = record.value
        val timestamp = IeTime56(record.timestamp!!)
        val cot = CauseOfTransmission.ACTIVATION
        if (flag === Flag.VALID && value != null) {
            when (typeId) {
                ASduType.C_DC_NA_1 -> {
                    val doubleCommandState = if (value.asBoolean()) DoubleCommandState.ON else DoubleCommandState.OFF
                    clientConnection!!.doubleCommand(
                        commonAddress, cot, informationObjectAddress,
                        IeDoubleCommand(doubleCommandState, 0, false)
                    )
                }

                ASduType.C_DC_TA_1 -> {
                    doubleCommandState = if (value.asBoolean()) DoubleCommandState.ON else DoubleCommandState.OFF
                    clientConnection!!.doubleCommandWithTimeTag(
                        commonAddress, cot, informationObjectAddress,
                        IeDoubleCommand(doubleCommandState, 0, false), timestamp
                    )
                }

                ASduType.C_BO_NA_1 -> {
                    val binaryStateInformation = IeBinaryStateInformation(value.asInt())
                    clientConnection!!.bitStringCommand(
                        commonAddress,
                        cot,
                        informationObjectAddress,
                        binaryStateInformation
                    )
                }

                ASduType.C_BO_TA_1 -> {
                    binaryStateInformation = IeBinaryStateInformation(value.asInt())
                    clientConnection!!.bitStringCommandWithTimeTag(
                        commonAddress, cot, informationObjectAddress,
                        binaryStateInformation, timestamp
                    )
                }

                ASduType.C_CD_NA_1 -> {
                    val time16 = IeTime16(record.timestamp!!)
                    clientConnection!!.delayAcquisitionCommand(commonAddress, cot, time16)
                }

                ASduType.C_CI_NA_1 -> {
                    val baQualifier = value.asByteArray()
                    if (baQualifier!!.size == 2) {
                        val qualifier = IeQualifierOfCounterInterrogation(
                            baQualifier[0].toInt(),
                            baQualifier[1].toInt()
                        )
                        clientConnection!!.counterInterrogation(commonAddress, cot, qualifier)
                    } else {
                        throw TypeConversionException(
                            typeId.toString() + "(" + typeId.id
                                    + "): Only byte array with length 2 allowed. byte[0]=request, byte[1]=freeze]"
                        )
                    }
                }

                ASduType.C_CS_NA_1 -> clientConnection!!.synchronizeClocks(
                    commonAddress,
                    IeTime56(System.currentTimeMillis())
                )

                ASduType.C_IC_NA_1 -> {
                    val ieQualifierOfInterrogation = IeQualifierOfInterrogation(value.asInt())
                    clientConnection!!.interrogation(commonAddress, cot, ieQualifierOfInterrogation)
                }

                ASduType.C_RC_NA_1 -> {
                    val regulatingStepCommand = getIeRegulatingStepCommand(typeId, value)
                    clientConnection!!.regulatingStepCommand(
                        commonAddress, cot, informationObjectAddress,
                        regulatingStepCommand
                    )
                }

                ASduType.C_RC_TA_1 -> try {
                    regulatingStepCommand = getIeRegulatingStepCommand(typeId, value)
                    clientConnection!!.regulatingStepCommandWithTimeTag(
                        commonAddress, cot, informationObjectAddress,
                        regulatingStepCommand, timestamp
                    )
                } catch (e: Exception) {
                    logger.error("", e)
                }

                ASduType.C_RD_NA_1 -> clientConnection!!.readCommand(commonAddress, informationObjectAddress)
                ASduType.C_RP_NA_1 -> clientConnection!!.resetProcessCommand(
                    commonAddress,
                    IeQualifierOfResetProcessCommand(value.asInt())
                )

                ASduType.C_SC_NA_1 -> {
                    val singleCommand = getIeSingeleCommand(typeId, value)
                    clientConnection!!.singleCommand(commonAddress, cot, informationObjectAddress, singleCommand)
                }

                ASduType.C_SC_TA_1 -> {
                    singleCommand = getIeSingeleCommand(typeId, value)
                    clientConnection!!.singleCommandWithTimeTag(
                        commonAddress, cot, informationObjectAddress, singleCommand,
                        timestamp
                    )
                }

                ASduType.C_SE_NA_1 -> {
                    val values = value.asByteArray()
                    val arrayLength = 6
                    val valueLength = 4
                    checkLength(
                        typeId, values, arrayLength,
                        "byte[0-3]=command state, byte[4]=qualifier of command, byte[5]=execute/select"
                    )
                    val ieQualifierOfSetPointCommand = getIeQualifierSetPointCommand(
                        values,
                        arrayLength
                    )
                    val ieNormalizedValue = IeNormalizedValue(
                        bytesToSignedInt32(values, valueLength, false)
                    )
                    clientConnection!!.setNormalizedValueCommand(
                        commonAddress, cot, informationObjectAddress,
                        ieNormalizedValue, ieQualifierOfSetPointCommand
                    )
                }

                ASduType.C_SE_NB_1 -> {
                    values = value.asByteArray()
                    arrayLength = 4
                    checkLength(
                        typeId, values, arrayLength,
                        "byte[0-1]=command state, byte[2]=qualifier of command, byte[3]=execute/select"
                    )
                    ieQualifierOfSetPointCommand = getIeQualifierSetPointCommand(values, arrayLength)
                    val scaledValue = IeScaledValue(bytesToSignedInt32(values, 2, false))
                    clientConnection!!.setScaledValueCommand(
                        commonAddress, cot, informationObjectAddress, scaledValue,
                        ieQualifierOfSetPointCommand
                    )
                }

                ASduType.C_SE_NC_1 -> {
                    val shortFloat = IeShortFloat(value.asFloat())
                    val qualifier = IeQualifierOfSetPointCommand(0, qualifierSelect)
                    clientConnection!!.setShortFloatCommand(
                        commonAddress, cot, informationObjectAddress, shortFloat,
                        qualifier
                    )
                }

                ASduType.C_SE_TA_1 -> {
                    values = value.asByteArray()
                    arrayLength = 6
                    valueLength = 4
                    checkLength(
                        typeId, values, arrayLength,
                        "byte[0-3]=command state, byte[4]=qualifier of command, byte[5]=execute/select"
                    )
                    ieQualifierOfSetPointCommand = getIeQualifierSetPointCommand(values, arrayLength)
                    ieNormalizedValue = IeNormalizedValue(bytesToSignedInt32(values, valueLength, false))
                    clientConnection!!.setNormalizedValueCommandWithTimeTag(
                        commonAddress, cot, informationObjectAddress,
                        ieNormalizedValue, ieQualifierOfSetPointCommand, timestamp
                    )
                }

                ASduType.C_SE_TB_1 -> {
                    values = value.asByteArray()
                    arrayLength = 4
                    checkLength(
                        typeId, values, arrayLength,
                        "byte[0-1]=command state, byte[2]=qualifier of command, byte[3]=execute/select"
                    )
                    ieQualifierOfSetPointCommand = getIeQualifierSetPointCommand(values, arrayLength)
                    scaledValue = IeScaledValue(bytesToSignedInt32(values, 2, false))
                    clientConnection!!.setScaledValueCommandWithTimeTag(
                        commonAddress, cot, informationObjectAddress,
                        scaledValue, ieQualifierOfSetPointCommand, timestamp
                    )
                }

                ASduType.C_SE_TC_1 -> throw UnsupportedOperationException(
                    "TypeID " + typeId + "(" + typeId.id + ") is not supported, yet."
                )

                ASduType.C_TS_NA_1 -> clientConnection!!.testCommand(commonAddress)
                ASduType.C_TS_TA_1 -> clientConnection!!.testCommandWithTimeTag(
                    commonAddress, IeTestSequenceCounter(value.asInt()),
                    timestamp
                )

                ASduType.F_AF_NA_1, ASduType.F_DR_TA_1, ASduType.F_FR_NA_1, ASduType.F_LS_NA_1, ASduType.F_SC_NA_1, ASduType.F_SC_NB_1, ASduType.F_SG_NA_1, ASduType.F_SR_NA_1, ASduType.M_BO_NA_1, ASduType.M_BO_TA_1, ASduType.M_BO_TB_1, ASduType.M_DP_NA_1, ASduType.M_DP_TA_1, ASduType.M_DP_TB_1, ASduType.M_EI_NA_1, ASduType.M_EP_TA_1, ASduType.M_EP_TB_1, ASduType.M_EP_TC_1, ASduType.M_EP_TD_1, ASduType.M_EP_TE_1, ASduType.M_EP_TF_1, ASduType.M_IT_NA_1, ASduType.M_IT_TA_1, ASduType.M_IT_TB_1, ASduType.M_ME_NA_1, ASduType.M_ME_NB_1, ASduType.M_ME_NC_1, ASduType.M_ME_ND_1, ASduType.M_ME_TA_1, ASduType.M_ME_TB_1, ASduType.M_ME_TC_1, ASduType.M_ME_TD_1, ASduType.M_ME_TE_1, ASduType.M_ME_TF_1, ASduType.M_PS_NA_1, ASduType.M_SP_NA_1, ASduType.M_SP_TA_1, ASduType.M_SP_TB_1, ASduType.M_ST_NA_1, ASduType.M_ST_TA_1, ASduType.M_ST_TB_1, ASduType.P_AC_NA_1, ASduType.P_ME_NA_1, ASduType.P_ME_NB_1, ASduType.P_ME_NC_1, ASduType.PRIVATE_128, ASduType.PRIVATE_129, ASduType.PRIVATE_130, ASduType.PRIVATE_131, ASduType.PRIVATE_132, ASduType.PRIVATE_133, ASduType.PRIVATE_134, ASduType.PRIVATE_135, ASduType.PRIVATE_136, ASduType.PRIVATE_137, ASduType.PRIVATE_138, ASduType.PRIVATE_139, ASduType.PRIVATE_140, ASduType.PRIVATE_141, ASduType.PRIVATE_142, ASduType.PRIVATE_143, ASduType.PRIVATE_144, ASduType.PRIVATE_145, ASduType.PRIVATE_146, ASduType.PRIVATE_147, ASduType.PRIVATE_148, ASduType.PRIVATE_149, ASduType.PRIVATE_150, ASduType.PRIVATE_151, ASduType.PRIVATE_152, ASduType.PRIVATE_153, ASduType.PRIVATE_154, ASduType.PRIVATE_155, ASduType.PRIVATE_156, ASduType.PRIVATE_157, ASduType.PRIVATE_158, ASduType.PRIVATE_159, ASduType.PRIVATE_160, ASduType.PRIVATE_161, ASduType.PRIVATE_162, ASduType.PRIVATE_163, ASduType.PRIVATE_164, ASduType.PRIVATE_165, ASduType.PRIVATE_166, ASduType.PRIVATE_167, ASduType.PRIVATE_168, ASduType.PRIVATE_169, ASduType.PRIVATE_170, ASduType.PRIVATE_171, ASduType.PRIVATE_172, ASduType.PRIVATE_173, ASduType.PRIVATE_174, ASduType.PRIVATE_175, ASduType.PRIVATE_176, ASduType.PRIVATE_177, ASduType.PRIVATE_178, ASduType.PRIVATE_179, ASduType.PRIVATE_180, ASduType.PRIVATE_181, ASduType.PRIVATE_182, ASduType.PRIVATE_183, ASduType.PRIVATE_184, ASduType.PRIVATE_185, ASduType.PRIVATE_186, ASduType.PRIVATE_187, ASduType.PRIVATE_188, ASduType.PRIVATE_189, ASduType.PRIVATE_190, ASduType.PRIVATE_191, ASduType.PRIVATE_192, ASduType.PRIVATE_193, ASduType.PRIVATE_194, ASduType.PRIVATE_195, ASduType.PRIVATE_196, ASduType.PRIVATE_197, ASduType.PRIVATE_198, ASduType.PRIVATE_199, ASduType.PRIVATE_200, ASduType.PRIVATE_201, ASduType.PRIVATE_202, ASduType.PRIVATE_203, ASduType.PRIVATE_204, ASduType.PRIVATE_205, ASduType.PRIVATE_206, ASduType.PRIVATE_207, ASduType.PRIVATE_208, ASduType.PRIVATE_209, ASduType.PRIVATE_210, ASduType.PRIVATE_211, ASduType.PRIVATE_212, ASduType.PRIVATE_213, ASduType.PRIVATE_214, ASduType.PRIVATE_215, ASduType.PRIVATE_216, ASduType.PRIVATE_217, ASduType.PRIVATE_218, ASduType.PRIVATE_219, ASduType.PRIVATE_220, ASduType.PRIVATE_221, ASduType.PRIVATE_222, ASduType.PRIVATE_223, ASduType.PRIVATE_224, ASduType.PRIVATE_225, ASduType.PRIVATE_226, ASduType.PRIVATE_227, ASduType.PRIVATE_228, ASduType.PRIVATE_229, ASduType.PRIVATE_230, ASduType.PRIVATE_231, ASduType.PRIVATE_232, ASduType.PRIVATE_233, ASduType.PRIVATE_234, ASduType.PRIVATE_235, ASduType.PRIVATE_236, ASduType.PRIVATE_237, ASduType.PRIVATE_238, ASduType.PRIVATE_239, ASduType.PRIVATE_240, ASduType.PRIVATE_241, ASduType.PRIVATE_242, ASduType.PRIVATE_243, ASduType.PRIVATE_244, ASduType.PRIVATE_245, ASduType.PRIVATE_246, ASduType.PRIVATE_247, ASduType.PRIVATE_248, ASduType.PRIVATE_249, ASduType.PRIVATE_250, ASduType.PRIVATE_251, ASduType.PRIVATE_252, ASduType.PRIVATE_253, ASduType.PRIVATE_254, ASduType.PRIVATE_255 -> throw UnsupportedOperationException(
                    "TypeID " + typeId + "(" + typeId.id + ") is not supported, yet."
                )

                else -> throw UnsupportedOperationException(
                    "TypeID " + typeId + "(" + typeId.id + ") is not supported, yet."
                )
            }
        }
    }

    @Throws(TypeConversionException::class)
    private fun checkLength(typeId: ASduType, values: ByteArray?, maxLength: Int, commands: String) {
        val length = values!!.size
        if (length != maxLength) {
            throw UnsupportedOperationException(
                typeId.toString() + "(" + typeId.id + ONLY_BYTE_ARRAY_WITH_LENGTH + maxLength + " allowed. " + commands
            )
        }
    }

    private fun getIeQualifierSetPointCommand(values: ByteArray?, maxLength: Int): IeQualifierOfSetPointCommand {
        val qualifier = values!![maxLength - 2].toInt()
        val select = values[maxLength - 1] >= 0
        return IeQualifierOfSetPointCommand(qualifier, select)
    }

    @Throws(TypeConversionException::class)
    private fun getIeSingeleCommand(typeId: ASduType, value: Value): IeSingleCommand {
        val values = value.asByteArray()
        val commandStateOn: Boolean
        val select: Boolean
        val length = 3
        if (values!!.size == length) {
            commandStateOn = values[0] >= 0
            select = values[1] >= 0
        } else {
            throw TypeConversionException(
                typeId.toString() + "(" + typeId.id + ONLY_BYTE_ARRAY_WITH_LENGTH + length
                        + " allowed. byte[0]=command state on, byte[1]=execute/select, byte[2]=qualifier of command"
            )
        }
        return IeSingleCommand(commandStateOn, values[2].toInt(), select)
    }

    @Throws(TypeConversionException::class)
    private fun getIeRegulatingStepCommand(typeId: ASduType, value: Value): IeRegulatingStepCommand {
        val values = value.asByteArray()
        val commandState: StepCommandState
        val select: Boolean
        val length = 3
        if (values!!.size == length) {
            commandState = StepCommandState.getInstance(values[0].toInt())
            select = values[1] >= 0
        } else {
            throw TypeConversionException(
                typeId.toString() + "(" + typeId.id + ONLY_BYTE_ARRAY_WITH_LENGTH + length
                        + " allowed. byte[0]=command state, byte[1]=execute/select, byte[2]=qualifier of command "
            )
        }
        return IeRegulatingStepCommand(commandState, values[2].toInt(), select)
    }

    fun handleInformationObject(
        aSdu: ASdu, timestamp: Long, channelAddress: ChannelAddress?,
        informationObject: InformationObject
    ): Record {
        val record: Record
        if (channelAddress!!.multiple() > 1) {
            record = handleMultipleElementObjects(aSdu, timestamp, channelAddress, informationObject)
        } else {
            val informationElements: Array<InformationElement>?
            informationElements = try {
                handleSingleElementObject(aSdu, timestamp, channelAddress, informationObject)
            } catch (e: ConfigurationException) {
                logger.warn(e.message)
                return Record(Flag.DRIVER_ERROR_CHANNEL_ADDRESS_SYNTAX_INVALID)
            }
            record = if (informationElements != null) {
                creatNewRecord(
                    informationElements, aSdu.typeIdentification,
                    channelAddress, timestamp
                )
            } else {
                Record(Flag.UNKNOWN_ERROR)
            }
        }
        return record
    }

    private fun creatNewRecord(
        informationElements: Array<InformationElement>, typeId: ASduType,
        channelAddress: ChannelAddress?, timestamp: Long
    ): Record {
        return if (channelAddress!!.dataType() != "v") {
            getQualityDescriptorAsRecord(
                channelAddress.dataType(),
                informationElements,
                typeId,
                timestamp
            )
        } else {
            when (typeId) {
                ASduType.M_ME_NA_1, ASduType.M_ME_TA_1, ASduType.M_ME_ND_1, ASduType.M_ME_TD_1, ASduType.C_SE_NA_1, ASduType.P_ME_NA_1 -> {
                    val normalizedValue = informationElements[0] as IeNormalizedValue // TODO: is 0 correct?
                    Record(
                        DoubleValue(normalizedValue.normalizedValue),
                        timestamp
                    )
                }

                ASduType.M_ME_NB_1, ASduType.M_ME_TB_1, ASduType.M_ME_TE_1, ASduType.C_SE_NB_1, ASduType.P_ME_NB_1 -> {
                    val scaledValue = informationElements[0] as IeScaledValue // TODO: is 0 correct?
                    Record(
                        IntValue(scaledValue.unnormalizedValue),
                        timestamp
                    ) // test this
                }

                ASduType.M_ME_NC_1, ASduType.M_ME_TC_1, ASduType.M_ME_TF_1, ASduType.C_SE_NC_1, ASduType.P_ME_NC_1 -> {
                    val shortFloat = informationElements[0] as IeShortFloat
                    Record(
                        DoubleValue(shortFloat.value.toDouble()),
                        timestamp
                    )
                }

                ASduType.M_BO_NA_1, ASduType.M_BO_TA_1, ASduType.M_BO_TB_1 -> {
                    val binaryStateInformation = informationElements[0] as IeBinaryStateInformation
                    Record(
                        ByteArrayValue(
                            ByteBuffer.allocate(4).putInt(binaryStateInformation.value).array()
                        ),
                        timestamp
                    )
                }

                ASduType.M_SP_NA_1, ASduType.M_SP_TA_1, ASduType.M_PS_NA_1, ASduType.M_SP_TB_1, ASduType.M_ST_NA_1, ASduType.M_ST_TA_1, ASduType.M_ST_TB_1 -> {
                    // TODO: test this!!! It's not really a SinglePointInformation
                    val singlePointWithQuality = informationElements[0] as IeSinglePointWithQuality
                    Record(
                        BooleanValue(singlePointWithQuality.isOn),
                        timestamp
                    )
                }

                ASduType.M_DP_NA_1, ASduType.M_DP_TA_1, ASduType.M_DP_TB_1 -> {
                    val doublePointWithQuality = informationElements[0] as IeDoublePointWithQuality
                    Record(
                        IntValue(doublePointWithQuality.doublePointInformation.ordinal),
                        timestamp
                    ) // TODO: check this solution. Is Enum to int correct?
                }

                ASduType.M_IT_NA_1, ASduType.M_IT_TA_1, ASduType.M_IT_TB_1 -> {
                    val binaryCounterReading = informationElements[0] as IeBinaryCounterReading
                    // TODO: change to String because of more values e.g. getSequenceNumber, isCarry, ... ?
                    Record(
                        IntValue(binaryCounterReading.counterReading),
                        timestamp
                    )
                }

                else -> {
                    logger.debug("Not supported Type Identification.")
                    Record(Flag.DRIVER_ERROR_CHANNEL_VALUE_TYPE_CONVERSION_EXCEPTION)
                }
            }
        }
    }

    private fun getQualityDescriptorAsRecord(
        dataType: String?, informationElements: Array<InformationElement>,
        typeIdentification: ASduType, timestamp: Long
    ): Record {
        var record: Record? = null
        val informationElement = informationElements[informationElements.size - 1]
        if (typeIdentification.id <= 14 || typeIdentification.id == 20 || typeIdentification.id >= 30 && typeIdentification.id <= 36) {
            record = quality(dataType, timestamp, informationElement)
        } else if (typeIdentification.id >= 15 && typeIdentification.id <= 16
            || typeIdentification.id == 37
        ) {
            record = binaryCounterReading(dataType, timestamp, informationElement)
        } else if (typeIdentification.id >= 17 && typeIdentification.id <= 19
            || typeIdentification.id >= 38 && typeIdentification.id <= 40
        ) {
            record = protectionQuality(dataType, timestamp, informationElement)
        }
        if (record == null) {
            logger.debug("Not supported Quality Descriptor.")
            record = Record(Flag.DRIVER_ERROR_CHANNEL_VALUE_TYPE_CONVERSION_EXCEPTION)
        }
        return record
    }

    private fun quality(dataType: String?, timestamp: Long, informationElement: InformationElement): Record? {
        val quality = informationElement as IeQuality
        var record: Record? = null
        when (dataType) {
            "iv" -> record = Record(BooleanValue(quality.isInvalid), timestamp)
            "sb" -> record = Record(BooleanValue(quality.isSubstituted), timestamp)
            "nt" -> record = Record(BooleanValue(quality.isNotTopical), timestamp)
            "bl" -> record = Record(BooleanValue(quality.isBlocked), timestamp)
            else -> {}
        }
        return record
    }

    private fun protectionQuality(dataType: String?, timestamp: Long, informationElement: InformationElement): Record? {
        val quality = informationElement as IeProtectionQuality
        var record: Record? = null
        when (dataType) {
            "iv" -> record = Record(BooleanValue(quality.isInvalid), timestamp)
            "sb" -> record = Record(BooleanValue(quality.isSubstituted), timestamp)
            "nt" -> record = Record(BooleanValue(quality.isNotTopical), timestamp)
            "bl" -> record = Record(BooleanValue(quality.isBlocked), timestamp)
            "ei" -> record = Record(BooleanValue(quality.isElapsedTimeInvalid), timestamp)
            else -> {}
        }
        return record
    }

    private fun binaryCounterReading(
        dataType: String?,
        timestamp: Long,
        informationElement: InformationElement
    ): Record? {
        val quality = informationElement as IeBinaryCounterReading
        var record: Record? = null
        val flags = quality.flags
        when (dataType) {
            "iv" -> record = Record(BooleanValue(flags.contains(IeBinaryCounterReading.Flag.INVALID)), timestamp)
            "ca" -> record = Record(
                BooleanValue(flags.contains(IeBinaryCounterReading.Flag.COUNTER_ADJUSTED)),
                timestamp
            )

            "cy" -> record = Record(BooleanValue(flags.contains(IeBinaryCounterReading.Flag.CARRY)), timestamp)
            else -> {}
        }
        return record
    }

    private fun handleMultipleElementObjects(
        aSdu: ASdu, timestamp: Long, channelAddress: ChannelAddress?,
        informationObject: InformationObject
    ): Record {
        val singleSize = sizeOfType(aSdu.typeIdentification)
        val arrayLength = singleSize * channelAddress!!.multiple()
        val byteBuffer = ByteBuffer.allocate(arrayLength)
        for (i in 0 until channelAddress.multiple()) {
            var informationElements: Array<InformationElement>?
            try {
                informationElements = handleSingleElementObject(aSdu, timestamp, channelAddress, informationObject)
                if (informationElements != null && informationElements.size > 0) {
                    val binaryStateInformation = informationElements[0] as IeBinaryStateInformation
                    byteBuffer.putInt(binaryStateInformation.value)
                } else {
                    logger.warn("Information element of IAO {} {}", channelAddress.ioa(), "is null or empty.")
                    return Record(Flag.UNKNOWN_ERROR)
                }
            } catch (e: ConfigurationException) {
                logger.warn(e.message)
                return Record(Flag.DRIVER_ERROR_CHANNEL_ADDRESS_SYNTAX_INVALID)
            }
        }
        val value = byteBuffer.array()
        return Record(ByteArrayValue(value), timestamp)
    }

    @Throws(ConfigurationException::class)
    private fun handleSingleElementObject(
        aSdu: ASdu, timestamp: Long,
        channelAddress: ChannelAddress?, informationObject: InformationObject
    ): Array<InformationElement>? {
        var informationElements: Array<InformationElement>? = null
        if (channelAddress!!.ioa() == informationObject.informationObjectAddress) {
            informationElements = if (aSdu.isSequenceOfElements) {
                sequenceOfElements(aSdu, timestamp, channelAddress, informationObject)
            } else {
                informationObject.informationElements[0]
            }
        }
        return informationElements
    }

    @Throws(ConfigurationException::class)
    private fun sequenceOfElements(
        aSdu: ASdu, timestamp: Long, channelAddress: ChannelAddress?,
        informationObject: InformationObject
    ): Array<InformationElement>? {
        var informationElements: Array<InformationElement>? = null
        informationElements = if (channelAddress!!.index() >= -1) {
            informationObject.informationElements[channelAddress.index()]
        } else {
            throw ConfigurationException(
                "Got ASdu with same TypeId, Common Address and IOA, but it is a Sequence Of Elements. For this index in ChannelAddress is needed."
            )
        }
        return informationElements
    }

    private fun sizeOfType(typeIdentification: ASduType): Int {
        var size = -1 // size in byte
        when (typeIdentification) {
            ASduType.M_BO_NA_1, ASduType.M_BO_TA_1, ASduType.M_BO_TB_1 -> size = 4
            else -> logger.debug("Not able to set Data Type {}  as multiple IOAs or Indices.", typeIdentification)
        }
        return size
    }

    private fun bytesToSignedInt32(bytes: ByteArray?, length: Int, isLitteleEndian: Boolean): Int {
        return if (length <= INT32_BYTE_LENGTH) {
            var returnValue = 0
            val lengthLoop = bytes!!.size - 1
            if (isLitteleEndian) {
                reverseByteOrder(bytes)
            }
            for (i in 0..lengthLoop) {
                val shift = length - i shl 3
                returnValue = (returnValue.toLong() or ((bytes[i].toInt() and 0xff).toLong() shl shift)).toInt()
            }
            returnValue
        } else {
            throw IllegalArgumentException(
                "Unable to convert bytes due to wrong number of bytes. Minimum 1 byte, maximum " + INT32_BYTE_LENGTH
                        + " bytes needed for conversion."
            )
        }
    }

    private fun reverseByteOrder(bytes: ByteArray?) {
        val indexLength = bytes!!.size - 1
        val halfLength = bytes.size / 2
        for (i in 0 until halfLength) {
            val index = indexLength - i
            val temp = bytes[i]
            bytes[i] = bytes[index]
            bytes[index] = temp
        }
    }
}
