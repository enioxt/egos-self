"""EGOS Self — Git Layer (GitHub Provider).

Abstraction layer for Git operations. Phase 1 uses GitHub API.
Future phases will add Forgejo and local Git providers.
"""

import json
import urllib.request
import urllib.error
from dataclasses import dataclass

from egos_self.config import get_github_token

GITHUB_API = "https://api.github.com"


@dataclass
class GitUser:
    username: str
    name: str
    bio: str
    public_repos: int
    followers: int
    avatar_url: str


@dataclass
class GitRepo:
    name: str
    full_name: str
    description: str
    private: bool
    language: str
    stars: int
    url: str
    updated_at: str


def _github_request(endpoint: str, method: str = "GET", data: dict | None = None) -> dict | list:
    """Make an authenticated request to the GitHub API."""
    token = get_github_token()
    if not token:
        raise RuntimeError("Not logged in. Run: egos login")

    url = f"{GITHUB_API}{endpoint}"
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "egos-self-cli",
    }

    body = json.dumps(data).encode() if data else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        error_body = e.read().decode() if e.fp else ""
        raise RuntimeError(f"GitHub API error {e.code}: {error_body[:200]}")


def get_authenticated_user() -> GitUser:
    """Get the authenticated GitHub user."""
    data = _github_request("/user")
    return GitUser(
        username=data.get("login", ""),
        name=data.get("name", ""),
        bio=data.get("bio", "") or "",
        public_repos=data.get("public_repos", 0),
        followers=data.get("followers", 0),
        avatar_url=data.get("avatar_url", ""),
    )


def list_repos(sort: str = "updated", per_page: int = 20) -> list[GitRepo]:
    """List the authenticated user's repos."""
    data = _github_request(f"/user/repos?sort={sort}&per_page={per_page}&type=owner")
    repos = []
    for r in data:
        repos.append(GitRepo(
            name=r.get("name", ""),
            full_name=r.get("full_name", ""),
            description=r.get("description", "") or "",
            private=r.get("private", False),
            language=r.get("language", "") or "",
            stars=r.get("stargazers_count", 0),
            url=r.get("html_url", ""),
            updated_at=r.get("updated_at", "")[:10],
        ))
    return repos


def validate_token(token: str) -> GitUser | None:
    """Validate a GitHub token by making a test API call."""
    url = f"{GITHUB_API}/user"
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "egos-self-cli",
    }
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode())
            return GitUser(
                username=data.get("login", ""),
                name=data.get("name", ""),
                bio=data.get("bio", "") or "",
                public_repos=data.get("public_repos", 0),
                followers=data.get("followers", 0),
                avatar_url=data.get("avatar_url", ""),
            )
    except Exception:
        return None
