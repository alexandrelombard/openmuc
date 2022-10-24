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
package org.openmuc.framework.lib.parser.openmuc

import com.google.gson.*
import org.openmuc.framework.data.*
import org.openmuc.framework.data.Record
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.openmuc.framework.parser.spi.ParserService
import org.openmuc.framework.parser.spi.SerializationException
import org.slf4j.LoggerFactory
import java.lang.reflect.Type
import java.util.*

/**
 * Parser implementation for OpenMUC to OpenMUC communication e.g. for the AMQP driver.
 */
class OpenmucParserServiceImpl : ParserService {
    private val logger = LoggerFactory.getLogger(OpenmucParserServiceImpl::class.java)
    private val gson: Gson
    private var valueType: ValueType? = null

    init {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(Record::class.java, RecordInstanceCreator())
        gsonBuilder.registerTypeAdapter(Value::class.java, ValueDeserializer())
        gsonBuilder.registerTypeAdapter(Record::class.java, RecordAdapter())
        gsonBuilder.disableHtmlEscaping()
        gson = gsonBuilder.create()
    }

    @Synchronized
    override fun serialize(openMucRecord: LoggingRecord?): ByteArray? {
        val serializedString = gson.toJson(openMucRecord!!.record)
        return serializedString.toByteArray()
    }

    @Synchronized
    @Throws(SerializationException::class)
    override fun serialize(openMucRecords: List<LoggingRecord?>?): ByteArray? {
        val sb = StringBuilder()
        for (openMucRecord in openMucRecords!!) {
            sb.append(String(serialize(openMucRecord)!!))
            sb.append('\n')
        }
        return sb.toString().toByteArray()
    }

    @Synchronized
    override fun deserialize(byteArray: ByteArray?, valueType: ValueType?): Record? {
        this.valueType = valueType
        return gson.fromJson(String(byteArray!!), Record::class.java)
    }

    private inner class RecordInstanceCreator : InstanceCreator<Record> {
        override fun createInstance(type: Type): Record {
            return Record(Flag.DISABLED)
        }
    }

    private inner class RecordAdapter : JsonSerializer<Record> {
        override fun serialize(record: Record, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()
            val value = record.value
            obj.addProperty("timestamp", record.timestamp)
            obj.addProperty("flag", record.flag.toString())
            if (value != null && record.flag === Flag.VALID) {
                val valueString = "value"
                when (value.valueType) {
                    ValueType.BOOLEAN -> obj.addProperty(valueString, record.value!!.asBoolean())
                    ValueType.BYTE -> obj.addProperty(valueString, record.value!!.asByte())
                    ValueType.BYTE_ARRAY -> obj.addProperty(
                        valueString, Base64.getEncoder().encodeToString(
                            record.value!!.asByteArray()
                        )
                    )

                    ValueType.DOUBLE -> obj.addProperty(valueString, record.value!!.asDouble())
                    ValueType.FLOAT -> obj.addProperty(valueString, record.value!!.asFloat())
                    ValueType.INTEGER -> obj.addProperty(valueString, record.value!!.asInt())
                    ValueType.LONG -> obj.addProperty(valueString, record.value!!.asLong())
                    ValueType.SHORT -> obj.addProperty(valueString, record.value!!.asShort())
                    ValueType.STRING -> obj.addProperty(valueString, record.value!!.asString())
                    else -> {}
                }
            }
            return obj
        }
    }

    private inner class ValueDeserializer : JsonDeserializer<Value?> {
        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Value? {
            return when (valueType) {
                ValueType.BOOLEAN -> BooleanValue(json.asBoolean)
                ValueType.BYTE_ARRAY -> ByteArrayValue(
                    Base64.getDecoder().decode(json.asString)
                )

                ValueType.BYTE -> ByteValue(json.asByte)
                ValueType.DOUBLE -> DoubleValue(json.asDouble)
                ValueType.FLOAT -> FloatValue(json.asFloat)
                ValueType.INTEGER -> IntValue(json.asInt)
                ValueType.LONG -> LongValue(json.asLong)
                ValueType.SHORT -> ShortValue(json.asShort)
                ValueType.STRING -> StringValue(json.asString)
                else -> {
                    logger.warn("Unsupported ValueType: {}", valueType)
                    null
                }
            }
        }
    }
}
