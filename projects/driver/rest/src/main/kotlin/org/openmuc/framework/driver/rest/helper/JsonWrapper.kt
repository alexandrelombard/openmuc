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
package org.openmuc.framework.driver.rest.helper

import com.google.gson.JsonElement
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.lib.rest1.Const
import java.io.InputStream

class JsonWrapper {
    fun fromRecord(remoteRecord: Record?, valueType: ValueType?): String {
        val toJson = ToJson()
        toJson.addRecord(remoteRecord, valueType)
        return toJson.toString()
    }

    @Throws(IOException::class)
    fun tochannelScanInfos(stream: InputStream?): List<ChannelScanInfo> {
        val jsonString = getStringFromInputStream(stream)
        val fromJson = FromJson(jsonString)
        val channelList: List<RestChannel> = fromJson.getRestChannelList()
        val channelScanInfos: ArrayList<ChannelScanInfo> = ArrayList<ChannelScanInfo>()
        for (restChannel in channelList) {
            // TODO: get channel config list with valueTypeLength, description, ...
            val channelScanInfo = ChannelScanInfo(
                restChannel.getId(), "", restChannel.getValueType(),
                0
            )
            channelScanInfos.add(channelScanInfo)
        }
        return channelScanInfos
    }

    @Throws(IOException::class)
    fun toRecord(stream: InputStream?, valueType: ValueType?): Record {
        val jsonString = getStringFromInputStream(stream)
        val fromJson = FromJson(jsonString)
        return fromJson.getRecord(valueType)
    }

    @Throws(IOException::class)
    fun toTimestamp(stream: InputStream?): Long {
        val jsonString = getStringFromInputStream(stream)
        val fromJson = FromJson(jsonString)
        val timestamp: JsonElement = fromJson.getJsonObject().get(Const.TIMESTAMP)
            ?: return -1
        return timestamp.getAsNumber().longValue()
    }

    @Throws(IOException::class)
    private fun getStringFromInputStream(stream: InputStream?): String {
        val streamReader = BufferedReader(InputStreamReader(stream, "UTF-8"))
        val responseStrBuilder = StringBuilder()
        var inputStr: String?
        while (streamReader.readLine().also { inputStr = it } != null) {
            responseStrBuilder.append(inputStr)
        }
        return responseStrBuilder.toString()
    }
}
