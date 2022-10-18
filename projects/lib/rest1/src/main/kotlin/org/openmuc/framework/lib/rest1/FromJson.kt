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
import org.openmuc.framework.data.Record.value
import org.openmuc.framework.dataaccess.DeviceState
import org.openmuc.framework.lib.rest1.exceptions.MissingJsonObjectException
import org.openmuc.framework.lib.rest1.exceptions.RestConfigIsNotCorrectException
import org.openmuc.framework.lib.rest1.rest.objects.*

class FromJson(jsonString: String?) {
    val gson: Gson
    val jsonObject: JsonObject

    init {
        val gsonBuilder = GsonBuilder().serializeSpecialFloatingPointValues()
        gson = gsonBuilder.create()
        jsonObject = gson.fromJson(jsonString, JsonObject::class.java)
    }

    @Throws(ClassCastException::class)
    fun getRecord(valueType: ValueType): Record? {
        val jse = jsonObject[Const.RECORD]
        return if (jse.isJsonNull) {
            null
        } else convertRestRecordToRecord(gson.fromJson(jse, RestRecord::class.java), valueType)
    }

    @Throws(ClassCastException::class)
    fun getRecordArrayList(valueType: ValueType): ArrayList<Record?>? {
        var recordList: ArrayList<Record?>? = ArrayList()
        val jse = jsonObject[Const.RECORDS]
        if (jse != null && jse.isJsonArray) {
            val jsa = jse.asJsonArray
            val iteratorJsonArray: Iterator<JsonElement> = jsa.iterator()
            while (iteratorJsonArray.hasNext()) {
                recordList!!.add(getRecord(valueType))
            }
        }
        if (recordList!!.isEmpty()) {
            recordList = null
        }
        return recordList
    }

    @Throws(ClassCastException::class)
    fun getValue(valueType: ValueType): Value? {
        var value: Value? = null
        val jse = jsonObject[Const.RECORD]
        if (!jse.isJsonNull) {
            val record = getRecord(valueType)
            if (record != null) {
                value = record.value
            }
        }
        return value
    }

    val isRunning: Boolean
        get() = jsonObject[Const.RUNNING].asBoolean
    val deviceState: DeviceState?
        get() {
            var ret: DeviceState? = null
            val jse = jsonObject[Const.STATE]
            if (!jse.isJsonNull) {
                ret = gson.fromJson(jse, DeviceState::class.java)
            }
            return ret
        }

    @Throws(
        JsonSyntaxException::class,
        IdCollisionException::class,
        RestConfigIsNotCorrectException::class,
        MissingJsonObjectException::class
    )
    fun setChannelConfig(channelConfig: ChannelConfig?, id: String) {
        val jse = jsonObject[Const.CONFIGS]
        if (jse.isJsonNull) {
            throw MissingJsonObjectException()
        }
        RestChannelConfigMapper.setChannelConfig(channelConfig, gson.fromJson(jse, RestChannelConfig::class.java), id)
    }

    @Throws(
        JsonSyntaxException::class,
        IdCollisionException::class,
        RestConfigIsNotCorrectException::class,
        MissingJsonObjectException::class
    )
    fun setDeviceConfig(deviceConfig: DeviceConfig?, id: String) {
        val jse = jsonObject[Const.CONFIGS]
        if (!jse.isJsonNull) {
            RestDeviceConfigMapper.setDeviceConfig(deviceConfig, gson.fromJson(jse, RestDeviceConfig::class.java), id)
        } else {
            throw MissingJsonObjectException()
        }
    }

    @Throws(
        JsonSyntaxException::class,
        IdCollisionException::class,
        RestConfigIsNotCorrectException::class,
        MissingJsonObjectException::class
    )
    fun setDriverConfig(driverConfig: DriverConfig?, id: String) {
        val jse = jsonObject[Const.CONFIGS]
        if (!jse.isJsonNull) {
            RestDriverConfigMapper.setDriverConfig(driverConfig, gson.fromJson(jse, RestDriverConfig::class.java), id)
        } else {
            throw MissingJsonObjectException()
        }
    }

    fun getStringArrayList(listName: String?): ArrayList<String?>? {
        var resultList: ArrayList<String?>? = ArrayList()
        val jse = jsonObject[listName]
        if (jse != null && jse.isJsonArray) {
            val jsa = jse.asJsonArray
            val iteratorJsonArray: Iterator<JsonElement> = jsa.iterator()
            while (iteratorJsonArray.hasNext()) {
                resultList!!.add(iteratorJsonArray.next().toString())
            }
        }
        if (resultList!!.isEmpty()) {
            resultList = null
        }
        return resultList
    }

    fun getStringArray(listName: String?): Array<String?>? {
        var stringArray: Array<String?>? = null
        val jse = jsonObject[listName]
        if (!jse.isJsonNull && jse.isJsonArray) {
            stringArray = gson.fromJson<Array<String?>>(jse, Array<String>::class.java)
        }
        return stringArray
    }

    val restChannelList: List<RestChannel>?
        get() {
            val recordList = ArrayList<RestChannel>()
            val jse = jsonObject["records"]
            val jsa: JsonArray
            if (!jse.isJsonNull && jse.isJsonArray) {
                jsa = jse.asJsonArray
                val jseIterator: Iterator<JsonElement> = jsa.iterator()
                while (jseIterator.hasNext()) {
                    val jsoIterated = jseIterator.next().asJsonObject
                    val rc = gson.fromJson(jsoIterated, RestChannel::class.java)
                    recordList.add(rc)
                }
            }
            return if (recordList.isEmpty()) {
                null
            } else recordList
        }
    val restUserConfig: RestUserConfig
        get() {
            val jso = jsonObject[Const.CONFIGS].asJsonObject
            return gson.fromJson(jso, RestUserConfig::class.java)
        }

    // TODO: another name?
    val deviceScanInfoList: List<DeviceScanInfo?>?
        get() {
            var returnValue: MutableList<DeviceScanInfo?>? = ArrayList()
            val jse = jsonObject[Const.CHANNELS] // TODO: another name?
            val jsa: JsonArray
            if (jse.isJsonArray) {
                jsa = jse.asJsonArray
                val jseIterator: Iterator<JsonElement> = jsa.iterator()
                while (jseIterator.hasNext()) {
                    val jso = jseIterator.next().asJsonObject
                    val id = getString(jso[Const.ID])
                    val deviceAddress = getString(jso[Const.DEVICEADDRESS])
                    val settings = getString(jso[Const.SETTINGS])
                    val description = getString(jso[Const.DESCRIPTION])
                    returnValue!!.add(DeviceScanInfo(id, deviceAddress, settings, description))
                }
            } else {
                returnValue = null
            }
            return returnValue
        }

    // TODO: another name?
    val channelScanInfoList: List<ChannelScanInfo?>?
        get() {
            var returnValue: MutableList<ChannelScanInfo?>? = ArrayList()
            val jse = jsonObject[Const.CHANNELS] // TODO: another name?
            val jsa: JsonArray
            if (jse.isJsonArray) {
                jsa = jse.asJsonArray
                val jseIterator: Iterator<JsonElement> = jsa.iterator()
                while (jseIterator.hasNext()) {
                    val jso = jseIterator.next().asJsonObject
                    val channelAddress = getString(jso[Const.CHANNELADDRESS])
                    val valueType = ValueType.valueOf(getString(jso[Const.VALUETYPE]))
                    val valueTypeLength = getInt(jso[Const.VALUETYPELENGTH])
                    val description = getString(jso[Const.DESCRIPTION])
                    val readable = getBoolean(jso[Const.READABLE])
                    val writeable = getBoolean(jso[Const.WRITEABLE])
                    val metadata = getString(jso[Const.METADATA])
                    returnValue!!.add(
                        ChannelScanInfo(
                            channelAddress, description, valueType, valueTypeLength, readable,
                            writeable, metadata
                        )
                    )
                }
            } else {
                returnValue = null
            }
            return returnValue
        }

    private fun getString(jse: JsonElement?): String {
        return if (jse != null) {
            jse.asString
        } else {
            ""
        }
    }

    private fun getInt(jse: JsonElement?): Int {
        return jse?.asInt ?: 0
    }

    private fun getBoolean(jse: JsonElement?): Boolean {
        return jse?.asBoolean ?: true
    }

    @Throws(ClassCastException::class)
    private fun convertRestRecordToRecord(rrc: RestRecord, type: ValueType): Record {
        val value = rrc.value
        val flag = rrc.flag
        var retValue: Value? = null
        if (value != null) {
            retValue = convertValueToMucValue(type, value)
        }
        return if (flag == null) {
            Record(retValue, rrc.timestamp)
        } else {
            Record(retValue, rrc.timestamp, rrc.flag)
        }
    }

    @Throws(ClassCastException::class)
    private fun convertValueToMucValue(type: ValueType, value: Any): Value {
        // TODO: check all value types, if it is really a float, double, ...
        var value: Any? = value
        if (value!!.javaClass.isInstance(RestValue())) {
            value = (value as RestValue?).getValue()
        }
        return when (type) {
            ValueType.FLOAT -> FloatValue((value as Double?)!!.toFloat())
            ValueType.DOUBLE -> DoubleValue((value as Double?)!!)
            ValueType.SHORT -> ShortValue((value as Double?)!!.toInt())
            ValueType.INTEGER -> IntValue((value as Double?)!!.toInt())
            ValueType.LONG -> LongValue((value as Double?)!!.toLong())
            ValueType.BYTE -> ByteValue((value as Double?)!!.toInt())
            ValueType.BOOLEAN -> BooleanValue((value as Boolean?)!!)
            ValueType.BYTE_ARRAY -> {
                val arrayList: List<Double>? = value as ArrayList<Double>?
                val byteArray = ByteArray(arrayList!!.size)
                var i = 0
                while (i < arrayList.size) {
                    byteArray[i] = arrayList[i].toInt().toByte()
                    ++i
                }
                ByteArrayValue(byteArray)
            }

            ValueType.STRING -> StringValue((value as String?)!!)
            else ->             // should not occur
                StringValue(value.toString())
        }
    }
}
