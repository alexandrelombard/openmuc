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
package org.openmuc.framework.driver.mbus

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.openmuc.framework.data.*
import org.openmuc.framework.dataaccess.Channel
import org.openmuc.framework.driver.mbus.Helper.hexToBytes
import org.openmuc.framework.driver.spi.ChannelRecordContainer
import org.openmuc.framework.driver.spi.ConnectionException
import org.openmuc.jmbus.MBusConnection
import org.openmuc.jmbus.SecondaryAddress
import org.openmuc.jmbus.VariableDataStructure
import org.openmuc.jrxtx.SerialPortTimeoutException
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.io.IOException
import java.util.*

@RunWith(PowerMockRunner::class)
@PrepareForTest(DriverConnection::class)
class DriverConnectionTest {
    private val delay = 100 // in ms
    private val interfaces: Map<String, ConnectionInterface> = HashMap()

    @Throws(Exception::class)
    private fun newConnection(mBusAdresse: String): DriverConnection {
        val con = PowerMockito.mock(MBusConnection::class.java)
        val vds = VariableDataStructure(
            NZR_ANSWER,
            6,
            NZR_ANSWER.size - 6,
            null,
            null
        )
        vds.decode()
        PowerMockito.`when`(con.read(ArgumentMatchers.anyInt())).thenReturn(vds)
        val serialIntervace = ConnectionInterface(con, mBusAdresse, delay, interfaces)
        serialIntervace.increaseConnectionCounter()
        val deviceAddressTokens =
            mBusAdresse.trim { it <= ' ' }.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val mBusAddress: Int
        var secondaryAddress: SecondaryAddress? = null
        if (deviceAddressTokens[1].length == 16) {
            mBusAddress = 0xfd
            val addressData =
                hexToBytes(deviceAddressTokens[1])
            secondaryAddress = SecondaryAddress.newFromLongHeader(addressData, 0)
        } else {
            mBusAddress = Integer.decode(deviceAddressTokens[1])
        }
        return DriverConnection(serialIntervace, mBusAddress, secondaryAddress, delay)
    }

    @Test
    @Throws(Exception::class)
    fun testScanForChannels() {
        val mBusConnection = newConnection("/dev/ttyS100:5")
        mBusConnection.disconnect()
        Assert.assertEquals(ValueType.LONG, mBusConnection.scanForChannels(null)!![0]!!.valueType)
    }

    @Test
    @Throws(Exception::class)
    fun testScanForChannelsByteArray() {
        val con = PowerMockito.mock(MBusConnection::class.java)
        val vds = VariableDataStructure(
            SIEMENS_UH50_ANSWER, 6, SIEMENS_UH50_ANSWER.size - 6,
            null, null
        )
        vds.decode()
        PowerMockito.`when`(con.read(ArgumentMatchers.anyInt())).thenReturn(vds)
        val serialIntervace = ConnectionInterface(con, "/dev/ttyS100:5", delay, interfaces)
        serialIntervace.increaseConnectionCounter()
        val deviceAddressTokens = arrayOf("/dev/ttyS100", "5")
        val mBusConnection = DriverConnection(serialIntervace, deviceAddressTokens[1].toInt(), null, delay)
        mBusConnection.disconnect()
        val scanForChannels = mBusConnection.scanForChannels(null)
        for (info in scanForChannels!!) {
            println(info!!.description + " " + info.unit)
        }
        val actual = mBusConnection.scanForChannels(null)!![22]!!.valueType
        Assert.assertEquals(ValueType.LONG, actual)
    }

    @Test
    @Throws(Exception::class)
    fun testReadWithSec() {
        val con = newConnection("/dev/ttyS100:74973267a7320404")
        val records = Arrays.asList(
            newChannelRecordContainer("04:03"),
            newChannelRecordContainer("02:fd5b")
        )
        con.read(records, null, null)
    }

    @Test
    @Throws(Exception::class)
    fun testRead() {
        val con = newConnection("/dev/ttyS100:5")
        val records = Arrays.asList(
            newChannelRecordContainer("04:03"),
            newChannelRecordContainer("02:fd5b")
        )
        con.read(records, null, null)
    }

    @Test
    @Throws(Exception::class)
    fun testReadBcdDateLong() {
        val con = PowerMockito.mock(MBusConnection::class.java)
        val vds = VariableDataStructure(
            SIEMENS_UH50_ANSWER, 6, SIEMENS_UH50_ANSWER.size - 6,
            null, null
        )
        vds.decode()
        PowerMockito.`when`(con.read(ArgumentMatchers.anyInt())).thenReturn(vds)
        val serialIntervace = ConnectionInterface(con, "/dev/ttyS100:5", delay, interfaces)
        serialIntervace.increaseConnectionCounter()
        val deviceAddressTokens = arrayOf("/dev/ttyS100", "5")
        val mBusConnection = DriverConnection(serialIntervace, deviceAddressTokens[1].toInt(), null, delay)
        val records: MutableList<ChannelRecordContainer?> = LinkedList()
        records.add(newChannelRecordContainer("09:74"))
        records.add(newChannelRecordContainer("42:6c"))
        records.add(newChannelRecordContainer("8c01:14"))
        mBusConnection.read(records, null, null)
    }

    @Test
    @Throws(Exception::class)
    fun testReadWrongChannelAddressAtContainer() {
        val crc: MutableList<ChannelRecordContainer?> = LinkedList()
        val mBusConnection = newConnection("/dev/ttyS100:5")
        crc.add(newChannelRecordContainer("X04:03:5ff0"))
        mBusConnection.read(crc, null, null)
        Assert.assertEquals(Flag.VALID, crc[0]!!.record!!.flag)
    }

    @Test
    @Throws(Exception::class)
    fun testReadWithX() {
        val mBusConnection = newConnection("/dev/ttyS100:5")
        val records = Arrays.asList(newChannelRecordContainer("X04:03"))
        mBusConnection.read(records, null, null)
    }

    @Test
    @Throws(Exception::class)
    fun testReadAndDisconnect() {
        val mBusConnection = newConnection("/dev/ttyS100:5")
        val records = Arrays.asList(newChannelRecordContainer("X04:03"))
        mBusConnection.read(records, null, null)
        mBusConnection.disconnect()
    }

    @Test
    @Throws(Exception::class)
    fun testDisconnect() {
        val mBusConnection = newConnection("/dev/ttyS100:5")
        mBusConnection.disconnect()
        mBusConnection.disconnect()
    }

    @Test(expected = ConnectionException::class)
    @Throws(Exception::class)
    fun testDisconnectRead() {
        val mBusConnection = newConnection("/dev/ttyS100:5")
        mBusConnection.disconnect()
        val crc: List<ChannelRecordContainer?> = emptyList<ChannelRecordContainer>()
        mBusConnection.read(crc, null, null)
    }

    @Test
    @Throws(Exception::class)
    fun testReadThrowsIOException() {
        val con = PowerMockito.mock(MBusConnection::class.java)
        val vds = VariableDataStructure(NZR_ANSWER, 6, NZR_ANSWER.size - 6, null, null)
        vds.decode()
        PowerMockito.`when`(con.read(ArgumentMatchers.anyInt())).thenThrow(IOException())
        val serialIntervace = ConnectionInterface(con, "/dev/ttyS100:5", delay, interfaces)
        serialIntervace.increaseConnectionCounter()
        val deviceAddressTokens = arrayOf("/dev/ttyS100", "5")
        val address = deviceAddressTokens[1].toInt()
        val driverCon = DriverConnection(serialIntervace, address, null, delay)
        val records = Arrays.asList(newChannelRecordContainer("04:03"))
        driverCon.read(records, null, null)
        val actualFlag = records[0]!!.record!!.flag
        Assert.assertEquals(Flag.DRIVER_ERROR_TIMEOUT, actualFlag)
    }

    @Test
    @Throws(Exception::class)
    fun testReadThrowsTimeoutException() {
        val con = PowerMockito.mock(MBusConnection::class.java)
        val vds = VariableDataStructure(NZR_ANSWER, 6, NZR_ANSWER.size - 6, null, null)
        vds.decode()
        PowerMockito.`when`(con.read(ArgumentMatchers.anyInt())).thenThrow(SerialPortTimeoutException())
        val serialIntervace = ConnectionInterface(con, "/dev/ttyS100:5", delay, interfaces)
        serialIntervace.increaseConnectionCounter()
        val deviceAddressTokens = arrayOf("/dev/ttyS100", "5")
        val address = deviceAddressTokens[1].toInt()
        val driverCon = DriverConnection(serialIntervace, address, null, delay)
        val records = Arrays.asList(newChannelRecordContainer("04:03"))
        driverCon.read(records, null, null)
        Assert.assertEquals(Flag.DRIVER_ERROR_TIMEOUT, records[0]!!.record!!.flag)
    }

    @Test(expected = ConnectionException::class)
    @Throws(Exception::class)
    fun testScanThrowsTimeoutException() {
        val con = PowerMockito.mock(MBusConnection::class.java)
        val vds = VariableDataStructure(NZR_ANSWER, 6, NZR_ANSWER.size - 6, null, null)
        vds.decode()
        PowerMockito.`when`(con.read(ArgumentMatchers.anyInt())).thenThrow(SerialPortTimeoutException())
        val serialIntervace = ConnectionInterface(con, "/dev/ttyS100:5", delay, interfaces)
        serialIntervace.increaseConnectionCounter()
        val deviceAddressTokens = arrayOf("/dev/ttyS100", "5")
        val driverCon = DriverConnection(
            serialIntervace, deviceAddressTokens[1].toInt(),
            null, delay
        )
        driverCon.scanForChannels(null)
    }

    @Test(expected = ConnectionException::class)
    @Throws(Exception::class)
    fun testScanThrowsIOException() {
        val con = PowerMockito.mock(MBusConnection::class.java)
        val vds = VariableDataStructure(NZR_ANSWER, 6, NZR_ANSWER.size - 6, null, null)
        vds.decode()
        PowerMockito.`when`(con.read(ArgumentMatchers.anyInt())).thenThrow(IOException())
        val serialIntervace = ConnectionInterface(con, "/dev/ttyS100:5", delay, interfaces)
        serialIntervace.increaseConnectionCounter()
        val deviceAddressTokens = arrayOf("/dev/ttyS100", "5")
        val mBusConnection = DriverConnection(serialIntervace, deviceAddressTokens[1].toInt(), null, delay)
        mBusConnection.scanForChannels(null)
    }

    companion object {
        private val NZR_ANSWER = byteArrayOf(
            104, 50, 50, 104, 8, 5, 114, 8, 6, 16, 48, 82, 59, 1, 2, 2, 0, 0, 0, 4,
            3, -25, 37, 0, 0, 4, -125, 127, -25, 37, 0, 0, 2, -3, 72, 54, 9, 2, -3, 91, 0, 0, 2, 43, 0, 0, 12, 120, 8,
            6, 16, 48, 15, 63, -79, 22
        )
        private val SIEMENS_UH50_ANSWER = byteArrayOf(
            0x68,
            0xf8.toByte(),
            0xf8.toByte(),
            0x68,
            0x8,
            100.toByte(),
            0x72,
            0x74,
            0x97.toByte(),
            0x32,
            0x67,
            0xa7.toByte(),
            0x32,
            0x4,
            0x4,
            0x0,
            0x0,
            0x0,
            0x0,
            0x9,
            0x74,
            0x4,
            0x9,
            0x70,
            0x4,
            0x0c,
            0x6,
            0x44,
            0x5,
            0x5,
            0x0,
            0x0c,
            0x14,
            0x69,
            0x37,
            0x32,
            0x0,
            0x0b,
            0x2d,
            0x71,
            0x0,
            0x0,
            0x0b,
            0x3b,
            0x50,
            0x13,
            0x0,
            0x0a,
            0x5b,
            0x43,
            0x0,
            0x0a,
            0x5f,
            0x39,
            0x0,
            0x0a,
            0x62,
            0x46,
            0x0,
            0x4c,
            0x14,
            0x0,
            0x0,
            0x0,
            0x0,
            0x4c,
            0x6,
            0x0,
            0x0,
            0x0,
            0x0,
            0x0c,
            0x78,
            0x74,
            0x97.toByte(),
            0x32,
            0x67,
            0x89.toByte(),
            0x10,
            0x71,
            0x60,
            0x9b.toByte(),
            0x10,
            0x2d,
            0x62,
            0x5,
            0x0,
            0xdb.toByte(),
            0x10,
            0x2d,
            0x0,
            0x0,
            0x0,
            0x9b.toByte(),
            0x10,
            0x3b,
            0x20,
            0x22,
            0x0,
            0x9a.toByte(),
            0x10,
            0x5b,
            0x76,
            0x0,
            0x9a.toByte(),
            0x10,
            0x5f,
            0x66,
            0x0,
            0x0c,
            0x22,
            0x62,
            0x32,
            0x0,
            0x0,
            0x3c,
            0x22,
            0x56,
            0x4,
            0x0,
            0x0,
            0x7c,
            0x22,
            0x0,
            0x0,
            0x0,
            0x0,
            0x42,
            0x6c,
            0x1,
            0x1,
            0x8c.toByte(),
            0x20,
            0x6,
            0x0,
            0x0,
            0x0,
            0x0,
            0x8c.toByte(),
            0x30,
            0x6,
            0x0,
            0x0,
            0x0,
            0x0,
            0x8c.toByte(),
            0x80.toByte(),
            0x10,
            0x6,
            0x0,
            0x0,
            0x0,
            0x0,
            0xcc.toByte(),
            0x20,
            0x6,
            0x0,
            0x0,
            0x0,
            0x0,
            0xcc.toByte(),
            0x30,
            0x6,
            0x0,
            0x0,
            0x0,
            0x0,
            0xcc.toByte(),
            0x80.toByte(),
            0x10,
            0x6,
            0x0,
            0x0,
            0x0,
            0x0,
            0x9a.toByte(),
            0x11,
            0x5b,
            0x69,
            0x0,
            0x9a.toByte(),
            0x11,
            0x5f,
            0x64,
            0x0,
            0x9b.toByte(),
            0x11,
            0x3b,
            0x20,
            0x16,
            0x0,
            0x9b.toByte(),
            0x11,
            0x2d,
            0x62,
            0x5,
            0x0,
            0xbc.toByte(),
            0x1,
            0x22,
            0x56,
            0x4,
            0x0,
            0x0,
            0x8c.toByte(),
            0x1,
            0x6,
            0x10,
            0x62,
            0x4,
            0x0,
            0x8c.toByte(),
            0x21,
            0x6,
            0x0,
            0x0,
            0x0,
            0x0,
            0x8c.toByte(),
            0x31,
            0x6,
            0x0,
            0x0,
            0x0,
            0x0,
            0x8c.toByte(),
            0x81.toByte(),
            0x10,
            0x6,
            0x0,
            0x0,
            0x0,
            0x0,
            0x8c.toByte(),
            0x1,
            0x14,
            0x44,
            0x27,
            0x26,
            0x0,
            0x4,
            0x6d,
            0x2a,
            0x14,
            0xba.toByte(),
            0x17,
            0x0f,
            0x21,
            0x4,
            0x0,
            0x10,
            0xa0.toByte(),
            0xa9.toByte(),
            0x16
        )

        private fun newChannelRecordContainer(channelAddress: String): ChannelRecordContainer {
            val channelAddress = channelAddress
            public get () {
                return field
            }
            return object : ChannelRecordContainer {
                var longValue: Value = LongValue(9073)
                override var record: Record? = Record(longValue, System.currentTimeMillis())
                override fun getRecord(): Record? {
                    return record
                }

                override val channel: Channel
                    get() = PowerMockito.mock(
                        Channel::class.java
                    )

                override fun setRecord(record: Record?) {
                    this.record = record
                }

                override var channelHandle: Any?
                    get() = null
                    set(handle) {}

                override fun copy(): ChannelRecordContainer? {
                    return newChannelRecordContainer(this.channelAddress!!)
                }
            }
        }
    }
}
