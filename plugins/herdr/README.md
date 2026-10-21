# Herdr

Sinon packages the upstream Herdr skill for Codex and Claude Code with native plugin metadata.

Its entry condition requires `HERDR_ENV=1`, and the upstream skill retains its lifecycle and approval safeguards.

This package follows upstream release `v0.9.5` and includes the upstream Apache-2.0 license.

Herdr does not publish a standalone plugin manifest or a separate skill version, so updates follow upstream releases.

For an update, review the upstream skill and license, carry over its content with Sinon prose formatting, and synchronize the package and marketplace versions.

## Package Contents

- `.codex-plugin/plugin.json`: Codex plugin identity.
- `skills/herdr/SKILL.md`: upstream Herdr skill.
- `LICENSE`: upstream Apache-2.0 license.
