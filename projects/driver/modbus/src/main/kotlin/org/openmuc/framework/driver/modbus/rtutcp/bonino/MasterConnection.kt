package org.openmuc.framework.driver.modbus.rtutcp.bonino

import org.openmuc.framework.driver.spi.ChannelValueContainer.value

/**
 * A common interface for master connections (not strictly covering serial connections)
 *
 * @author bonino
 *
 * https://github.com/dog-gateway/jamod-rtu-over-tcp
 */
interface MasterConnection {
    @Throws(Exception::class)
    fun connect()
    val isConnected: Boolean
    fun close()
}
