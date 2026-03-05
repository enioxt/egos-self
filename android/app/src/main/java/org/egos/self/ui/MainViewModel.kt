package org.egos.self.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.egos.self.EgosDevice
import org.egos.self.protocol.*

data class UiState(
    val devices: List<DeviceInfo> = emptyList(),
    val messages: List<MessageItem> = emptyList(),
    val connectionStatus: String = "Discovering...",
    val relayConnected: Boolean = false,
    val messageInput: String = ""
)

data class MessageItem(
    val text: String,
    val from: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "msg"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val deviceId = EgosDevice.id
    private val deviceName = android.os.Build.MODEL

    private val discovery = DeviceDiscovery(deviceId, deviceName) { device ->
        _state.update { state ->
            val existing = state.devices.indexOfFirst { it.deviceId == device.deviceId }
            val updated = if (existing >= 0) {
                state.devices.toMutableList().apply { set(existing, device) }
            } else {
                state.devices + device
            }
            state.copy(
                devices = updated,
                connectionStatus = "${updated.size} device(s) found"
            )
        }
    }

    private var activeConnection: DeviceConnection? = null
    private var relay: WebSocketRelay? = null

    init {
        discovery.start(viewModelScope)
    }

    fun connectToDevice(device: DeviceInfo) {
        activeConnection?.disconnect()
        activeConnection = DeviceConnection(
            device = device,
            onPacketReceived = { packet -> handlePacket(packet, device.deviceName) },
            onDisconnected = {
                _state.update { it.copy(connectionStatus = "Disconnected from ${device.deviceName}") }
            }
        )
        activeConnection?.connect(viewModelScope)
        _state.update { it.copy(connectionStatus = "Connected to ${device.deviceName}") }
    }

    fun connectRelay(url: String) {
        relay?.disconnect()
        relay = WebSocketRelay(
            relayUrl = url,
            deviceId = deviceId,
            onMessageReceived = { raw ->
                KdeConnectProtocol.parsePacket(raw)?.let { packet ->
                    handlePacket(packet, "relay")
                }
            },
            onConnectionChanged = { connected ->
                _state.update { it.copy(relayConnected = connected) }
            }
        )
        relay?.connect()
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val msg = MessageItem(text = text, from = "me")
        _state.update {
            it.copy(
                messages = it.messages + msg,
                messageInput = ""
            )
        }

        // Send via active connection (LAN)
        activeConnection?.sendMessage(text)

        // Also send via relay (WAN) if connected
        relay?.let {
            if (it.isConnected) {
                it.send(KdeConnectProtocol.createEgosMessage(text, from = "phone"))
            }
        }
    }

    fun sendPing() {
        activeConnection?.sendPing()
        addSystemMessage("Ping sent")
    }

    fun updateMessageInput(input: String) {
        _state.update { it.copy(messageInput = input) }
    }

    private fun handlePacket(packet: Packet, source: String) {
        when (packet.type) {
            KdeConnectProtocol.TYPE_EGOS_MSG -> {
                val text = packet.body.optString("text", "")
                val from = packet.body.optString("from", source)
                if (text.isNotEmpty()) {
                    _state.update {
                        it.copy(messages = it.messages + MessageItem(text = text, from = from))
                    }
                }
            }
            KdeConnectProtocol.TYPE_PING -> {
                addSystemMessage("Ping from $source")
            }
            KdeConnectProtocol.TYPE_BATTERY -> {
                val level = packet.body.optInt("currentCharge", -1)
                val charging = packet.body.optBoolean("isCharging", false)
                addSystemMessage("Battery: $level% ${if (charging) "⚡" else ""}")
            }
            KdeConnectProtocol.TYPE_PAIR -> {
                val pair = packet.body.optBoolean("pair", false)
                if (pair) {
                    addSystemMessage("Pairing accepted by $source")
                } else {
                    addSystemMessage("Pairing rejected by $source")
                }
            }
        }
    }

    private fun addSystemMessage(text: String) {
        _state.update {
            it.copy(messages = it.messages + MessageItem(text = text, from = "system", type = "system"))
        }
    }

    override fun onCleared() {
        super.onCleared()
        discovery.stop()
        activeConnection?.disconnect()
        relay?.disconnect()
    }
}
