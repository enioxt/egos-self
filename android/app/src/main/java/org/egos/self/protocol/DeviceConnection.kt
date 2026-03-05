package org.egos.self.protocol

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * TCP/TLS connection to a paired device.
 * Handles sending/receiving KDE Connect packets over encrypted channel.
 */
class DeviceConnection(
    private val device: DeviceInfo,
    private val onPacketReceived: (Packet) -> Unit,
    private val onDisconnected: () -> Unit
) {
    companion object {
        private const val TAG = "EgosConnection"
    }

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var running = false
    private var readJob: Job? = null

    fun connect(scope: CoroutineScope, useTls: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            try {
                socket = if (useTls) {
                    createTlsSocket(device.host, device.port)
                } else {
                    Socket(device.host, device.port)
                }

                socket?.let { s ->
                    s.soTimeout = 30000
                    writer = BufferedWriter(OutputStreamWriter(s.getOutputStream()))
                    reader = BufferedReader(InputStreamReader(s.getInputStream()))
                    running = true

                    Log.d(TAG, "Connected to ${device.deviceName} at ${device.host}:${device.port}")

                    readJob = scope.launch(Dispatchers.IO) {
                        readLoop()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
                onDisconnected()
            }
        }
    }

    fun send(packet: String) {
        try {
            writer?.apply {
                write(packet)
                flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
            disconnect()
        }
    }

    fun sendMessage(text: String) {
        send(KdeConnectProtocol.createEgosMessage(text, from = "phone"))
    }

    fun sendPing() {
        send(KdeConnectProtocol.createPingPacket())
    }

    fun requestPair() {
        send(KdeConnectProtocol.createPairPacket(true))
    }

    fun disconnect() {
        running = false
        readJob?.cancel()
        try {
            writer?.close()
            reader?.close()
            socket?.close()
        } catch (_: Exception) {}
        onDisconnected()
    }

    val isConnected: Boolean
        get() = socket?.isConnected == true && running

    private suspend fun readLoop() {
        try {
            while (running) {
                val line = reader?.readLine() ?: break
                val packet = KdeConnectProtocol.parsePacket(line)
                if (packet != null) {
                    Log.d(TAG, "Received: ${packet.type}")
                    withContext(Dispatchers.Main) {
                        onPacketReceived(packet)
                    }
                }
            }
        } catch (e: Exception) {
            if (running) {
                Log.e(TAG, "Read error: ${e.message}")
            }
        } finally {
            if (running) disconnect()
        }
    }

    @Suppress("TrustAllX509TrustManager", "CustomX509TrustManager")
    private fun createTlsSocket(host: String, port: Int): SSLSocket {
        // For initial dev: trust all certs (KDE Connect uses self-signed)
        // TODO: Implement proper certificate pinning after pairing
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        return sslContext.socketFactory.createSocket(host, port) as SSLSocket
    }
}
