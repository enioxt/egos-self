# EGOS Self — Domain Rules

## Device Communication

- KDE Connect protocol v7 compatibility is mandatory
- UDP discovery on port 1716 (broadcast)
- TCP/TLS on port 1716 (paired communication)
- WebSocket relay for internet-wide (WAN) connectivity
- All messages stored locally in SQLite (append-only)

## Security

- GitHub tokens stored in ~/.config/egos-self/credentials.toml (600 permissions)
- No data leaves device without explicit user action
- TLS for all paired device communication
- Relay is pure pass-through (no data stored on relay)

## Android

- Min SDK 26 (Android 8.0+)
- Foreground service for background discovery
- Notification channel for persistent connection status
- Network security config allows cleartext on local networks only

## CLI

- All commands work offline (except login/whoami/repos)
- `egos status` is the entry point — always works
- `egos send` delivers via LAN first, relay second, local log as fallback
