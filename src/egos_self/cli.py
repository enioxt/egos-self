"""EGOS Self CLI — Universal human-AI-device communication."""

import asyncio
import json
import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path

import click
from rich.console import Console
from rich.table import Table

from egos_self import __version__

console = Console()

DATA_DIR = Path.home() / ".local" / "share" / "egos-self"
DB_PATH = DATA_DIR / "events.db"


def ensure_db() -> sqlite3.Connection:
    """Create database and tables if they don't exist."""
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(DB_PATH))
    conn.execute("""
        CREATE TABLE IF NOT EXISTS events (
            id TEXT PRIMARY KEY,
            ts TEXT NOT NULL,
            type TEXT NOT NULL,
            device_from TEXT NOT NULL,
            device_to TEXT,
            body TEXT NOT NULL,
            ai_route TEXT,
            ai_confidence REAL,
            ai_model TEXT
        )
    """)
    conn.commit()
    return conn


def log_event(
    event_type: str,
    body: dict,
    device_from: str = "cli",
    device_to: str | None = None,
) -> dict:
    """Log an event to the local database."""
    conn = ensure_db()
    event = {
        "id": str(uuid.uuid4()),
        "ts": datetime.now(timezone.utc).isoformat(),
        "type": event_type,
        "device_from": device_from,
        "device_to": device_to,
        "body": json.dumps(body),
    }
    conn.execute(
        "INSERT INTO events (id, ts, type, device_from, device_to, body) VALUES (?, ?, ?, ?, ?, ?)",
        (event["id"], event["ts"], event["type"], event["device_from"], event["device_to"], event["body"]),
    )
    conn.commit()
    conn.close()
    return event


async def get_kdeconnect_devices() -> list[dict]:
    """Get paired KDE Connect devices via DBus (low-level Message API)."""
    try:
        from dbus_next.aio import MessageBus
        from dbus_next import Message

        bus = await MessageBus().connect()

        # Get all device IDs via low-level call
        msg = Message(
            destination="org.kde.kdeconnect",
            path="/modules/kdeconnect",
            interface="org.kde.kdeconnect.daemon",
            member="devices",
            signature="bb",
            body=[False, False],
        )
        reply = await bus.call(msg)
        device_ids = reply.body[0] if reply.body else []

        devices = []
        for dev_id in device_ids:
            dev_path = f"/modules/kdeconnect/devices/{dev_id}"
            dev_intro = await bus.introspect("org.kde.kdeconnect", dev_path)
            dev_obj = bus.get_proxy_object(
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
            })

        bus.disconnect()
        return devices
    except Exception as e:
        return [{"error": str(e)}]


async def send_notification(device_id: str, text: str) -> bool:
    """Send a ping with custom message to a KDE Connect device."""
    try:
        from dbus_next.aio import MessageBus
        from dbus_next import Message

        bus = await MessageBus().connect()
        msg = Message(
            destination="org.kde.kdeconnect",
            path=f"/modules/kdeconnect/devices/{device_id}/ping",
            interface="org.kde.kdeconnect.device.ping",
            member="sendPing",
            signature="s",
            body=[text],
        )
        await bus.call(msg)
        bus.disconnect()
        return True
    except Exception:
        return False


@click.group()
@click.version_option(__version__)
def main():
    """EGOS Self — Universal CLI for human-AI-device communication."""
    pass


@main.command()
def status():
    """Show connected devices, battery, and signal."""
    console.print(f"[bold]EGOS Self[/bold] v{__version__}", style="cyan")
    console.print(f"Data: {DATA_DIR}")
    console.print()

    devices = asyncio.run(get_kdeconnect_devices())

    if not devices or (len(devices) == 1 and "error" in devices[0]):
        err = devices[0].get("error", "Unknown") if devices else "No DBus"
        console.print(f"[yellow]KDE Connect not available:[/yellow] {err}")
        console.print("Make sure kdeconnectd is running.")
        return

    table = Table(title="KDE Connect Devices")
    table.add_column("Name", style="bold")
    table.add_column("ID", style="dim")
    table.add_column("Reachable", justify="center")
    table.add_column("Paired", justify="center")

    for dev in devices:
        reach = "[green]YES[/green]" if dev.get("reachable") else "[red]NO[/red]"
        paired = "[green]YES[/green]" if dev.get("paired") else "[red]NO[/red]"
        name = dev.get("name", "?")
        dev_type = dev.get("type", "")
        if dev_type:
            name = f"{name} ({dev_type})"
        table.add_row(name, dev.get("id", "?")[:12] + "...", reach, paired)

    console.print(table)

    # Show event count
    try:
        conn = ensure_db()
        count = conn.execute("SELECT COUNT(*) FROM events").fetchone()[0]
        conn.close()
        console.print(f"\nLocal events: [bold]{count}[/bold]")
    except Exception:
        pass


@main.command()
def ping():
    """Verify channel is alive — ping all reachable devices."""
    devices = asyncio.run(get_kdeconnect_devices())
    reachable = [d for d in devices if d.get("reachable") and d.get("paired")]

    if not reachable:
        console.print("[red]No reachable paired devices found.[/red]")
        return

    for dev in reachable:
        ok = asyncio.run(send_notification(dev["id"], "EGOS Self ping"))
        status = "[green]OK[/green]" if ok else "[red]FAIL[/red]"
        console.print(f"  {dev['name']}: {status}")
        if ok:
            log_event("ping", {"target": dev["name"]})


@main.command()
@click.argument("text")
def send(text: str):
    """Send a message to all paired devices."""
    devices = asyncio.run(get_kdeconnect_devices())
    reachable = [d for d in devices if d.get("reachable") and d.get("paired")]
    delivered = False

    if not reachable:
        console.print("[yellow]No reachable devices — message stored locally.[/yellow]")
    else:
        for dev in reachable:
            ok = asyncio.run(send_notification(dev["id"], text))
            status_str = "[green]sent[/green]" if ok else "[red]failed[/red]"
            console.print(f"  → {dev['name']}: {status_str}")
            if ok:
                delivered = True

    log_event("msg", {"text": text, "delivered": delivered}, device_to="all" if reachable else "local")
    console.print("[dim]Event logged.[/dim]")


@main.command()
@click.option("-n", "--limit", default=20, help="Number of events to show")
def log(limit: int):
    """Show recent events from local database."""
    conn = ensure_db()
    rows = conn.execute(
        "SELECT ts, type, device_from, device_to, body FROM events ORDER BY ts DESC LIMIT ?",
        (limit,),
    ).fetchall()
    conn.close()

    if not rows:
        console.print("[dim]No events yet. Try: egos send 'hello'[/dim]")
        return

    table = Table(title=f"Last {limit} Events")
    table.add_column("Time", style="dim")
    table.add_column("Type", style="bold")
    table.add_column("From")
    table.add_column("To")
    table.add_column("Body")

    for ts, etype, dfrom, dto, body in rows:
        short_ts = ts[11:19] if len(ts) > 19 else ts
        body_data = json.loads(body)
        body_str = body_data.get("text", json.dumps(body_data))[:60]
        table.add_row(short_ts, etype, dfrom or "", dto or "", body_str)

    console.print(table)


@main.command()
def stats():
    """Show database statistics."""
    conn = ensure_db()
    total = conn.execute("SELECT COUNT(*) FROM events").fetchone()[0]
    types = conn.execute(
        "SELECT type, COUNT(*) FROM events GROUP BY type ORDER BY COUNT(*) DESC"
    ).fetchall()
    conn.close()

    console.print(f"[bold]Total events:[/bold] {total}")
    if types:
        for t, c in types:
            console.print(f"  {t}: {c}")


@main.command()
def share():
    """Share EGOS Self with someone. Generate an install link."""
    console.print()
    console.print("[bold cyan]Share EGOS Self[/bold cyan]")
    console.print()
    console.print("If this tool helped you, share it with someone who needs it.")
    console.print("It's free, open source, and takes 2 minutes to install.")
    console.print()
    console.print("[bold]Send them this:[/bold]")
    console.print()
    console.print("  [green]git clone https://github.com/enioxt/egos-self.git[/green]")
    console.print("  [green]cd egos-self && pip install -e .[/green]")
    console.print("  [green]egos status[/green]")
    console.print()
    console.print("[dim]GitHub:[/dim] https://github.com/enioxt/egos-self")
    console.print("[dim]License:[/dim] MIT — free forever, no account needed")
    console.print()
    console.print("[bold yellow]The best tool is the one you share.[/bold yellow]")
    console.print()

    log_event("share", {"action": "generated_invite"})


@main.command()
@click.option("--port", default=8765, help="Port to run relay on")
@click.option("--host", default="0.0.0.0", help="Host to bind to")
def relay(port: int, host: str):
    """Start a WebSocket relay server for internet-wide communication."""
    try:
        import websockets  # noqa: F401
    except ImportError:
        console.print("[red]Install websockets first:[/red] pip install websockets")
        return

    from egos_self.relay import run_relay

    console.print(f"[bold cyan]EGOS Self Relay[/bold cyan] v{__version__}")
    console.print(f"Listening on ws://{host}:{port}")
    console.print("[dim]No data stored. Pure pass-through relay.[/dim]")
    console.print("[dim]Press Ctrl+C to stop.[/dim]")
    console.print()

    try:
        asyncio.run(run_relay(host=host, port=port))
    except KeyboardInterrupt:
        console.print("\n[yellow]Relay stopped.[/yellow]")


if __name__ == "__main__":
    main()
