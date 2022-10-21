package org.openmuc.framework.driver.modbus.rtutcp.bonino

import com.ghgande.j2mod.modbus.Modbus
import com.ghgande.j2mod.modbus.ModbusException
import com.ghgande.j2mod.modbus.ModbusIOException
import com.ghgande.j2mod.modbus.ModbusSlaveException
import com.ghgande.j2mod.modbus.io.ModbusTransaction
import com.ghgande.j2mod.modbus.io.ModbusTransport
import com.ghgande.j2mod.modbus.msg.ExceptionResponse
import com.ghgande.j2mod.modbus.msg.ModbusRequest
import com.ghgande.j2mod.modbus.msg.ModbusResponse

/**
 * @author bonino
 *
 * https://github.com/dog-gateway/jamod-rtu-over-tcp
 */
class ModbusRTUTCPTransaction : ModbusTransaction {
    // instance attributes and associations
    private var m_Connection: RTUTCPMasterConnection? = null
    private var m_IO: ModbusTransport? = null
    private var m_Request: ModbusRequest? = null
    private var m_Response: ModbusResponse? = null
    private var m_ValidityCheck = Modbus.DEFAULT_VALIDITYCHECK
    /**
     * Sets the flag that controls whether a connection is openend and closed for **each** execution or not.
     *
     * @param b
     * true if reconnecting, false otherwise.
     */ // setReconnecting
    /**
     * Tests if the connection will be openend and closed for **each** execution.
     *
     * @return true if reconnecting, false otherwise.
     */ // isReconnecting
    var isReconnecting = Modbus.DEFAULT_RECONNECTING
    private var m_Retries = Modbus.DEFAULT_RETRIES

    /**
     * Constructs a new `ModbusRTUTCPTransaction` instance.
     */
    constructor() {}

    /**
     * Constructs a new `ModbusTCPTransaction` instance with a given `ModbusRequest` to be send
     * when the transaction is executed.
     *
     * @param request
     * a `ModbusRequest` instance.
     */
    constructor(request: ModbusRequest) {
        setRequest(request)
    } // constructor

    /**
     * Constructs a new `ModbusTCPTransaction` instance with a given `TCPMasterConnection` to be
     * used for transactions.
     *
     * @param con
     * a `TCPMasterConnection` instance.
     */
    constructor(con: RTUTCPMasterConnection) {
        setConnection(con)
        m_IO = con.modbusTransport
    } // constructor

    /**
     * Sets the connection on which this `ModbusTransaction` should be executed.
     *
     *
     * An implementation should be able to handle open and closed connections. <br></br>
     *
     * @param con
     * a `TCPMasterConnection`.
     */
    fun setConnection(con: RTUTCPMasterConnection) {
        m_Connection = con
        m_IO = con.modbusTransport
    } // setConnection

    override fun setRequest(req: ModbusRequest) {
        m_Request = req
    } // setRequest

    override fun getRequest(): ModbusRequest {
        return m_Request!!
    } // getRequest

    override fun getResponse(): ModbusResponse {
        return m_Response!!
    } // getResponse

    override fun getTransactionID(): Int {
        /*
         * Ensure that the transaction ID is in the valid range between 1 and MAX_TRANSACTION_ID (65534). If not, the
         * value will be forced to 1.
         */
        if (c_TransactionID <= 0 && isCheckingValidity) {
            c_TransactionID = 1
        }
        if (c_TransactionID >= Modbus.MAX_TRANSACTION_ID) {
            c_TransactionID = 1
        }
        return c_TransactionID
    } // getTransactionID

    override fun setCheckingValidity(b: Boolean) {
        m_ValidityCheck = b
    } // setCheckingValidity

    override fun isCheckingValidity(): Boolean {
        return m_ValidityCheck
    } // isCheckingValidity

    override fun getRetries(): Int {
        return m_Retries
    } // getRetries

    override fun setRetries(num: Int) {
        m_Retries = num
    } // setRetries

    @Throws(ModbusIOException::class, ModbusSlaveException::class, ModbusException::class)
    override fun execute() {
        if (m_Request == null || m_Connection == null) {
            throw ModbusException("Invalid request or connection")
        }

        val m_Request = m_Request!!
        val m_Connection = m_Connection!!
        val m_IO = m_IO!!

        /*
         * Automatically re-connect if disconnected.
         */if (!m_Connection.isConnected) {
            try {
                m_Connection.connect()
            } catch (ex: Exception) {
                throw ModbusIOException("Connection failed.")
            }
        }

        /*
         * Try sending the message up to m_Retries time. Note that the message is read immediately after being written,
         * with no flushing of buffers.
         */
        var retryCounter = 0
        val retryLimit = if (m_Retries > 0) m_Retries else 1
        while (retryCounter < retryLimit) {
            try {
                synchronized(m_IO) {
                    if (Modbus.debug) {
                        System.err.println("request transaction ID = " + m_Request.transactionID)
                    }
                    m_IO.writeMessage(m_Request)
                    m_Response = null
                    do {
                        m_IO.readResponse().let {
                            m_Response = it
                            if (Modbus.debug) {
                                System.err.println("response transaction ID = " + it.transactionID)
                                if (it.transactionID != m_Request.transactionID) {
                                    System.err.println(
                                        "expected " + m_Request.transactionID + ", got " + it.transactionID
                                    )
                                }
                            }
                        }

                    } while (m_Response != null && (!isCheckingValidity || (m_Request.transactionID != 0
                                && m_Request.transactionID != m_Response!!.transactionID)) && ++retryCounter < retryLimit
                    )
                    if (retryCounter >= retryLimit) {
                        throw ModbusIOException("Executing transaction failed (tried $m_Retries times)")
                    }
                }
                // Both methods were successful, so the transaction must have been executed.
                break
            } catch (ex: ModbusIOException) {
                if (!m_Connection.isConnected) {
                    try {
                        m_Connection.connect()
                    } catch (e: Exception) {
                        /*
                         * Nope, fail this transaction.
                         */
                        throw ModbusIOException("Connection lost.")
                    }
                }
                retryCounter++
                if (retryCounter >= retryLimit) {
                    throw ModbusIOException("Executing transaction failed (tried $m_Retries times)")
                }
            }
        }

        // The slave may have returned an exception -- check for that.
        if (m_Response is ExceptionResponse) {
            throw ModbusSlaveException((m_Response as ExceptionResponse).exceptionCode)
        }

        // Close the connection if it isn't supposed to stick around.
        if (isReconnecting) {
            m_Connection.close()
        }

        // See if packets require validity checking.
        if (isCheckingValidity && m_Response != null) {
            checkValidity()
        }
        incrementTransactionID()
    }

    /**
     * checkValidity -- Verify the transaction IDs match or are zero.
     *
     * @throws ModbusException
     * if the transaction was not valid.
     */
    @Throws(ModbusException::class)
    private fun checkValidity() {
        if (m_Request!!.transactionID == 0 || m_Response!!.transactionID == 0) {
            return
        }
        if (m_Request!!.transactionID != m_Response!!.transactionID) {
            throw ModbusException("Transaction ID mismatch")
        }
    }

    /**
     * incrementTransactionID -- Increment the transaction ID for the next transaction. Note that the caller must get
     * the new transaction ID with getTransactionID(). This is only done validity checking is enabled so that dumb
     * slaves don't cause problems. The original request will have its transaction ID incremented as well so that
     * sending the same transaction again won't cause problems.
     */
    private fun incrementTransactionID() {
        if (isCheckingValidity) {
            if (c_TransactionID >= Modbus.MAX_TRANSACTION_ID) {
                c_TransactionID = 1
            } else {
                c_TransactionID++
            }
        }
        m_Request!!.transactionID = transactionID
    }

    companion object {
        // class attributes
        private var c_TransactionID = Modbus.DEFAULT_TRANSACTION_ID
    }
}
