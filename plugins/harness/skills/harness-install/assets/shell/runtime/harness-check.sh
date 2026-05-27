#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

MANIFEST=docs/harness/manifest.json
findings_file=$(mktemp)
trap 'rm -f "$findings_file"' EXIT

# Read a manifest field via python3 and print one value per line.
#
# @param expr Python expression evaluating to an iterable of strings.
# @return Writes one value per line.
manifest_query() {
    expr=$1
    python3 - "$MANIFEST" "$expr" <<'PYEOF'
import json
import sys
path = sys.argv[1]
expr = sys.argv[2]
with open(path, 'r', encoding='utf-8') as fh:
    M = json.load(fh)
for line in eval(expr):
    print(line)
PYEOF
}

# Read a single string field from the manifest.
#
# @param expr Python expression evaluating to a string.
# @return Writes the string value.
manifest_string() {
    expr=$1
    python3 - "$MANIFEST" "$expr" <<'PYEOF'
import json
import sys
path = sys.argv[1]
expr = sys.argv[2]
with open(path, 'r', encoding='utf-8') as fh:
    M = json.load(fh)
print(eval(expr))
PYEOF
}

# Emit a finding line in the canonical format.
#
# @param severity ERROR / WARN / INFO.
# @param category Add-on category name.
# @param message Human-readable message.
# @return Appends one line to the findings buffer.
emit() {
    severity=$1
    category=$2
    message=$3
    printf '[%s] %s: %s\n' "$severity" "$category" "$message" >>"$findings_file"
}

# Resolve a severity from the manifest with ERROR fallback.
#
# @param category Add-on category name.
# @return Writes the severity string.
severity_of() {
    category=$1
    manifest_string "M.get('$category', {}).get('severity', 'ERROR')"
}

# Resolve whether an add-on is enabled (default true).
#
# @param category Add-on category name.
# @return Writes 1 when enabled, 0 otherwise.
enabled_of() {
    category=$1
    manifest_string "1 if M.get('$category', {}).get('enabled', True) else 0"
}

# Validate that every parameters.paths entry exists as a regular file.
#
# @return Emits findings for missing files.
check_file_presence() {
    category=filePresence
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    sev=$(severity_of "$category")
    manifest_query "M['$category']['parameters']['paths']" | while IFS= read -r path; do
        if [ -n "$path" ] && [ ! -f "$path" ]; then
            emit "$sev" "$category" "missing file: $path"
        fi
    done
}

# Validate that every parameters.paths entry exists as a directory.
#
# @return Emits findings for missing directories.
check_directory_presence() {
    category=directoryPresence
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    sev=$(severity_of "$category")
    manifest_query "M['$category']['parameters']['paths']" | while IFS= read -r path; do
        if [ -n "$path" ] && [ ! -d "$path" ]; then
            emit "$sev" "$category" "missing directory: $path"
        fi
    done
}

# Validate that each parameters.directories entry has at least one tracked file
# or a .gitkeep placeholder.
#
# @return Emits findings for empty directories.
check_empty_directory_placeholders() {
    category=emptyDirectoryPlaceholders
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    sev=$(severity_of "$category")
    manifest_query "M['$category']['parameters']['directories']" | while IFS= read -r directory; do
        if [ -n "$directory" ] && [ -d "$directory" ]; then
            if [ -z "$(find "$directory" -mindepth 1 -print -quit)" ]; then
                emit "$sev" "$category" "empty directory must keep placeholder or real files: $directory"
            fi
        fi
    done
}

# Validate that each parameters.hooks file starts with parameters.expectedShebang.
#
# @return Emits findings for incorrect shebang.
check_hook_shebang() {
    category=hookShebang
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    sev=$(severity_of "$category")
    expected=$(manifest_string "M['$category']['parameters']['expectedShebang']")
    manifest_query "M['$category']['parameters']['hooks']" | while IFS= read -r hook; do
        if [ -n "$hook" ] && [ -f "$hook" ]; then
            first=$(sed -n '1p' "$hook")
            if [ "$first" != "$expected" ]; then
                emit "$sev" "$category" "$hook must start with $expected"
            fi
        fi
    done
}

# Validate that each parameters.hooks file has executable bit set.
#
# @return Emits findings for non-executable hooks.
check_hook_executable() {
    category=hookExecutable
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    sev=$(severity_of "$category")
    manifest_query "M['$category']['parameters']['hooks']" | while IFS= read -r hook; do
        if [ -n "$hook" ] && [ -f "$hook" ] && [ ! -x "$hook" ]; then
            emit "$sev" "$category" "$hook must be executable"
        fi
    done
}

# Validate that no scaffold leak pattern appears under parameters.scope.
#
# @return Emits findings for matched leak patterns.
check_scaffold_leaks() {
    category=scaffoldLeaks
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    sev=$(severity_of "$category")
    python3 - "$MANIFEST" "$category" "$sev" >>"$findings_file" <<'PYEOF'
import json
import re
import sys
from pathlib import Path
manifest_path = sys.argv[1]
category = sys.argv[2]
severity = sys.argv[3]
with open(manifest_path, 'r', encoding='utf-8') as fh:
    manifest = json.load(fh)
spec = manifest[category]['parameters']
scope = spec['scope']
bases = scope.get('bases', [])
excluded = scope.get('excludedSubtrees', [])
extensions = set(scope.get('extensions', []))
patterns = [(re.compile(p['pattern']), p['label']) for p in spec.get('patterns', [])]

def strip_markdown_code(text):
    stripped_lines = []
    in_fence = False
    fence_marker = ''
    for line in text.splitlines():
        fence_match = re.match(r' {0,3}(`{3,}|~{3,})', line)
        if fence_match:
            marker = fence_match.group(1)[0]
            if not in_fence:
                in_fence = True
                fence_marker = marker
            elif marker == fence_marker:
                in_fence = False
            stripped_lines.append('')
            continue
        if in_fence:
            stripped_lines.append('')
            continue
        stripped_lines.append(re.sub(r'`+[^`\n]*`+', '', line))
    return '\n'.join(stripped_lines)

def is_excluded(path_str):
    for sub in excluded:
        if path_str == sub or path_str.startswith(sub + '/'):
            return True
    return False
def is_safe_relative_root(value):
    path = Path(value)
    return value != '' and not path.is_absolute() and '..' not in path.parts
files = []
for base in bases:
    if not isinstance(base, str) or not is_safe_relative_root(base):
        continue
    base_path = Path(base)
    if not base_path.exists() or base_path.is_symlink():
        continue
    if base_path.is_file():
        files.append(base_path)
        continue
    for entry in base_path.rglob('*'):
        if entry.is_symlink():
            continue
        if entry.is_file():
            files.append(entry)
for file_path in files:
    rel = str(file_path)
    if is_excluded(rel):
        continue
    if file_path.suffix.lstrip('.') not in extensions:
        continue
    try:
        text = file_path.read_text(encoding='utf-8')
    except UnicodeDecodeError:
        continue
    text = strip_markdown_code(text)
    for pattern, label in patterns:
        if pattern.search(text):
            print(f"[{severity}] {category}: {label} in active asset: {rel}")
PYEOF
}

# Validate that no completed plan retains unchecked task lines.
#
# @return Emits findings for unchecked tasks under the completed plan directory.
check_unchecked_tasks() {
    category=uncheckedTasks
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    sev=$(severity_of "$category")
    directory=$(manifest_string "M['$category']['parameters']['directory']")
    pattern=$(manifest_string "M['$category']['parameters']['uncheckedTaskPattern']")
    if [ -d "$directory" ]; then
        find "$directory" -type f -name '*.md' | while IFS= read -r plan_file; do
            if grep -qE "$pattern" "$plan_file"; then
                emit "$sev" "$category" "completed plan has unchecked tasks: $plan_file"
            fi
        done
    fi
}

# Validate that every shell script passes shellcheck with no errors.
#
# @return Emits findings for shell scripts with violations.
check_shellcheck() {
    category=shellcheck
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    sev=$(severity_of "$category")
    sh_body=$(cat <<'BODY'
output=$(shellcheck "$1" 2>&1)
if [ -n "$output" ]; then
    printf "%s\n" "$1"
fi
BODY
)
    violators=$(find . -type f -name '*.sh' -not -path './.git/*' -print0 |
        xargs -0 -n 1 -P 4 sh -c "$sh_body" sh)
    if [ -n "$violators" ]; then
        printf '%s\n' "$violators" | while IFS= read -r file; do
            emit "$sev" "$category" "$file: shellcheck violations found"
        done
    fi
}

if [ ! -f "$MANIFEST" ]; then
    printf '[ERROR] manifest missing: %s\n' "$MANIFEST" >&2
    exit 1
fi

check_file_presence
check_directory_presence
check_empty_directory_placeholders
check_hook_shebang
check_hook_executable
check_scaffold_leaks
check_unchecked_tasks
check_shellcheck

errors=0
warns=0
infos=0
while IFS= read -r line; do
    case "$line" in
        '[ERROR]'*)
            printf '%s\n' "$line" >&2
            errors=$((errors + 1))
            ;;
        '[WARN]'*)
            printf '%s\n' "$line" >&2
            warns=$((warns + 1))
            ;;
        '[INFO]'*)
            printf '%s\n' "$line" >&2
            infos=$((infos + 1))
            ;;
    esac
done <"$findings_file"

if [ "$errors" -gt 0 ]; then
    printf 'Harness validation failed (errors=%s warns=%s infos=%s)\n' "$errors" "$warns" "$infos" >&2
    exit 1
fi
printf 'Harness validation passed (errors=0 warns=%s infos=%s)\n' "$warns" "$infos"
