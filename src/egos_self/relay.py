"""EGOS Self — Minimal WebSocket Relay Server.

A tiny relay that forwards messages between EGOS Self clients on different networks.
Run this on any server with a public IP to enable internet-wide communication.

Usage:
    egos relay --port 8765

Architecture:
    Client A (home WiFi) → WebSocket → Relay Server → WebSocket → Client B (mobile data)

Each client joins a "channel" (a short code). Messages are broadcast to all other
clients on the same channel. No data is stored on the relay — it's a pure pass-through.
"""

import asyncio
import json
import logging
from collections import defaultdict

logger = logging.getLogger("egos-relay")

# channel_id -> set of websocket connections
channels: dict[str, set] = defaultdict(set)


async def handle_client(websocket, path: str = ""):
    """Handle a single WebSocket client connection."""
    # Extract channel from path: /channel/abc123
    parts = path.strip("/").split("/")
    if len(parts) >= 2 and parts[0] == "channel":
        channel = parts[1]
    else:
        channel = "default"

    channels[channel].add(websocket)
    client_id = id(websocket)
    logger.info(f"Client {client_id} joined channel '{channel}' ({len(channels[channel])} clients)")

    try:
        async for message in websocket:
            # Broadcast to all OTHER clients on the same channel
            peers = channels[channel] - {websocket}
            if peers:
                await asyncio.gather(
                    *[peer.send(message) for peer in peers],
                    return_exceptions=True,
                )
                logger.debug(f"Channel '{channel}': relayed to {len(peers)} peers")
    except Exception as e:
        logger.debug(f"Client {client_id} disconnected: {e}")
    finally:
        channels[channel].discard(websocket)
        if not channels[channel]:
            del channels[channel]
        logger.info(f"Client {client_id} left channel '{channel}'")


async def run_relay(host: str = "0.0.0.0", port: int = 8765):
    """Start the WebSocket relay server."""
    try:
        import websockets
    except ImportError:
        print("Install websockets: pip install websockets")
        return

    logger.info(f"EGOS Self Relay starting on ws://{host}:{port}")
    logger.info("Share your channel code with your other devices.")
    logger.info("No data is stored. Pure pass-through relay.")

    async with websockets.serve(
        handle_client,
        host,
        port,
        ping_interval=30,
        ping_timeout=10,
        process_request=lambda path, headers: None,
    ):
        await asyncio.Future()  # Run forever
