package org.egos.self.protocol

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

/**
 * WebSocket relay client for internet-wide communication.
 * Connects to an EGOS Self relay server (egos relay on desktop/VPS).
 * This enables communication when devices are NOT on the same WiFi.
 */
class WebSocketRelay(
    private val relayUrl: String,
    private val deviceId: String,
    private val onMessageReceived: (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "EgosRelay"
    }

    private var client: WebSocketClient? = null
    var isConnected = false
        private set

    fun connect() {
        try {
            client = object : WebSocketClient(URI(relayUrl)) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    Log.d(TAG, "Relay connected: $relayUrl")
                    isConnected = true
                    onConnectionChanged(true)

                    // Register with device ID
                    send(KdeConnectProtocol.createIdentityPacket(deviceId, "EGOS Self Android"))
                }

                override fun onMessage(message: String?) {
                    message?.let {
                        Log.d(TAG, "Relay message: ${it.take(100)}")
                        onMessageReceived(it)
                    }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.d(TAG, "Relay disconnected: $reason")
                    isConnected = false
                    onConnectionChanged(false)
                }

                override fun onError(ex: Exception?) {
                    Log.e(TAG, "Relay error: ${ex?.message}")
                    isConnected = false
                    onConnectionChanged(false)
                }
            }
            client?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Relay connect failed: ${e.message}")
        }
    }

    fun send(message: String) {
        try {
            if (isConnected) {
                client?.send(message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Relay send failed: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            client?.close()
        } catch (_: Exception) {}
        isConnected = false
    }
}
