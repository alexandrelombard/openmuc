package org.openmuc.framework.driver.modbus.rtutcp.bonino

import com.ghgande.j2mod.modbus.Modbus
import com.ghgande.j2mod.modbus.io.ModbusTransport
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.Socket

/**
 * @author bonino
 *
 * https://github.com/dog-gateway/jamod-rtu-over-tcp
 */
class RTUTCPMasterConnection// store the IP address of the destination

// store the port of the destination
/**
 * Constructs an [RTUTCPMasterConnection] instance with a given destination address and port. It permits to
 * handle Modbus RTU over TCP connections in a way similar to standard Modbus/TCP connections
 *
 * @param adr
 * the destination IP addres as an [InetAddress] instance.
 * @param port
 * the port to which connect on the destination address.
 */(
    /**
     * Returns the destination [InetAddress] of this [RTUTCPMasterConnection].
     *
     * @return the destination address as InetAddress.
     */ // getAddress
    // the ip address of the remote slave
    private var slaveIPAddress: InetAddress,
    /**
     * Returns the destination port of this [RTUTCPMasterConnection].
     *
     * @return the port number as an `int`.
     */ // getPort
    // the port to which connect on the remote slave
    var address: Int
) : MasterConnection {
    // the socket upon which sending/receiveing Modbus RTU data
    private var socket: Socket? = null

    // the timeout for the socket
    private var socketTimeout = Modbus.DEFAULT_TIMEOUT

    /**
     * Tests if this [RTUTCPMasterConnection] is active or not.
     *
     * @return `true` if connected, `false` otherwise.
     */ // isConnected
    // a flag for detecting if the connection is up or not
    override var isConnected = false
        private set
    /**
     * Sets the destination port of this [RTUTCPMasterConnection].
     *
     * @param address
     * the port number as `int`.
     */ // setPort
    /**
     * Sets the destination [InetAddress] of this [RTUTCPMasterConnection].
     *
     * @param slaveIPAddress
     * the destination address as [InetAddress].
     */ // setAddress
    // private int retries = Modbus.DEFAULT_RETRIES;
    // the RTU over TCP transport
    private var modbusRTUTCPTransport: ModbusRTUTCPTransport? = null

    /**
     * Opens the RTU over TCP connection represented by this object.
     *
     * @throws Exception
     * if the connection cannot be open (e.g., due to a network failure).
     */
    @Synchronized
    @Throws(Exception::class)
    override fun connect() {
        // if not connected, try to connect
        if (!isConnected) {
            // handle debug...(TODO: logging?)
            logger.info("connecting...")

            // create a socket towards the remote slave
            socket = Socket(slaveIPAddress, address)

            // set the socket timeout
            timeout = socketTimeout

            // prepare the RTU over TCP transport to handle communications
            prepareTransport()

            // set the connected flag at true
            isConnected = true
            logger.info("successfully connected")
        }
    } // connect

    /**
     * Closes the RTU over TCP connection represented by this object.
     */
    override fun close() {
        // if connected... disconnect, otherwise do nothing
        if (isConnected) {
            // try closing the transport...
            try {
                modbusRTUTCPTransport!!.close()
            } catch (e: IOException) {
                logger.error("error while closing the connection, cause:", e)
            }

            // if everything is fine, set the connected flag at false
            isConnected = false
        }
    } // close

    /**
     * Returns the ModbusTransport associated with this TCPMasterConnection.
     *
     * @return the connection's ModbusTransport.
     */ // getModbusTransport
    val modbusTransport: ModbusTransport?
        get() = modbusRTUTCPTransport

    /**
     * Prepares the associated [ModbusTransport] of this [RTUTCPMasterConnection] for use.
     *
     * @throws IOException
     * if an I/O related error occurs.
     */
    @Throws(IOException::class)
    private fun prepareTransport() {
        // if the modbus transport is not available, create it
        if (modbusRTUTCPTransport == null) {
            // create the transport
            modbusRTUTCPTransport = ModbusRTUTCPTransport(socket)
        } else {
            // just update the transport socket
            modbusRTUTCPTransport!!.setSocket(socket)
        }
    } // prepareIO// TODO: handle?
    // setReceiveTimeout
// store the current socket timeout

    // set the timeout on the socket, if available
    /**
     * Sets the timeout for this [RTUTCPMasterConnection].
     *
     * @param timeout
     * the timeout as an `int`.
     */
    /**
     * Returns the timeout for this [RTUTCPMasterConnection].
     *
     * @return the timeout as an `int` value.
     */ // getReceiveTimeout
    var timeout: Int
        get() = socketTimeout
        set(timeout) {
            // store the current socket timeout
            socketTimeout = timeout

            // set the timeout on the socket, if available
            if (socket != null) {
                try {
                    socket!!.soTimeout = socketTimeout
                } catch (ex: IOException) {
                    // TODO: handle?
                }
            }
        }

    companion object {
        private val logger = LoggerFactory.getLogger(RTUTCPMasterConnection::class.java)

        // the log identifier
        const val logId = "[RTUTCPMasterConnection]: "
    }
}
