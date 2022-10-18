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
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Rule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.rules.ExpectedException
import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.data.Record.value
import org.openmuc.framework.driver.spi.Connection
import org.openmuc.framework.driver.spi.ConnectionException
import java.io.IOException

class Iec61850DriverTest : Thread(), ServerEventListener {
    var port = TestHelper.getAvailablePort()
    var host = "127.0.0.1"
    var clientSap = ClientSap()
    var serverSap: ServerSap? = null
    var serversServerModel: ServerModel? = null

    @Rule
    var thrown = ExpectedException.none()
    @BeforeEach
    @Throws(SclParseException::class, IOException::class)
    fun initialize() {
        Iec61850Driver()
        clientSap.setTSelRemote(byteArrayOf(0, 1))
        clientSap.setTSelLocal(byteArrayOf(0, 0))

        // ---------------------------------------------------
        // -----------------start test server------------------
        serverSap = TestHelper.runServer(
            "src/test/resources/testOpenmuc.icd", port, serverSap, serversServerModel,
            this
        )
        start()
        println("IED Server is running")
        clientSap.setApTitleCalled(intArrayOf(1, 1, 999, 1))
    }

    @Test
    @Throws(ArgumentSyntaxException::class, ConnectionException::class)
    fun testConnectEmptySettings() {
        // test with valid syntax on the test server
        val testDeviceAdress = "$host:$port"
        val testSettings = ""
        val testIec61850Driver = Iec61850Driver()
        val testIec61850Connection = testIec61850Driver.connect(testDeviceAdress, testSettings)
        Assert.assertThat(
            testIec61850Connection, CoreMatchers.instanceOf(
                Connection::class.java
            )
        )
        testIec61850Connection!!.disconnect()
    }

    @Test
    @Throws(Exception::class)
    fun testConnectValidSettings1() {
        // Test 1
        val testDeviceAdress = "$host:$port"
        val testSettings = "-a 12 -lt 1 -rt 1"
        val testIec61850Driver = Iec61850Driver()
        val testIec61850Connection = testIec61850Driver.connect(testDeviceAdress, testSettings)
        Assert.assertThat(
            testIec61850Connection, CoreMatchers.instanceOf(
                Connection::class.java
            )
        )
        testIec61850Connection!!.disconnect()
    }

    @Test
    @Throws(Exception::class)
    fun testConnectValidSettings2() {
        // Test 1
        val testDeviceAdress = "$host:$port"
        val testSettings = "-a 12 -lt -rt "
        val testIec61850Driver = Iec61850Driver()
        val testIec61850Connection = testIec61850Driver.connect(testDeviceAdress, testSettings)
        Assert.assertThat(
            testIec61850Connection, CoreMatchers.instanceOf(
                Connection::class.java
            )
        )
        testIec61850Connection!!.disconnect()
    }

    @Test
    @Throws(Exception::class)
    fun testConnectInvalidSettings1() {
        // Test 1
        val testDeviceAdress = "$host:$port"
        val testSettings = "-a -lt 1 -rt 1"
        val exceptionMsg = "No authentication parameter was specified after the -a parameter"
        expectExeption(testDeviceAdress, testSettings, exceptionMsg, ArgumentSyntaxException())
    }

    @Test
    @Throws(Exception::class)
    fun testConnectInvalidSettings2() {
        // Test 1
        val testDeviceAdress = "$host:$port"
        val testSettings = "-a 12"
        val exceptionMsg = "Less than 4 or more than 6 arguments in the settings are not allowed."
        expectExeption(testDeviceAdress, testSettings, exceptionMsg, ArgumentSyntaxException())
    }

    @Test
    @Throws(Exception::class)
    fun testConnectInvalidSettings3() {
        // Test 1
        val testDeviceAdress = "$host:$port"
        val testSettings = "-b 12 -lt 1 -rt 1"
        val exceptionMsg = "Unexpected argument: -b"
        expectExeption(testDeviceAdress, testSettings, exceptionMsg, ArgumentSyntaxException())
    }

    @Test
    @Throws(Exception::class)
    fun testConnectInvalidAddress1() {
        // Test 1
        val testDeviceAdress = "$host:$port:foo"
        val testSettings = "-a 12 -lt 1 -rt 1"
        val exceptionMsg = "Invalid device address syntax."
        expectExeption(testDeviceAdress, testSettings, exceptionMsg, ArgumentSyntaxException())
    }

    @Test
    @Throws(Exception::class)
    fun testConnectInvalidAddress2() {
        // Test 1
        val testDeviceAdress = "a$host:$port"
        val testSettings = "-a 12 -lt 1 -rt 1"
        val exceptionMsg = "Unknown host: a127.0.0.1"
        expectExeption(testDeviceAdress, testSettings, exceptionMsg, ConnectionException())
    }

    @Test
    @Throws(Exception::class)
    fun testConnectInvalidAddress3() {
        // Test 1
        val testDeviceAdress = "$host:foo"
        val testSettings = "-a 12 -lt 1 -rt 1"
        val exceptionMsg = "The specified port is not an integer"
        expectExeption(testDeviceAdress, testSettings, exceptionMsg, ArgumentSyntaxException())
    }

    @AfterEach
    fun closeServerSap() {
        serverSap!!.stop()
        println("IED Server stopped")
    }

    override fun write(bdas: List<BasicDataAttribute>): List<ServiceError> {
        // TODO Auto-generated method stub
        return null
    }

    override fun serverStoppedListening(serverSAP: ServerSap) {
        // TODO Auto-generated method stub
    }

    private fun expectExeption(
        testDeviceAdress: String,
        testSettings: String,
        exeptionMsg: String,
        exception: Exception
    ) {
        val testIec61850Driver = Iec61850Driver()
        val e = Assertions.assertThrows(exception.javaClass) {
            val testIec61850Connection = testIec61850Driver.connect(testDeviceAdress, testSettings)
            testIec61850Connection!!.disconnect()
        }
        //        assertEquals(exeptionMsg, e.getMessage());
    }
}
