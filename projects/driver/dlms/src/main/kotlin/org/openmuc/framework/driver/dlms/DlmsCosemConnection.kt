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
package org.openmuc.framework.driver.dlms

import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.driver.dlms.settings.DeviceAddress
import org.openmuc.framework.driver.dlms.settings.DeviceSettings
import org.openmuc.framework.driver.spi.*
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import org.openmuc.jdlms.*
import org.openmuc.jdlms.datatypes.DataObject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.text.MessageFormat

internal class DlmsCosemConnection(deviceAddress: String?, settings: String?) : Connection {
    private val dlmsConnection: DlmsConnection?
    private val deviceAddress: DeviceAddress
    private val deviceSettings: DeviceSettings
    private val readHandle: ReadHandle
    private val writeHandle: WriteHandle

    init {
        this.deviceAddress = DeviceAddress(deviceAddress)
        deviceSettings = DeviceSettings(settings)
        dlmsConnection = Connector.buildDlmsConection(this.deviceAddress, deviceSettings)
        readHandle = ReadHandle(dlmsConnection)
        writeHandle = WriteHandle(dlmsConnection)
    }

    override fun disconnect() {
        try {
            dlmsConnection!!.close()
        } catch (e: IOException) {
            logger.warn("Failed to close DLMS connection.", e)
        }
    }

    @Throws(ConnectionException::class)
    override fun read(
        containers: List<ChannelRecordContainer?>?,
        containerListHandle: Any?,
        samplingGroup: String?
    ): Any? {
        readHandle.read(containers)
        return null
    }

    @Throws(ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer?>?, containerListHandle: Any?): Any? {
        writeHandle.write(containers)
        return null
    }

    @Throws(ConnectionException::class)
    override fun scanForChannels(settings: String?): List<ChannelScanInfo?>? {
        if (deviceSettings.useSn()) {
            throw UnsupportedOperationException("Scan devices for SN is not supported, yet.")
        }
        val scanChannel = AttributeAddress(15, "0.0.40.0.0.255", 2)
        val scanResult = executeScan(scanChannel)
        if (scanResult.resultCode != AccessResultCode.SUCCESS) {
            logger.error("Cannot scan device for channels. Resultcode: " + scanResult.resultCode)
            throw ConnectionException("Cannot scan device for channels.")
        }
        val objectArray = scanResult.resultData.getValue<List<DataObject>>()
        val result: MutableList<ChannelScanInfo?> = ArrayList(objectArray.size)
        for (objectDef in objectArray) {
            val defItems = objectDef.getValue<List<DataObject>>()
            var classId = defItems[0].getValue<Int>()
            classId = classId and 0xFF
            val instanceId = defItems[2].getValue<ByteArray>()
            val accessRight = defItems[3].getValue<List<DataObject>>()
            val attributes = accessRight[0].getValue<List<DataObject>>()
            for (attributeAccess in attributes) {
                val scanInfo = createScanInfoFor(classId, instanceId, attributeAccess)
                result.add(scanInfo)
            }
        }
        return result
    }

    @Throws(ConnectionException::class)
    private fun executeScan(scanChannel: AttributeAddress): GetResult {
        return try {
            dlmsConnection!![scanChannel]
        } catch (e: IOException) {
            throw ConnectionException("Problem to do action.", e)
        }
    }

    @Throws(ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer?>?, listener: RecordsReceivedListener?) {
        throw UnsupportedOperationException()
    }

    private enum class AttributeAccessMode(private val code: Int, val isReadable: Boolean, val isWriteable: Boolean) {
        NO_ACCESS(0, false, false), READ_ONLY(1, true, false), WRITE_ONLY(2, false, true), READ_AND_WRITE(
            3,
            true,
            true
        ),
        AUTHENTICATED_READ_ONLY(4, true, false), AUTHENTICATED_WRITE_ONLY(5, false, true), AUTHENTICATED_READ_AND_WRITE(
            6,
            true,
            true
        ),
        UNKNOWN_ACCESS_MODE(-1, false, false);

        companion object {
            fun accessModeFor(dataObject: DataObject): AttributeAccessMode {
                val code = dataObject.getValue<Number>()
                return accessModeFor(code.toInt() and 0xFF)
            }

            fun accessModeFor(code: Int): AttributeAccessMode {
                for (accessMode in values()) {
                    if (accessMode.code == code) {
                        return accessMode
                    }
                }
                return UNKNOWN_ACCESS_MODE
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DlmsCosemConnection::class.java)
        private fun createScanInfoFor(
            classId: Int,
            logicalName: ByteArray,
            attributeAccess: DataObject
        ): ChannelScanInfo {
            val value = attributeAccess.getValue<List<DataObject>>()
            val attributeId = extractNumVal(value[0])
            val accessMode = AttributeAccessMode.accessModeFor(
                value[1]
            )
            val instanceId = ObisCode(logicalName)
            val channelAddress = MessageFormat.format("a={0}/{1}/{2}", classId, instanceId, attributeId)
            val valueTypeLength = 0

            // TODO: more/better description
            return ChannelScanInfo(
                channelAddress, channelAddress, ValueType.DOUBLE, valueTypeLength,
                accessMode.isReadable(), accessMode.isWriteable()
            )
        }

        private fun extractNumVal(dataObject: DataObject): Int {
            val attributeId = dataObject.getValue<Number>()
            return attributeId.toInt() and 0xFF
        }
    }
}
