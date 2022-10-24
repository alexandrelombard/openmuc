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
package org.openmuc.framework.lib.rest1

import com.google.gson.*
import org.openmuc.framework.config.*
import org.openmuc.framework.data.*
import org.openmuc.framework.data.Record
import org.openmuc.framework.dataaccess.Channel
import org.openmuc.framework.dataaccess.DeviceState
import org.openmuc.framework.lib.rest1.rest.objects.*
import java.lang.reflect.Type

class ToJson {
    private val gson: Gson
    val jsonObject: JsonObject

    init {
        gson = GsonBuilder().serializeSpecialFloatingPointValues()
            .registerTypeAdapter(ByteArray::class.java, ByteArraySerializer())
            .create()
        jsonObject = JsonObject()
    }

    fun addJsonObject(propertyName: String?, jsonObject: JsonObject?) {
        this.jsonObject.add(propertyName, jsonObject)
    }

    override fun toString(): String {
        return gson.toJson(jsonObject)
    }

    @Throws(ClassCastException::class)
    fun addRecord(record: Record?, valueType: ValueType?) {
        jsonObject.add(Const.RECORD, getRecordAsJsonElement(record, valueType))
    }

    @Throws(ClassCastException::class)
    fun addRecordList(recordList: List<Record?>?, valueType: ValueType?) {
        val jsa = JsonArray()
        if (recordList != null) {
            for (record in recordList) {
                jsa.add(getRecordAsJsonElement(record, valueType))
            }
        }
        jsonObject.add(Const.RECORDS, jsa)
    }

    @Throws(ClassCastException::class)
    fun addChannelRecordList(channels: List<Channel>) {
        val jsa = JsonArray()
        for (channel in channels) {
            jsa.add(channelRecordToJson(channel))
        }
        jsonObject.add(Const.RECORDS, jsa)
    }

    fun addDeviceState(deviceState: DeviceState) {
        jsonObject.addProperty(Const.STATE, deviceState.name)
    }

    fun addNumber(propertyName: String?, value: Number?) {
        jsonObject.addProperty(propertyName, value)
    }

    fun addBoolean(propertyName: String?, value: Boolean) {
        jsonObject.addProperty(propertyName, value)
    }

    fun addValue(value: Value?, valueType: ValueType?) {
        if (value == null) {
            jsonObject.add(Const.VALUE_STRING, JsonNull.INSTANCE)
            return
        }
        when (valueType) {
            ValueType.BOOLEAN -> jsonObject.addProperty(Const.VALUE_STRING, value.asBoolean())
            ValueType.BYTE -> jsonObject.addProperty(Const.VALUE_STRING, value.asByte())
            ValueType.BYTE_ARRAY -> jsonObject.addProperty(Const.VALUE_STRING, gson.toJson(value.asByteArray()))
            ValueType.DOUBLE -> jsonObject.addProperty(Const.VALUE_STRING, value.asDouble())
            ValueType.FLOAT -> jsonObject.addProperty(Const.VALUE_STRING, value.asFloat())
            ValueType.INTEGER -> jsonObject.addProperty(Const.VALUE_STRING, value.asInt())
            ValueType.LONG -> jsonObject.addProperty(Const.VALUE_STRING, value.asLong())
            ValueType.SHORT -> jsonObject.addProperty(Const.VALUE_STRING, value.asShort())
            ValueType.STRING -> jsonObject.addProperty(Const.VALUE_STRING, value.asString())
            else -> jsonObject.add(Const.VALUE_STRING, JsonNull.INSTANCE)
        }
    }

    private inner class ByteArraySerializer : JsonSerializer<ByteArray> {
        override fun serialize(src: ByteArray, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val arr = JsonArray()
            for (element in src) {
                arr.add(element.toInt() and 0xff)
            }
            return arr
        }
    }

    fun addString(propertyName: String?, value: String?) {
        jsonObject.addProperty(propertyName, value)
    }

    fun addStringList(propertyName: String?, stringList: List<String?>?) {
        jsonObject.add(propertyName, gson.toJsonTree(stringList).asJsonArray)
    }

    fun addDriverList(driverConfigList: List<DriverConfig>) {
        val jsa = JsonArray()
        for (driverConfig in driverConfigList) {
            jsa.add(gson.toJsonTree(driverConfig.id))
        }
        jsonObject.add(Const.DRIVERS, jsa)
    }

    fun addDeviceList(deviceConfigList: List<DeviceConfig>) {
        val jsa = JsonArray()
        for (deviceConfig in deviceConfigList) {
            jsa.add(gson.toJsonTree(deviceConfig.id))
        }
        jsonObject.add(Const.DEVICES, jsa)
    }

    fun addChannelList(channelList: List<Channel>) {
        val jsa = JsonArray()
        for (channelConfig in channelList) {
            jsa.add(gson.toJsonTree(channelConfig.id))
        }
        jsonObject.add(Const.CHANNELS, jsa)
    }

    fun addDriverInfo(driverInfo: DriverInfo?) {
        jsonObject.add(Const.INFOS, gson.toJsonTree(driverInfo))
    }

    fun addDriverConfig(config: DriverConfig) {
        val restConfig = RestDriverConfigMapper.getRestDriverConfig(config)
        jsonObject.add(Const.CONFIGS, gson.toJsonTree(restConfig, RestDriverConfig::class.java).asJsonObject)
    }

    fun addDeviceConfig(config: DeviceConfig) {
        val restConfig = RestDeviceConfigMapper.getRestDeviceConfig(config)
        jsonObject.add(Const.CONFIGS, gson.toJsonTree(restConfig, RestDeviceConfig::class.java).asJsonObject)
    }

    fun addChannelConfig(config: ChannelConfig) {
        val restConfig = RestChannelConfigMapper.getRestChannelConfig(config)
        jsonObject.add(Const.CONFIGS, gson.toJsonTree(restConfig, RestChannelConfig::class.java).asJsonObject)
    }

    fun addDeviceScanProgressInfo(restScanProgressInfo: RestScanProgressInfo?) {
        jsonObject.add(Const.SCAN_PROGRESS_INFO, gson.toJsonTree(restScanProgressInfo))
    }

    fun addDeviceScanInfoList(deviceScanInfoList: List<DeviceScanInfo>) {
        val jsa = JsonArray()
        for (deviceScanInfo in deviceScanInfoList) {
            val jso = JsonObject()
            jso.addProperty(Const.ID, deviceScanInfo.id)
            jso.addProperty(Const.DEVICEADDRESS, deviceScanInfo.deviceAddress)
            jso.addProperty(Const.SETTINGS, deviceScanInfo.settings)
            jso.addProperty(Const.DESCRIPTION, deviceScanInfo.description)
            jsa.add(jso)
        }
        jsonObject.add(Const.DEVICES, jsa)
    }

    fun addChannelScanInfoList(channelScanInfoList: List<ChannelScanInfo>) {
        val jsa = JsonArray()
        for (channelScanInfo in channelScanInfoList) {
            val jso = JsonObject()
            jso.addProperty(Const.CHANNELADDRESS, channelScanInfo.channelAddress)
            jso.addProperty(Const.VALUETYPE, channelScanInfo.valueType.name)
            jso.addProperty(Const.VALUETYPELENGTH, channelScanInfo.valueTypeLength)
            jso.addProperty(Const.DESCRIPTION, channelScanInfo.description)
            jso.addProperty(Const.METADATA, channelScanInfo.metaData)
            jso.addProperty(Const.UNIT, channelScanInfo.unit)
            jsa.add(jso)
        }
        jsonObject.add(Const.CHANNELS, jsa)
    }

    fun addRestUserConfig(restUserConfig: RestUserConfig?) {
        jsonObject.add(Const.CONFIGS, gson.toJsonTree(restUserConfig, RestUserConfig::class.java).asJsonObject)
    }

    @Throws(ClassCastException::class)
    private fun channelRecordToJson(channel: Channel): JsonObject {
        val jso = JsonObject()
        jso.addProperty(Const.ID, channel.id)
        jso.addProperty(Const.VALUETYPE, channel.valueType.toString())
        jso.add(Const.RECORD, getRecordAsJsonElement(channel.latestRecord, channel.valueType))
        return jso
    }

    @Throws(ClassCastException::class)
    private fun getRecordAsJsonElement(record: Record?, valueType: ValueType?): JsonElement {
        return gson.toJsonTree(getRestRecord(record, valueType), RestRecord::class.java)
    }

    @Throws(ClassCastException::class)
    private fun getRestRecord(rc: Record?, valueType: ValueType?): RestRecord {
        val value = rc!!.value
        var flag = rc.flag
        val rrc = RestRecord()
        rrc.timestamp = rc.timestamp
        flag = try {
            handleInfinityAndNaNValue(value, valueType, flag)
        } catch (e: TypeConversionException) {
            Flag.DRIVER_ERROR_CHANNEL_VALUE_TYPE_CONVERSION_EXCEPTION
        }
        if (flag !== Flag.VALID) {
            rrc.flag = flag
            rrc.value = null
            return rrc
        }
        rrc.flag = flag
        setRestRecordValue(valueType, value, rrc)
        return rrc
    }

    @Throws(ClassCastException::class)
    private fun setRestRecordValue(valueType: ValueType?, value: Value?, rrc: RestRecord) {
        if (value == null) {
            rrc.value = null
            return
        }
        when (valueType) {
            ValueType.FLOAT -> rrc.value = value.asFloat()
            ValueType.DOUBLE -> rrc.value = value.asDouble()
            ValueType.SHORT -> rrc.value = value.asShort()
            ValueType.INTEGER -> rrc.value = value.asInt()
            ValueType.LONG -> rrc.value = value.asLong()
            ValueType.BYTE -> rrc.value = value.asByte()
            ValueType.BOOLEAN -> rrc.value = value.asBoolean()
            ValueType.BYTE_ARRAY -> rrc.value = value.asByteArray()
            ValueType.STRING -> rrc.value = value.asString()
            else -> rrc.value = null
        }
    }

    private fun handleInfinityAndNaNValue(value: Value?, valueType: ValueType?, flag: Flag): Flag {
        if (value == null) {
            return flag
        }
        when (valueType) {
            ValueType.DOUBLE -> if (java.lang.Double.isInfinite(value.asDouble())) {
                return Flag.VALUE_IS_INFINITY
            } else if (java.lang.Double.isNaN(value.asDouble())) {
                return Flag.VALUE_IS_NAN
            }

            ValueType.FLOAT -> if (java.lang.Float.isInfinite(value.asFloat())) {
                return Flag.VALUE_IS_INFINITY
            } else if (java.lang.Float.isNaN(value.asFloat())) {
                return Flag.VALUE_IS_NAN
            }

            else ->             // is not a floating point number
                return flag
        }
        return flag
    }

    companion object {
        @JvmStatic
        fun getDriverConfigAsJsonObject(config: DriverConfig): JsonObject {
            val restConfig = RestDriverConfigMapper.getRestDriverConfig(config)
            return Gson().toJsonTree(restConfig, RestDriverConfig::class.java).asJsonObject
        }

        @JvmStatic
        fun getDeviceConfigAsJsonObject(config: DeviceConfig): JsonObject {
            val restConfig = RestDeviceConfigMapper.getRestDeviceConfig(config)
            return Gson().toJsonTree(restConfig, RestDeviceConfig::class.java).asJsonObject
        }

        @JvmStatic
        fun getChannelConfigAsJsonObject(config: ChannelConfig): JsonObject {
            val restConfig = RestChannelConfigMapper.getRestChannelConfig(config)
            return Gson().toJsonTree(restConfig, RestChannelConfig::class.java).asJsonObject
        }
    }
}
