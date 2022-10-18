package org.openmuc.framework.driver.modbus.rtutcp.bonino

import com.ghgande.j2mod.modbus.Modbus
import com.ghgande.j2mod.modbus.ModbusIOException
import com.ghgande.j2mod.modbus.io.BytesInputStream
import com.ghgande.j2mod.modbus.io.BytesOutputStream
import com.ghgande.j2mod.modbus.io.ModbusTransaction
import com.ghgande.j2mod.modbus.io.ModbusTransport
import com.ghgande.j2mod.modbus.msg.ModbusMessage
import com.ghgande.j2mod.modbus.msg.ModbusRequest
import com.ghgande.j2mod.modbus.msg.ModbusResponse
import com.ghgande.j2mod.modbus.util.ModbusUtil
import org.openmuc.framework.driver.spi.ChannelValueContainer.value
import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.util.*

/**
 * @author bonino
 *
 * https://github.com/dog-gateway/jamod-rtu-over-tcp
 */
class ModbusRTUTCPTransport(socket: Socket?) : ModbusTransport {
    // The input stream from which reading the Modbus frames
    private var inputStream: DataInputStream? = null

    // The output stream to which writing the Modbus frames
    private var outputStream: DataOutputStream? = null

    // The Bytes output stream to use as output buffer for Modbus frames
    private var outputBuffer: BytesOutputStream? = null

    // The BytesInputStream wrapper for the transport input stream
    private var inputBuffer: BytesInputStream? = null

    // The last request sent over the transport ?? useful ??
    private var lastRequest: ByteArray? = null

    // the socket used by this transport
    private var socket: Socket? = null

    // the read timeout timer
    private var readTimeoutTimer: Timer? = null

    // the read timout
    private val readTimeout = 5000 // ms

    // the timeou flag
    private var isTimedOut: Boolean
    private var m_Master: RTUTCPMasterConnection? = null
    private val m_Socket: Socket? = null

    /**
     * @param socket
     * the client socket to close
     *
     * @throws IOException
     * if a I/O exception occurs
     */
    init {
        // prepare the input and output streams...
        socket?.let { setSocket(it) }

        // set the timed out flag at false
        isTimedOut = false
    }

    /**
     * Stores the given [Socket] instance and prepares the related streams to use them for Modbus RTU over TCP
     * communication.
     *
     * @param socket
     * the client socket
     * @throws IOException
     * if a I/O exception occurs
     */
    @Throws(IOException::class)
    fun setSocket(socket: Socket?) {
        if (this.socket != null) {
            // TODO: handle clean closure of the streams
            outputBuffer!!.close()
            inputBuffer!!.close()
            inputStream!!.close()
            outputStream!!.close()
        }

        // store the socket used by this transport
        this.socket = socket

        // get the input and output streams
        inputStream = DataInputStream(socket!!.getInputStream())
        outputStream = DataOutputStream(this.socket!!.getOutputStream())

        // prepare the buffers
        outputBuffer = BytesOutputStream(Modbus.MAX_MESSAGE_LENGTH)
        inputBuffer = BytesInputStream(Modbus.MAX_MESSAGE_LENGTH)
    }

    /**
     * writes the given ModbusMessage over the physical transport handled by this object.
     *
     * @param msg
     * the [ModbusMessage] to be written on the transport.
     */
    @Synchronized
    @Throws(ModbusIOException::class)
    override fun writeMessage(msg: ModbusMessage) {
        try {
            // atomic access to the output buffer
            synchronized(outputBuffer!!) {


                // reset the output buffer
                outputBuffer!!.reset()

                // prepare the message for "virtual" serial transport
                msg.setHeadless()

                // write the message to the output buffer
                msg.writeTo(outputBuffer)

                // compute the CRC
                val crc = ModbusUtil.calculateCRC(outputBuffer!!.buffer, 0, outputBuffer!!.size())

                // write the CRC on the output buffer
                outputBuffer!!.writeByte(crc[0])
                outputBuffer!!.writeByte(crc[1])

                // store the buffer length
                val bufferLength = outputBuffer!!.size()

                // store the raw output buffer reference
                val rawBuffer = outputBuffer!!.buffer

                // write the buffer on the socket
                outputStream!!.write(rawBuffer, 0, bufferLength) // PDU +
                // CRC
                outputStream!!.flush()
                if (logger.isTraceEnabled) {
                    logger.trace("Sent: " + ModbusUtil.toHex(rawBuffer, 0, bufferLength))
                }

                // store the written buffer as the last request
                lastRequest = ByteArray(bufferLength)
                System.arraycopy(rawBuffer, 0, lastRequest, 0, bufferLength)

                // sleep for the time needed to receive the request at the other
                // point of the connection
                outputBuffer.wait(bufferLength.toLong())
            }
        } catch (ex: Exception) {
            throw ModbusIOException("I/O failed to write")
        }
    } // writeMessage

    // This is required for the slave that is not supported
    @Synchronized
    @Throws(ModbusIOException::class)
    override fun readRequest(): ModbusRequest {
        throw RuntimeException("Operation not supported.")
    } // readRequest

    @Synchronized
    @Throws(ModbusIOException::class)
    /**
     * Lazy implementation: avoid CRC validation...
     */
    override fun readResponse(): ModbusResponse {
        // the received response
        var response: ModbusResponse? = null

        // reset the timed out flag
        isTimedOut = false

        // init and start the timeout timer
        readTimeoutTimer = Timer()
        readTimeoutTimer!!.schedule(object : TimerTask() {
            override fun run() {
                isTimedOut = true
            }
        }, readTimeout.toLong())
        try {
            // atomic access to the input buffer
            synchronized(inputBuffer!!) {

                // clean the input buffer
                inputBuffer!!.reset(ByteArray(Modbus.MAX_MESSAGE_LENGTH))

                // sleep for the time needed to receive the first part of the
                // response
                var available = inputStream!!.available()
                while (available < 4 && !isTimedOut) {
                    Thread.yield() // 1ms * #bytes (4bytes in the worst case)
                    available = inputStream!!.available()

                    // if (logger.isTraceEnabled()) {
                    // logger.trace("Available bytes: " + available);
                    // }
                }

                // check if timedOut
                if (isTimedOut) {
                    throw ModbusIOException("I/O exception - read timeout.\n")
                }

                // get a reference to the inner byte buffer
                val inBuffer = inputBuffer!!.buffer

                // read the first 2 bytes from the input stream
                inputStream!!.read(inBuffer, 0, 2)
                // this.inputStream.readFully(inBuffer);

                // read the progressive id
                val packetId = inputBuffer!!.readUnsignedByte()
                if (logger.isTraceEnabled) {
                    logger.trace(logId + "Read packet with progressive id: " + packetId)
                }

                // read the function code
                val functionCode = inputBuffer!!.readUnsignedByte()
                if (logger.isTraceEnabled) {
                    logger.trace(" uid: $packetId, function code: $functionCode")
                }

                // compute the number of bytes composing the message (including
                // the CRC = 2bytes)
                val packetLength = computePacketLength(functionCode)

                // sleep for the time needed to receive the first part of the
                // response
                while (inputStream!!.available() < packetLength - 3 && !isTimedOut) {
                    try {
                        inputBuffer.wait(10)
                    } catch (ie: InterruptedException) {
                        // do nothing
                        System.err.println("Sleep interrupted while waiting for response body...\n$ie")
                    }
                }

                // check if timedOut
                if (isTimedOut) {
                    throw ModbusIOException("I/O exception - read timeout.\n")
                }

                // read the remaining bytes
                inputStream!!.read(inBuffer, 3, packetLength)
                if (logger.isTraceEnabled) {
                    logger.trace(
                        " bytes: " + ModbusUtil.toHex(inBuffer, 0, packetLength) + ", desired length: "
                                + packetLength
                    )
                }

                // compute the CRC
                val crc = ModbusUtil.calculateCRC(inBuffer, 0, packetLength - 2)

                // check the CRC against the received one...
                if (ModbusUtil.unsignedByteToInt(inBuffer[packetLength - 2]) != crc[0]
                    || ModbusUtil.unsignedByteToInt(inBuffer[packetLength - 1]) != crc[1]
                ) {
                    throw IOException(
                        "CRC Error in received frame: " + packetLength + " bytes: "
                                + ModbusUtil.toHex(inBuffer, 0, packetLength)
                    )
                }

                // reset the input buffer to the given packet length (excluding
                // the CRC)
                inputBuffer!!.reset(inBuffer, packetLength - 2)

                // create the response
                response = ModbusResponse.createModbusResponse(functionCode)
                response.setHeadless()

                // read the response
                response.readFrom(inputBuffer)
            }
        } catch (e: IOException) {
            // debug
            System.err.println(logId + "Error while reading from socket: " + e)

            // clean the input stream
            try {
                while (inputStream!!.read() != -1) {
                }
            } catch (e1: IOException) {
                // debug
                System.err.println(logId + "Error while emptying input buffer from socket: " + e)
            }
            throw ModbusIOException("I/O exception - failed to read.\n$e")
        }

        // reset the timeout timer
        readTimeoutTimer!!.cancel()

        // return the response read from the socket stream
        return response!!

        /*-------------------------- SERIAL IMPLEMENTATION -----------------------------------
        
        try
        {
        	do
        	{
        		// block the input stream
        		synchronized (byteInputStream)
        		{
        			// get the packet uid
        			int uid = inputStream.read();
        			
        			if (Modbus.debug)
        				System.out.println(ModbusRTUTCPTransport.logId + "UID: " + uid);
        			
        			// if the uid is valid (i.e., > 0) continue
        			if (uid != -1)
        			{
        				// get the function code
        				int fc = inputStream.read();
        				
        				if (Modbus.debug)
        					System.out.println(ModbusRTUTCPTransport.logId + "Function code: " + uid);
        				
        				//bufferize the response
        				byteOutputStream.reset();
        				byteOutputStream.writeByte(uid);
        				byteOutputStream.writeByte(fc);
        				
        				// create the Modbus Response object to acquire length of message
        				response = ModbusResponse.createModbusResponse(fc);
        				response.setHeadless();
        				
        				// With Modbus RTU, there is no end frame. Either we
        				// assume the message is complete as is or we must do
        				// function specific processing to know the correct length.
        				
        				//bufferize the response according to the given function code
        				getResponse(fc, byteOutputStream);
        				
        				//compute the response length without considering the CRC
        				dlength = byteOutputStream.size() - 2; // less the crc
        				
        				//debug
        				if (Modbus.debug)
        					System.out.println("Response: "
        							+ ModbusUtil.toHex(byteOutputStream.getBuffer(), 0, dlength + 2));
        				
        				//TODO: check if needed (restore the buffer state, cursor at 0, same content)
        				byteInputStream.reset(inputBuffer, dlength);
        				
        				// cmopute the buffer CRC
        				int[] crc = ModbusUtil.calculateCRC(inputBuffer, 0, dlength);
        				
        				// check the CRC against the received one...
        				if (ModbusUtil.unsignedByteToInt(inputBuffer[dlength]) != crc[0]
        						|| ModbusUtil.unsignedByteToInt(inputBuffer[dlength + 1]) != crc[1])
        				{
        					throw new IOException("CRC Error in received frame: " + dlength + " bytes: "
        							+ ModbusUtil.toHex(byteInputStream.getBuffer(), 0, dlength));
        				}
        			}
        			else
        			{
        				throw new IOException("Error reading response");
        			}
        			
        			// restore the buffer state, cursor at 0, same content
        			byteInputStream.reset(inputBuffer, dlength);
        			
        			//actually read the response
        			if (response != null)
        			{
        				response.readFrom(byteInputStream);
        			}
        			
        			//flag completion...
        			done = true;
        			
        		}// synchronized
        	}
        	while (!done);
        	return response;
        }
        catch (Exception ex)
        {
        	System.err.println("Last request: " + ModbusUtil.toHex(lastRequest));
        	System.err.println(ex.getMessage());
        	throw new ModbusIOException("I/O exception - failed to read");
        }
        
        ------------------------------------------------------------------------------*/
    } // readResponse

    @Throws(IOException::class)
    private fun computePacketLength(functionCode: Int): Int {
        // packet length by function code:
        var length = 0
        when (functionCode) {
            0x01, 0x02, 0x03, 0x04, 0x0C, 0x11, 0x14, 0x15, 0x17 -> {

                // get a reference to the inner byte buffer
                val inBuffer = inputBuffer!!.buffer
                inputStream!!.read(inBuffer, 2, 1)
                val dataLength = inputBuffer!!.readUnsignedByte()
                length = dataLength + 5 // UID+FC+CRC(2bytes)
            }

            0x05, 0x06, 0x0B, 0x0F, 0x10 -> {

                // read status: only the CRC remains after address and
                // function code
                length = 6
            }

            0x07, 0x08 -> {
                length = 3
            }

            0x16 -> {
                length = 8
            }

            0x18 -> {

                // get a reference to the inner byte buffer
                val inBuffer = inputBuffer!!.buffer
                inputStream!!.read(inBuffer, 2, 2)
                length = inputBuffer!!.readUnsignedShort() + 6 // UID+FC+CRC(2bytes)
            }

            0x83 -> {

                // error code
                length = 5
            }
        }
        return length
    }

    @Throws(IOException::class)
    override fun close() {
        inputStream!!.close()
        outputStream!!.close()
    } // close

    override fun createTransaction(): ModbusTransaction {
        if (m_Master == null) {
            m_Master = RTUTCPMasterConnection(m_Socket!!.inetAddress, m_Socket.port)
        }
        return ModbusRTUTCPTransaction(m_Master!!)
    } /*
     * private void getResponse(int fn, BytesOutputStream out) throws IOException { int bc = -1, bc2 = -1, bcw = -1; int
     * inpBytes = 0; byte inpBuf[] = new byte[256];
     * 
     * try { switch (fn) { case 0x01: case 0x02: case 0x03: case 0x04: case 0x0C: case 0x11: // report slave ID version
     * and run/stop state case 0x14: // read log entry (60000 memory reference) case 0x15: // write log entry (60000
     * memory reference) case 0x17: // read the byte count; bc = inputStream.read(); out.write(bc); // now get the
     * specified number of bytes and the 2 CRC bytes setReceiveThreshold(bc + 2); inpBytes = inputStream.read(inpBuf, 0,
     * bc + 2); out.write(inpBuf, 0, inpBytes); m_CommPort.disableReceiveThreshold(); if (inpBytes != bc + 2) {
     * System.out.println("Error: looking for " + (bc + 2) + " bytes, received " + inpBytes); } break; case 0x05: case
     * 0x06: case 0x0B: case 0x0F: case 0x10: // read status: only the CRC remains after address and // function code
     * setReceiveThreshold(6); inpBytes = inputStream.read(inpBuf, 0, 6); out.write(inpBuf, 0, inpBytes);
     * m_CommPort.disableReceiveThreshold(); break; case 0x07: case 0x08: // read status: only the CRC remains after
     * address and // function code setReceiveThreshold(3); inpBytes = inputStream.read(inpBuf, 0, 3); out.write(inpBuf,
     * 0, inpBytes); m_CommPort.disableReceiveThreshold(); break; case 0x16: // eight bytes in addition to the address
     * and function codes setReceiveThreshold(8); inpBytes = inputStream.read(inpBuf, 0, 8); out.write(inpBuf, 0,
     * inpBytes); m_CommPort.disableReceiveThreshold(); break; case 0x18: // read the byte count word bc =
     * inputStream.read(); out.write(bc); bc2 = inputStream.read(); out.write(bc2); bcw = ModbusUtil.makeWord(bc, bc2);
     * // now get the specified number of bytes and the 2 CRC bytes setReceiveThreshold(bcw + 2); inpBytes =
     * inputStream.read(inpBuf, 0, bcw + 2); out.write(inpBuf, 0, inpBytes); m_CommPort.disableReceiveThreshold();
     * break; } } catch (IOException e) { m_CommPort.disableReceiveThreshold(); throw new IOException(
     * "getResponse serial port exception"); } }// getResponse
     */

    companion object {
        private val logger = LoggerFactory.getLogger(ModbusRTUTCPTransport::class.java)
        const val logId = "[ModbusRTUTCPTransport]: "
    }
}
