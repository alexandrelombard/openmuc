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
import org.junit.jupiter.api.Test
import org.openmuc.framework.datalogger.mqtt.util.MqttChannelLogSettings.getTopic
import javax.management.openmbean.InvalidKeyException

class MqttChannelLogSettingsTest {
    @Test
    fun testCorrectTopic() {
        val logSettings = "amqplogger:queue=my/queue,setting=true,test=123;mqttlogger:topic=/my/topic/"
        val topic = getTopic(logSettings)
        Assertions.assertTrue(topic == "/my/topic/")
    }

    @Test
    fun testMissingMqttSettings() {
        Assertions.assertThrows(InvalidKeyException::class.java) {
            val logSettings = "amqplogger:queue=my/queue,setting=true,test=123"
            val topic = getTopic(logSettings)
        }
    }

    @Test
    fun testMissingMqttTopic() {
        Assertions.assertThrows(InvalidKeyException::class.java) {
            val logSettings = "mqttlogger"
            val topic = getTopic(logSettings)
        }
    }
}
