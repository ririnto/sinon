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

```bash
#!/bin/bash
set -euo pipefail

# Validate JSON input before processing.
# @param input Raw JSON from stdin.
# @return Extracts validated fields or exits with error.
validate_input() {
    local input
    input=$(cat)
    local tool_name
    if ! tool_name=$(echo "$input" | jq --exit-status -r '.tool_name // empty' 2>/dev/null); then
        echo '{"decision": "deny", "reason": "Invalid JSON or parse failure"}' >&2
        exit 2
    fi
    if [[ -z "$tool_name" ]]; then
        echo '{"decision": "deny", "reason": "Missing tool_name field"}' >&2
        exit 2
    fi
    if ! [[ "$tool_name" =~ ^[a-zA-Z0-9_]+$ ]]; then
        echo '{"decision": "deny", "reason": "Invalid tool_name format"}' >&2
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

```bash
#!/bin/bash
input=$(cat)
tool_name=$(echo "$input" | jq -r '.tool_name')
# Dangerous: no validation of format or presence
rm -rf "/projects/$tool_name"
```

Risk: `tool_name` could be `..` or `/tmp` or contain spaces, leading to unintended deletions.

## Path safety validation

### Correct: reject traversal and sensitive paths

```bash
#!/bin/bash
set -euo pipefail

# Validate file paths for safety before processing.
# @param file_path File path to validate.
# @return Exits 0 if safe; exits 2 with error JSON if unsafe.
validate_path() {
    local file_path="$1"
    if [[ -z "$file_path" ]]; then
        echo '{"decision": "deny", "reason": "Missing file path"}' >&2
        exit 2
    fi
    if [[ "$file_path" == *".."* ]]; then
        echo '{"decision": "deny", "reason": "Path traversal (..) detected"}' >&2
        exit 2
    fi
    if [[ "$file_path" =~ ^/ ]]; then
        echo '{"decision": "deny", "reason": "Absolute paths not allowed"}' >&2
        exit 2
    fi
    if [[ "$file_path" =~ \.(env|aws|pem|key|ssh)$ ]]; then
        echo '{"decision": "deny", "reason": "Sensitive file extension"}' >&2
        exit 2
    fi
    if [[ "$file_path" =~ (secring\.gpg|\.gpg\.key|\.gnupg/.*)$ ]]; then
        echo '{"decision": "deny", "reason": "GPG secret key file"}' >&2
        exit 2
    fi
    if [[ "$file_path" =~ ^(node_modules|\.git|\.env\..*) ]]; then
        echo '{"decision": "deny", "reason": "Protected directory"}' >&2
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

```bash
#!/bin/bash

# Reject writes to system directories.
# @param file_path Path to validate.
# @return Exits 2 if system path; exits 0 otherwise.
reject_system_paths() {
    local file_path="$1"
    local system_prefixes=("/bin" "/usr" "/etc" "/sys" "/var" "/opt" "/boot" "/proc" "/dev")
    for prefix in "${system_prefixes[@]}"; do
        if [[ "$file_path" == "$prefix"* ]]; then
            echo '{"decision": "deny", "reason": "System path"}' >&2
            exit 2
        fi
    done
    return 0
}
```

### Broken: insufficient path validation

```bash
#!/bin/bash
file_path=$(cat | jq -r '.tool_input.file_path')
# Only checks for ..
if [[ "$file_path" != *".."* ]]; then
    cp "$file_path" /tmp/upload
fi
# Risk: allows absolute paths like /etc/passwd
```

## Sensitive file detection

### Correct: multi-layer detection

```bash
#!/bin/bash
set -euo pipefail

# Comprehensive sensitive file detection.
# @param file_path Path to check.
# @return Exits 2 if sensitive; exits 0 otherwise.
detect_sensitive_files() {
    local file_path="$1"
    local basename
    basename=$(basename "$file_path")
    local dirname
    dirname=$(dirname "$file_path")
    case "$basename" in
        .env|.env.local|.env.*|.aws|.npmrc|.yarnrc|*.pem|*.key|*.gpg|*.ssh|id_rsa|id_ed25519|*.crt|*.cer|*.p12|*.pfx|.gitignore|.dockerignore)
            echo '{"decision": "deny", "reason": "Sensitive file detected"}' >&2
            exit 2
            ;;
    esac
    case "$basename" in
        package-lock.json|yarn.lock|composer.lock|Gemfile.lock)
            if [[ "$file_path" != *".."* ]] && [[ "$file_path" != /* ]]; then
                echo '{"decision": "deny", "reason": "Lock file modifications dangerous"}' >&2
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

```bash
basename=$(basename "$file_path")
if [[ "$basename" == ".env" ]]; then
    echo "deny" >&2
fi
# Allows /tmp/.env or /other/path/.env without checking
```

## Shell injection prevention

### Correct: safe command construction

```bash
#!/bin/bash
set -euo pipefail

# Build and execute safe shell commands.
# @param command Command name to run.
# @param arg Command argument.
# @return Executes command safely.
run_command() {
    local command="$1"
    local arg="$2"
    if ! [[ "$command" =~ ^(grep|find|ls|cat)$ ]]; then
        echo '{"decision": "deny", "reason": "Unsafe command"}' >&2
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

```bash
#!/bin/bash
search_term=$(cat | jq -r '.search_term')
# Dangerous: search_term could be '; rm -rf /'
grep "$search_term" /tmp/file.txt
```

## Numeric validation

### Correct: type and range checking

```bash
#!/bin/bash
set -euo pipefail

# Validate numeric input with range constraints.
# @param max_value Numeric string to validate.
# @return Exits 2 if invalid; sets MAX on success.
validate_numeric() {
    local max_value="$1"
    if ! [[ "$max_value" =~ ^[0-9]+$ ]]; then
        echo '{"decision": "deny", "reason": "max_value must be numeric"}' >&2
        exit 2
    fi
    if [[ $max_value -lt 1 ]] || [[ $max_value -gt 1000 ]]; then
        echo '{"decision": "deny", "reason": "max_value must be 1-1000"}' >&2
        exit 2
    fi
    echo "$max_value"
}
```

### Broken: trusting numeric input

```bash
max_value=$(cat | jq -r '.max_value')
if [[ $max_value -gt 100 ]]; then
    # Risk: max_value could be non-numeric or contain operators
fi
```

## Complete example: hardened PreToolUse hook

```bash
#!/bin/bash
set -euo pipefail

# Comprehensive file write validation hook.
# @return JSON output with permissionDecision and systemMessage.
main() {
    local input
    input=$(cat)
    local tool_name file_path
    if ! tool_name=$(echo "$input" | jq --exit-status -r '.tool_name // empty' 2>/dev/null); then
        echo '{"permissionDecision": "deny", "systemMessage": "JSON parse failure"}' >&2
        exit 2
    fi
    if ! file_path=$(echo "$input" | jq --exit-status -r '.tool_input.file_path // empty' 2>/dev/null); then
        echo '{"permissionDecision": "deny", "systemMessage": "JSON parse failure"}' >&2
        exit 2
    fi
    if [[ -z "$tool_name" ]] || [[ -z "$file_path" ]]; then
        echo '{"permissionDecision": "deny", "systemMessage": "Missing required fields"}' >&2
        exit 2
    fi
    if ! [[ "$tool_name" =~ ^[a-zA-Z0-9_]+$ ]]; then
        echo '{"permissionDecision": "deny", "systemMessage": "Invalid tool_name"}' >&2
        exit 2
    fi
    if [[ "$file_path" == *".."* ]]; then
        echo '{"permissionDecision": "deny", "systemMessage": "Path traversal detected"}' >&2
        exit 2
    fi
    if [[ "$file_path" =~ \.(env|aws|pem|key)$ ]]; then
        echo '{"permissionDecision": "deny", "systemMessage": "Sensitive file"}' >&2
        exit 2
    fi
    if [[ "$file_path" =~ ^/ ]]; then
        echo '{"permissionDecision": "deny", "systemMessage": "Absolute paths not allowed"}' >&2
        exit 2
    fi
    echo '{"permissionDecision": "allow", "systemMessage": "Path validation passed"}'
    exit 0
}
main
```

## Testing security patterns

Validate hook script with sample attack payloads:

```bash
cat > /tmp/test-attack.json << 'EOF'
{
  "tool_name": "Write",
  "tool_input": {
    "file_path": "../../../../etc/passwd"
  }
}
EOF

bash hooks/validate.sh < /tmp/test-attack.json
# Expected: deny output on stderr, exit 2
```

Test with various paths:

```bash
# Path traversal
echo '{"tool_name":"Write","tool_input":{"file_path":"../../../etc/passwd"}}' | bash hooks/validate.sh

# Sensitive file
echo '{"tool_name":"Write","tool_input":{"file_path":".env"}}' | bash hooks/validate.sh

# System path
echo '{"tool_name":"Write","tool_input":{"file_path":"/usr/bin/malware"}}' | bash hooks/validate.sh

# Safe path (should succeed)
echo '{"tool_name":"Write","tool_input":{"file_path":"src/index.js"}}' | bash hooks/validate.sh
```

## References

Refer to `SKILL.md` for hook event types and output contracts.

Refer to `references/lifecycle.md` for environment variable handling.

Refer to `references/performance.md` for timeouts and error recovery.
