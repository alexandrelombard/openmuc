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
package org.openmuc.framework.lib.mqtt

import com.hivemq.client.internal.mqtt.MqttClientConfig
import com.hivemq.client.mqtt.lifecycle.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.stubbing.Answer
import org.openmuc.framework.lib.mqtt.MqttConnection
import java.io.File
import java.io.IOException
import java.nio.file.FileSystems

@ExtendWith(MockitoExtension::class)
class MqttWriterTest {
    private var mqttWriter: MqttWriter? = null
    @BeforeEach
    fun setup() {
        val connection = mock(MqttConnection::class.java)
        Mockito.doAnswer(Answer<Void?> { invocation: InvocationOnMock ->
            connectedListener = invocation.getArgument(0)
            null
        }).`when`(connection).addConnectedListener(
            ArgumentMatchers.any(
                MqttClientConnectedListener::class.java
            )
        )
        Mockito.doAnswer(Answer<Void?> { invocation: InvocationOnMock ->
            disconnectedListener = invocation.getArgument(0)
            null
        }).`when`(connection).addDisconnectedListener(
            ArgumentMatchers.any(
                MqttClientDisconnectedListener::class.java
            )
        )
        Mockito.`when`(connection.settings)
            .thenReturn(MqttSettings("localhost", 1883, null, "", false, 1, 1, 2, 5000, 10, DIRECTORY))
        mqttWriter = MqttWriterStub(connection)
        connectedListener!!.onConnected { mock(MqttClientConfig::class.java) }
    }

    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun testWriteWithReconnectionAndSimulatedDisconnection() {
        val disconnectedContext = mock(
            MqttClientDisconnectedContext::class.java
        )
        val reconnector = mock(
            MqttClientReconnector::class.java
        )
        Mockito.`when`(reconnector.isReconnect).thenReturn(true)
        val config = mock(
            MqttClientConfig::class.java
        )
        Mockito.`when`(config.serverHost).thenReturn("test")
        val cause = mock(Throwable::class.java)
        Mockito.`when`(cause.message).thenReturn("test")
        val source = MqttDisconnectSource.USER
        Mockito.`when`(disconnectedContext.reconnector).thenReturn(reconnector)
        Mockito.`when`(disconnectedContext.clientConfig).thenReturn(config)
        Mockito.`when`(disconnectedContext.cause).thenReturn(cause)
        Mockito.`when`(disconnectedContext.source).thenReturn(source)
        disconnectedListener!!.onDisconnected(disconnectedContext)
        val topic = "topic1"
        val file = FileSystems.getDefault().getPath(DIRECTORY, "topic1", "buffer.0.log").toFile()
        val file1 = FileSystems.getDefault().getPath(DIRECTORY, "topic1", "buffer.1.log").toFile()
        val message300bytes = ("Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula "
                + "eget dolor. Aenean massa. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur "
                + "ridiculus mus. Donec quam felis, ultricies nec, pellentesque eu, pretium quis, sem. Nulla consequat"
                + " massa quis enim. Donec.")
        mqttWriter!!.write(topic, message300bytes.toByteArray()) // 300
        mqttWriter!!.write(topic, message300bytes.toByteArray()) // 600
        mqttWriter!!.write(topic, message300bytes.toByteArray()) // 900
        // buffer limit not yet reached
        // assertFalse(file.exists() || file1.exists());
        mqttWriter!!.write(topic, message300bytes.toByteArray()) // 1200 > 1024 write to file => 0
        // buffer limit reached, first file written
        Assertions.assertTrue(file.exists() && !file1.exists())
        mqttWriter!!.write(topic, message300bytes.toByteArray()) // 300
        mqttWriter!!.write(topic, message300bytes.toByteArray()) // 600
        mqttWriter!!.write(topic, message300bytes.toByteArray()) // 900
        mqttWriter!!.write(topic, message300bytes.toByteArray()) // 1200 > 1024 write to file
        // buffer limit reached, second file written
        Assertions.assertTrue(file.exists() && file1.exists())

        // simulate connection
        connectedListener!!.onConnected { mock(MqttClientConfig::class.java) }

        // wait for recovery thread to terminate
        Thread.sleep(1000)

        // files should be emptied and therefore removed
        Assertions.assertFalse(file.exists() || file1.exists())
    }

    companion object {
        private const val DIRECTORY = "/tmp/openmuc/mqtt_writer_test"
        private var connectedListener: MqttClientConnectedListener? = null
        private var disconnectedListener: MqttClientDisconnectedListener? = null
        @AfterAll
        fun cleanUp() {
            deleteDirectory(FileSystems.getDefault().getPath(DIRECTORY).toFile())
        }

        private fun deleteDirectory(directory: File) {
            if (!directory.exists()) {
                return
            }
            for (child in directory.listFiles()) {
                if (child.isDirectory) {
                    deleteDirectory(child)
                } else {
                    child.delete()
                }
            }
            directory.delete()
        }
    }
}
