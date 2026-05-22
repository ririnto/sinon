---
name: plugin-settings
description: >-
  Author plugin-level configuration via `settings.json` and per-project state via `.claude/<plugin-name>.local.md` with YAML frontmatter parsing patterns. Use this skill when adding user-configurable plugin behavior offline.
---

# Plugin Settings

Author plugin-level static configuration and per-project plugin state with YAML frontmatter patterns, readable offline from hooks, commands, and agents.

## Goal

Enable plugins to store configurable state in `.claude/<plugin-name>.local.md` files so projects can customize plugin behavior without code changes, and document the file structure and parsing patterns needed to read and validate settings.

## Scope

This skill covers two independent surfaces:

### Plugin-Level Settings

`settings.json` at the plugin root, declared in `plugin.json` via `"settings": "./settings.json"`. Static configuration, shared across all projects. One file per plugin.

### Per-Project State

`.claude/<plugin-name>.local.md` in user projects, gitignored, user-managed. YAML frontmatter (structured key-value config) + markdown body (freeform content). One file per plugin per project.

## Operating Rules

1. Plugin-level settings are read-only artifacts bundled with the plugin; project state files are writable by end users.
2. State files use YAML frontmatter as the authoritative config layer; markdown body is secondary context.
3. Hooks and agents MUST check file existence before parsing to avoid errors on first run.
4. Changes to `.local.md` files require Claude Code restart before hooks recognize them.
5. All `.local.md` and `.local.json` entries MUST be added to project `.gitignore`.
6. File paths in settings MUST be validated for path traversal (no `..`); file permissions MUST be `chmod 600`.
7. User input written to settings files MUST be sanitized (escape quotes, validate types).
8. When the settings file is absent or invalid, behavior MUST fall back to documented defaults.

## File Structure

### Plugin-level settings.json

Minimal schema with only fields the plugin actually uses:

```json
{
  "fieldName": "default-value",
  "numericField": 10,
  "listField": ["option1", "option2"],
  "booleanField": false
}
```text

Declare in `plugin.json`:

```json
{
  "$schema": "https://anthropic.com/claude-code/plugin.schema.json",
  "name": "your-plugin",
  "description": "Plugin description",
  "author": { "name": "your-handle" },
  "commands": "./commands/",
  "skills": "./skills/",
  "settings": "./settings.json"
}
```text

### Per-project .claude/`<plugin-name>`.local.md

Template with frontmatter (YAML key-value pairs) and optional markdown body:

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
Edit settings above and restart Claude Code for changes to take effect.
```text

Common frontmatter keys:

- `enabled` (boolean): Master on/off switch for hook and command behavior.
- Plugin-specific keys: custom fields matching the plugin's config schema.
- `coordinator_session` (string): tmux session name for multi-agent coordination.
- `task_number` (string): Agent task identifier for swarm workflows.
- `additional_instructions` (string): Freeform instructions fed back to Claude.

## Reading Settings from Hooks

Hooks (bash scripts) that read settings follow a three-step pattern: existence check, frontmatter extraction, field parsing.

Pattern: Check file exists, extract frontmatter between `---` delimiters, parse individual fields with `grep` + `sed`:

```sh
if [ ! -f "$STATE_FILE" ]; then
    return 0
fi
FRONTMATTER=$(sed -n '/^---$/,/^---$/{ /^---$/d; p; }' "$STATE_FILE")
ENABLED=$(echo "$FRONTMATTER" | grep '^enabled:' | sed 's/enabled: *//')
```text

See `references/frontmatter-parsing.md` for per-field type patterns (boolean, string, numeric, array, multi-line), edge cases, validation, and complete working examples.

## Reading Settings from Commands and Agents

Commands use the `Read` tool to fetch `.local.md` files, then parse YAML frontmatter in the result.

### In Command Markdown

```markdown
# Your Command

Check for settings at `.claude/your-plugin.local.md`.
If present, read the file, parse YAML frontmatter for `enabled`, `mode`, and other fields, then adapt behavior.
```text

Agents reference settings in their instructions:

```markdown
---
name: configured-agent
description: >-
  Adapts behavior to project settings
---

Check for plugin settings at `.claude/your-plugin.local.md`.
If the file exists:
- Parse YAML frontmatter (the content between `---` markers at the top)
- Read the `enabled`, `mode`, and other fields
- Apply settings to your behavior
If the file is absent, use documented defaults.
```text

## Common Patterns

### Pattern 1: Conditional Hook Activation

Use `enabled` flag to activate/deactivate hooks without editing `hooks.json` (which requires restart):

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
ENABLED=$(echo "$FRONTMATTER" | grep '^enabled:' | sed 's/enabled: *//')
if [ "$ENABLED" != "true" ]; then
    exit 0
fi
```text

Restart Claude Code after changing `enabled: true/false`.

### Pattern 2: Agent Task Coordination

Store agent identity and coordinator session for multi-agent swarms:

`.claude/multi-agent-swarm.local.md:`

```markdown
---
agent_name: auth-service
task_number: 3.5
pr_number: 1234
coordinator_session: team-leader
enabled: true
---

# Task: Implement JWT Authentication

Coordinate with database-agent on schema changes.
```text

Hook reads fields and sends notifications to coordinator:

```bash
# Validate tmux session name format.
#
# @param session Session name.
# @return Exits 0 if valid; 1 otherwise.
validate_tmux_session() {
    session="$1"
    if ! printf '%s' "$session" | grep -qE '^[a-zA-Z0-9_-]+$'; then
        return 1
    fi
    return 0
}

AGENT_NAME=$(echo "$FRONTMATTER" | grep '^agent_name:' | sed 's/agent_name: *//')
COORDINATOR=$(echo "$FRONTMATTER" | grep '^coordinator_session:' | sed 's/coordinator_session: *//')
if validate_tmux_session "$COORDINATOR"; then
    safe_agent=$(printf '%q' "$AGENT_NAME")
    tmux send-keys -t "$COORDINATOR" "Agent $safe_agent completed task" Enter
fi
```text

### Pattern 3: Configuration-Driven Validation Mode

Store validation policy and apply in hooks or commands:

`.claude/security-plugin.local.md:`

```markdown
---
validation_level: strict
max_file_size: 1000000
allowed_extensions:
  - ".ts"
  - ".js"
---
```text

Switch behavior based on mode:

```sh
LEVEL=$(echo "$FRONTMATTER" | grep '^validation_level:' | sed 's/validation_level: *//')
case "$LEVEL" in
    strict)
        # Apply strict checks
        ;;
    standard)
        # Apply standard checks
        ;;
    lenient)
        # Minimal checks
        ;;
esac
```text

## Creating Settings Files

Commands can scaffold settings files when first run or requested by the user.

### Steps

1. Ask user for configuration preferences.
2. Sanitize user input (escape quotes, validate types).
3. Write `.claude/<plugin-name>.local.md` with YAML frontmatter and optional body.
4. Add `.claude/*.local.md` to project `.gitignore`.
5. Inform user that settings are active and require Claude Code restart.

### Sanitization Example

```sh
# Write sanitized user input to plugin settings file.
#
# @param USER_INPUT Raw user input string.
# @return Creates .claude/your-plugin.local.md with escaped values.
USER_INPUT="$1"
SAFE_VALUE=$(echo "$USER_INPUT" | sed 's/"/\\"/g')
cat > ".claude/your-plugin.local.md" <<EOF
---
user_setting: "$SAFE_VALUE"
enabled: true
---

# Your Plugin Configuration
EOF
chmod 600 ".claude/your-plugin.local.md"
```text

Validate path fields to prevent path traversal:

```sh
# Canonicalize and validate path against base directory.
#
# @param base_dir Base directory (e.g., ${CLAUDE_PROJECT_DIR}).
# @param path_value User-provided path to validate.
# @return Exits 2 if unsafe; exits 0 and echoes normalized path otherwise.
validate_path_safe() {
    base_dir="$1"
    path_value="$2"
    if [ -z "$path_value" ]; then
        echo "⚠️ Empty path" >&2
        return 2
    fi
    normalized=$(realpath -m "$base_dir/$path_value" || echo "")
    if [ -z "$normalized" ]; then
        echo "⚠️ Invalid path" >&2
        return 2
    fi
    base_real=$(realpath -m "$base_dir" || echo "")
    if [ -z "$base_real" ] || { [ "$normalized" != "$base_real" ] && ! printf '%s' "$normalized" | grep -qE "^${base_real}/"; }; then
        echo "⚠️ Path escapes base directory" >&2
        return 2
    fi
    echo "$normalized"
    return 0
}

FILE_PATH=$(echo "$FRONTMATTER" | grep '^data_file:' | sed 's/data_file: *//')
if validate_path_safe "${CLAUDE_PROJECT_DIR}" "$FILE_PATH" > /dev/null; then
    echo "⚠️ Invalid path in settings" >&2
    exit 2
fi
```text

## Gitignore and Defaults

### Add to project .gitignore

User scope state files MUST never be committed:

```text
.claude/*.local.md
.claude/*.local.json
```text

Document this in plugin README:

```markdown
## Configuration

Create `.claude/your-plugin.local.md` in your project:

\`\`\`markdown
---
enabled: true
mode: standard
---
\`\`\`

Note: This file is local to your project and should be added to `.gitignore`.
```text

### Defaults when file is absent

```sh
if [ ! -f "$STATE_FILE" ]; then
    ENABLED=true
    MODE="standard"
    MAX_RETRIES=3
else
    # Parse from file
fi
```text

Validate numeric ranges:

```sh
MAX=$(echo "$FRONTMATTER" | grep '^max_retries:' | sed 's/max_retries: *//')
if ! printf '%s' "$MAX" | grep -qE '^[0-9]+$' || [ "$MAX" -lt 1 ] || [ "$MAX" -gt 100 ]; then
    echo "⚠️ Invalid max_retries (must be 1-100), using default 3" >&2
    MAX=3
fi
```text

## Restart Requirement

Critical: Changes to `.local.md` files require Claude Code restart before hooks reload them.

Document in plugin README:

```markdown
## Changing Settings

After editing `.claude/your-plugin.local.md`:
1. Save the file
2. Exit Claude Code (or use `/exit`)
3. Restart: `claude` or `cc`
4. New settings will be active
```text

Hooks loaded during startup cannot be hot-reloaded within the same session.

## Security

### Input Sanitization

Always escape user input before writing to YAML:

```sh
SAFE_VALUE=$(echo "$INPUT" | sed 's/"/\\"/g' | sed "s/'/\\\\'/g")
echo "field: \"$SAFE_VALUE\"" >> "$STATE_FILE"
```text

### Path Validation

Reject paths containing `..` or absolute paths when not intended:

```sh
PATH_VALUE=$(echo "$FRONTMATTER" | grep '^output_dir:' | sed 's/output_dir: *//')
if printf '%s' "$PATH_VALUE" | grep -qE '^/' || [ "$PATH_VALUE" != "${PATH_VALUE%..*}" ]; then
    echo "⚠️ Invalid path in settings" >&2
    PATH_VALUE="./output"
fi
```text

### File Permissions

Settings files MUST be readable only by the user:

```sh
chmod 600 ".claude/your-plugin.local.md"
```text

## First Safe Commands

Check that a settings file exists and is valid YAML:

```sh
if [ -f ".claude/your-plugin.local.md" ]; then
    head -20 ".claude/your-plugin.local.md"
fi
```text

Extract frontmatter without parsing:

```sh
sed -n '/^---$/,/^---$/{ /^---$/d; p; }' ".claude/your-plugin.local.md"
```text

Read a single field:

```sh
grep '^enabled:' ".claude/your-plugin.local.md" | sed 's/enabled: *//'
```text

## Output Contract

When implementing settings support in a plugin, return:

1. The template `.claude/<plugin-name>.local.md` file showing example frontmatter and body.
2. Example hook code that reads and uses settings.
3. Updated plugin README documenting the settings schema, defaults, and restart requirement.
4. Updated `.gitignore` entry for `.claude/*.local.md`.

## Pitfalls

- DO: check file existence with `[ -f "$FILE" ]` before parsing to avoid parse errors on first run.
- DO: fallback to documented defaults when settings file is absent.
- DO: validate numeric ranges and string values; warn and use defaults on invalid input.
- DO: add `.claude/*.local.md` and `.claude/*.local.json` to `.gitignore`.
- DO: document restart requirement clearly in plugin README.
- DO: sanitize user input before writing to files (escape quotes, validate types).
- DO: set file permissions to `chmod 600` for user scope state files.

- DON'T: assume the settings file exists or is valid YAML; always check and provide defaults.
- DON'T: put plugin-level settings in `.local.md` files; use `settings.json` in the plugin root instead.
- DON'T: commit user scope files to git; rely on `.gitignore`.
- DON'T: try to hot-reload hooks within the same Claude Code session; document the restart requirement.
- DON'T: store secrets (API keys, tokens) in `.local.md` files without encryption; use Claude Code secrets or environment variables instead.
- DON'T: allow arbitrary file paths in settings without validation; reject `..` and absolute paths when not explicitly intended.

## References

- `references/frontmatter-parsing.md` — Complete parsing patterns, per-field type handling, and edge cases.
- `references/multi-agent-coordination.md` — Task numbering, coordinator sessions, and real-world swarm examples.
