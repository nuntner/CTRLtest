package io.ctrltest.connections

import android.util.Log
import com.jcraft.jsch.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

class SSHConnection(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String
) {

    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var listener: OnMessageReceivedListener? = null

    // Set the message listener
    fun setOnMessageReceivedListener(listener: OnMessageReceivedListener) {
        this.listener = listener
    }

    // Establish an SSH connection
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val jsch = JSch()
            session = jsch.getSession(username, host, port)
            session?.setPassword(password)
            session?.setConfig("StrictHostKeyChecking", "no")
            session?.connect(5000)

            channel = session?.openChannel("shell") as ChannelShell
            channel?.connect(5000)

            // Start receiving messages
            startReceivingMessages()

            true
        } catch (e: JSchException) {
            false
        }
    }

    // Start receiving messages
    private fun startReceivingMessages() {
        val inputStream = channel?.inputStream ?: return
        Thread {
            try {
                val buffer = ByteArray(1024)
                while (channel?.isClosed == false) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        listener?.onMessageReceived(data)
                    }
                }
            } catch (e: Exception) {
                // Handle exception if needed
            }
        }.start()
    }

    // Send a command over the SSH connection
    suspend fun sendCommand(command: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val outputStream = channel?.outputStream ?: return@withContext null
            outputStream.write((command + "\n").toByteArray(Charsets.UTF_8))
            outputStream.flush()
            null // Response is handled asynchronously
        } catch (e: Exception) {
            null
        }
    }

    // Close the connection
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            channel?.disconnect()
            session?.disconnect()
        } catch (e: Exception) {
            // Handle exception if needed
        } finally {
            channel = null
            session = null
        }
    }
}
