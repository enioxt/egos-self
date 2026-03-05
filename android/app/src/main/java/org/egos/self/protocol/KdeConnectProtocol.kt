package org.egos.self.protocol

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * KDE Connect Protocol v7 implementation.
 * Handles packet creation/parsing for device discovery, pairing, and messaging.
 * Reference: https://invent.kde.org/network/kdeconnect-meta/-/blob/master/protocol.md
 */
object KdeConnectProtocol {
    const val PROTOCOL_VERSION = 7
    const val PORT = 1716
    const val BROADCAST_PORT = 1716

    // Standard KDE Connect packet types
    const val TYPE_IDENTITY = "kdeconnect.identity"
    const val TYPE_PAIR = "kdeconnect.pair"
    const val TYPE_PING = "kdeconnect.ping"
    const val TYPE_NOTIFICATION = "kdeconnect.notification"
    const val TYPE_BATTERY = "kdeconnect.battery"
    const val TYPE_CLIPBOARD = "kdeconnect.clipboard"

    // EGOS Self custom packet types
    const val TYPE_EGOS_MSG = "org.egos.self.msg"
    const val TYPE_EGOS_TASK = "org.egos.self.task"
    const val TYPE_EGOS_CONTEXT = "org.egos.self.context"
    const val TYPE_EGOS_INTENT = "org.egos.self.intent"

    fun createIdentityPacket(deviceId: String, deviceName: String): String {
        val packet = JSONObject().apply {
            put("id", System.currentTimeMillis())
            put("type", TYPE_IDENTITY)
            put("body", JSONObject().apply {
                put("deviceId", deviceId)
                put("deviceName", deviceName)
                put("protocolVersion", PROTOCOL_VERSION)
                put("deviceType", "phone")
                put("incomingCapabilities", JSONArray().apply {
                    put(TYPE_PING)
                    put(TYPE_NOTIFICATION)
                    put(TYPE_BATTERY)
                    put(TYPE_CLIPBOARD)
                    put(TYPE_EGOS_MSG)
                    put(TYPE_EGOS_TASK)
                    put(TYPE_EGOS_CONTEXT)
                    put(TYPE_EGOS_INTENT)
                })
                put("outgoingCapabilities", JSONArray().apply {
                    put(TYPE_PING)
                    put(TYPE_NOTIFICATION)
                    put(TYPE_BATTERY)
                    put(TYPE_CLIPBOARD)
                    put(TYPE_EGOS_MSG)
                    put(TYPE_EGOS_TASK)
                    put(TYPE_EGOS_CONTEXT)
                    put(TYPE_EGOS_INTENT)
                })
                put("tcpPort", PORT)
            })
        }
        return packet.toString() + "\n"
    }

    fun createPairPacket(pair: Boolean): String {
        val packet = JSONObject().apply {
            put("id", System.currentTimeMillis())
            put("type", TYPE_PAIR)
            put("body", JSONObject().apply {
                put("pair", pair)
            })
        }
        return packet.toString() + "\n"
    }

    fun createPingPacket(): String {
        val packet = JSONObject().apply {
            put("id", System.currentTimeMillis())
            put("type", TYPE_PING)
            put("body", JSONObject())
        }
        return packet.toString() + "\n"
    }

    fun createEgosMessage(text: String, from: String = "phone"): String {
        val packet = JSONObject().apply {
            put("id", System.currentTimeMillis())
            put("type", TYPE_EGOS_MSG)
            put("body", JSONObject().apply {
                put("text", text)
                put("from", from)
                put("to", "all")
                put("uuid", UUID.randomUUID().toString())
                put("ts", System.currentTimeMillis())
            })
        }
        return packet.toString() + "\n"
    }

    fun createBatteryPacket(level: Int, charging: Boolean): String {
        val packet = JSONObject().apply {
            put("id", System.currentTimeMillis())
            put("type", TYPE_BATTERY)
            put("body", JSONObject().apply {
                put("currentCharge", level)
                put("isCharging", charging)
                put("thresholdEvent", if (level <= 15) 1 else 0)
            })
        }
        return packet.toString() + "\n"
    }

    fun parsePacket(raw: String): Packet? {
        return try {
            val json = JSONObject(raw.trim())
            Packet(
                id = json.getLong("id"),
                type = json.getString("type"),
                body = json.getJSONObject("body")
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class Packet(
    val id: Long,
    val type: String,
    val body: JSONObject
)

data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String = "desktop",
    val host: String = "",
    val port: Int = KdeConnectProtocol.PORT,
    val paired: Boolean = false,
    val reachable: Boolean = true,
    val batteryLevel: Int = -1,
    val batteryCharging: Boolean = false
)
