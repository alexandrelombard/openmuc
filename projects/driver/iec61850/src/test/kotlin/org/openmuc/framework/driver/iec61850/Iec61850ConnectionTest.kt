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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openmuc.framework.data.*
import org.openmuc.framework.dataaccess.Channel
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.framework.driver.spi.ChannelValueContainer
import org.openmuc.framework.driver.spi.ConnectionException
import java.io.IOException
import java.net.InetAddress
import javax.naming.ConfigurationException

class Iec61850ConnectionTest : Thread(), ClientEventListener, ServerEventListener {
    var port = TestHelper.availablePort
    var host = "127.0.0.1"
    var clientSap = ClientSap()
    var serverSap: ServerSap? = null
    var clientAssociation: ClientAssociation? = null
    var serversServerModel: ServerModel? = null
    @BeforeEach
    @Throws(SclParseException::class, IOException::class)
    fun initialize() {
        clientSap.setTSelRemote(byteArrayOf(0, 1))
        clientSap.setTSelLocal(byteArrayOf(0, 0))

        // ---------------------------------------------------
        // ----------------- Start test server------------------
        serverSap = TestHelper.runServer(
            "src/test/resources/testOpenmuc.icd", port, serverSap, serversServerModel,
            this
        )
        start()
        println("IED Server is running")
        clientSap.setApTitleCalled(intArrayOf(1, 1, 999, 1))
    }

    @Test
    @Throws(
        IOException::class,
        ServiceError::class,
        ConfigurationException::class,
        ConfigurationException::class,
        SclParseException::class,
        InterruptedException::class,
        UnsupportedOperationException::class,
        ConnectionException::class
    )
    fun testScanForChannels() {
        println("Attempting to connect to server $host on port $port")
        val clientAssociation = clientSap.associate(InetAddress.getByName(host), port, null, this)
        this.clientAssociation = clientAssociation
        val serverModel = SclParser.parse("src/test/resources/testOpenmuc.icd")[0]
        clientAssociation.setServerModel(serverModel)
        getAllBdas(serverModel, clientAssociation)
        val testConnection = Iec61850Connection(clientAssociation, serverModel)
        val testChannelScanList = testConnection.scanForChannels("")
        for (i in 14..22) {
            print(
                """
    ${testChannelScanList!![i]!!.channelAddress}
    
    """.trimIndent()
            )
            Assertions.assertEquals("BYTE_ARRAY", testChannelScanList[i]!!.valueType.toString())
        }
        Assertions.assertEquals("LONG", testChannelScanList!![23]!!.valueType.toString())
        Assertions.assertEquals("BOOLEAN", testChannelScanList[24]!!.valueType.toString())
        Assertions.assertEquals("FLOAT", testChannelScanList[25]!!.valueType.toString())
        Assertions.assertEquals("DOUBLE", testChannelScanList[26]!!.valueType.toString())
        Assertions.assertEquals("BYTE", testChannelScanList[27]!!.valueType.toString())
        Assertions.assertEquals("SHORT", testChannelScanList[28]!!.valueType.toString())
        Assertions.assertEquals("SHORT", testChannelScanList[29]!!.valueType.toString())
        Assertions.assertEquals("INTEGER", testChannelScanList[30]!!.valueType.toString())
        Assertions.assertEquals("INTEGER", testChannelScanList[31]!!.valueType.toString())
        Assertions.assertEquals("LONG", testChannelScanList[32]!!.valueType.toString())
        Assertions.assertEquals("LONG", testChannelScanList[33]!!.valueType.toString())
        Assertions.assertEquals("BYTE_ARRAY", testChannelScanList[34]!!.valueType.toString())
    }

    @Test
    @Throws(
        IOException::class,
        ServiceError::class,
        ConfigurationException::class,
        ConfigurationException::class,
        SclParseException::class,
        InterruptedException::class,
        UnsupportedOperationException::class,
        ConnectionException::class
    )
    fun testRead() {
        println("Attempting to connect to server $host on port $port")
        val clientAssociation = clientSap.associate(InetAddress.getByName(host), port, null, this)
        this.clientAssociation = clientAssociation
        val serverModel = SclParser.parse("src/test/resources/testOpenmuc.icd")[0]
        clientAssociation.setServerModel(serverModel)
        getAllBdas(serverModel, clientAssociation)
        // ------------SCAN FOR CHANNELS-------------------
        val testIec61850Connection = Iec61850Connection(clientAssociation, serverModel)
        val testRecordContainers: MutableList<ChannelRecordContainer> = arrayListOf()
        val testChannelScanList = testIec61850Connection.scanForChannels("")
        for (i in 14..33) {
            testRecordContainers.add(
                ChannelRecordContainerImpl(
                    testChannelScanList[i]!!.channelAddress
                )
            )
        }
        // ----------READ-------------------
        testIec61850Connection.read(testRecordContainers, null, "")
        print(
            """
    recordContainer:${testRecordContainers[0].record}
    
    """.trimIndent()
        )
        Assertions.assertEquals("[64]", testRecordContainers[0].record!!.value.toString())
        print(
            """
    recordContainer:${testRecordContainers[0].record}
    
    """.trimIndent()
        )
        Assertions.assertEquals("[64]", testRecordContainers[0].record!!.value.toString())
    }

    @Test
    @Throws(
        IOException::class,
        ServiceError::class,
        ConfigurationException::class,
        ConfigurationException::class,
        SclParseException::class,
        InterruptedException::class,
        UnsupportedOperationException::class,
        ConnectionException::class
    )
    fun testWrite() {
        println("Attempting to connect to server $host on port $port")
        val clientAssociation = clientSap.associate(InetAddress.getByName(host), port, null, this)
        this.clientAssociation = clientAssociation
        val serverModel = SclParser.parse("src/test/resources/testOpenmuc.icd")[0]
        clientAssociation.setServerModel(serverModel)
        getAllBdas(serverModel, clientAssociation)

        // ------------SCAN FOR CHANNELS-------------------
        val testIec61850Connection = Iec61850Connection(clientAssociation, serverModel)
        val testChannelScanList = testIec61850Connection.scanForChannels("")

        // ----------WRITE-----------------
        val testChannelValueContainers: MutableList<ChannelValueContainer> = arrayListOf()
        val newValue = byteArrayOf(0x44)
        testChannelValueContainers.add(
            ChannelValueContainerImpl(
                testChannelScanList[14]!!.channelAddress,
                ByteArrayValue(newValue)
            )
        )
        testChannelValueContainers.add(
            ChannelValueContainerImpl(
                testChannelScanList[25]!!.channelAddress,
                FloatValue(12.5.toFloat())
            )
        )
        testChannelValueContainers.add(
            ChannelValueContainerImpl(testChannelScanList[24]!!.channelAddress, BooleanValue(true))
        )
        testIec61850Connection.write(testChannelValueContainers, null)

        // Create record container to read the changes made by "write"
        val testRecordContainers: MutableList<ChannelRecordContainer> = ArrayList()
        for (i in 0..33) {
            testRecordContainers.add(
                ChannelRecordContainerImpl(
                    testChannelScanList[i]!!.channelAddress
                )
            )
        }
        testIec61850Connection.read(testRecordContainers, null, "")
        Assertions.assertEquals("[68]", testRecordContainers[14].record!!.value.toString())
        Assertions.assertEquals("12.5", testRecordContainers[25].record!!.value.toString())
        Assertions.assertEquals("true", testRecordContainers[24].record!!.value.toString())
    }

    @AfterEach
    fun closeServerSap() {
        clientAssociation!!.disconnect()
        serverSap!!.stop()
    }

    @Throws(ServiceError::class, IOException::class)
    private fun getAllBdas(serverModel: ServerModel, clientAssociation: ClientAssociation?) {
        for (ld in serverModel) {
            for (ln in ld) {
                getDataRecursive(ln, clientAssociation)
            }
        }
    }

    override fun write(bdas: List<BasicDataAttribute>): List<ServiceError>? {
        // TODO Auto-generated method stub
        return null
    }

    override fun serverStoppedListening(serverSAP: ServerSap) {
        // TODO Auto-generated method stub
    }

    override fun newReport(report: Report) {
        // TODO Auto-generated method stub
    }

    override fun associationClosed(e: IOException) {
        // TODO Auto-generated method stub
    }

    class ChannelRecordContainerImpl(override val channelAddress: String) : ChannelRecordContainer {
        override var channelHandle: Any? = null
        override var record: Record? = null

        override val channel: Channel?
            get() = null

        override fun copy(): ChannelRecordContainer? {
            return null
        }
    }

    private class ChannelValueContainerImpl(override val channelAddress: String, override val value: Value) :
        ChannelValueContainer {
        override var flag: Flag? = null

        override var channelHandle: Any?
            get() = null
            set(_) {}
    }

    companion object {
        @Throws(ServiceError::class, IOException::class)
        private fun getDataRecursive(modelNode: ModelNode, clientAssociation: ClientAssociation?) {
            if (modelNode.children == null) {
                return
            }
            for (childNode in modelNode) {
                val fcChildNode = childNode as FcModelNode
                if (fcChildNode.fc != Fc.CO) {
                    println("calling GetDataValues(" + childNode.getReference() + ")")
                    clientAssociation!!.getDataValues(fcChildNode)
                }
                // clientAssociation.setDataValues(fcChildNode);
                getDataRecursive(childNode, clientAssociation)
            }
        }
    }
}
