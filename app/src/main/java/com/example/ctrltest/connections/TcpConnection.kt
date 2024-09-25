package io.ctrltest.connections

import android.util.Log
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class TcpConnection(
    private val host: String,
    private val port: Int,
    private val timeout: Int = 5000
) {

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var listener: OnMessageReceivedListener? = null
    private var receiveJob: Job? = null

    // Set the message listener
    fun setOnMessageReceivedListener(listener: OnMessageReceivedListener) {
        this.listener = listener
    }

    // Establish a TCP/IP connection
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = Socket()
            socket?.connect(InetSocketAddress(host, port), timeout)
            socket?.let { sock ->
                outputStream = sock.getOutputStream()
                inputStream = sock.getInputStream()
            }

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

    // Send a command over the TCP/IP connection
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

    // Close the connection
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            receiveJob?.cancel()
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (e: IOException) {
        } finally {
            outputStream = null
            inputStream = null
            socket = null
        }
    }
}
