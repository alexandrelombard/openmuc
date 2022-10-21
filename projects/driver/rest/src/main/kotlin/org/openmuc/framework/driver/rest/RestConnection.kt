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
package org.openmuc.framework.driver.rest

import org.apache.commons.codec.binary.Base64
import org.openmuc.framework.config.ChannelScanInfo
import org.openmuc.framework.data.Flag
import org.openmuc.framework.data.Record
import org.openmuc.framework.data.Value
import org.openmuc.framework.data.ValueType
import org.openmuc.framework.dataaccess.DataAccessService
import org.openmuc.framework.driver.rest.helper.JsonWrapper
import org.openmuc.framework.driver.spi.*
import org.openmuc.framework.lib.rest1.Const
import java.io.*
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLConnection
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

class RestConnection internal constructor(
    deviceAddress: String, credentials: String, timeout: Int, checkTimestamp: Boolean,
    dataAccessService: DataAccessService?
) : Connection {
    private val wrapper: JsonWrapper
    private var url: URL? = null
    private var con: URLConnection? = null
    private var baseAddress: String? = null
    private val timeout: Int
    private var isHTTPS = false
    private val authString: String
    private val dataAccessService: DataAccessService?
    private var connectionAddress: String? = null
    private val checkTimestamp: Boolean

    init {
        this.checkTimestamp = checkTimestamp
        this.dataAccessService = dataAccessService
        this.timeout = timeout
        wrapper = JsonWrapper()
        authString = String(Base64.encodeBase64(credentials.toByteArray()))
        if (!deviceAddress.endsWith("/")) {
            baseAddress = "$deviceAddress/rest/channels/"
            connectionAddress = "$deviceAddress/rest/connect/"
        } else {
            baseAddress = deviceAddress + "rest/channels/"
            connectionAddress = deviceAddress + "rest/connect/"
        }
        isHTTPS = deviceAddress.startsWith("https://")
        if (isHTTPS) {
            val trustManager = trustManager
            try {
                val sc = SSLContext.getInstance("SSL")
                sc.init(null, trustManager, SecureRandom())
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            } catch (e1: KeyManagementException) {
                throw ConnectionException(e1.message)
            } catch (e: NoSuchAlgorithmException) {
                throw ConnectionException(e.message)
            }

            // Create all-trusting host name verifier
            val allHostsValid = hostnameVerifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)
        }
    }

    private val hostnameVerifier: HostnameVerifier
        get() = HostnameVerifier { _, _ -> true }
    private val trustManager: Array<TrustManager>
        get() = arrayOf(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate>? {
                return null
            }

            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        })

    @Throws(ConnectionException::class)
    private fun readChannel(channelAddress: String, valueType: ValueType?): Record? {
        val newRecord = try {
            wrapper.toRecord(get(channelAddress), valueType)
        } catch (e: IOException) {
            throw ConnectionException(e.message)
        }
        return newRecord
    }

    @Throws(ConnectionException::class)
    private fun readChannelTimestamp(channelAddress: String): Long {
        var channelAddress = channelAddress
        var timestamp: Long = -1
        try {
            channelAddress += if (channelAddress.endsWith("/")) {
                Const.TIMESTAMP
            } else {
                '/'.toString() + Const.TIMESTAMP
            }
            timestamp = wrapper.toTimestamp(get(channelAddress))
        } catch (e: IOException) {
            throw ConnectionException(e.message)
        }
        return timestamp
    }

    @Throws(ConnectionException::class)
    private fun readDeviceChannelList(): List<ChannelScanInfo> {
        return try {
            wrapper.tochannelScanInfos(get(""))
        } catch (e: IOException) {
            throw ConnectionException(e.message)
        }
    }

    @Throws(ConnectionException::class)
    private fun writeChannel(channelAddress: String?, value: Value?, valueType: ValueType?): Flag {
        val remoteRecord = Record(value, System.currentTimeMillis(), Flag.VALID)
        return put(channelAddress, wrapper.fromRecord(remoteRecord, valueType))
    }

    @Throws(ConnectionException::class)
    fun connect() {
        try {
            url = URL(connectionAddress)
            con = url!!.openConnection()
            setConnectionProberties()
        } catch (e: MalformedURLException) {
            throw ConnectionException("malformed URL: $connectionAddress")
        } catch (e: IOException) {
            throw ConnectionException(e.message)
        }
        try {
            con!!.connect()
            checkResponseCode(con)
        } catch (e: IOException) {
            throw ConnectionException(e.message)
        }
    }

    @Throws(ConnectionException::class)
    private operator fun get(suffix: String): InputStream {
        var stream: InputStream?
        try {
            url = URL(baseAddress + suffix)
            con = url!!.openConnection()
            setConnectionProberties()
            stream = con!!.getInputStream()
        } catch (e: MalformedURLException) {
            throw ConnectionException("malformed URL: $baseAddress")
        } catch (e: IOException) {
            throw ConnectionException(e.message)
        }
        checkResponseCode(con)
        return stream
    }

    @Throws(ConnectionException::class)
    private fun put(suffix: String?, output: String?): Flag {
        try {
            url = URL(baseAddress + suffix)
            con = url!!.openConnection()
            con!!.setDoOutput(true)
            setConnectionProberties()
            if (isHTTPS) {
                (con as HttpsURLConnection?)!!.requestMethod = "PUT"
            } else {
                (con as HttpURLConnection?)!!.requestMethod = "PUT"
            }
            val out = OutputStreamWriter(con!!.getOutputStream())
            out.write(output)
            out.close()
        } catch (e: MalformedURLException) {
            throw ConnectionException("malformed URL: $baseAddress")
        } catch (e: IOException) {
            throw ConnectionException(e.message)
        }
        return checkResponseCode(con)
    }

    private fun setConnectionProberties() {
        con!!.connectTimeout = timeout
        con!!.readTimeout = timeout
        con!!.setRequestProperty("Connection", "Keep-Alive")
        con!!.setRequestProperty("Content-Type", "application/json")
        con!!.setRequestProperty("Accept", "application/json")
        con!!.setRequestProperty("Authorization", "Basic $authString")
    }

    @Throws(ConnectionException::class)
    private fun checkResponseCode(con: URLConnection?): Flag {
        val respCode: Int
        return try {
            if (isHTTPS) {
                respCode = (con as HttpsURLConnection?)!!.responseCode
                if (!(respCode >= 200 && respCode < 300)) {
                    throw ConnectionException(
                        "HTTPS " + respCode + ":" + con!!.responseMessage
                    )
                }
            } else {
                respCode = (con as HttpURLConnection?)!!.responseCode
                if (!(respCode >= 200 && respCode < 300)) {
                    throw ConnectionException(
                        "HTTP " + respCode + ":" + con!!.responseMessage
                    )
                }
            }
            Flag.VALID
        } catch (e: IOException) {
            throw ConnectionException(e.message)
        }
    }

    override fun disconnect() {
        if (isHTTPS) {
            (con as HttpsURLConnection?)!!.disconnect()
        } else {
            (con as HttpURLConnection?)!!.disconnect()
        }
    }

    @Throws(ConnectionException::class)
    override fun read(containers: List<ChannelRecordContainer>, containerListHandle: Any?, samplingGroup: String?): Any? {
        // TODO: add grouping (reading device/driver at once)
        for (container in containers) {
            var record: Record?
            if (checkTimestamp) {
                val channelId = container.channel!!.id
                val channel = dataAccessService!!.getChannel(channelId)
                record = channel!!.latestRecord
                if (record!!.timestamp == null || record.flag !== Flag.VALID || record.timestamp!! < readChannelTimestamp(
                        container.channelAddress
                    )
                ) {
                    record = readChannel(container.channelAddress, container.channel!!.valueType)
                }
            } else {
                record = readChannel(container.channelAddress, container.channel!!.valueType)
            }
            if (record != null) {
                container.record = record
            } else {
                container.record = Record(Flag.DRIVER_ERROR_READ_FAILURE)
            }
        }
        return null
    }

    @Throws(ConnectionException::class)
    override fun scanForChannels(settings: String): List<ChannelScanInfo> {
        return readDeviceChannelList()
    }

    @Throws(ConnectionException::class)
    override fun startListening(containers: List<ChannelRecordContainer>, listener: RecordsReceivedListener?) {
        throw UnsupportedOperationException()
    }

    @Throws(ConnectionException::class)
    override fun write(containers: List<ChannelValueContainer>, containerListHandle: Any?): Any? {
        for (cont in containers) {
            val value = cont.value
            val flag = writeChannel(cont.channelAddress, value, value.valueType)
            cont.flag = flag
        }
        return null
    }
}
