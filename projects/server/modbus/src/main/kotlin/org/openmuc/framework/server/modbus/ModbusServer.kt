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
package org.openmuc.framework.server.modbus

import com.ghgande.j2mod.modbus.ModbusException
import com.ghgande.j2mod.modbus.procimg.Register
import com.ghgande.j2mod.modbus.procimg.SimpleInputRegister
import com.ghgande.j2mod.modbus.procimg.SimpleProcessImage
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.ghgande.j2mod.modbus.slave.ModbusSlave
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.dataaccess.Channel
import org.openmuc.framework.lib.osgi.config.*
import org.openmuc.framework.server.modbus.register.*
import org.openmuc.framework.server.spi.ServerMappingContainer
import org.openmuc.framework.server.spi.ServerService
import org.osgi.service.cm.ConfigurationException
import org.osgi.service.cm.ManagedService
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.*
import kotlin.ConcurrentModificationException

class ModbusServer : ServerService, ManagedService {
    private val spi = SimpleProcessImage()
    private var slave: ModbusSlave? = null
    private val property: PropertyHandler
    private val settings: Settings

    init {
        val pid = ModbusServer::class.java.name
        settings = Settings()
        property = PropertyHandler(settings, pid)
    }

    @Throws(IOException::class)
    private fun startServer(spi: SimpleProcessImage) {
        val address = property.getString(Settings.ADDRESS)
        val port = property.getInt(Settings.PORT)
        val type = property.getString(Settings.TYPE)!!.lowercase(Locale.getDefault())
        var isRtuTcp = false
        logServerSettings()
        try {
            when (type) {
                "udp" -> slave = ModbusSlaveFactory.createUDPSlave(InetAddress.getByName(address), port)
                "serial" -> {
                    logger.error("Serial connection is not supported, yet. Using RTU over TCP with default values.")
                    isRtuTcp = true
                    slave = ModbusSlaveFactory.createTCPSlave(
                        InetAddress.getByName(address), port,
                        property.getInt(Settings.POOLSIZE), isRtuTcp
                    )
                }

                "rtutcp" -> {
                    isRtuTcp = true
                    slave = ModbusSlaveFactory.createTCPSlave(
                        InetAddress.getByName(address), port,
                        property.getInt(Settings.POOLSIZE), isRtuTcp
                    )
                }

                "tcp" -> slave = ModbusSlaveFactory.createTCPSlave(
                    InetAddress.getByName(address), port,
                    property.getInt(Settings.POOLSIZE), isRtuTcp
                )

                else -> slave = ModbusSlaveFactory.createTCPSlave(
                    InetAddress.getByName(address), port,
                    property.getInt(Settings.POOLSIZE), isRtuTcp
                )
            }
            slave.let {
                if(it != null) {
                    it.threadName = "modbusServerListener"
                    it.addProcessImage(property.getInt(Settings.UNITID), spi)
                    it.open()
                } else {
                    throw ConcurrentModificationException("Slave must not be null")
                }
            }
        } catch (e: ModbusException) {
            throw IOException(e.message)
        } catch (e: UnknownHostException) {
            logger.error("Unknown host: {}", address)
            throw IOException(e.message)
        }
    }

    private fun logServerSettings() {
        if (logger.isDebugEnabled) {
            logger.debug("Address:  {}", property.getString(Settings.ADDRESS))
            logger.debug("Port:     {}", property.getString(Settings.PORT))
            logger.debug("UnitId:   {}", property.getString(Settings.UNITID))
            logger.debug("Type:     {}", property.getString(Settings.TYPE))
            logger.debug("Poolsize: {}", property.getString(Settings.POOLSIZE))
        }
    }

    fun shutdown() {
        if (slave != null) {
            slave!!.close()
        }
    }

    override val id: String
        get() = "modbus"

    override fun updatedConfiguration(mappings: List<ServerMappingContainer?>?) {
        bindMappings(mappings)
        try {
            startServer(spi)
        } catch (e: IOException) {
            logger.error("Error starting server.")
            throw RuntimeException(e)
        }
    }

    override fun serverMappings(mappings: List<ServerMappingContainer?>?) {
        logger.debug("serverMappings")
        bindMappings(mappings)
    }

    private fun bindMappings(mappings: List<ServerMappingContainer?>?) {
        if (logger.isDebugEnabled) {
            logger.debug("Bind mappings of {} channel.", mappings!!.size)
        }
        for (container in mappings!!) {
            val serverAddress = container!!.serverMapping.serverAddress
            val primaryTable = EPrimaryTable.getEnumfromString(serverAddress.substring(0, serverAddress.indexOf(':')))
            val modbusAddress =
                serverAddress.substring(serverAddress.indexOf(':') + 1, serverAddress.lastIndexOf(':')).toInt()
            val dataType = serverAddress.substring(serverAddress.lastIndexOf(':') + 1)
            val valueType = ValueType.valueOf(dataType)
            logMapping(primaryTable, modbusAddress, valueType, container.channel)
            when (primaryTable) {
                EPrimaryTable.INPUT_REGISTERS -> addInputRegisters(spi, modbusAddress, valueType, container.channel)
                EPrimaryTable.HOLDING_REGISTERS -> addHoldingRegisters(spi, modbusAddress, valueType, container.channel)
                EPrimaryTable.COILS -> {}
                EPrimaryTable.DISCRETE_INPUTS -> {}
                else -> {}
            }
        }
    }

    private fun logMapping(primaryTable: EPrimaryTable, modbusAddress: Int, valueType: ValueType, channel: Channel) {
        if (logger.isDebugEnabled) {
            logger.debug(
                "ChannelId: {}, Register: {}, Address: {}, ValueType: {}, Channel valueType: {}",
                channel.id, primaryTable, modbusAddress, valueType, channel.valueType
            )
        }
    }

    private fun addHoldingRegisters(
        spi: SimpleProcessImage,
        modbusAddress: Int,
        valueType: ValueType,
        channel: Channel
    ) {
        while (spi.registerCount <= modbusAddress + 4) {
            spi.addRegister(SimpleRegister())
        }
        when (valueType) {
            ValueType.DOUBLE -> {
                val eightByteDoubleRegister3: Register = LinkedMappingHoldingRegister(
                    DoubleMappingInputRegister(channel, 6, 7), channel, null, valueType, 6, 7
                )
                val eightByteDoubleRegister2: Register = LinkedMappingHoldingRegister(
                    DoubleMappingInputRegister(channel, 4, 5), channel,
                    eightByteDoubleRegister3 as LinkedMappingHoldingRegister, valueType, 4, 5
                )
                val eightByteDoubleRegister1: Register = LinkedMappingHoldingRegister(
                    DoubleMappingInputRegister(channel, 2, 3), channel,
                    eightByteDoubleRegister2 as LinkedMappingHoldingRegister, valueType, 2, 3
                )
                val eightByteDoubleRegister0: Register = LinkedMappingHoldingRegister(
                    DoubleMappingInputRegister(channel, 0, 1), channel,
                    eightByteDoubleRegister1 as LinkedMappingHoldingRegister, valueType, 0, 1
                )
                spi.setRegister(modbusAddress, eightByteDoubleRegister0)
                spi.setRegister(modbusAddress + 1, eightByteDoubleRegister1)
                spi.setRegister(modbusAddress + 2, eightByteDoubleRegister2)
                spi.setRegister(modbusAddress + 3, eightByteDoubleRegister3)
            }

            ValueType.LONG -> {
                val eightByteLongRegister3: Register = LinkedMappingHoldingRegister(
                    LongMappingInputRegister(channel, 6, 7), channel, null, valueType, 6, 7
                )
                val eightByteLongRegister2: Register = LinkedMappingHoldingRegister(
                    LongMappingInputRegister(channel, 4, 5), channel,
                    eightByteLongRegister3 as LinkedMappingHoldingRegister, valueType, 4, 5
                )
                val eightByteLongRegister1: Register = LinkedMappingHoldingRegister(
                    LongMappingInputRegister(channel, 2, 3), channel,
                    eightByteLongRegister2 as LinkedMappingHoldingRegister, valueType, 2, 3
                )
                val eightByteLongRegister0: Register = LinkedMappingHoldingRegister(
                    LongMappingInputRegister(channel, 0, 1), channel,
                    eightByteLongRegister1 as LinkedMappingHoldingRegister, valueType, 0, 1
                )
                spi.setRegister(modbusAddress, eightByteLongRegister0)
                spi.setRegister(modbusAddress + 1, eightByteLongRegister1)
                spi.setRegister(modbusAddress + 2, eightByteLongRegister2)
                spi.setRegister(modbusAddress + 3, eightByteLongRegister3)
            }

            ValueType.INTEGER -> {
                val fourByteIntRegister1: Register = LinkedMappingHoldingRegister(
                    IntegerMappingInputRegister(channel, 2, 3), channel, null, valueType, 2, 3
                )
                val fourByteIntRegister0: Register = LinkedMappingHoldingRegister(
                    IntegerMappingInputRegister(channel, 0, 1), channel,
                    fourByteIntRegister1 as LinkedMappingHoldingRegister, valueType, 0, 1
                )
                spi.setRegister(modbusAddress, fourByteIntRegister0)
                spi.setRegister(modbusAddress + 1, fourByteIntRegister1)
            }

            ValueType.FLOAT -> {
                val fourByteFloatRegister1: Register = LinkedMappingHoldingRegister(
                    FloatMappingInputRegister(channel, 2, 3), channel, null, valueType, 2, 3
                )
                val fourByteFloatRegister0: Register = LinkedMappingHoldingRegister(
                    FloatMappingInputRegister(channel, 0, 1), channel,
                    fourByteFloatRegister1 as LinkedMappingHoldingRegister, valueType, 0, 1
                )
                spi.setRegister(modbusAddress, fourByteFloatRegister0)
                spi.setRegister(modbusAddress + 1, fourByteFloatRegister1)
            }

            ValueType.SHORT -> {
                val twoByteShortRegister: Register = LinkedMappingHoldingRegister(
                    ShortMappingInputRegister(channel, 0, 1), channel, null, valueType, 0, 1
                )
                spi.setRegister(modbusAddress, twoByteShortRegister)
            }

            ValueType.BOOLEAN -> {
                val twoByteBooleanRegister: Register = LinkedMappingHoldingRegister(
                    BooleanMappingInputRegister(channel, 0, 1), channel, null, valueType, 0, 1
                )
                spi.setRegister(modbusAddress, twoByteBooleanRegister)
            }

            else -> {}
        }
    }

    private fun addInputRegisters(spi: SimpleProcessImage, modbusAddress: Int, valueType: ValueType, channel: Channel) {
        while (spi.inputRegisterCount <= modbusAddress + 4) {
            spi.addInputRegister(SimpleInputRegister())
        }
        when (valueType) {
            ValueType.DOUBLE -> {
                var i = 0
                while (i < 4) {
                    spi.setInputRegister(modbusAddress + i, DoubleMappingInputRegister(channel, 2 * i, 2 * i + 1))
                    i++
                }
            }

            ValueType.LONG -> {
                var i = 0
                while (i < 4) {
                    spi.setInputRegister(modbusAddress + i, LongMappingInputRegister(channel, 2 * i, 2 * i + 1))
                    i++
                }
            }

            ValueType.INTEGER -> {
                var i = 0
                while (i < 2) {
                    spi.setInputRegister(modbusAddress + i, IntegerMappingInputRegister(channel, 2 * i, 2 * i + 1))
                    i++
                }
            }

            ValueType.FLOAT -> {
                var i = 0
                while (i < 2) {
                    spi.setInputRegister(modbusAddress + i, FloatMappingInputRegister(channel, 2 * i, 2 * i + 1))
                    i++
                }
            }

            ValueType.SHORT -> spi.setInputRegister(modbusAddress, ShortMappingInputRegister(channel, 0, 1))
            ValueType.BOOLEAN -> spi.setInputRegister(modbusAddress, BooleanMappingInputRegister(channel, 0, 1))
            else -> {}
        }
    }

    enum class EPrimaryTable {
        COILS, DISCRETE_INPUTS, INPUT_REGISTERS, HOLDING_REGISTERS;

        companion object {
            fun getEnumfromString(enumAsString: String?): EPrimaryTable {
                var returnValue: EPrimaryTable? = null
                if (enumAsString != null) {
                    for (value in values()) {
                        if (enumAsString.equals(value.toString(), ignoreCase = true)) {
                            returnValue = valueOf(enumAsString.uppercase(Locale.getDefault()))
                            break
                        }
                    }
                }
                if (returnValue == null) {
                    throw RuntimeException(
                        enumAsString + " is not supported. Use one of the following supported primary tables: "
                                + supportedValues
                    )
                }
                return returnValue
            }

            /**
             * @return all supported values as a comma separated string
             */
            val supportedValues: String
                get() {
                    val sb = StringBuilder()
                    for (value in values()) {
                        sb.append(value.toString()).append(", ")
                    }
                    return sb.toString()
                }
        }
    }

    @Throws(ConfigurationException::class)
    override fun updated(propertiesDict: Dictionary<String, *>) {
        val dict = DictionaryPreprocessor(propertiesDict)
        if (!dict.wasIntermediateOsgiInitCall()) {
            tryProcessConfig(dict)
        }
    }

    private fun tryProcessConfig(newConfig: DictionaryPreprocessor) {
        try {
            property.processConfig(newConfig)
            if (property.configChanged()) {
                shutdown()
                startServer(spi)
            }
        } catch (e: ServicePropertyException) {
            logger.error("Update properties failed", e)
            shutdown()
        } catch (e: IOException) {
            logger.error("Update properties failed", e)
            shutdown()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ModbusServer::class.java)
    }
}
