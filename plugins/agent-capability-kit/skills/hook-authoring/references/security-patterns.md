---
name: security-patterns
description: |-
  Input validation, injection prevention, path safety, and sensitive file detection patterns for hook scripts.
---

# Security Patterns for Hook Scripts

Hook scripts run with elevated context and must strictly validate all inputs. This reference extends SKILL.md with working patterns for input validation, path safety, sensitive file detection, and shell injection prevention.

See SKILL.md for foundational rules: JSON validation via jq, path traversal/sensitive file rejection, variable quoting, timeouts.

## Input validation patterns

### JSON parsing (CORRECT)

Parse JSON inputs with `jq` and validate field presence:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Validate JSON input before processing.
#
# @param input Raw JSON from stdin.
# @return Extracts validated fields or exits with error.
validate_input() {
    input=$(cat)
    if ! tool_name=$(printf '%s' "$input" | jq --exit-status -r '.tool_name // empty'); then
        printf '{"decision": "deny", "reason": "Invalid JSON or parse failure"}\n' >&2
        exit 2
    fi
    if [ -z "$tool_name" ]; then
        printf '{"decision": "deny", "reason": "Missing tool_name field"}\n' >&2
        exit 2
    fi
    if ! printf '%s' "$tool_name" | grep -qE '^[a-zA-Z0-9_]+$'; then
        printf '{"decision": "deny", "reason": "Invalid tool_name format"}\n' >&2
        exit 2
    fi
    echo "$tool_name"
}
```

Rules:

- Use `jq -r` to extract string values
- Use `// empty` to provide defaults
- Validate field presence before use
- Validate format with regex

### JSON parsing (BROKEN)

Trusting input without validation:

```sh
#!/bin/sh
input=$(cat)
tool_name=$(printf '%s' "$input" | jq -r '.tool_name')
# Dangerous: no validation of format or presence
rm -rf "/projects/$tool_name"
```

Risk: `tool_name` could be `..` or `/tmp` or contain spaces, leading to unintended deletions.

## Path safety validation

### Correct: reject traversal and sensitive paths

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Validate file paths for safety before processing.
#
# @param file_path File path to validate.
# @return Exits 0 if safe; exits 2 with error JSON if unsafe.
validate_path() {
    file_path="$1"
    if [ -z "$file_path" ]; then
        printf '{"decision": "deny", "reason": "Missing file path"}\n' >&2
        exit 2
    fi
    if [ "$file_path" != "${file_path%..*}" ]; then
        printf '{"decision": "deny", "reason": "Path traversal (..) detected"}\n' >&2
        exit 2
    fi
    if printf '%s' "$file_path" | grep -qE '^/'; then
        printf '{"decision": "deny", "reason": "Absolute paths not allowed"}\n' >&2
        exit 2
    fi
    if printf '%s' "$file_path" | grep -qE '\.(env|aws|pem|key|ssh)$'; then
        printf '{"decision": "deny", "reason": "Sensitive file extension"}\n' >&2
        exit 2
    fi
    if printf '%s' "$file_path" | grep -qE '(secring\.gpg|\.gpg\.key|\.gnupg/.*)$'; then
        printf '{"decision": "deny", "reason": "GPG secret key file"}\n' >&2
        exit 2
    fi
    if printf '%s' "$file_path" | grep -qE '^(node_modules|\.git|\.env\..*)'; then
        printf '{"decision": "deny", "reason": "Protected directory"}\n' >&2
        exit 2
    fi
    return 0
}
```

Patterns to reject:

- `..` or `../` — path traversal
- Leading `/` — absolute paths
- `.env`, `.aws`, `.pem`, `.key`, `.ssh` — sensitive files; `.gpg` only when matching private key patterns (e.g., `secring.gpg`, `*.gpg.key`, or paths under `~/.gnupg/`)
- `node_modules/`, `.git/`, `.env.*` — protected directories

### Correct: system path detection

```sh
#!/bin/sh

# Reject writes to system directories.
#
# @param file_path Path to validate.
# @return Exits 2 if system path; exits 0 otherwise.
reject_system_paths() {
    file_path="$1"
    for prefix in "/bin" "/usr" "/etc" "/sys" "/var" "/opt" "/boot" "/proc" "/dev"; do
        case "$file_path" in
            "$prefix"*)
                printf '{"decision": "deny", "reason": "System path"}\n' >&2
                exit 2
                ;;
        esac
    done
    return 0
}
```

### Broken: insufficient path validation

```sh
#!/bin/sh
file_path=$(cat | jq -r '.tool_input.file_path')
# Only checks for ..
if [ "$file_path" = "${file_path%..*}" ]; then
    cp "$file_path" /tmp/upload
fi
# Risk: allows absolute paths like /etc/passwd
```

## Sensitive file detection

### Correct: multi-layer detection

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Comprehensive sensitive file detection.
#
# @param file_path Path to check.
# @return Exits 2 if sensitive; exits 0 otherwise.
detect_sensitive_files() {
    file_path="$1"
    basename=$(basename "$file_path")
    case "$basename" in
        .env|.env.local|.env.*|.aws|.npmrc|.yarnrc|*.pem|*.key|*.gpg|*.ssh|id_rsa|id_ed25519|*.crt|*.cer|*.p12|*.pfx|.gitignore|.dockerignore)
            printf '{"decision": "deny", "reason": "Sensitive file detected"}\n' >&2
            exit 2
            ;;
    esac
    case "$basename" in
        package-lock.json|yarn.lock|composer.lock|Gemfile.lock)
            if [ "$file_path" = "${file_path%..*}" ] && ! printf '%s' "$file_path" | grep -qE '^/'; then
                printf '{"decision": "deny", "reason": "Lock file modifications dangerous"}\n' >&2
                exit 2
            fi
            ;;
    esac
    return 0
}
```

Categories:

- Secrets: `.env`, `.aws`, `.pem`, `.key`, `.ssh`
- GPG secret keys: `secring.gpg`, `*.gpg.key`, `~/.gnupg/*` (not opaque `.gpg` encrypted blobs)
- Certificates: `.crt`, `.cer`, `.p12`, `.pfx`
- Private keys: `id_rsa`, `id_ed25519`, `*.key`
- Credentials: `.docker/config.json`, `~/.aws/credentials`
- Configuration: `.gitignore`, `.npmrc`, `.yarnrc`, lock files

### Broken: name-only detection

```sh
basename=$(basename "$file_path")
if [ "$basename" = ".env" ]; then
    printf 'deny\n' >&2
fi
# Allows /tmp/.env or /other/path/.env without checking
```

## Shell injection prevention

### Correct: safe command construction

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Build and execute safe shell commands.
#
# @param command Command name to run.
# @param arg Command argument.
# @return Executes command safely.
run_command() {
    command="$1"
    arg="$2"
    if ! printf '%s' "$command" | grep -qE '^(grep|find|ls|cat)$'; then
        printf '{"decision": "deny", "reason": "Unsafe command"}\n' >&2
        exit 2
    fi
    "$command" "$arg"
}
```

Safe patterns:

- Array arguments: `"${arr[@]}"` prevents word splitting
- Validated commands: whitelist known-safe commands
- Separate args from command: `"$cmd" "$arg"` not `"$cmd $arg"`

### Broken: command injection via variable

```sh
#!/bin/sh
search_term=$(cat | jq -r '.search_term')
# Dangerous: search_term could be '; rm -rf /'
grep "$search_term" /tmp/file.txt
```

## Numeric validation

### Correct: type and range checking

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Validate numeric input with range constraints.
#
# @param max_value Numeric string to validate.
# @return Exits 2 if invalid; sets MAX on success.
validate_numeric() {
    max_value="$1"
    if ! printf '%s' "$max_value" | grep -qE '^[0-9]+$'; then
        printf '{"decision": "deny", "reason": "max_value must be numeric"}\n' >&2
        exit 2
    fi
    if [ "$max_value" -lt 1 ] || [ "$max_value" -gt 1000 ]; then
        printf '{"decision": "deny", "reason": "max_value must be 1-1000"}\n' >&2
        exit 2
    fi
    echo "$max_value"
}
```

### Broken: trusting numeric input

```sh
max_value=$(cat | jq -r '.max_value')
if [ "$max_value" -gt 100 ]; then
    # Risk: max_value could be non-numeric or contain operators
fi
```

## Complete example: hardened PreToolUse hook

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
# shellcheck disable=SC2034
set -e

# Comprehensive file write validation hook.
#
# @return JSON output with permissionDecision and systemMessage.
main() {
    input=$(cat)
    if ! tool_name=$(printf '%s' "$input" | jq --exit-status -r '.tool_name // empty'); then
        printf '{"permissionDecision": "deny", "systemMessage": "JSON parse failure"}\n' >&2
        exit 2
    fi
    if ! file_path=$(printf '%s' "$input" | jq --exit-status -r '.tool_input.file_path // empty'); then
        printf '{"permissionDecision": "deny", "systemMessage": "JSON parse failure"}\n' >&2
        exit 2
    fi
    if [ -z "$tool_name" ] || [ -z "$file_path" ]; then
        printf '{"permissionDecision": "deny", "systemMessage": "Missing required fields"}\n' >&2
        exit 2
    fi
    if ! printf '%s' "$tool_name" | grep -qE '^[a-zA-Z0-9_]+$'; then
        printf '{"permissionDecision": "deny", "systemMessage": "Invalid tool_name"}\n' >&2
        exit 2
    fi
    if [ "$file_path" != "${file_path%..*}" ]; then
        printf '{"permissionDecision": "deny", "systemMessage": "Path traversal detected"}\n' >&2
        exit 2
    fi
    if printf '%s' "$file_path" | grep -qE '\.(env|aws|pem|key)$'; then
        printf '{"permissionDecision": "deny", "systemMessage": "Sensitive file"}\n' >&2
        exit 2
    fi
    if printf '%s' "$file_path" | grep -qE '^/'; then
        printf '{"permissionDecision": "deny", "systemMessage": "Absolute paths not allowed"}\n' >&2
        exit 2
    fi
    printf '{"permissionDecision": "allow", "systemMessage": "Path validation passed"}\n'
    exit 0
}
main
```

## Testing security patterns

Validate hook script with sample attack payloads:

```sh
cat > /tmp/test-attack.json << 'EOF'
{
  "tool_name": "Write",
  "tool_input": {
    "file_path": "../../../../etc/passwd"
  }
}
EOF

sh hooks/validate.sh < /tmp/test-attack.json
# Expected: deny output on stderr, exit 2
```

Test with various paths:

```sh
# Path traversal
echo '{"tool_name":"Write","tool_input":{"file_path":"../../../etc/passwd"}}' | sh hooks/validate.sh

# Sensitive file
echo '{"tool_name":"Write","tool_input":{"file_path":".env"}}' | sh hooks/validate.sh

# System path
echo '{"tool_name":"Write","tool_input":{"file_path":"/usr/bin/malware"}}' | sh hooks/validate.sh

# Safe path (should succeed)
echo '{"tool_name":"Write","tool_input":{"file_path":"src/index.js"}}' | sh hooks/validate.sh
```

## References

Refer to `SKILL.md` for hook event types and output contracts.

Refer to `references/lifecycle.md` for environment variable handling.

Refer to `references/performance.md` for timeouts and error recovery.
