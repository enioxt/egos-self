# EGOS Self

> **Your personal intelligence channel — a universal CLI connecting you to yourself across every device.**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Python 3.11+](https://img.shields.io/badge/python-3.11+-blue.svg)](https://python.org)

---

## What Is EGOS Self?

**EGOS Self** is an open-source CLI that creates a communication channel between you and your own devices — Linux desktop, Android phone, Raspberry Pi, or anything with a terminal.

It uses **KDE Connect** as the transport layer (pairing, encryption, reconnection are already solved) and adds an **AI-powered intent router** on top.

**This is not a chat app.** It's an operating system for your thoughts:

```
Note → Task → Command → Action → Knowledge
```

Every message is classified, routed, and stored. Your intelligence flows between devices without friction.

## Why?

In the AI era, every person builds their own framework. But our tools are fragmented:
- Notes in one app, tasks in another, commands in a third
- Phone and computer don't share context
- AI assistants live in browser tabs, disconnected from your filesystem

**EGOS Self fixes this.** One CLI. Any device. Your intelligence, everywhere.

> **If this helped you, share it.** That's the whole business model. There is no business model.
> Free forever. No account. No telemetry. Just help.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                    EGOS Self CLI                     │
│                                                     │
│  Layer 2: AI Intent Router                          │
│  ┌─────────────────────────────────────────────┐    │
│  │ Classify → Route → Act → Store → Notify     │    │
│  │ (note / task / query / command / sync)       │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  Layer 1: EGOS Self Channel                         │
│  ┌─────────────────────────────────────────────┐    │
│  │ Envelope: {type, id, ts, device, body, ai}  │    │
│  │ Packet types: org.egos.self.*               │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  Layer 0: Transport (pluggable)                     │
│  ┌─────────────────────────────────────────────┐    │
│  │ KDE Connect (default) │ TCP │ Bluetooth │ …  │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

## Quick Start

```bash
# Prerequisites: Python 3.11+, KDE Connect installed and paired
git clone https://github.com/enioxt/egos-self.git
cd egos-self
pip install -e .

# Check your devices
egos status

# Send a message to your phone
egos send "Hello from my desktop"

# View event log
egos log
```

## CLI Commands

| Command | Description |
|---------|-------------|
| `egos status` | Show connected devices, battery, signal |
| `egos ping` | Verify channel is alive |
| `egos send "text"` | Send text notification to paired devices |
| `egos log` | Show recent events (append-only local store) |
| `egos stats` | Database statistics by event type |
| `egos share` | Generate an invite link to share with someone |
| `egos relay` | Start a WebSocket relay for internet-wide communication |

### Coming Soon (Phase 2+)

| Command | Description |
|---------|-------------|
| `egos ask "question"` | AI answers, optionally notifies phone |
| `egos tasks` | List tasks extracted from messages |
| `egos sync` | Bidirectional context sync between devices |
| `egos capture --voice` | Voice to transcription to event |

## Envelope Schema (v0.1)

Every event follows this structure:

```json
{
  "type": "msg | task | query | command | sync",
  "id": "uuid-v4",
  "ts": 1709654321000,
  "device": { "from": "linux-desktop", "to": "android-phone" },
  "body": { "text": "..." },
  "ai": {
    "route": "store | respond | execute | confirm",
    "confidence": 0.92,
    "model": "local/gemma-3",
    "tool_calls": []
  }
}
```

## Beyond WiFi — Internet-Wide Communication

KDE Connect requires the same WiFi network. EGOS Self breaks that barrier with a **WebSocket relay**:

```bash
# On any server with a public IP (or your VPS):
egos relay --port 8765

# Now any EGOS Self client anywhere on the internet can connect
# through the relay. No data is stored — pure pass-through.
```

Transport priority: KDE Connect (LAN) → WebSocket relay (WAN) → Direct TCP (future)

The relay is ~80 lines of Python. Run your own. Trust no one but yourself.

## Design Principles

1. **Transport agnostic** — KDE Connect first, WebSocket relay for internet, TCP direct for known IPs
2. **AI agnostic** — Local (Ollama) or cloud (OpenRouter), you choose
3. **Device agnostic** — Linux x86, ARM (Raspberry Pi), Android, anything with a terminal
4. **Append-only** — Events are never deleted, only new events are added
5. **Privacy first** — All data local by default, cloud is opt-in per message
6. **No account required** — Works offline, no signup, no server
7. **Composable** — Each command is a Unix-style building block

## Tech Stack

| Layer | Technology |
|-------|------------|
| CLI | Python 3.11+, Click, Rich |
| Transport (LAN) | dbus-next (KDE Connect via DBus) |
| Transport (WAN) | websockets (relay server) |
| Storage | SQLite (append-only event log) |
| AI (Phase 2) | litellm + Ollama (local) or OpenRouter (cloud) |
| Android (Phase 3) | KDE Connect plugin (Kotlin) |

## Roadmap

| Phase | Name | Status | Description |
|-------|------|--------|-------------|
| 1 | CLI Foundation | Active | Working CLI with KDE Connect transport + SQLite |
| 2 | AI Router | Next | Intent classification, `egos ask`, local LLM |
| 3 | Android Channel | Planned | Custom KDE Connect plugin for `org.egos.self.*` packets |
| 4 | Universal Agent | Planned | Tool execution, voice input, scheduled tasks |
| 5 | Platform | Planned | Install script, docs, F-Droid, Raspberry Pi tested |

Full roadmap: [ROADMAP.md](ROADMAP.md)

## Cost

```
Transport:  $0  (KDE Connect is free/open source)
Storage:    $0  (SQLite is local)
AI:         $0  (Ollama local) or ~$0.001/msg (cloud API)
Total:      $0/month for full functionality
```

## Share It

If EGOS Self helped you, share it with someone who needs it:

```bash
egos share  # generates a ready-to-send install guide
```

Or just send them this:

```
git clone https://github.com/enioxt/egos-self.git
cd egos-self && pip install -e .
egos status
```

**The best tool is the one you share.**

## Contributing

This is an early-stage project. Contributions welcome:

1. Fork the repo
2. Create a feature branch (`git checkout -b feat/my-feature`)
3. Commit with conventional messages (`feat:`, `fix:`, `docs:`)
4. Open a Pull Request

## Part of the EGOS Ecosystem

EGOS Self is a node in the [EGOS](https://github.com/enioxt/egos-lab) ecosystem — an open-source platform for ethical governance, collective intelligence, and autonomous agents.

| Project | Description |
|---------|-------------|
| [egos-lab](https://github.com/enioxt/egos-lab) | Agentic AI platform + community tools |
| [EGOS Inteligencia](https://github.com/enioxt/EGOS-Inteligencia) | Public data intelligence (9.2M entities) |
| **egos-self** | Personal intelligence channel (this repo) |

## License

MIT — see [LICENSE](LICENSE).

---

*"You don't need a cloud to think. You need a channel to yourself."*
