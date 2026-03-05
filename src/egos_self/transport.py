"""EGOS Self — Pluggable Transport Layer.

Transports handle sending/receiving messages between devices.
Each transport implements the same interface: connect, send, receive, disconnect.

Available transports:
- KDEConnectTransport: LAN via DBus (default, zero config)
- WebSocketTransport: WAN via relay server (internet-wide)
- DirectTCPTransport: Point-to-point when IP is known (future)
"""

import asyncio
import json
import uuid
from abc import ABC, abstractmethod
from datetime import datetime, timezone
from typing import Any


class Envelope:
    """Standard message envelope for all transports."""

    def __init__(
        self,
        event_type: str,
        body: dict,
        device_from: str = "cli",
        device_to: str | None = None,
    ):
        self.id = str(uuid.uuid4())
        self.ts = datetime.now(timezone.utc).isoformat()
        self.type = event_type
        self.device_from = device_from
        self.device_to = device_to
        self.body = body
        self.ai: dict | None = None

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "ts": self.ts,
            "type": self.type,
            "device": {"from": self.device_from, "to": self.device_to},
            "body": self.body,
            "ai": self.ai,
        }

    def to_json(self) -> str:
        return json.dumps(self.to_dict())

    @classmethod
    def from_json(cls, data: str) -> "Envelope":
        d = json.loads(data)
        env = cls(
            event_type=d["type"],
            body=d["body"],
            device_from=d["device"]["from"],
            device_to=d["device"]["to"],
        )
        env.id = d["id"]
        env.ts = d["ts"]
        env.ai = d.get("ai")
        return env


class Transport(ABC):
    """Base class for all transports."""

    name: str = "base"

    @abstractmethod
    async def connect(self) -> bool:
        """Establish connection. Returns True if successful."""
        ...

    @abstractmethod
    async def send(self, envelope: Envelope) -> bool:
        """Send an envelope. Returns True if delivered."""
        ...

    @abstractmethod
    async def disconnect(self) -> None:
        """Clean up connection."""
        ...

    @abstractmethod
    async def get_devices(self) -> list[dict]:
        """List available devices on this transport."""
        ...


class KDEConnectTransport(Transport):
    """LAN transport via KDE Connect DBus. Zero config, same WiFi required."""

    name = "kdeconnect"

    async def connect(self) -> bool:
        try:
            from dbus_next.aio import MessageBus
            self._bus = await MessageBus().connect()
            return True
        except Exception:
            self._bus = None
            return False

    async def get_devices(self) -> list[dict]:
        if not self._bus:
            return []
        try:
            from dbus_next import Message

            msg = Message(
                destination="org.kde.kdeconnect",
                path="/modules/kdeconnect",
                interface="org.kde.kdeconnect.daemon",
                member="devices",
                signature="bb",
                body=[False, False],
            )
            reply = await self._bus.call(msg)
            device_ids = reply.body[0] if reply.body else []

            devices = []
            for dev_id in device_ids:
                dev_path = f"/modules/kdeconnect/devices/{dev_id}"
                dev_intro = await self._bus.introspect("org.kde.kdeconnect", dev_path)
                dev_obj = self._bus.get_proxy_object(
                    "org.kde.kdeconnect", dev_path, dev_intro
                )
                props = dev_obj.get_interface("org.freedesktop.DBus.Properties")

                name = await props.call_get("org.kde.kdeconnect.device", "name")
                is_paired = await props.call_get("org.kde.kdeconnect.device", "isPaired")
                is_reachable = await props.call_get("org.kde.kdeconnect.device", "isReachable")
                dev_type = await props.call_get("org.kde.kdeconnect.device", "type")

                devices.append({
                    "id": dev_id,
                    "name": name.value,
                    "reachable": is_reachable.value,
                    "paired": is_paired.value,
                    "type": dev_type.value,
                    "transport": self.name,
                })

            return devices
        except Exception:
            return []

    async def send(self, envelope: Envelope) -> bool:
        if not self._bus:
            return False
        try:
            from dbus_next import Message

            devices = await self.get_devices()
            reachable = [d for d in devices if d.get("reachable") and d.get("paired")]
            if not reachable:
                return False

            text = envelope.body.get("text", envelope.to_json())
            for dev in reachable:
                msg = Message(
                    destination="org.kde.kdeconnect",
                    path=f"/modules/kdeconnect/devices/{dev['id']}/ping",
                    interface="org.kde.kdeconnect.device.ping",
                    member="sendPing",
                    signature="s",
                    body=[text],
                )
                await self._bus.call(msg)
            return True
        except Exception:
            return False

    async def disconnect(self) -> None:
        if self._bus:
            self._bus.disconnect()
            self._bus = None


class WebSocketTransport(Transport):
    """WAN transport via WebSocket relay server. Works over internet."""

    name = "websocket"

    def __init__(self, relay_url: str = "wss://relay.egos.ia.br", channel: str | None = None):
        self.relay_url = relay_url
        self.channel = channel or str(uuid.uuid4())[:8]
        self._ws = None

    async def connect(self) -> bool:
        try:
            import websockets
            self._ws = await websockets.connect(
                f"{self.relay_url}/channel/{self.channel}",
                ping_interval=30,
                ping_timeout=10,
            )
            return True
        except Exception:
            self._ws = None
            return False

    async def get_devices(self) -> list[dict]:
        if not self._ws:
            return []
        return [{
            "id": f"ws-{self.channel}",
            "name": f"WebSocket Channel ({self.channel})",
            "reachable": self._ws is not None and self._ws.open,
            "paired": True,
            "type": "relay",
            "transport": self.name,
        }]

    async def send(self, envelope: Envelope) -> bool:
        if not self._ws:
            return False
        try:
            await self._ws.send(envelope.to_json())
            return True
        except Exception:
            return False

    async def receive(self, timeout: float = 30.0) -> Envelope | None:
        """Wait for an incoming message."""
        if not self._ws:
            return None
        try:
            data = await asyncio.wait_for(self._ws.recv(), timeout=timeout)
            return Envelope.from_json(data)
        except Exception:
            return None

    async def disconnect(self) -> None:
        if self._ws:
            await self._ws.close()
            self._ws = None


class TransportManager:
    """Manages multiple transports, trying each in priority order."""

    def __init__(self):
        self.transports: list[Transport] = []

    def add(self, transport: Transport) -> None:
        self.transports.append(transport)

    async def connect_all(self) -> dict[str, bool]:
        results = {}
        for t in self.transports:
            results[t.name] = await t.connect()
        return results

    async def get_all_devices(self) -> list[dict]:
        devices = []
        for t in self.transports:
            devices.extend(await t.get_devices())
        return devices

    async def send(self, envelope: Envelope) -> bool:
        """Try to send via each transport until one succeeds."""
        for t in self.transports:
            if await t.send(envelope):
                return True
        return False

    async def disconnect_all(self) -> None:
        for t in self.transports:
            await t.disconnect()
