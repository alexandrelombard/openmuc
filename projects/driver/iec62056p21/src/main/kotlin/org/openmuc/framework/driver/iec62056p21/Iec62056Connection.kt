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
package org.openmuc.framework.driver.iec62056p21

import org.openmuc.framework.config.*
import org.openmuc.framework.data.*
import org.openmuc.framework.driver.spi.*
import org.openmuc.j62056.DataMessage
import org.openmuc.j62056.DataSet
import org.openmuc.j62056.Iec21Port
import org.slf4j.LoggerFactory
import java.io.IOException

class Iec62056Connection(
    private val configuredBuilder: Iec21Port.Builder, retries: Int, private val readStandard: Boolean,
    private val requestStartCharacter: String
) : Connection {
    private var iec21Port: Iec21Port
    private val retries: Int

    init {
        if (retries > 0) {
            this.retries = retries
        } else {
            this.retries = 0
        }
        iec21Port = try {
            configuredBuilder.buildAndOpen()
        } catch (e: IOException) {
            throw ConnectionException(e.message)
        }
        try {
            iec21Port.read()
        } catch (e: IOException) {
            iec21Port.close()
            throw ConnectionException("IOException trying to read meter: " + e.message, e)
        }
        sleep(5000)
    }

    @Throws(UnsupportedOperationException::class, ScanException::class, ConnectionException::class)
    override fun scanForChannels(settings: String): List<ChannelScanInfo> {
        val dataSets: List<DataSet>
        val dataMessage: DataMessage
        dataMessage = try {
            iec21Port.read()
        } catch (e: IOException) {
            throw ScanException(e)
        }
        dataSets = dataMessage.dataSets
        if (dataSets == null) {
            throw ScanException("Read timeout.")
        }
        val scanInfos: MutableList<ChannelScanInfo> = ArrayList(dataSets.size)
        for (dataSet in dataSets) {
            try {
                dataSet.value.toDouble()
                scanInfos.add(ChannelScanInfo(dataSet.address, "", ValueType.DOUBLE, 0))
            } catch (e: NumberFormatException) {
                scanInfos.add(
                    ChannelScanInfo(dataSet.address, "", ValueType.STRING, dataSet.value.length)
                )
            }
        }
        return scanInfos
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun read(
        containers: List<ChannelRecordContainer>,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        val dataSets: MutableList<DataSet> = ArrayList()
        dataSets.addAll(read(containers)!!)
        if (readStandard) {
            configuredBuilder.setRequestStartCharacters("/?")
            setPort(configuredBuilder)
            sleep(500)
            dataSets.addAll(read(containers)!!)
            configuredBuilder.setRequestStartCharacters(requestStartCharacter)
            setPort(configuredBuilder)
        }
        setRecords(containers, dataSets)
        return null
    }

    private fun read(containers: List<ChannelRecordContainer>): List<DataSet> {
        var dataSetsRet: MutableList<DataSet> = arrayListOf()
        var dataMessage: DataMessage
        var i = 0
        while (i <= retries) {
            try {
                dataMessage = iec21Port.read()
                val dataSets = dataMessage.dataSets
                i = retries
                dataSetsRet = dataSets
            } catch (e: IOException) {
                if (i >= retries) {
                    for (container in containers) {
                        container.record = Record(Flag.DRIVER_ERROR_READ_FAILURE)
                    }
                }
            }
            ++i
        }
        return dataSetsRet
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer>, listener: RecordsReceivedListener?) {
        val iec62056Listener = Iec62056Listener()
        iec62056Listener.registerOpenMucListener(containers, listener)
        try {
            iec21Port.listen(iec62056Listener)
        } catch (e: IOException) {
            throw ConnectionException(e)
        }
    }

    @Throws(UnsupportedOperationException::class, ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer>, containerListHandle: Any?): Any? {
        throw UnsupportedOperationException()
    }

    override fun disconnect() {
        iec21Port.close()
    }

    @Throws(ConnectionException::class)
    private fun setPort(configuredBuilder: Iec21Port.Builder) {
        if (!iec21Port.isClosed) {
            iec21Port.close()
        }
        iec21Port = try {
            configuredBuilder.buildAndOpen()
        } catch (e: IOException) {
            throw ConnectionException(e.message)
        }
    }

    private fun sleep(sleeptime: Int) {
        try { // FIXME: Sleep to avoid to early read after connection. Meters have some delay.
            Thread.sleep(sleeptime.toLong())
        } catch (e1: InterruptedException) {
            logger.error(e1.message)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Iec62056Connection::class.java)
        fun setRecords(containers: List<ChannelRecordContainer?>?, dataSets: List<DataSet>) {
            val time = System.currentTimeMillis()
            for (container in containers!!) {
                for (dataSet in dataSets) {
                    if (dataSet.address == container!!.channelAddress) {
                        val value = dataSet.value
                        if (value != null) {
                            try {
                                container.record = Record(DoubleValue(dataSet.value.toDouble()), time)
                            } catch (e: NumberFormatException) {
                                container.record = Record(StringValue(dataSet.value), time)
                            }
                        }
                        break
                    }
                }
            }
        }
    }
}
