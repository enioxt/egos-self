# AGENTS.md — EGOS Self

> **VERSION:** 1.0.0 | **UPDATED:** 2026-03-05
> **TYPE:** Universal CLI + Android App

---

## Project Overview

| Item | Value |
|------|-------|
| **Project** | EGOS Self |
| **Description** | Personal intelligence channel — connect devices, send messages, share context |
| **Path** | /home/enio/egos-self-repo |
| **Repo** | github.com/enioxt/egos-self |
| **License** | MIT |
| **Stars** | 0 (just launched) |

## Architecture

```
egos-self-repo/
├── src/egos_self/       # Python CLI (Click + Rich)
│   ├── cli.py           # 11 commands (481 lines)
│   ├── transport.py     # KDE Connect LAN (DBus)
│   ├── relay.py         # WebSocket relay (WAN)
│   ├── config.py        # Config + credentials
│   └── git_layer.py     # GitHub API integration
├── android/             # Kotlin/Jetpack Compose app
│   └── app/src/main/java/org/egos/self/
│       ├── protocol/    # KDE Connect protocol v7
│       └── ui/          # Material3 dark theme
├── docs/                # Architecture docs
├── .guarani/            # Agent rules (from egos-lab canonical)
└── .windsurf/           # Workflows
```

## Tech Stack

| Layer | Technology |
|-------|------------|
| **CLI** | Python 3.11+, Click, Rich, SQLite |
| **Android** | Kotlin 2.0, Jetpack Compose, Material3 |
| **Protocol** | KDE Connect v7 (UDP discovery + TLS) |
| **Relay** | WebSocket (internet-wide) |
| **Auth** | GitHub PAT (optional, for Git Layer) |

## Commands

```bash
# CLI
pip install -e .
egos status          # Show devices + connection status
egos send "msg"      # Send message to all devices
egos ping            # Ping paired device
egos log -n 10       # Show last 10 events
egos stats           # Event statistics
egos share           # Generate install link
egos relay           # Start WebSocket relay server
egos login           # GitHub PAT authentication
egos whoami          # Show GitHub identity
egos repos           # List GitHub repos
egos logout          # Remove stored credentials

# Android
cd android && ./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

## 4 Layers

| Layer | Transport | Scope |
|-------|-----------|-------|
| 0 | KDE Connect (UDP/TCP) | LAN (same WiFi) |
| 0.5 | WebSocket Relay | WAN (internet) |
| 1 | Envelope + SQLite | Local storage |
| 3 | GitHub API | Git Layer (repos, issues) |

## SSOT Files

| File | Purpose |
|------|---------|
| `AGENTS.md` | Project config (this file) |
| `TASKS.md` | All tasks |
| `.windsurfrules` | Agent rules |
| `.guarani/PREFERENCES.md` | Coding standards |
| `.guarani/IDENTITY.md` | Agent identity |

---

*"The best tool is the one you share."*
