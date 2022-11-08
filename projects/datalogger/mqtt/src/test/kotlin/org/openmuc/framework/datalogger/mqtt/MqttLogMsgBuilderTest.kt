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
package org.openmuc.framework.datalogger.mqtt

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.openmuc.framework.data.DoubleValue
import org.openmuc.framework.data.Record
import org.openmuc.framework.datalogger.mqtt.dto.MqttLogChannel
import org.openmuc.framework.datalogger.mqtt.dto.MqttLogMsg
import org.openmuc.framework.datalogger.mqtt.util.MqttLogMsgBuilder
import org.openmuc.framework.datalogger.spi.LogChannel
import org.openmuc.framework.datalogger.spi.LoggingRecord
import org.openmuc.framework.lib.parser.openmuc.OpenmucParserServiceImpl

/**
 * Test checks if the correct log messages are generated for a given set of loggingRecords and given logger settings
 */
// FIXME refactor to remove some code duplication
class MqttLogMsgBuilderTest {
    // set true when developing tests, set false for gradle to avoid debug messages on "gradle build"
    private val isDebugEnabled = false
    @Test
    fun test_logSingleChannel_multipleFalse() {

        // 1. prepare channels to log - equal to logger.setChannelsToLog(...) call
        val channelsToLog = HashMap<String, MqttLogChannel>()
        channelsToLog[logChannelMockA.id] = MqttLogChannel(logChannelMockA)

        // 2. apply settings to logger
        val isLogMultiple = false

        // 3. prepare records which should be logged
        val records: MutableList<LoggingRecord> = ArrayList()
        records.add(LoggingRecord(logChannelMockA.id, record3!!))

        // 4. equal to calling logger.log(..) method
        val builder = MqttLogMsgBuilder(channelsToLog, parser)
        val messages = builder.buildLogMsg(records, isLogMultiple)
        printDebug(isDebugEnabled, messages)
        val controlString = TOPIC_1 + ": {\"timestamp\":" + TIMESTAMP + ",\"flag\":\"VALID\",\"value\":3.0}"
        Assertions.assertEquals(
            controlString, TOPIC_1 + ": " + String(
                messages[0].message
            )
        )
    }

    @Test
    fun test_logSingleChannel_multipleTrue() {

        // 1. prepare channels to log - equal to logger.setChannelsToLog(...) call
        val channelsToLog = HashMap<String, MqttLogChannel>()
        channelsToLog[logChannelMockA.id] = MqttLogChannel(logChannelMockA)

        // 2. apply settings to logger
        val isLogMultiple = true

        // 3. prepare records which should be logged
        val records: MutableList<LoggingRecord> = ArrayList()
        records.add(LoggingRecord(logChannelMockA.id, record3!!))

        // 4. equal to calling logger.log(..) method
        val builder = MqttLogMsgBuilder(channelsToLog, parser)
        val messages = builder.buildLogMsg(records, isLogMultiple)
        printDebug(isDebugEnabled, messages)
        val controlString = """
            $TOPIC_1: {"timestamp":$TIMESTAMP,"flag":"VALID","value":3.0}
            
            """.trimIndent()
        Assertions.assertEquals(
            controlString, TOPIC_1 + ": " + String(
                messages[0].message
            )
        )
    }

    @Test
    fun test_logTwoChannels_sameTopic_multipleFalse() {

        // 1. prepare channels to log - equal to logger.setChannelsToLog(...) call
        val channelsToLog = HashMap<String, MqttLogChannel>()
        channelsToLog[logChannelMockA.id] = MqttLogChannel(logChannelMockA)
        channelsToLog[logChannelMockB.id] = MqttLogChannel(logChannelMockB)

        // 2. apply settings to logger
        val isLogMultiple = false

        // 3. prepare records which should be logged
        val records: MutableList<LoggingRecord> = ArrayList()
        records.add(LoggingRecord(logChannelMockA.id, record3!!))
        records.add(LoggingRecord(logChannelMockB.id, record5!!))

        // 4. equal to calling logger.log(..) method
        val builder = MqttLogMsgBuilder(channelsToLog, parser)
        val messages = builder.buildLogMsg(records, isLogMultiple)
        printDebug(isDebugEnabled, messages)

        // expected size = 2 since isLogMultiple = false;
        Assertions.assertEquals(2, messages.size)

        // check content of the messages
        val referenceString1 = TOPIC_1 + ": {\"timestamp\":" + TIMESTAMP + ",\"flag\":\"VALID\",\"value\":3.0}"
        val referenceString2 = TOPIC_1 + ": {\"timestamp\":" + TIMESTAMP + ",\"flag\":\"VALID\",\"value\":5.0}"
        Assertions.assertEquals(
            referenceString1, TOPIC_1 + ": " + String(
                messages[0].message
            )
        )
        Assertions.assertEquals(
            referenceString2, TOPIC_1 + ": " + String(
                messages[1].message
            )
        )
    }

    @Test
    fun test_logTwoChannels_sameTopic_multipleTrue() {

        // 1. prepare channels to log - equal to logger.setChannelsToLog(...) call
        val channelsToLog = HashMap<String, MqttLogChannel>()
        channelsToLog[logChannelMockA.id] =
            MqttLogChannel(logChannelMockA)
        channelsToLog[logChannelMockB.id] =
            MqttLogChannel(logChannelMockB)

        // 2. apply settings to logger
        val isLogMultiple = true

        // 3. prepare records which should be logged
        val records: MutableList<LoggingRecord> = ArrayList()
        records.add(LoggingRecord(logChannelMockA.id, record3!!))
        records.add(LoggingRecord(logChannelMockB.id, record5!!))

        // 4. equal to calling logger.log(..) method
        val builder = MqttLogMsgBuilder(channelsToLog, parser)
        val messages = builder.buildLogMsg(records, isLogMultiple)
        printDebug(isDebugEnabled, messages)

        // expected size = 1 since isLogMultiple = true;
        Assertions.assertEquals(1, messages.size)

        // check content of the messages
        val sbRef = StringBuilder()
        sbRef.append(TOPIC_1 + ": {\"timestamp\":" + TIMESTAMP + ",\"flag\":\"VALID\",\"value\":3.0}")
        sbRef.append("\n")
        sbRef.append("{\"timestamp\":" + TIMESTAMP + ",\"flag\":\"VALID\",\"value\":5.0}")
        sbRef.append("\n")
        val referenceString = sbRef.toString()
        val sbTest = StringBuilder()
        sbTest.append(TOPIC_1 + ": ").append(String(messages[0].message))
        val testString = sbTest.toString()
        Assertions.assertEquals(referenceString, testString)
    }

    @Test
    fun test_logTwoChannels_differentTopic_multipleTrue() {

        // 1. prepare channels to log - equal to logger.setChannelsToLog(...) call
        val channelsToLog = HashMap<String, MqttLogChannel>()
        channelsToLog[logChannelMockA.id] =
            MqttLogChannel(logChannelMockA)
        channelsToLog[logChannelMockC.id] =
            MqttLogChannel(logChannelMockC)

        // 2. apply settings to logger
        val isLogMultiple = true

        // 3. prepare records which should be logged
        val records: MutableList<LoggingRecord> = ArrayList()
        records.add(LoggingRecord(logChannelMockA.id, record3!!))
        records.add(LoggingRecord(logChannelMockC.id, record7!!))

        // 4. equal to calling logger.log(..) method
        val builder = MqttLogMsgBuilder(channelsToLog, parser)
        Assertions.assertThrows(UnsupportedOperationException::class.java) {
            val messages = builder.buildLogMsg(records, isLogMultiple)
        }
    }

    private fun printDebug(isEnabled: Boolean, messages: List<MqttLogMsg>) {
        if (isEnabled) { // enable/disable debug output
            var i = 1
            for (msg in messages) {
                println("msgNr " + i++)
                println(msg.topic + " " + String(msg.message))
            }
        }
    }

    companion object {
        private lateinit var logChannelMockA: LogChannel
        private lateinit var logChannelMockB: LogChannel
        private lateinit var logChannelMockC: LogChannel
        private lateinit var logChannelMockD: LogChannel
        private var record3: Record? = null
        private var record5: Record? = null
        private var record7: Record? = null
        private var parser: OpenmucParserServiceImpl? = null
        private const val TIMESTAMP = 1599122299230L
        private const val TOPIC_1 = "topic1"
        private const val TOPIC_2 = "topic2"
        @JvmStatic
        @BeforeAll
        fun setup() {
            initChannelMocks()
            initDummyRecords()
            parser = OpenmucParserServiceImpl()
        }

        private fun initDummyRecords() {
            // some dummy records
            record3 = Record(DoubleValue(3.0), TIMESTAMP)
            record5 = Record(DoubleValue(5.0), TIMESTAMP)
            record7 = Record(DoubleValue(7.0), TIMESTAMP)
        }

        private fun initChannelMocks() {
            // prepare some channels for tests
            logChannelMockA = Mockito.mock(LogChannel::class.java)
            Mockito.`when`(logChannelMockA.id).thenReturn("ChannelA")
            Mockito.`when`(logChannelMockA.loggingSettings).thenReturn("mqttlogger:topic=" + TOPIC_1)

            // NOTE: same topic as channel1
            logChannelMockB = Mockito.mock(LogChannel::class.java)
            Mockito.`when`(logChannelMockB.id).thenReturn("ChannelB")
            Mockito.`when`(logChannelMockB.loggingSettings).thenReturn("mqttlogger:topic=" + TOPIC_1)
            logChannelMockC = Mockito.mock(LogChannel::class.java)
            Mockito.`when`(logChannelMockC.id).thenReturn("ChannelC")
            Mockito.`when`(logChannelMockC.loggingSettings).thenReturn("mqttlogger:topic=" + TOPIC_2)
            logChannelMockD = Mockito.mock(LogChannel::class.java)
            Mockito.`when`(logChannelMockD.id).thenReturn("ChannelD")
            Mockito.`when`(logChannelMockD.loggingSettings).thenReturn("mqttlogger:topic=topic4")
        }
    }
}
