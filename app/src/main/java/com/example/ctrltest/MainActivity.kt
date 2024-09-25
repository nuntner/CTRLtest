package io.ctrltest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import io.ctrltest.connections.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class MainActivity : AppCompatActivity(), OnMessageReceivedListener {

    private var selectedProtocol = "TCP/IP"  // Default protocol
    private var tcpConnection: TcpConnection? = null
    private var telnetConnection: TelnetConnection? = null
    private var sshConnection: SSHConnection? = null
    private var isConnected = false
    private var currentHost: String? = null // Capture the host IP address
    private var currentPort: Int? = null    // Capture the port number
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { disconnect() } // Timeout task

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemUI()

        val rootView = findViewById<View>(R.id.root_layout)

        // Make rootView clickable to receive click events
        rootView.isClickable = true

        // Hide keyboard when touching outside EditText
        rootView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    hideKeyboard()
                }
                MotionEvent.ACTION_UP -> {
                    v.performClick()
                }
            }
            false
        }

        val protocolSpinner = findViewById<Spinner>(R.id.spinner_protocol)
        val spinnerCR1 = findViewById<Spinner>(R.id.spinner_cr_1)
        val spinnerCR2 = findViewById<Spinner>(R.id.spinner_cr_2)
        val spinnerCR3 = findViewById<Spinner>(R.id.spinner_cr_3)
        val etHost = findViewById<EditText>(R.id.et_host)
        val etPort = findViewById<EditText>(R.id.et_port)
        val etCommand1 = findViewById<EditText>(R.id.et_command1)
        val etCommand2 = findViewById<EditText>(R.id.et_command2)
        val etCommand3 = findViewById<EditText>(R.id.et_command3)
        val btnSend1 = findViewById<Button>(R.id.btn_send1)
        val btnSend2 = findViewById<Button>(R.id.btn_send2)
        val btnSend3 = findViewById<Button>(R.id.btn_send3)
        val btnConnect = findViewById<Button>(R.id.btn_connect)
        val btnConnected = findViewById<Button>(R.id.btn_connected)
        val cbHex1 = findViewById<CheckBox>(R.id.cb_hex1)
        val cbHex2 = findViewById<CheckBox>(R.id.cb_hex2)
        val cbHex3 = findViewById<CheckBox>(R.id.cb_hex3)

        val ivLogo = findViewById<ImageView>(R.id.iv_ctrlbot_logo)
        val tvVersion = findViewById<TextView>(R.id.tv_CTRLtest_version)

        val websiteUrl = "https://ctrlbot.io/"

        val openWebsiteListener = View.OnClickListener {
            openWebsite(websiteUrl)
        }

        ivLogo.setOnClickListener(openWebsiteListener)
        tvVersion.setOnClickListener(openWebsiteListener)

        // Set the listener for the protocol spinner
        protocolSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: android.view.View,
                position: Int,
                id: Long
            ) {
                selectedProtocol = parent.getItemAtPosition(position).toString()

                // Set default port based on selected protocol
                when (selectedProtocol) {
                    "TCP/IP" -> etPort.setText("23")
                    "Telnet" -> etPort.setText("23")
                    "SSH" -> etPort.setText("22")
                }

                // Update terminal instead of Toast
                appendToTerminal("Selected protocol: $selectedProtocol", true)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }

        // HEX Checkbox listener for Command 1
        cbHex1.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                spinnerCR1.visibility = View.GONE
                etCommand1.hint = "Enter HEX command"
                setHexInputFilter(etCommand1)
                etCommand1.text.clear()
            } else {
                spinnerCR1.visibility = View.VISIBLE
                etCommand1.hint = "Command 1"
                etCommand1.filters = arrayOf()
                etCommand1.text.clear()
            }
        }

        // HEX Checkbox listener for Command 2
        cbHex2.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                spinnerCR2.visibility = View.GONE
                etCommand2.hint = "Enter HEX command"
                setHexInputFilter(etCommand2)
                etCommand2.text.clear()
            } else {
                spinnerCR2.visibility = View.VISIBLE
                etCommand2.hint = "Command 2"
                etCommand2.filters = arrayOf()
                etCommand2.text.clear()
            }
        }

        // HEX Checkbox listener for Command 3
        cbHex3.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                spinnerCR3.visibility = View.GONE
                etCommand3.hint = "Enter HEX command"
                setHexInputFilter(etCommand3)
                etCommand3.text.clear()
            } else {
                spinnerCR3.visibility = View.VISIBLE
                etCommand3.hint = "Command 3"
                etCommand3.filters = arrayOf()
                etCommand3.text.clear()
            }
        }

        // Connect button listener to handle connection based on the selected protocol
        btnConnect.setOnClickListener {
            hideKeyboard() // Hide the keyboard

            val host = etHost.text.toString().trim()
            val port = etPort.text.toString().toIntOrNull() ?: 0

            // Validate the IP address format
            if (host.isEmpty() || !isValidIpAddress(host)) {
                appendToTerminal("Invalid IP address", true)
                return@setOnClickListener
            }

            // Save the current host and port for later use in messages
            currentHost = host
            currentPort = port

            lifecycleScope.launch(Dispatchers.IO) {
                when (selectedProtocol) {
                    "TCP/IP" -> {
                        tcpConnection?.disconnect()
                        tcpConnection = TcpConnection(host, port)
                        handleTcpConnection(host, port)
                    }
                    "Telnet" -> {
                        telnetConnection?.disconnect()
                        telnetConnection = TelnetConnection(host, port)
                        handleTelnetConnection(host, port)
                    }
                    "SSH" -> {
                        sshConnection?.disconnect()
                        // Prompt for credentials
                        withContext(Dispatchers.Main) {
                            promptForCredentials { username, password ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    sshConnection = SSHConnection(host, port, username, password)
                                    handleSshConnection(host, port, username, password)
                                }
                            }
                        }
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            appendToTerminal("Invalid protocol", true)
                        }
                    }
                }
            }
        }

        // Connected button listener to disconnect
        btnConnected.setOnClickListener {
            hideKeyboard() // Hide the keyboard
            disconnect()
        }

        // Send command button listener for Command 1
        btnSend1.setOnClickListener {
            hideKeyboard() // Hide the keyboard

            val commandText = etCommand1.text.toString()
            if (commandText.isNotEmpty()) {
                val isHexMode = cbHex1.isChecked

                lifecycleScope.launch(Dispatchers.IO) {
                    // Connect if not connected
                    if (!isConnected) {
                        when (selectedProtocol) {
                            "TCP/IP" -> handleTcpConnection(currentHost ?: "", currentPort ?: 0)
                            "Telnet" -> handleTelnetConnection(currentHost ?: "", currentPort ?: 0)
                            "SSH" -> {
                                // Prompt for credentials
                                withContext(Dispatchers.Main) {
                                    promptForCredentials { username, password ->
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            sshConnection = SSHConnection(currentHost ?: "", currentPort ?: 22, username, password)
                                            handleSshConnection(currentHost ?: "", currentPort ?: 22, username, password)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val response: String? = if (isHexMode) {
                        // HEX Mode
                        val byteArray = parseHexStringToByteArray(commandText)
                        if (byteArray != null) {
                            withContext(Dispatchers.Main) {
                                appendToTerminal("Sent: $commandText", isSentMessage = true)
                            }
                            when (selectedProtocol) {
                                "TCP/IP" -> {
                                    tcpConnection?.sendCommand(byteArray)
                                }
                                "Telnet" -> {
                                    telnetConnection?.sendCommand(byteArray)
                                }
                                "SSH" -> {
                                    // For SSH, send the HEX command as bytes converted to a String
                                    sshConnection?.sendCommand(String(byteArray, Charsets.UTF_8))
                                }
                                else -> null
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                appendToTerminal("Invalid HEX input.", true)
                            }
                            null
                        }
                    } else {
                        // ASCII Mode
                        val delimiter = spinnerCR1.selectedItem.toString()
                        val lineEndingString = getLineEnding(delimiter)

                        val fullCommand = commandText + lineEndingString

                        val displayCommand = commandText + getLineEndingDisplayText(delimiter)
                        withContext(Dispatchers.Main) {
                            appendToTerminal("Sent: $displayCommand", isSentMessage = true)
                        }

                        when (selectedProtocol) {
                            "TCP/IP" -> {
                                tcpConnection?.sendCommand(fullCommand.toByteArray(Charsets.UTF_8))
                            }
                            "Telnet" -> {
                                telnetConnection?.sendCommand(fullCommand.toByteArray(Charsets.UTF_8))
                            }
                            "SSH" -> {
                                sshConnection?.sendCommand(fullCommand)
                            }
                            else -> null
                        }
                    }
                    // Reset the timeout
                    resetTimeout()

                    if (!response.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            appendToTerminal("Received: $response", isReceivedMessage = true)
                        }
                    }
                }
            } else {
                appendToTerminal("Please enter a command.", true)
            }
        }

        // Send command button listener for Command 2
        btnSend2.setOnClickListener {
            hideKeyboard() // Hide the keyboard

            val commandText = etCommand2.text.toString()
            if (commandText.isNotEmpty()) {
                val isHexMode = cbHex2.isChecked

                lifecycleScope.launch(Dispatchers.IO) {
                    // Connect if not connected
                    if (!isConnected) {
                        when (selectedProtocol) {
                            "TCP/IP" -> handleTcpConnection(currentHost ?: "", currentPort ?: 0)
                            "Telnet" -> handleTelnetConnection(currentHost ?: "", currentPort ?: 0)
                            "SSH" -> {
                                // Prompt for credentials
                                withContext(Dispatchers.Main) {
                                    promptForCredentials { username, password ->
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            sshConnection = SSHConnection(currentHost ?: "", currentPort ?: 22, username, password)
                                            handleSshConnection(currentHost ?: "", currentPort ?: 22, username, password)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val response: String? = if (isHexMode) {
                        // HEX Mode
                        val byteArray = parseHexStringToByteArray(commandText)
                        if (byteArray != null) {
                            withContext(Dispatchers.Main) {
                                appendToTerminal("Sent: $commandText", isSentMessage = true)
                            }
                            when (selectedProtocol) {
                                "TCP/IP" -> {
                                    tcpConnection?.sendCommand(byteArray)
                                }
                                "Telnet" -> {
                                    telnetConnection?.sendCommand(byteArray)
                                }
                                "SSH" -> {
                                    sshConnection?.sendCommand(String(byteArray, Charsets.UTF_8))
                                }
                                else -> null
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                appendToTerminal("Invalid HEX input.", true)
                            }
                            null
                        }
                    } else {
                        // ASCII Mode
                        val delimiter = spinnerCR2.selectedItem.toString()
                        val lineEndingString = getLineEnding(delimiter)

                        val fullCommand = commandText + lineEndingString

                        val displayCommand = commandText + getLineEndingDisplayText(delimiter)
                        withContext(Dispatchers.Main) {
                            appendToTerminal("Sent: $displayCommand", isSentMessage = true)
                        }

                        when (selectedProtocol) {
                            "TCP/IP" -> {
                                tcpConnection?.sendCommand(fullCommand.toByteArray(Charsets.UTF_8))
                            }
                            "Telnet" -> {
                                telnetConnection?.sendCommand(fullCommand.toByteArray(Charsets.UTF_8))
                            }
                            "SSH" -> {
                                sshConnection?.sendCommand(fullCommand)
                            }
                            else -> null
                        }
                    }
                    // Reset the timeout
                    resetTimeout()

                    if (!response.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            appendToTerminal("Received: $response", isReceivedMessage = true)
                        }
                    }
                }
            } else {
                appendToTerminal("Please enter a command.", true)
            }
        }

        // Send command button listener for Command 3
        btnSend3.setOnClickListener {
            hideKeyboard() // Hide the keyboard

            val commandText = etCommand3.text.toString()
            if (commandText.isNotEmpty()) {
                val isHexMode = cbHex3.isChecked

                lifecycleScope.launch(Dispatchers.IO) {
                    // Connect if not connected
                    if (!isConnected) {
                        when (selectedProtocol) {
                            "TCP/IP" -> handleTcpConnection(currentHost ?: "", currentPort ?: 0)
                            "Telnet" -> handleTelnetConnection(currentHost ?: "", currentPort ?: 0)
                            "SSH" -> {
                                // Prompt for credentials
                                withContext(Dispatchers.Main) {
                                    promptForCredentials { username, password ->
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            sshConnection = SSHConnection(currentHost ?: "", currentPort ?: 22, username, password)
                                            handleSshConnection(currentHost ?: "", currentPort ?: 22, username, password)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val response: String? = if (isHexMode) {
                        // HEX Mode
                        val byteArray = parseHexStringToByteArray(commandText)
                        if (byteArray != null) {
                            withContext(Dispatchers.Main) {
                                appendToTerminal("Sent: $commandText", isSentMessage = true)
                            }
                            when (selectedProtocol) {
                                "TCP/IP" -> {
                                    tcpConnection?.sendCommand(byteArray)
                                }
                                "Telnet" -> {
                                    telnetConnection?.sendCommand(byteArray)
                                }
                                "SSH" -> {
                                    sshConnection?.sendCommand(String(byteArray, Charsets.UTF_8))
                                }
                                else -> null
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                appendToTerminal("Invalid HEX input.", true)
                            }
                            null
                        }
                    } else {
                        // ASCII Mode
                        val delimiter = spinnerCR3.selectedItem.toString()
                        val lineEndingString = getLineEnding(delimiter)

                        val fullCommand = commandText + lineEndingString

                        val displayCommand = commandText + getLineEndingDisplayText(delimiter)
                        withContext(Dispatchers.Main) {
                            appendToTerminal("Sent: $displayCommand", isSentMessage = true)
                        }

                        when (selectedProtocol) {
                            "TCP/IP" -> {
                                tcpConnection?.sendCommand(fullCommand.toByteArray(Charsets.UTF_8))
                            }
                            "Telnet" -> {
                                telnetConnection?.sendCommand(fullCommand.toByteArray(Charsets.UTF_8))
                            }
                            "SSH" -> {
                                sshConnection?.sendCommand(fullCommand)
                            }
                            else -> null
                        }
                    }
                    // Reset the timeout
                    resetTimeout()

                    if (!response.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            appendToTerminal("Received: $response", isReceivedMessage = true)
                        }
                    }
                }
            } else {
                appendToTerminal("Please enter a command.", true)
            }
        }

    } // End of onCreate()

    private fun openWebsite(url: String) {
        val webpage: Uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, webpage)
        // Verify that there is an app to handle the intent
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "No application can handle this request. Please install a web browser.", Toast.LENGTH_LONG).show()
        }
    }

    // Implement the onMessageReceived method
    override fun onMessageReceived(message: ByteArray) {
        runOnUiThread {
            val displayMessage = processReceivedData(message)
            appendToTerminal("Received: $displayMessage", isReceivedMessage = true)
        }
    }

    // Function to process received data and display ASCII and HEX
    private fun processReceivedData(data: ByteArray): String {
        val builder = StringBuilder()
        var hasPrintableChars = false

        for (byte in data) {
            val intVal = byte.toInt() and 0xFF
            val char = intVal.toChar()
            if (char.isPrintableAscii()) {
                builder.append(char)
                hasPrintableChars = true
            } else {
                builder.append("{%02X}".format(intVal))
            }
        }

        // If no printable characters were found, display the data as HEX
        if (!hasPrintableChars) {
            return data.joinToString(" ") { "%02X".format(it) }
        }

        return builder.toString()
    }

    // Extension function to check if a character is printable ASCII
    private fun Char.isPrintableAscii(): Boolean {
        return this.code in 32..126
    }

    // Function to validate if the given string is a valid IP address
    private fun isValidIpAddress(ip: String): Boolean {
        val ipPattern = Pattern.compile(
            "^([0-9]{1,3}\\.){3}[0-9]{1,3}$"
        )
        return ipPattern.matcher(ip).matches() && ip.split(".").all { it.toInt() in 0..255 }
    }

    // Handle TCP connection
    private suspend fun handleTcpConnection(host: String, port: Int) {
        if (tcpConnection == null) {
            tcpConnection = TcpConnection(host, port)
        }

        try {
            val connected = tcpConnection?.connect() ?: false
            if (connected) {
                tcpConnection?.setOnMessageReceivedListener(this) // Set the listener
                updateConnectionState(true, "TCP/IP", host, port)
            } else {
                withContext(Dispatchers.Main) {
                    appendToTerminal("Failed to connect to $host:$port", true)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                appendToTerminal("Connection error to $host:$port: ${e.message}", true)
            }
        }
    }

    // Handle Telnet connection
    private suspend fun handleTelnetConnection(host: String, port: Int) {
        if (telnetConnection == null) {
            telnetConnection = TelnetConnection(host, port)
        }

        try {
            val connected = telnetConnection?.connect() ?: false
            if (connected) {
                telnetConnection?.setOnMessageReceivedListener(this) // Set the listener
                updateConnectionState(true, "Telnet", host, port)
            } else {
                withContext(Dispatchers.Main) {
                    appendToTerminal("Failed to connect to $host:$port", true)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                appendToTerminal("Connection error to $host:$port: ${e.message}", true)
            }
        }
    }

    // Handle SSH connection
    private suspend fun handleSshConnection(host: String, port: Int, username: String, password: String) {
        if (sshConnection == null) {
            sshConnection = SSHConnection(host, port, username, password)
        }

        try {
            val connected = sshConnection?.connect() ?: false
            if (connected) {
                isConnected = true
                // You might need to set a listener if your SSHConnection supports it
                updateConnectionState(true, "SSH", host, port)
            } else {
                withContext(Dispatchers.Main) {
                    appendToTerminal("Failed to connect to $host:$port", true)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                appendToTerminal("Connection error to $host:$port: ${e.message}", true)
            }
        }
    }

    // Disconnect logic
    private fun disconnect() {
        val currentProtocol = selectedProtocol ?: "Unknown"
        val currentHostValue = currentHost ?: "Unknown"
        val currentPortValue = currentPort ?: 0

        lifecycleScope.launch(Dispatchers.IO) {
            tcpConnection?.disconnect()
            tcpConnection = null
            telnetConnection?.disconnect()
            telnetConnection = null
            sshConnection?.disconnect()
            sshConnection = null
            isConnected = false
            withContext(Dispatchers.Main) {
                updateConnectionState(false, currentProtocol, currentHostValue, currentPortValue)
                appendToTerminal("Disconnected from $currentHostValue:$currentPortValue", true)
            }
        }
    }

    // Update connection state (toggle buttons)
    private suspend fun updateConnectionState(connected: Boolean, protocol: String, host: String, port: Int) {
        withContext(Dispatchers.Main) {
            val btnConnect = findViewById<Button>(R.id.btn_connect)
            val btnConnected = findViewById<Button>(R.id.btn_connected)
            isConnected = connected

            val address = "$host:$port"

            if (connected) {
                btnConnect.visibility = View.GONE
                btnConnected.visibility = View.VISIBLE
                appendToTerminal("Connected to $address", true) // Green connection message
                resetTimeout()  // Start/reset the timeout
            } else {
                btnConnect.visibility = View.VISIBLE
                btnConnected.visibility = View.GONE
                appendToTerminal("Disconnected from $address", true) // Green disconnection message
            }
        }
    }

    // Append messages to the terminal output
    private fun appendToTerminal(
        message: String,
        isStatusMessage: Boolean = false,
        isSentMessage: Boolean = false,
        isReceivedMessage: Boolean = false
    ) {
        runOnUiThread {
            val tvTerminalOutput = findViewById<TextView>(R.id.tv_terminal_output)
            val scrollView = findViewById<ScrollView>(R.id.scroll_view_terminal_output)

            val coloredMessage = when {
                isStatusMessage -> "<font color='#00FF00'>$message</font><br>" // Green text for status
                isSentMessage -> "<font color='#008000'>$message</font><br>"   // Green text for sent messages
                isReceivedMessage -> "<font color='#0000FF'>$message</font><br>" // Blue text for received messages
                else -> "$message<br>"  // Normal message
            }

            tvTerminalOutput.append(android.text.Html.fromHtml(coloredMessage))

            // Scroll to the bottom after appending new text
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    // Reset connection timeout
    private fun resetTimeout() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        timeoutHandler.postDelayed(timeoutRunnable, 60000)  // 60 seconds timeout
    }

    // Hide system UI for full-screen mode
    private fun hideSystemUI() {
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        timeoutHandler.removeCallbacks(timeoutRunnable)
    }

    // Prompt user for SSH credentials
    private fun promptForCredentials(callback: (String, String) -> Unit) {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_credentials, null)
        val etUsername = dialogLayout.findViewById<EditText>(R.id.et_username)
        val etPassword = dialogLayout.findViewById<EditText>(R.id.et_password)

        with(builder) {
            setTitle("SSH Credentials")
            setView(dialogLayout)
            setPositiveButton("OK") { _, _ ->
                val username = etUsername.text.toString()
                val password = etPassword.text.toString()
                if (username.isNotEmpty() && password.isNotEmpty()) {
                    callback(username, password)
                } else {
                    appendToTerminal("Please enter both username and password.", true)
                }
            }
            setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            show()
        }
    }

    // Hide the keyboard
    private fun hideKeyboard() {
        val view = currentFocus
        if (view != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    // Functions to get line endings
    private fun getLineEnding(delimiter: String): String {
        return when (delimiter) {
            "0D 0A" -> "\r\n"
            "0D" -> "\r"
            "0A" -> "\n"
            "None" -> ""
            else -> ""
        }
    }

    private fun getLineEndingDisplayText(delimiter: String): String {
        return when (delimiter) {
            "0D 0A" -> "\\r\\n"
            "0D" -> "\\r"
            "0A" -> "\\n"
            "None" -> ""
            else -> ""
        }
    }

    // Set HEX input filter for EditText
    private fun setHexInputFilter(editText: EditText) {
        val hexFilter = InputFilter { source, start, end, dest, dstart, dend ->
            val builder = StringBuilder()
            var totalChars = dest.length - dest.count { it == ' ' }
            for (i in start until end) {
                val char = source[i]
                if (char.isDigit() || char.uppercaseChar() in 'A'..'F') {
                    builder.append(char.uppercaseChar())
                    totalChars++
                    // Add a space every two characters
                    if (totalChars % 2 == 0 && (dstart + builder.length) < 100) {
                        builder.append(' ')
                    }
                }
            }
            builder.toString()
        }
        editText.filters = arrayOf(hexFilter)
    }

    // Parse HEX string to ByteArray
    private fun parseHexStringToByteArray(hexString: String): ByteArray? {
        return try {
            val cleanHexString = hexString.replace(" ", "")
            val len = cleanHexString.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                val byte = cleanHexString.substring(i, i + 2).toInt(16)
                data[i / 2] = byte.toByte()
                i += 2
            }
            data
        } catch (e: Exception) {
            null
        }
    }
}
