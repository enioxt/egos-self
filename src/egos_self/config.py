"""EGOS Self — Configuration management.

Config stored in ~/.config/egos-self/config.toml
Credentials stored separately with 600 permissions.
"""

import os
import tomllib
from pathlib import Path
from typing import Any

CONFIG_DIR = Path.home() / ".config" / "egos-self"
CONFIG_FILE = CONFIG_DIR / "config.toml"
CREDENTIALS_FILE = CONFIG_DIR / "credentials.toml"


def ensure_config_dir() -> None:
    """Create config directory if it doesn't exist."""
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)


def load_config() -> dict[str, Any]:
    """Load config from TOML file."""
    if not CONFIG_FILE.exists():
        return {}
    with open(CONFIG_FILE, "rb") as f:
        return tomllib.load(f)


def save_config(config: dict[str, Any]) -> None:
    """Save config to TOML file."""
    ensure_config_dir()
    lines = []
    for key, value in config.items():
        if isinstance(value, dict):
            lines.append(f"\n[{key}]")
            for k, v in value.items():
                lines.append(f'{k} = {_toml_value(v)}')
        else:
            lines.append(f'{key} = {_toml_value(value)}')
    CONFIG_FILE.write_text("\n".join(lines) + "\n")


def load_credentials() -> dict[str, str]:
    """Load credentials from secure file."""
    if not CREDENTIALS_FILE.exists():
        return {}
    with open(CREDENTIALS_FILE, "rb") as f:
        return tomllib.load(f)


def save_credentials(creds: dict[str, str]) -> None:
    """Save credentials with restricted permissions (600)."""
    ensure_config_dir()
    lines = []
    for key, value in creds.items():
        lines.append(f'{key} = "{value}"')
    CREDENTIALS_FILE.write_text("\n".join(lines) + "\n")
    os.chmod(CREDENTIALS_FILE, 0o600)


def get_github_token() -> str | None:
    """Get GitHub PAT from credentials."""
    creds = load_credentials()
    return creds.get("github_token")


def set_github_token(token: str) -> None:
    """Store GitHub PAT in credentials."""
    creds = load_credentials()
    creds["github_token"] = token
    save_credentials(creds)


def remove_github_token() -> None:
    """Remove GitHub PAT from credentials."""
    creds = load_credentials()
    creds.pop("github_token", None)
    save_credentials(creds)


def get_relay_url() -> str | None:
    """Get default relay URL from config."""
    config = load_config()
    return config.get("relay", {}).get("url") if isinstance(config.get("relay"), dict) else None


def _toml_value(v: Any) -> str:
    """Convert a Python value to TOML string representation."""
    if isinstance(v, str):
        return f'"{v}"'
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, int):
        return str(v)
    return f'"{v}"'
