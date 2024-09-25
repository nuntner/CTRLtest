package io.ctrltest.connections

interface OnMessageReceivedListener {
    fun onMessageReceived(message: ByteArray)
}
