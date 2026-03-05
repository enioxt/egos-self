package org.egos.self.protocol

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.*

/**
 * UDP-based device discovery following KDE Connect protocol.
 * Broadcasts identity packets on port 1716 and listens for responses.
 */
class DeviceDiscovery(
    private val deviceId: String,
    private val deviceName: String,
    private val onDeviceFound: (DeviceInfo) -> Unit
) {
    companion object {
        private const val TAG = "EgosDiscovery"
    }

    private var running = false
    private var listenJob: Job? = null
    private var broadcastJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (running) return
        running = true

        listenJob = scope.launch(Dispatchers.IO) {
            listenForDevices()
        }

        broadcastJob = scope.launch(Dispatchers.IO) {
            broadcastIdentity()
        }
    }

    fun stop() {
        running = false
        listenJob?.cancel()
        broadcastJob?.cancel()
    }

    private suspend fun listenForDevices() {
        try {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(KdeConnectProtocol.BROADCAST_PORT))
                socket.soTimeout = 2000

                val buffer = ByteArray(4096)

                while (running) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)

                        val raw = String(packet.data, 0, packet.length)
                        val parsed = KdeConnectProtocol.parsePacket(raw)

                        if (parsed != null && parsed.type == KdeConnectProtocol.TYPE_IDENTITY) {
                            val body = parsed.body
                            val remoteId = body.optString("deviceId", "")

                            if (remoteId.isNotEmpty() && remoteId != deviceId) {
                                val device = DeviceInfo(
                                    deviceId = remoteId,
                                    deviceName = body.optString("deviceName", "Unknown"),
                                    deviceType = body.optString("deviceType", "desktop"),
                                    host = packet.address.hostAddress ?: "",
                                    port = body.optInt("tcpPort", KdeConnectProtocol.PORT),
                                    reachable = true
                                )
                                Log.d(TAG, "Discovered: ${device.deviceName} at ${device.host}")
                                onDeviceFound(device)
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Normal timeout, continue listening
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Listen error: ${e.message}")
        }
    }

    private suspend fun broadcastIdentity() {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true

                while (running) {
                    try {
                        val identityPacket = KdeConnectProtocol.createIdentityPacket(deviceId, deviceName)
                        val data = identityPacket.toByteArray()

                        // Broadcast to 255.255.255.255:1716
                        val broadcastAddr = InetAddress.getByName("255.255.255.255")
                        val udpPacket = DatagramPacket(
                            data, data.size,
                            broadcastAddr, KdeConnectProtocol.BROADCAST_PORT
                        )
                        socket.send(udpPacket)

                        // Also try subnet broadcast
                        getLocalBroadcastAddress()?.let { subnetBroadcast ->
                            val subnetPacket = DatagramPacket(
                                data, data.size,
                                subnetBroadcast, KdeConnectProtocol.BROADCAST_PORT
                            )
                            socket.send(subnetPacket)
                        }

                        Log.d(TAG, "Broadcast identity sent")
                    } catch (e: Exception) {
                        Log.w(TAG, "Broadcast failed: ${e.message}")
                    }

                    delay(5000) // Broadcast every 5 seconds
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast error: ${e.message}")
        }
    }

    private fun getLocalBroadcastAddress(): InetAddress? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.interfaceAddresses }
                ?.firstOrNull { it.address is Inet4Address }
                ?.broadcast
        } catch (_: Exception) {
            null
        }
    }
}
