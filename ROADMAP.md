# EGOS Self — Roadmap

> **Version:** 1.0.0 | **Date:** 2026-03-05

---

## Phase 1: CLI Foundation (v0.1) — "Talk to yourself"

**Goal:** A working CLI on Linux that detects KDE Connect devices and sends/receives messages.

| Task | Description | Status |
|------|-------------|--------|
| Project scaffold | pyproject.toml, src/, CLI entry point | |
| `egos status` | Detect paired KDE Connect devices via DBus | |
| `egos ping` | Verify device is reachable | |
| `egos send` | Send text notification to phone | |
| `egos log` | Append-only local event log (SQLite) | |
| Envelope schema | JSON schema v0.1 for all messages | |

**Stack:** Python 3.12+, dbus-next (async DBus), SQLite, Click (CLI)
**No AI yet.** Just the channel.

---

## Phase 2: AI Router (v0.2) — "Think before acting"

**Goal:** Every message passes through an AI intent classifier before being stored/routed.

| Task | Description | Status |
|------|-------------|--------|
| Intent classifier | msg/task/query/command/sync routing | |
| `egos ask` | Send question → AI responds → optionally notify phone | |
| `egos tasks` | List tasks extracted from messages | |
| Local LLM support | Ollama/llama.cpp integration | |
| API fallback | OpenRouter for cloud models | |
| Config file | `~/.config/egos-self/config.toml` | |

**Stack:** + litellm (model routing), ollama (local)

---

## Phase 3: Android Channel (v0.3) — "Bridge the gap"

**Goal:** Custom KDE Connect plugin creating the `org.egos.self.*` packet types.

| Task | Description | Status |
|------|-------------|--------|
| Plugin spec | Define packet types (msg, task, query, command, sync) | |
| Desktop plugin | KDE Connect plugin (C++/Qt or Python wrapper) | |
| Android plugin | Fork KDE Connect Android, add EGOS Self plugin | |
| APK build | Signed APK for sideloading | |
| Two-way sync | Events.db sync between devices | |

**Stack:** + Kotlin (Android), Qt/C++ (desktop plugin)

---

## Phase 4: Universal Agent (v0.4) — "Act on your behalf"

**Goal:** The CLI becomes an agent that can execute tasks, not just route messages.

| Task | Description | Status |
|------|-------------|--------|
| Tool system | Pluggable tools (git, files, calendar, web) | |
| Confirmation flow | Dangerous actions require phone approval | |
| Voice input | Whisper STT on phone → text → CLI | |
| Quick tiles | Android quick settings tile for fast capture | |
| Cron/scheduled | Recurring tasks, daily summaries | |
| Context window | Rolling summary of recent events for AI | |

**Stack:** + Whisper (Groq API), cron integration

---

## Phase 5: Platform (v1.0) — "Open the doors"

**Goal:** Others can install and use EGOS Self with their own devices and AI providers.

| Task | Description | Status |
|------|-------------|--------|
| Install script | `curl -sL egos.ia.br/self | bash` | |
| Documentation | Full setup guide for Linux + Android | |
| Raspberry Pi | Tested and documented on ARM | |
| Plugin API | Third-party tools can register | |
| Transport agnostic | TCP fallback when KDE Connect unavailable | |
| F-Droid | Android app on F-Droid | |
| Website | Landing page on egos.ia.br/self | |

---

## Design Principles

1. **Transport agnostic** — KDE Connect first, TCP fallback, future: Bluetooth, USB
2. **AI agnostic** — Local (Ollama) or cloud (OpenRouter), user chooses
3. **Device agnostic** — Linux x86, ARM (RPi), Android, anything with a terminal
4. **Append-only** — Events are never deleted, only new events are added
5. **Privacy first** — All data local by default, cloud opt-in per message
6. **No account required** — Works offline, no signup, no server dependency
7. **Composable** — Each command is a Unix-style building block

---

## The Math: Why This Works

```
Traditional app: UI + Backend + Auth + Database + Cloud = months of work
EGOS Self:       CLI + KDE Connect + SQLite + LLM = days to useful

Cost per user:
  Transport: $0 (KDE Connect is free/open)
  Storage: $0 (SQLite is local)
  AI: $0 (Ollama local) or ~$0.001/msg (API)
  Total: $0/month for full functionality
```

The key insight: **KDE Connect already solved** pairing, encryption, reconnection, and cross-device communication. We don't rebuild any of that. We add intelligence on top.

---

*"You don't need a cloud to think. You need a channel to yourself."*
