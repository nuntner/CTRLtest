package io.ctrltest.connections

import android.util.Log
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class TelnetConnection(
    private val host: String,
    private val port: Int,
    private val timeout: Int = 5000
) {

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var listener: OnMessageReceivedListener? = null
    private var receiveJob: Job? = null

    companion object {
        const val IAC = 255      // Interpret As Command
        const val DO = 253       // DO option command
        const val DONT = 254     // DONT option command
        const val WILL = 251     // WILL option command
        const val WONT = 252     // WONT option command
        const val SB = 250       // Subnegotiation start
        const val SE = 240       // Subnegotiation end
    }

    // Set the message listener
    fun setOnMessageReceivedListener(listener: OnMessageReceivedListener) {
        this.listener = listener
    }

    // Establish a Telnet connection
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = Socket()
            socket?.connect(InetSocketAddress(host, port), timeout)
            socket?.soTimeout = timeout
            socket?.let { sock ->
                outputStream = sock.getOutputStream()
                inputStream = sock.getInputStream()
            }

            // Perform Telnet negotiation
            handleNegotiation()

            // Start receiving messages
            startReceivingMessages()

            true
        } catch (e: SocketTimeoutException) {
            false
        } catch (e: IOException) {
            false
        }
    }

    // Start receiving messages in the background
    private fun startReceivingMessages() {
        receiveJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            while (isActive && socket?.isConnected == true) {
                try {
                    val bytesRead = inputStream?.read(buffer)
                    if (bytesRead != null && bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        listener?.onMessageReceived(data)
                    }
                } catch (e: IOException) {
                    break
                }
            }
        }
    }

    // Handle Telnet negotiations by processing IAC commands
    private suspend fun handleNegotiation() = withContext(Dispatchers.IO) {
        try {
            val input = inputStream ?: return@withContext
            var read: Int

            while (input.read().also { read = it } != -1) {
                if (read == IAC) {
                    val command = input.read()
                    val option = input.read()

                    // Handle common negotiation options
                    when (command) {
                        DO, DONT -> sendRawBytes(byteArrayOf(IAC.toByte(), WONT.toByte(), option.toByte()))
                        WILL, WONT -> sendRawBytes(byteArrayOf(IAC.toByte(), DONT.toByte(), option.toByte()))
                        else -> {}
                    }
                } else {
                    // Non-command data can be handled here if needed
                    break // Exit negotiation loop
                }
            }
        } catch (e: IOException) {
            // Handle exception if needed
        }
    }

    // Send a command over the Telnet connection
    suspend fun sendCommand(command: ByteArray): String? = withContext(Dispatchers.IO) {
        if (socket?.isConnected == true) {
            try {
                outputStream?.apply {
                    write(command)
                    flush()
                }
                null
            } catch (e: IOException) {
                null
            }
        } else {
            null
        }
    }

    // Helper method to send raw bytes (used during negotiation)
    private suspend fun sendRawBytes(data: ByteArray) = withContext(Dispatchers.IO) {
        try {
            outputStream?.apply {
                write(data)
                flush()
            }
        } catch (e: IOException) {
            // Handle exception if needed
        }
    }

    // Close the connection
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            receiveJob?.cancel()
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            // Handle exception if needed
        } finally {
            outputStream = null
            inputStream = null
            socket = null
        }
    }
}
