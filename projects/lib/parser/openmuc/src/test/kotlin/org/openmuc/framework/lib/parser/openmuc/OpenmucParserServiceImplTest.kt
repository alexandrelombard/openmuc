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

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openmuc.framework.data.*
import org.openmuc.framework.data.Record
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.openmuc.framework.parser.spi.ParserService
import org.openmuc.framework.parser.spi.SerializationException
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import java.util.stream.Collectors

/**
 * ToDo: add more tests for different datatypes
 */
internal class OpenmucParserServiceImplTest {
    private var parserService: ParserService? = null
    @BeforeEach
    private fun setupService() {
        parserService = OpenmucParserServiceImpl()
    }

    @Test
    @Throws(SerializationException::class)
    fun serializeMultipleRecords() {
        val sb = StringBuilder()
        sb.append("{\"timestamp\":1582722316,\"flag\":\"VALID\",\"value\":3.0}")
        sb.append("\n")
        sb.append("{\"timestamp\":1582722316,\"flag\":\"VALID\",\"value\":5.0}")
        sb.append("\n")
        val controlString = sb.toString()
        val doubleValue1: Value = DoubleValue(3.0)
        val timestamp1: Long = 1582722316
        val flag1 = Flag.VALID
        val record1 = Record(doubleValue1, timestamp1, flag1)
        val doubleValue2: Value = DoubleValue(5.0)
        val timestamp2: Long = 1582722316
        val flag2 = Flag.VALID
        val record2 = Record(doubleValue2, timestamp2, flag2)
        val openMucRecords: MutableList<LoggingRecord?> = ArrayList()
        openMucRecords.add(LoggingRecord("channel1", record1))
        openMucRecords.add(LoggingRecord("channel2", record2))
        val serializedRecord = parserService!!.serialize(openMucRecords)
        val serializedJson = String(serializedRecord!!)
        Assertions.assertEquals(controlString, serializedJson)
    }

    @Test
    @Throws(SerializationException::class)
    fun serializeDoubleValue() {
        val controlString = "{\"timestamp\":1582722316,\"flag\":\"VALID\",\"value\":3.0}"
        val doubleValue: Value = DoubleValue(3.0)
        val timestamp: Long = 1582722316
        val flag = Flag.VALID
        val record = Record(doubleValue, timestamp, flag)
        val serializedRecord = parserService!!.serialize(LoggingRecord("test", record))
        val serializedJson = String(serializedRecord!!)
        Assertions.assertEquals(controlString, serializedJson)
    }

    @Test
    @Throws(SerializationException::class)
    fun serializeStringValue() {
        val controlString = "{\"timestamp\":1582722316,\"flag\":\"VALID\",\"value\":\"test\"}"
        val doubleValue: Value = StringValue("test")
        val timestamp: Long = 1582722316
        val flag = Flag.VALID
        val record = Record(doubleValue, timestamp, flag)
        val serializedRecord = parserService!!.serialize(LoggingRecord("test", record))
        val serializedJson = String(serializedRecord!!)
        Assertions.assertEquals(controlString, serializedJson)
    }

    @Test
    @Throws(SerializationException::class)
    fun serializeByteArrayValue() {
        val controlString = "{\"timestamp\":1582722316,\"flag\":\"VALID\",\"value\":\"dGVzdA==\"}"
        val byteArrayValue: Value = ByteArrayValue("test".toByteArray())
        val timestamp: Long = 1582722316
        val flag = Flag.VALID
        val record = Record(byteArrayValue, timestamp, flag)
        val serializedRecord = parserService!!.serialize(LoggingRecord("test", record))
        val serializedJson = String(serializedRecord!!)
        Assertions.assertEquals(controlString, serializedJson)
    }

    @Test
    fun deserializeTestDoubleValue() {
        val inputString = "{\"timestamp\":1582722316,\"flag\":\"VALID\",\"value\":3.0}"
        val recordDes = parserService!!.deserialize(inputString.toByteArray(), ValueType.DOUBLE)
        Assertions.assertEquals(3.0, recordDes!!.value!!.asDouble())
    }

    @Test
    fun deserializeByteArrayValue() {
        val inputString = "{\"timestamp\":1582722316,\"flag\":\"VALID\",\"value\":\"dGVzdA==\"}"
        val recordDes = parserService!!.deserialize(inputString.toByteArray(), ValueType.BYTE_ARRAY)
        Assertions.assertEquals("test", String(recordDes!!.value!!.asByteArray()!!))
    }

    @Test
    fun deserializeTimestamp() {
        val inputString = "{\"timestamp\":1582722316,\"flag\":\"VALID\",\"value\":3.0}"
        val recordDes = parserService!!.deserialize(inputString.toByteArray(), ValueType.DOUBLE)
        Assertions.assertEquals(1582722316, recordDes!!.timestamp!!.toLong())
    }

    @Test
    fun deserializeFlag() {
        val inputString = "{\"timestamp\":1582722316,\"flag\":\"VALID\",\"value\":3.0}"
        val recordDes = parserService!!.deserialize(inputString.toByteArray(), ValueType.DOUBLE)
        Assertions.assertEquals("VALID", recordDes!!.flag.name)
    }

    @Test
    fun serialisationAndDeserialisationAreThreadSafe() {
        // this is pretty hard to test (at least I (dwerner) could not figure out how to in 1h, so I'm giving up now)
        // the methods should be thread safe if:
        // 1. there are no members in the class (making the methods inherited by ReactParser effectively static and thus
        // thread safe)
        // 2. the inherited methods have the 'synchronized' keyword -> looking for all public methods here, just to be
        // safe
        val members = Arrays.stream(
            OpenmucParserServiceImpl::class.java.declaredFields
        )
            .filter { f: Field -> !Modifier.isStatic(f.modifiers) }
            .collect(Collectors.toSet())
        if (members.isEmpty()) {
            println("OpenmucParserServiceImpl does not have non-static members and should be thread safe")
            return
        } else {
            val publicMethods = Arrays.stream(
                OpenmucParserServiceImpl::class.java.declaredMethods
            )
                .filter { m: Method -> Modifier.isPublic(m.modifiers) }
                .collect(Collectors.toSet())
            for (method in publicMethods) {
                Assertions.assertTrue(
                    Modifier.isSynchronized(method.modifiers),
                    "Method '$method' should have the 'synchronized' keyword"
                )
            }
        }
    }
}
