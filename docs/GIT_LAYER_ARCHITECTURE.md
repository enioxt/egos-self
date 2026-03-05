# EGOS Self — Git Layer Architecture

> **Version:** 0.1.0 | **Date:** 2026-03-05
> **Status:** Design Document — Phase 1 implementing, Phase 2+ planned

---

## The Vision

GitHub is EGOS Self's first identity and sync layer. But GitHub is a centralized service.
The long-term goal is a **complete open layer** that can:

1. **Today**: Use GitHub as identity + data backbone (OAuth, repos, issues, API)
2. **Tomorrow**: Run the same functionality self-hosted (embedded Git server)
3. **Future**: Federate between EGOS Self nodes without any central service

```
Evolution:

Phase 1 (NOW)     Phase 2 (NEXT)      Phase 3 (FUTURE)
┌──────────┐     ┌──────────────┐     ┌─────────────────┐
│ GitHub   │     │ GitHub OR    │     │ Any Git host    │
│ API      │ →   │ Self-hosted  │ →   │ OR peer-to-peer │
│ (PAT)    │     │ (Forgejo)    │     │ (no server)     │
└──────────┘     └──────────────┘     └─────────────────┘
```

---

## Phase 1: GitHub as Backbone (NOW)

### Identity
- `egos login` → GitHub Personal Access Token (stored locally)
- Token never leaves the device. Never sent to any EGOS server.
- Your GitHub username becomes your EGOS Self identity.

### Data Sync
- Events can be pushed to a GitHub repo as JSON files
- Config synced via a private `.egos-self` repo
- Issues become tasks. Repos become projects.

### What GitHub Gives Us (for free)
| Feature | GitHub API | EGOS Self Command |
|---------|-----------|-------------------|
| Identity | OAuth/PAT | `egos login` |
| File storage | Repos | `egos sync` |
| Task tracking | Issues | `egos tasks` |
| Collaboration | PRs/Forks | `egos fork`, `egos pr` |
| Discovery | Search API | `egos search` |
| Hosting | Pages | `egos publish` |
| CI/CD | Actions | `egos deploy` |

### CLI Commands (Phase 1)

```bash
egos login                    # Authenticate with GitHub PAT
egos whoami                   # Show current identity
egos repos                    # List your GitHub repos
egos sync                     # Push/pull events to GitHub repo
egos logout                   # Remove stored credentials
```

---

## Phase 2: Abstraction Layer (NEXT)

The key insight: **don't call GitHub directly from commands**. Build an abstraction:

```python
# Instead of:
github_api.create_repo(name)

# Do:
git_layer.create_repo(name)  # dispatches to GitHub, Forgejo, or local

class GitProvider(ABC):
    def authenticate(self) -> bool: ...
    def list_repos(self) -> list[Repo]: ...
    def create_repo(self, name: str) -> Repo: ...
    def push_file(self, repo: str, path: str, content: str) -> bool: ...
    def list_issues(self, repo: str) -> list[Issue]: ...
    def create_issue(self, repo: str, title: str, body: str) -> Issue: ...

class GitHubProvider(GitProvider): ...      # Phase 1
class ForgejoProvider(GitProvider): ...     # Phase 2
class LocalGitProvider(GitProvider): ...    # Phase 3
```

This means: **every feature we build with GitHub works identically with self-hosted alternatives.**

---

## Phase 3: Self-Hosted Git (FUTURE)

### Option A: Embedded Forgejo
- Forgejo is a lightweight, self-hosted GitHub alternative (Go binary, ~50MB)
- EGOS Self could bundle it or install alongside
- `egos server start` → runs Forgejo + EGOS Self relay on the same machine
- Full web UI for repos, issues, PRs — accessible from any browser

### Option B: Bare Git + Custom UI
- Just `git daemon` + a minimal web interface
- Lighter than Forgejo but less features
- Good for Raspberry Pi / low-resource devices

### Option C: Peer-to-Peer Git (no server)
- Git already supports P2P via `git bundle` and direct push/pull
- EGOS Self relay could broker git operations between nodes
- No central server needed at all

### Recommendation
Start with **Option A (Forgejo)** for Phase 2 — it's battle-tested, open source, and gives us a full GitHub-like experience. Then evolve toward Option C for true decentralization.

---

## The Complete Git Layer (all phases)

```
┌────────────────────────────────────────────────────────────┐
│                    EGOS Self Git Layer                      │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              GitProvider Interface                    │  │
│  │  authenticate() | list_repos() | push_file()         │  │
│  │  list_issues()  | create_issue() | create_repo()     │  │
│  └──────────┬──────────────┬──────────────┬─────────────┘  │
│             │              │              │                 │
│  ┌──────────▼──┐  ┌───────▼──────┐  ┌───▼────────────┐   │
│  │   GitHub    │  │   Forgejo    │  │  Local Git     │   │
│  │   (API)     │  │   (API)      │  │  (bare repos)  │   │
│  │  Phase 1    │  │  Phase 2     │  │  Phase 3       │   │
│  └─────────────┘  └──────────────┘  └────────────────┘   │
│                                                            │
│  Cross-platform access:                                    │
│  CLI | WebApp | Desktop | Android | iOS | Raspberry Pi     │
└────────────────────────────────────────────────────────────┘
```

### Web Interface (any device with a browser)

When running `egos server`, a web UI is served that provides:
- Repository browser (files, commits, branches)
- Issue tracker
- Event log viewer
- Device status dashboard
- Settings / config editor

This means: **any device with a browser can interact with EGOS Self.**
No app installation needed for basic access.

---

## Security Model

| Layer | Mechanism |
|-------|-----------|
| Identity | GitHub PAT (Phase 1), local keys (Phase 2+) |
| Transport | TLS (GitHub API), WSS (relay), SSH (git) |
| Storage | Tokens in ~/.config/egos-self/ with 600 permissions |
| Access | Token never leaves device. No EGOS server sees it. |
| Revocation | `egos logout` deletes local token immediately |

---

## Why This Matters

Most tools force you into their ecosystem:
- GitHub requires GitHub
- Notion requires Notion
- Slack requires Slack

EGOS Self inverts this:
- **Start** with GitHub (everyone has an account)
- **Graduate** to self-hosted (when you want independence)
- **Federate** with others (no central authority)

The transition is seamless because the abstraction layer means **your data and workflows don't change** — only the provider underneath.

---

*"Use GitHub today. Own your data tomorrow. Federate with everyone forever."*
