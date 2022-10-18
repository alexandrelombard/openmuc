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
import org.openmuc.framework.config.*
import org.openmuc.framework.driver.spi.ConnectionException
import org.openmuc.framework.driver.spi.DriverDeviceScanListener
import org.openmuc.jmbus.MBusConnection
import org.openmuc.jmbus.MBusConnection.MBusSerialBuilder
import org.openmuc.jmbus.VariableDataStructure
import org.openmuc.jrxtx.Parity
import org.openmuc.jrxtx.SerialPortTimeoutException
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.io.IOException
import java.io.InterruptedIOException

@RunWith(PowerMockRunner::class)
@PrepareForTest(Driver::class, MBusConnection::class)
class DriverTest {
    @Test
    fun testGetDriverInfo() {
        Assert.assertEquals("mbus", Driver().info.id)
    }

    /*
     * Test the connect Method of MBusDriver without the functionality of jMBus Called the {@link #connect(String
     * channelAdress, String bautrate) connect} Method
     */
    @Test
    @Throws(Exception::class)
    fun testConnectSucceed() {
        val channelAdress = "/dev/ttyS100:5"
        val bautrate = "2400"
        connect(channelAdress, bautrate)
    }

    @Test
    @Throws(Exception::class)
    fun testConnectSucceedWithSecondary() {
        val channelAdress = "/dev/ttyS100:74973267a7320404"
        val bautrate = "2400"
        connect(channelAdress, bautrate)
    }

    @Test
    @Throws(Exception::class)
    fun testConnectionBautrateIsEmpty() {
        val channelAdress = "/dev/ttyS100:5"
        val bautrate = ""
        connect(channelAdress, bautrate)
    }

    @Test
    @Throws(Exception::class)
    fun TestConnectTwoTimes() {
        val channelAdress = "/dev/ttyS100:5"
        val bautrate = "2400"
        val mdriver = Driver()
        val mockedMBusSap = PowerMockito.mock(MBusConnection::class.java)
        PowerMockito.whenNew(MBusConnection::class.java).withAnyArguments().thenReturn(mockedMBusSap)
        PowerMockito.doNothing().`when`(mockedMBusSap).linkReset(ArgumentMatchers.anyInt())
        PowerMockito.`when`(mockedMBusSap.read(ArgumentMatchers.anyInt())).thenReturn(null)
        Assert.assertNotNull(mdriver.connect(channelAdress, bautrate))
        Assert.assertNotNull(mdriver.connect(channelAdress, bautrate))
    }

    /*
     * This Testmethod will test the connect Method of MBus Driver, without testing jMBus Library functions. With
     * Mockito and PowerMockito its possible to do this. At first it will create an MBusDriver Objekt. Then we mocking
     * an MBusSap Objects without functionality. If new MBusSap will created, it will return the mocked Object
     * "mockedMBusSap". If the linkReset Method will called, it will do nothing. If the read Method will call, we return
     * null.
     */
    @Throws(Exception::class)
    private fun connect(deviceAddress: String, bautrate: String) {
        val driver = Driver()
        val con = PowerMockito.mock(MBusConnection::class.java)
        val builder = PowerMockito.mock(MBusSerialBuilder::class.java)
        PowerMockito.whenNew(MBusSerialBuilder::class.java).withAnyArguments().thenReturn(builder)
        PowerMockito.`when`(builder.setBaudrate(ArgumentMatchers.anyInt())).thenReturn(builder)
        PowerMockito.`when`(builder.setTimeout(ArgumentMatchers.anyInt())).thenReturn(builder)
        PowerMockito.`when`(
            builder.setParity(
                ArgumentMatchers.any(
                    Parity::class.java
                )
            )
        ).thenReturn(builder)
        PowerMockito.`when`(builder.build()).thenReturn(con)
        PowerMockito.doNothing().`when`(con).linkReset(ArgumentMatchers.anyInt())
        PowerMockito.`when`(con.read(ArgumentMatchers.anyInt())).thenReturn(null)
        Assert.assertNotNull(driver.connect(deviceAddress, bautrate))
    }

    @Test(expected = ArgumentSyntaxException::class)
    @Throws(Exception::class)
    fun testConnectionArgumentSyntaxExceptionNoPortSet() {
        val channelAdress = "/dev/ttyS100:"
        val bautrate = "2400"
        connect(channelAdress, bautrate)
    }

    @Test(expected = ArgumentSyntaxException::class)
    @Throws(Exception::class)
    fun testConnectWithWrongSecondary() {
        val channelAdress = "/dev/ttyS100:74973267a20404"
        val bautrate = "2400"
        connect(channelAdress, bautrate)
    }

    @Test(expected = ArgumentSyntaxException::class)
    @Throws(Exception::class)
    fun testConnectionChannelAddressEmpty() {
        val channelAdress = ""
        val bautrate = "2400"
        connect(channelAdress, bautrate)
    }

    @Test(expected = ArgumentSyntaxException::class)
    @Throws(Exception::class)
    fun testConnectionArgumentSyntaxExceptionChannelAddressWrongSyntax() {
        val channelAdress = "/dev/ttyS100:a"
        val bautrate = "2400"
        connect(channelAdress, bautrate)
    }

    @Test(expected = ArgumentSyntaxException::class)
    @Throws(Exception::class)
    fun testConnectionArgumentSyntaxExceptionToManyArguments() {
        val channelAdress = "/dev/ttyS100:5:1"
        val bautrate = "2400"
        connect(channelAdress, bautrate)
    }

    @Test(expected = ArgumentSyntaxException::class)
    @Throws(Exception::class)
    fun testConnectionArgumentSyntaxExceptionBautIsNotANumber() {
        val channelAdress = "/dev/ttyS100:5"
        val bautrate = "asd"
        connect(channelAdress, bautrate)
    }

    @Test(expected = ConnectionException::class)
    @Throws(Exception::class)
    fun testMBusSapLinkResetThrowsIOException() {
        val mdriver = Driver()
        val mockedMBusSap = PowerMockito.mock(MBusConnection::class.java)
        PowerMockito.whenNew(MBusConnection::class.java).withAnyArguments().thenReturn(mockedMBusSap)
        PowerMockito.doThrow(IOException()).`when`(mockedMBusSap).linkReset(ArgumentMatchers.anyInt())
        PowerMockito.`when`(mockedMBusSap.read(ArgumentMatchers.anyInt())).thenReturn(null)
        mdriver.connect("/dev/ttyS100:5", "2400:lr:sc")
    }

    @Test(expected = ConnectionException::class)
    @Throws(Exception::class)
    fun testMBusSapReadThrowsTimeoutException() {
        val mdriver = Driver()
        val mockedMBusSap = PowerMockito.mock(MBusConnection::class.java)
        PowerMockito.whenNew(MBusConnection::class.java).withAnyArguments().thenReturn(mockedMBusSap)
        PowerMockito.doThrow(SerialPortTimeoutException()).`when`(mockedMBusSap).linkReset(ArgumentMatchers.anyInt())
        mdriver.connect("/dev/ttyS100:5", "2400:sc")
    }

    @Test(expected = ConnectionException::class)
    @Throws(Exception::class)
    fun testMBusSapReadThrowsTimeoutExceptionAtSecondRun() {
        val con = PowerMockito.mock(MBusConnection::class.java)
        PowerMockito.whenNew(MBusConnection::class.java).withAnyArguments().thenReturn(con)
        PowerMockito.doNothing().`when`(con).linkReset(ArgumentMatchers.anyInt())
        PowerMockito.`when`(con.read(ArgumentMatchers.anyInt())).thenReturn(null)
        val mdriver = Driver()
        Assert.assertNotNull(mdriver.connect("/dev/ttyS100:5", "2400"))
        PowerMockito.doThrow(IOException()).`when`(con).linkReset(ArgumentMatchers.anyInt())
        mdriver.connect("/dev/ttyS100:5", "2400:sc")
    }

    @Test
    @Throws(Exception::class)
    fun testScanForDevices() {
        scan("/dev/ttyS100:2400")
    }

    @Test
    @Throws(Exception::class)
    fun testScanForDevicesWithOutBautRate() {
        scan("/dev/ttyS100")
    }

    @Test(expected = ArgumentSyntaxException::class)
    @Throws(Exception::class)
    fun scanEmptySettings() {
        scan("")
    }

    @Test(expected = ArgumentSyntaxException::class)
    @Throws(Exception::class)
    fun testScanForDevicesBautrateIsNotANumber() {
        scan("/dev/ttyS100:aaa")
    }

    @Test(expected = ArgumentSyntaxException::class)
    @Throws(Exception::class)
    fun scanToManyArgs() {
        scan("/dev/ttyS100:2400:assda")
    }

    @Test(expected = ScanInterruptedException::class)
    @Throws(Exception::class)
    fun testInterrupedException() {
        val mdriver = Driver()
        val ddsl = PowerMockito.mock(DriverDeviceScanListener::class.java)
        PowerMockito.doAnswer {
            mdriver.interruptDeviceScan()
            null
        }.`when`(ddsl).deviceFound(
            ArgumentMatchers.any(
                DeviceScanInfo::class.java
            )
        )
        val con = PowerMockito.mock(MBusConnection::class.java)
        PowerMockito.`when`(con.read(ArgumentMatchers.anyInt()))
            .thenReturn(VariableDataStructure(null, 0, 0, null, null))
        PowerMockito.whenNew(MBusConnection::class.java).withAnyArguments().thenReturn(con)
        PowerMockito.doNothing().`when`(con).linkReset(ArgumentMatchers.anyInt())
        PowerMockito.`when`(con.read(ArgumentMatchers.anyInt())).thenReturn(null)
        mdriver.scanForDevices("/dev/ttyS100:2400", ddsl)
    }

    @Test(expected = ScanException::class)
    @Throws(Exception::class)
    fun scanConOpenIOException() {
        val builder = PowerMockito.mock(MBusSerialBuilder::class.java)
        PowerMockito.whenNew(MBusSerialBuilder::class.java).withAnyArguments().thenReturn(builder)
        PowerMockito.`when`(builder.setBaudrate(ArgumentMatchers.anyInt())).thenReturn(builder)
        PowerMockito.`when`(builder.setTimeout(ArgumentMatchers.anyInt())).thenReturn(builder)
        PowerMockito.`when`(builder.build()).thenThrow(IOException())
        Driver().scanForDevices(
            "/dev/ttyS100:2400", PowerMockito.mock(
                DriverDeviceScanListener::class.java
            )
        )
    }

    @Test(expected = ScanException::class)
    @Throws(Exception::class)
    fun testScanMBusSapReadThrowsIOException() {
        val con = mockNewBuilderCon()
        PowerMockito.`when`(con.read(1)).thenReturn(VariableDataStructure(null, 0, 0, null, null))
        PowerMockito.`when`(con.read(250)).thenThrow(SerialPortTimeoutException())
        PowerMockito.`when`(con.read(ArgumentMatchers.anyInt())).thenThrow(IOException())
        val mdriver = Driver()

        class InterruptScanThread : Runnable {
            override fun run() {
                try {
                    Thread.sleep(100)
                    mdriver.interruptDeviceScan()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
        InterruptScanThread().run()
        mdriver.scanForDevices("/dev/ttyS100:2400", PowerMockito.mock(DriverDeviceScanListener::class.java))
    }

    companion object {
        // ******************* SCAN TESTS ********************//
        @Throws(Exception::class)
        private fun scan(settings: String) {
            val con = PowerMockito.mock(MBusConnection::class.java)
            PowerMockito.whenNew(MBusConnection::class.java).withAnyArguments().thenReturn(con)
            PowerMockito.`when`(con.read(1)).thenReturn(VariableDataStructure(null, 0, 0, null, null))
            PowerMockito.`when`(con.read(250)).thenThrow(SerialPortTimeoutException())
            PowerMockito.`when`(con.read(ArgumentMatchers.anyInt())).thenThrow(SerialPortTimeoutException())
            val mdriver = Driver()
            mdriver.interruptDeviceScan()
            mdriver.scanForDevices(settings, PowerMockito.mock(DriverDeviceScanListener::class.java))
        }

        @Throws(IOException::class, InterruptedIOException::class, Exception::class)
        private fun mockNewBuilderCon(): MBusConnection {
            val builder = PowerMockito.mock(MBusSerialBuilder::class.java)
            val con = PowerMockito.mock(MBusConnection::class.java)
            PowerMockito.doNothing().`when`(con).linkReset(ArgumentMatchers.anyInt())
            PowerMockito.`when`(con.read(ArgumentMatchers.anyInt())).thenReturn(null)
            PowerMockito.whenNew(MBusSerialBuilder::class.java).withAnyArguments().thenReturn(builder)
            PowerMockito.`when`(builder.setBaudrate(ArgumentMatchers.anyInt())).thenReturn(builder)
            PowerMockito.`when`(builder.setTimeout(ArgumentMatchers.anyInt())).thenReturn(builder)
            PowerMockito.`when`(
                builder.setParity(
                    ArgumentMatchers.any(
                        Parity::class.java
                    )
                )
            ).thenReturn(builder)
            PowerMockito.`when`(builder.build()).thenReturn(con)
            return con
        }
    }
}
