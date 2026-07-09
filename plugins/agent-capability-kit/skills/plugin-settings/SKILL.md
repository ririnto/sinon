---
name: plugin-settings
description: >-
  Author plugin configuration across three surfaces: plugin-root `settings.json` for host-supported static settings, manifest `userConfig` for prompted install-time values, and a plugin-defined `.claude/{{plugin-name}}.local.md` state file for per-project configuration that plugin hooks and agents read.
  Triggers on plugin settings creation, prompted `userConfig` wiring, per-project state file design, or YAML frontmatter extraction from markdown for plugin runtime code.
---

# Plugin Settings

Choose the right configuration surface for a plugin and author it so hooks, agents, and bundled runtime code can read it offline.

## Goal

Give a plugin a trustworthy place to keep configuration, and document the parsing patterns plugin code uses to read it.

## Scope: choose the right surface

Start by picking the surface that matches where the value comes from and who edits it.
Two surfaces are host-supported; the third is a plugin-defined convention.

### Plugin-level settings - `settings.json`

Plugin-root `settings.json`, auto-discovered by Claude Code.
Host-supported static settings shared across every project that enables the plugin.

Use it for host-supported settings keys such as `permissions`, `env`, `model`, `statusLine`, `outputStyle`, and `hooks`.
Consult the Claude Code settings schema for the full key set; do not invent keys.

```json
{
  "env": {
    "MY_PLUGIN_DEFAULT_MODE": "standard"
  },
  "statusLine": "echo my-plugin active"
}
```

Do not redeclare `settings` as a manifest path when `settings.json` at the plugin root is the only surface.

### Prompted config - `userConfig`

Declared in the plugin manifest under `userConfig`.
Claude Code prompts the user at enable time for each declared value; non-sensitive values are saved to `settings.json` and sensitive values go to secure storage.
Use it for values that vary per user and are chosen up front, such as an API endpoint or a preferred mode.

```json
{
  "userConfig": {
    "apiEndpoint": {
      "type": "string",
      "title": "API endpoint",
      "description": "Base URL the plugin calls.",
      "default": "https://api.example.com"
    }
  }
}
```

Read prompted values through `${user_config.KEY}` substitution in plugin configuration, or through the `CLAUDE_PLUGIN_OPTION_<KEY>` environment variable in plugin subprocesses.

### Per-project local state - `.claude/{{plugin-name}}.local.md`

A plugin-defined convention, not a host feature.
A gitignored, user-managed Markdown file with YAML frontmatter that the plugin's own hooks and agents read each time they run.
Use it for per-project configuration that users edit directly and that is not a host setting.

One file per plugin per project.

## Decision table

| Need | Surface | Why |
| --- | --- | --- |
| Static host setting, shared across projects | `settings.json` | Host-supported, auto-discovered |
| Value chosen once at install, varies per user | `userConfig` | Host prompts the user; `${user_config.KEY}` exposes it |
| Per-project value, user-edited, read by plugin code | `.local.md` | Plugin convention; no host contract to satisfy |

## Operating rules

1. Pick one surface per value using the decision table; do not mirror the same value across surfaces.
2. Plugin-level `settings.json` holds host-supported keys only; do not invent keys the host does not recognize.
3. `.local.md` files are a plugin convention: the plugin's own hooks and agents read them; no host feature is implied.
4. Hooks and agents MUST check file existence before parsing to avoid errors on first run.
5. Content changes in a `.local.md` file take effect the next time a plugin command hook reads it, because command hooks run their script fresh each fire.
   Only structural changes to `hooks.json` need a plugin reload.
6. All `.local.md` and `.local.json` entries MUST be added to project `.gitignore`.
7. File paths supplied through settings MUST be validated for path traversal (no `..`); user-scoped state files SHOULD use `chmod 600`.
8. User input written to settings files MUST be sanitized (escape quotes, validate types).
9. When the state file is absent or invalid, behavior MUST fall back to documented defaults.

## Per-project state file structure

`.claude/{{plugin-name}}.local.md` with YAML frontmatter (structured key-value config) and an optional Markdown body:

```markdown
---
enabled: true
mode: standard
max_retries: 3
custom_setting: "value"
list_setting:
  - item1
  - item2
---

# Plugin Configuration

This file controls per-project behavior for your-plugin.
Edit the frontmatter above; settings apply on the next hook run.
```

Common frontmatter keys are plugin-defined.
Typical examples:

- `enabled` (boolean): master on/off switch for hook behavior.
- `mode` (string): selects a behavior profile the plugin recognizes.
- Plugin-specific keys: custom fields matching the plugin's own schema.

## Reading settings from hooks

Hooks that read state follow a three-step pattern: existence check, frontmatter extraction, field parsing.
Check the file exists, extract frontmatter between `---` delimiters, then parse individual fields with `grep` and `sed`:

```sh
if [ ! -f "$STATE_FILE" ]; then
    return 0
fi
FRONTMATTER=$(sed -n '/^---$/,/^---$/{ /^---$/d; p; }' "$STATE_FILE")
ENABLED=$(printf '%s\n' "$FRONTMATTER" | grep '^enabled:' | sed 's/enabled: *//')
```

See `references/frontmatter-parsing.md` for per-field type patterns (boolean, string, numeric, array, multi-line), edge cases, validation, and complete working examples.

## Reading settings from agents

Agents reference state in their instructions:

```markdown
---
name: configured-agent
description: >-
  Adapts behavior to project settings.
---

- Check for plugin state at `.claude/your-plugin.local.md`.
- If the file exists:
  - Parse YAML frontmatter (the content between `---` markers at the top).
  - Read the `enabled`, `mode`, and other fields.
  - Apply settings to your behavior.
- If the file is absent, use documented defaults.
```

## Common patterns

### Pattern 1: Conditional hook activation

Use an `enabled` flag to activate or deactivate hook logic without editing `hooks.json`:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Load plugin state and conditionally enable hook logic.
#
# @param STATE_FILE Path to .claude/your-plugin.local.md.
# @return Exits 0 if disabled or file missing; executes hook logic if enabled.
STATE_FILE=".claude/your-plugin.local.md"
if [ ! -f "$STATE_FILE" ]; then
    exit 0
fi
FRONTMATTER=$(sed -n '/^---$/,/^---$/{ /^---$/d; p; }' "$STATE_FILE")
ENABLED=$(printf '%s\n' "$FRONTMATTER" | grep '^enabled:' | sed 's/enabled: *//')
if [ "$ENABLED" != "true" ]; then
    exit 0
fi
```

Changing `enabled: true` to `false` applies on the next hook run; no restart is required for content changes.

### Pattern 2: Configuration-driven validation mode

Store a validation policy and apply it in hooks or bundled runtime code:

```markdown
---
validation_level: strict
max_file_size: 1000000
allowed_extensions:
  - ".ts"
  - ".js"
---
```

Switch behavior based on mode:

```sh
LEVEL=$(printf '%s\n' "$FRONTMATTER" | grep '^validation_level:' | sed 's/validation_level: *//')
case "$LEVEL" in
    strict)
        # Apply strict checks.
        ;;
    standard)
        # Apply standard checks.
        ;;
    lenient)
        # Minimal checks.
        ;;
esac
```

## Creating state files

Agents can scaffold state files when requested by the user.

1. Ask the user for configuration preferences.
2. Sanitize user input (escape quotes, validate types).
3. Write `.claude/{{plugin-name}}.local.md` with YAML frontmatter and an optional body.
4. Add `.claude/*.local.md` to the project `.gitignore`.
5. Tell the user the settings apply on the next hook run.

### Sanitization

```sh
# Write sanitized user input to a plugin state file.
#
# @param USER_INPUT Raw user input string.
# @return Creates .claude/your-plugin.local.md with escaped values.
USER_INPUT="$1"
SAFE_VALUE=$(printf '%s' "$USER_INPUT" | sed 's/"/\\"/g')
cat > ".claude/your-plugin.local.md" <<EOF
---
user_setting: "$SAFE_VALUE"
enabled: true
---

# Your Plugin Configuration
EOF
chmod 600 ".claude/your-plugin.local.md"
```

### Path validation

Reject absolute paths and `..` traversal segments before using a path from settings.
This check is POSIX and avoids GNU-only `realpath -m`:

```sh
# Validate a relative path stays inside the base directory.
#
# @param base_dir Base directory (e.g., ${CLAUDE_PROJECT_DIR}).
# @param path_value User-provided relative path to validate.
# @return Returns 2 and prints to stderr if unsafe; returns 0 and echoes base/value otherwise.
validate_path_safe() {
    base_dir="$1"
    path_value="$2"
    if [ -z "$path_value" ]; then
        echo "error: empty path" >&2
        return 2
    fi
    case "$path_value" in
        ..|../*|*/..|*/../*|/*)
            echo "error: path escapes base directory" >&2
            return 2
            ;;
    esac
    printf '%s/%s\n' "$base_dir" "$path_value"
    return 0
}
```

## Defaults when the file is absent

```sh
if [ ! -f "$STATE_FILE" ]; then
    ENABLED=true
    MODE="standard"
    MAX_RETRIES=3
else
    FRONTMATTER=$(sed -n '/^---$/,/^---$/{ /^---$/d; p; }' "$STATE_FILE")
fi
```

Validate numeric ranges and fall back on invalid input:

```sh
MAX=$(printf '%s\n' "$FRONTMATTER" | grep '^max_retries:' | sed 's/max_retries: *//')
if ! printf '%s' "$MAX" | grep -qE '^[0-9]+$' || [ "$MAX" -lt 1 ] || [ "$MAX" -gt 100 ]; then
    echo "error: invalid max_retries (must be 1-100), using default 3" >&2
    MAX=3
fi
```

## When a reload is actually required

Two different reload rules apply; do not conflate them.

- `.local.md` content changes: read fresh by plugin command hooks on the next fire.
  No restart is required for content edits.
- `hooks.json` structure changes (adding, removing, or rewiring events or matchers): take effect on the next plugin load.
  Restart the session when a structural change must be guaranteed live.

Document this distinction in the plugin README so users know which edits are live immediately.

## Security

### Input sanitization

Escape user input before writing it to YAML:

```sh
SAFE_VALUE=$(printf '%s' "$INPUT" | sed 's/"/\\"/g' | sed "s/'/\\\\'/g")
printf 'field: "%s"\n' "$SAFE_VALUE" >> "$STATE_FILE"
```

### Path validation

Reject paths containing `..` or unintended absolute paths using `validate_path_safe` above.

### File permissions

User-scoped state files SHOULD be readable only by the user:

```sh
chmod 600 ".claude/your-plugin.local.md"
```

## First safe commands

Check that a state file exists:

```sh
if [ -f ".claude/your-plugin.local.md" ]; then
    head -20 ".claude/your-plugin.local.md"
fi
```

Extract frontmatter without parsing:

```sh
sed -n '/^---$/,/^---$/{ /^---$/d; p; }' ".claude/your-plugin.local.md"
```

Read a single field:

```sh
grep '^enabled:' ".claude/your-plugin.local.md" | sed 's/enabled: *//'
```

## Output contract

When implementing settings support in a plugin, return:

1. The chosen surface per value, justified by the decision table.
2. The template `.claude/{{plugin-name}}.local.md` file, if per-project state is in scope, showing example frontmatter and body.
3. Example hook or agent code that reads and uses the settings.
4. The updated plugin README documenting the settings schema, defaults, and the reload rules.
5. The updated `.gitignore` entry for `.claude/*.local.md` and `.claude/*.local.json`.

## Pitfalls

- DO: pick one surface per value using the decision table.
- DO: check file existence with `[ -f "$FILE" ]` before parsing to avoid parse errors on first run.
- DO: fall back to documented defaults when the state file is absent or invalid.
- DO: validate numeric ranges and string values; warn and use defaults on invalid input.
- DO: add `.claude/*.local.md` and `.claude/*.local.json` to `.gitignore`.
- DO: sanitize user input before writing to files (escape quotes, validate types).
- DO: document the reload rules (content vs `hooks.json` structure) in the plugin README.
- DON'T: treat `.local.md` as a host feature; it is a plugin convention the plugin's own code reads.
- DON'T: put host-supported static settings in `.local.md`; use plugin-root `settings.json`.
- DON'T: put install-time prompted values in `.local.md`; use manifest `userConfig`.
- DON'T: assume the state file exists or is valid YAML; always check and provide defaults.
- DON'T: commit user-scoped files to git; rely on `.gitignore`.
- DON'T: store secrets (API keys, tokens) in `.local.md` without encryption; use Claude Code secrets or environment variables instead.
- DON'T: allow arbitrary file paths in settings without validation; reject `..` and absolute paths when not explicitly intended.

## References

- `references/frontmatter-parsing.md` - Complete parsing patterns, per-field type handling, and edge cases.
- `references/multi-agent-coordination.md` - Task numbering, coordinator sessions, and real-world swarm examples.
