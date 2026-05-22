---
name: authentication
description: |-
  OAuth flow, token rotation, secret hygiene, and multi-server credential isolation patterns for MCP servers.
---

# MCP Authentication: OAuth Flow, Token Rotation, and Secret Hygiene

Open this reference when configuring OAuth for SSE servers, managing bearer tokens, setting up per-server credential isolation, or implementing secret rotation.

This reference covers OAuth authentication for SSE servers, bearer token management, env var injection, multi-server credential isolation, and secret storage patterns.

## OAuth flow: step-by-step

OAuth 2.0 with authorization code flow (used by all SSE servers).

### First use: authorization

```text
1. User starts Claude Code
2. Runs command that uses MCP tool from new SSE server
3. Claude Code detects no stored token
4. Opens browser to: https://mcp.SERVICE.com/authorize?client_id=claude-code&redirect_uri=...&scope=...
5. User logs in at service, grants permissions
6. Browser redirected to: http://localhost:PORT/callback?code=AUTH_CODE&state=STATE
7. Claude Code exchanges AUTH_CODE for access token
8. Token stored locally in ~/.claude/mcp_tokens.json (encrypted)
9. Token used for all subsequent tool calls
```text

### Subsequent uses: token reuse

```text
1. User starts Claude Code
2. Runs command with MCP tool
3. Claude Code reads stored token from ~/.claude/mcp_tokens.json
4. Token passed in Authorization header: Bearer ${TOKEN}
5. Tool call succeeds
```text

### Token refresh: automatic

```text
1. Tool call returns 401 Unauthorized (token expired)
2. Claude Code uses refresh token to get new access token
3. New token stored locally
4. Tool call retried with new token
5. User sees no interruption
```text

Claude Code handles refresh automatically; user action not required.

## Environment variable injection

### Bearer token via env var

Store API token in environment variable; reference in MCP config:

```json
{
  "api-service": {
    "type": "http",
    "url": "https://api.example.com/mcp",
    "headers": {
      "Authorization": "Bearer ${API_TOKEN}"
    }
  }
}
```text

Set token in shell environment:

```sh
export API_TOKEN="sk_live_abc123xyz..."
claude
```text

Or in user's shell profile (`~/.zshrc` or `~/.bashrc`):

```sh
export API_TOKEN="sk_live_abc123xyz..."
```text

> [!CAUTION]
>
> Bearer tokens via env var are not the same as OAuth scoping. An env-var token carries whatever permissions were issued at creation; revocation requires manual rotation of the token across all consumers. OAuth (especially with refresh tokens and per-scope grants) supports per-resource, time-bound, and user-attributable scoping. Prefer OAuth where the upstream MCP server supports it; use env-var bearer tokens only when OAuth is unavailable or the token is explicitly scoped at issuance.

### Multiple servers, different credentials

Each server uses its own env var:

```json
{
  "github": {
    "type": "sse",
    "url": "https://mcp.github.com/sse"
  },
  "slack": {
    "type": "sse",
    "url": "https://mcp.slack.com/sse"
  },
  "custom-api": {
    "type": "http",
    "url": "https://api.example.com/mcp",
    "headers": {
      "Authorization": "Bearer ${CUSTOM_API_TOKEN}"
    }
  }
}
```text

Each service's token stored separately:

- GitHub: OAuth token in `~/.claude/mcp_tokens.json` (key: github)
- Slack: OAuth token in `~/.claude/mcp_tokens.json` (key: slack)
- Custom API: env var `${CUSTOM_API_TOKEN}` from shell

Separation ensures compromise of one service doesn't affect others.

### Env var expansion timing

Variables are expanded at session start, not at config parse time.

If env var changes during session, change is not reflected:

```sh
# Session 1: API_TOKEN=old_token
claude
# ... session running ...

# (User updates token in shell)
export API_TOKEN=new_token

# ... still using old_token in this session ...

/exit           # Exit session
claude          # New session: uses new_token
```text

Changing credentials requires session restart.

## Token storage and encryption

### Encrypted token storage

OAuth tokens stored in `~/.claude/mcp_tokens.json`, encrypted at rest:

```json
{
  "github": {
    "access_token": "ghu_...",
    "refresh_token": "ghr_...",
    "expires_at": 1234567890,
    "scopes": ["repo", "gist"]
  },
  "slack": {
    "access_token": "xoxb-...",
    "expires_at": 1234567890
  }
}
```text

File permissions: `600` (readable only by user).

### Token exposure risk

#### BROKEN: hardcoded token in config

```json
{
  "type": "http",
  "headers": {
    "Authorization": "Bearer sk_live_secret123"
  }
}
```text

Risk: Token visible in version control, logs, and process listings.

#### CORRECT: env var expansion

```json
{
  "type": "http",
  "headers": {
    "Authorization": "Bearer ${API_TOKEN}"
  }
}
```text

Token stored in env var (outside repo), never hardcoded.

### Secret scanning

If token is committed accidentally:

1. Revoke token immediately at service
2. Generate new token
3. Update env var or ~/.bashrc
4. Force-push git history (rewrite commits) or use BFG Repo Cleaner

Mitigation: add to `.gitignore`:

```text
# Never commit secrets
.env
.env.local
~/.claude/mcp_tokens.json
```text

## Multi-server credential isolation

### Scenario: three MCP servers with different auth

Server 1 (GitHub, OAuth):

```json
{
  "github": {
    "type": "sse",
    "url": "https://mcp.github.com/sse"
  }
}
```text

Server 2 (Asana, OAuth):

```json
{
  "asana": {
    "type": "sse",
    "url": "https://mcp.asana.com/sse"
  }
}
```text

Server 3 (Custom API, bearer token):

```json
{
  "internal-api": {
    "type": "http",
    "url": "https://api.internal.company.com/mcp",
    "headers": {
      "Authorization": "Bearer ${INTERNAL_API_TOKEN}"
    }
  }
}
```text

Each server's credentials:

- GitHub: Stored in `~/.claude/mcp_tokens.json[github]`, OAuth-managed
- Asana: Stored in `~/.claude/mcp_tokens.json[asana]`, OAuth-managed
- Internal API: Stored in `$INTERNAL_API_TOKEN` env var, user-managed

Compromise of GitHub token does not expose Asana or Internal API credentials.

### Credential rotation schedule

Rotate tokens periodically (recommended: every 90 days):

```sh
# 1. Generate new token at service
# 2. Update env var
export INTERNAL_API_TOKEN=new_token_xyz

# 3. Test new token
curl -H "Authorization: Bearer $INTERNAL_API_TOKEN" https://api.internal.company.com/mcp/tools

# 4. If successful, restart Claude Code
/exit
claude

# 5. Revoke old token at service dashboard
```text

For OAuth servers (GitHub, Asana), token refresh is automatic. Manual rotation only needed if service requires it.

## Per-project credential management

Projects can override global credentials via `.claude/<plugin-name>.local.md`:

```markdown
---
api_token_env_var: PROJECT_SPECIFIC_TOKEN
enabled: true
---

# Override token for this project

Use PROJECT_SPECIFIC_TOKEN instead of GLOBAL_TOKEN.
```text

Hook reads settings and adjusts:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Load project-specific token override.
#
# @param settings_file .claude/<plugin-name>.local.md
# @return Exports adjusted API_TOKEN.
load_project_token() {
    settings_file="$1"
    if [ ! -f "$settings_file" ]; then
        return 0
    fi
    fm=$(sed -n '/^---$/,/^---$/{ /^---$/d; p; }' "$settings_file")
    token_env=$(echo "$fm" | grep '^api_token_env_var:' | sed 's/api_token_env_var: *//')
    if [ -n "$token_env" ]; then
        export API_TOKEN="$(eval echo \"\$$token_env\")"
    fi
}
```text

## API key scoping: principle of least privilege

OAuth scopes limit what service token can access.

### GitHub scopes example

Scopes available for GitHub MCP:

- `repo` — Read/write access to repos
- `gist` — Create and modify gists
- `user` — Read user profile
- `workflow` — Manage workflows

Authorization prompt shows:

```text
Claude Code requests access to:
- Read and write repositories
- Create gists
- Read user profile

Allow?
```text

#### Correct: minimal scope

If using GitHub MCP only to list repos and create issues, request minimal scopes:

```text
Requested scopes: repo (sufficient for repo + issue access)
```text

#### Broken: over-permission

```text
Requested scopes: repo, gist, user, workflow, admin
```text

Unnecessary scopes increase attack surface if token leaked.

### Asana scopes example

Asana MCP might request:

- `tasks:read` — Read task data
- `tasks:write` — Create/modify tasks
- `projects:read` — Read project info

Request only what tool needs.

## Secret hygiene checklist

- [ ] Never hardcode tokens in code or config files
- [ ] Use env vars for all credentials (e.g., `${API_TOKEN}`)
- [ ] Store env vars in `~/.bashrc` or `~/.zshrc`, not in repo
- [ ] Add `.env`, `.secrets`, and `mcp_tokens.json` to `.gitignore`
- [ ] Encrypt sensitive files at rest (use GPG for `.env` if storing)
- [ ] Use OAuth for service-hosted credentials (automatic refresh)
- [ ] Rotate tokens every 90 days (or per service policy)
- [ ] Revoke compromised tokens immediately
- [ ] Use minimal scopes for OAuth tokens
- [ ] Isolate credentials per server (don't reuse tokens)
- [ ] Monitor token usage via service audit logs
- [ ] Enable 2FA on service accounts that issue tokens

## Testing token configuration

Validate token without running main tool:

```sh
# Test HTTP with bearer token
curl -H "Authorization: Bearer $API_TOKEN" https://api.example.com/mcp/tools | jq .

# Test OAuth (after authorization)
/mcp test github

# View stored tokens (encrypted, not readable)
ls -la ~/.claude/mcp_tokens.json
```text

## Token debugging

Enable debug output to see token-related messages:

```sh
claude --debug 2>&1 | grep -i token
```text

Output:

```text
[mcp] Loading token for 'github' from cache
[mcp] Token expires in 3600 seconds
[mcp] Attempting refresh for 'github' (token expired)
[mcp] New token obtained, expiration: ...
```text

Do not log actual token values (only metadata).

## References

Refer to `SKILL.md` for env var expansion syntax.

Refer to `references/transport-types.md` for transport-specific auth (OAuth vs token).

Refer to `references/performance.md` for token caching and latency optimization.
