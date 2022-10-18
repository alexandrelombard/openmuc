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
package org.openmuc.framework.driver.dlms.settings

import org.openmuc.framework.driver.spi.ChannelValueContainer.value

class DeviceSettings(settings: String?) : GenericSetting() {
    @Option(value = "ld", range = "int")
    val logicalDeviceAddress = 1

    @Option("cid")
    val clientId = 16

    @Option("sn")
    private val useSn = false

    @Option("emech")
    val encryptionMechanism = -1

    @Option("amech")
    val authenticationMechanism = 0

    @Option("ekey")
    val encryptionKey = byteArrayOf()

    @Option("akey")
    val authenticationKey = byteArrayOf()

    @Option("pass")
    val password = ""

    @Option("cl")
    val challengeLength = 16

    @Option("rt")
    val responseTimeout = 20000

    @Option("mid")
    val manufacturerId = "MMM"

    @Option("did")
    val deviceId: Long = 1

    init {
        super.parseFields(settings)
    }

    fun useSn(): Boolean {
        return useSn
    }
}
