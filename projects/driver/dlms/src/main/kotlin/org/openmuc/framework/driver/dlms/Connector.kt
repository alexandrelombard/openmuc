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

import org.openmuc.framework.config.ArgumentSyntaxException
import org.openmuc.framework.driver.dlms.settings.DeviceAddress
import org.openmuc.framework.driver.dlms.settings.DeviceSettings
import org.openmuc.framework.driver.spi.ChannelValueContainer
import org.openmuc.framework.driver.spi.ConnectionException
import org.openmuc.jdlms.*
import org.openmuc.jdlms.SecuritySuite.EncryptionMechanism
import org.openmuc.jdlms.settings.client.ReferencingMethod
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

internal object Connector {
    @Throws(ArgumentSyntaxException::class, ConnectionException::class)
    fun buildDlmsConection(deviceAddress: DeviceAddress, deviceSettings: DeviceSettings): DlmsConnection {
        val refMethod = extractReferencingMethod(deviceSettings)
        return try {
            newConnectionBuilder(deviceAddress, deviceSettings)
                .setSecuritySuite(setSecurityLevel(deviceSettings))
                .setChallengeLength(deviceSettings.challengeLength)
                .setClientId(deviceSettings.clientId)
                .setSystemTitle(deviceSettings.manufacturerId, deviceSettings.deviceId)
                .setLogicalDeviceId(deviceSettings.logicalDeviceAddress)
                .setPhysicalDeviceAddress(deviceAddress.physicalDeviceAddress)
                .setResponseTimeout(deviceSettings.responseTimeout)
                .setSystemTitle(deviceSettings.manufacturerId, deviceSettings.deviceId)
                .setReferencingMethod(refMethod)
                .build()
        } catch (e: IOException) {
            throw ConnectionException(e.message)
        }
    }

    @Throws(ArgumentSyntaxException::class)
    private fun newConnectionBuilder(
        deviceAddress: DeviceAddress,
        deviceSettings: DeviceSettings
    ): ConnectionBuilder<*> {
        return when (deviceAddress.connectionType.lowercase(Locale.getDefault())) {
            "tcp" -> newTcpConectionBuilderFor(deviceAddress, deviceSettings)
            "serial" -> newSerialConectionBuilderFor(deviceAddress)
            else -> throw ArgumentSyntaxException("Only TCP and Serial are supported connection types.")
        }
    }

    private fun extractReferencingMethod(deviceSettings: DeviceSettings): ReferencingMethod {
        return if (deviceSettings.useSn()) {
            ReferencingMethod.SHORT
        } else {
            ReferencingMethod.LOGICAL
        }
    }

    private fun newTcpConectionBuilderFor(
        deviceAddress: DeviceAddress,
        deviceSettings: DeviceSettings
    ): ConnectionBuilder<TcpConnectionBuilder> {
        val tcpConnectionBuilder = TcpConnectionBuilder(deviceAddress.hostAddress)
            .setPort(deviceAddress.port)
        if (deviceAddress.useHdlc()) {
            tcpConnectionBuilder.useHdlc()
        } else {
            tcpConnectionBuilder.useWrapper()
        }
        return tcpConnectionBuilder
    }

    private fun newSerialConectionBuilderFor(deviceAddress: DeviceAddress): ConnectionBuilder<*> {
        val serialConnectionBuilder = SerialConnectionBuilder(deviceAddress.serialPort)
            .setBaudRate(deviceAddress.baudrate)
            .setBaudRateChangeTime(deviceAddress.baudRateChangeDelay)
            .setIec21Address(deviceAddress.iec21Address)
        if (deviceAddress.enableBaudRateHandshake()) {
            serialConnectionBuilder.enableHandshake()
        } else {
            serialConnectionBuilder.disableHandshake()
        }
        return serialConnectionBuilder
    }

    @Throws(ArgumentSyntaxException::class)
    private fun setSecurityLevel(deviceSettings: DeviceSettings): SecuritySuite {
        val encryptionMechanism: EncryptionMechanism
        val authenticationMechanism: AuthenticationMechanism
        encryptionMechanism = try {
            EncryptionMechanism.getInstance(deviceSettings.encryptionMechanism.toLong())
        } catch (e: IllegalArgumentException) {
            throw ArgumentSyntaxException(
                "The given Encryption Mechanism " + deviceSettings.encryptionMechanism + " is unknown."
            )
        }
        authenticationMechanism = try {
            AuthenticationMechanism.forId(deviceSettings.authenticationMechanism)
        } catch (e: IllegalArgumentException) {
            throw ArgumentSyntaxException(
                "The given Authentication Mechanism " + deviceSettings.encryptionMechanism + " is unknown."
            )
        }
        var encryptionKeyBytes: ByteArray? = null
        if (encryptionMechanism == EncryptionMechanism.AES_GCM_128
            || authenticationMechanism == AuthenticationMechanism.HLS5_GMAC
        ) {
            encryptionKeyBytes = deviceSettings.encryptionKey
        }
        val authKeyData = extractAuthKey(deviceSettings, encryptionMechanism, authenticationMechanism)
        val pwData = extractPassword(deviceSettings, authenticationMechanism)
        return SecuritySuite.builder()
            .setAuthenticationMechanism(authenticationMechanism)
            .setEncryptionMechanism(encryptionMechanism)
            .setAuthenticationKey(authKeyData)
            .setGlobalUnicastEncryptionKey(encryptionKeyBytes)
            .setPassword(pwData)
            .build()
    }

    private fun extractAuthKey(
        deviceSettings: DeviceSettings, encryptionMechanism: EncryptionMechanism,
        authenticationMechanism: AuthenticationMechanism
    ): ByteArray? {
        val hlsAuth = (encryptionMechanism == EncryptionMechanism.AES_GCM_128
                || authenticationMechanism == AuthenticationMechanism.HLS5_GMAC)
        return if (hlsAuth) {
            deviceSettings.authenticationKey
        } else {
            null
        }
    }

    private fun extractPassword(
        deviceSettings: DeviceSettings,
        authenticationMechanism: AuthenticationMechanism
    ): ByteArray? {
        if (authenticationMechanism != AuthenticationMechanism.LOW) {
            return null
        }
        return if (deviceSettings.password.startsWith("0x")) {
            val hexStr = deviceSettings.password.substring(2)
            hexToBytes(hexStr)
        } else {
            deviceSettings.password.toByteArray(StandardCharsets.US_ASCII)
        }
    }

    private fun hexToBytes(s: String): ByteArray {
        val b = ByteArray(s.length / 2)
        var index: Int
        for (i in b.indices) {
            index = i * 2
            b[i] = s.substring(index, index + 2).toInt(16).toByte()
        }
        return b
    }
}
