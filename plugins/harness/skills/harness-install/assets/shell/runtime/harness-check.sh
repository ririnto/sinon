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
    emit_severity=$1
    emit_category=$2
    emit_message=$3
    printf '[%s] %s: %s\n' "$emit_severity" "$emit_category" "$emit_message" >>"$findings_file"
}

# Resolve a severity from the manifest with ERROR fallback.
#
# @param category Add-on category name.
# @return Writes the severity string.
severity_of() {
    severity_category=$1
    manifest_string "M.get('$severity_category', {}).get('severity', 'ERROR')"
}

# Resolve a message template from the manifest with a fallback.
#
# @param category Add-on category name.
# @param key Message key.
# @param fallback Fallback message template.
# @return Writes the message template.
message_of() {
    message_category=$1
    message_key=$2
    message_fallback=$3
    python3 - "$MANIFEST" "$message_category" "$message_key" "$message_fallback" <<'PYEOF'
import json
import sys
path = sys.argv[1]
category = sys.argv[2]
key = sys.argv[3]
fallback = sys.argv[4]
with open(path, 'r', encoding='utf-8') as fh:
    M = json.load(fh)
print(M.get(category, {}).get('messages', {}).get(key, fallback))
PYEOF
}

# Emit a path message resolved from the manifest.
#
# @param severity ERROR / WARN / INFO.
# @param category Add-on category name.
# @param key Message key.
# @param fallback Fallback message template.
# @param path Relative path for {path} substitution.
# @return Appends one line to the findings buffer.
emit_path_message() {
    path_message_severity=$1
    path_message_category=$2
    path_message_key=$3
    path_message_fallback=$4
    path_message_path=$5
    path_message_template=$(message_of "$path_message_category" "$path_message_key" "$path_message_fallback")
    path_message_text=$(printf '%s\n' "$path_message_template" | sed "s|{path}|$path_message_path|g")
    emit "$path_message_severity" "$path_message_category" "$path_message_text"
}

# Resolve whether an add-on is enabled (default true).
#
# @param category Add-on category name.
# @return Writes 1 when enabled, 0 otherwise.
enabled_of() {
    enabled_category=$1
    manifest_string "1 if M.get('$enabled_category', {}).get('enabled', True) else 0"
}

# Return whether a path is an allowed root contract symlink.
#
# @param path Relative path to check.
# @return Returns 0 when AGENTS.md and CLAUDE.md point to each other safely.
is_allowed_root_contract_symlink() {
    path=$1
    case "$path" in
        AGENTS.md)
            if [ ! -L "$path" ]; then
                return 1
            fi
            target=$(readlink "$path") || return 1
            if [ "$target" = CLAUDE.md ] && [ -f CLAUDE.md ] && [ ! -L CLAUDE.md ]; then
                return 0
            fi
            return 1
            ;;
        CLAUDE.md)
            if [ ! -L "$path" ]; then
                return 1
            fi
            target=$(readlink "$path") || return 1
            if [ "$target" = AGENTS.md ] && [ -f AGENTS.md ] && [ ! -L AGENTS.md ]; then
                return 0
            fi
            return 1
            ;;
        *)
            return 1
            ;;
    esac
}

# Return whether a required file symlink is allowed by the root contract.
#
# @param path Relative path to check.
# @return Returns 0 when the symlink may be treated as a required file.
is_allowed_file_symlink() {
    path=$1
    is_allowed_root_contract_symlink "$path"
}

# Return whether a manifest path is a safe relative path.
#
# @param path Manifest-controlled path to check.
# @return Returns 0 when the path is relative and contains no parent traversal.
is_safe_manifest_path() {
    path=$1
    case "$path" in
        '' | /* | .. | ../* | */.. | */../*) return 1 ;;
        -*) return 1 ;;
        *) return 0 ;;
    esac
}

# Return whether every path component of a manifest path is free of symlinks.
#
# @param path Manifest-controlled path to check.
# @return Returns 0 when no intermediate or final component is a symlink.
is_safe_manifest_path_symlinks() {
    path=$1
    probe=
    remaining=$path
    while [ -n "$remaining" ]; do
        component=${remaining%%/*}
        if [ "$remaining" = "$component" ]; then
            remaining=
        else
            remaining=${remaining#*/}
        fi
        if [ -z "$component" ]; then
            continue
        fi
        if [ -n "$probe" ]; then
            probe=$probe/$component
        else
            probe=$component
        fi
        if [ -L "$probe" ]; then
            return 1
        fi
    done
    return 0
}

# Return whether a manifest file path may be read safely.
#
# @param path Manifest-controlled file path to check.
# @return Returns 0 when the path is safe and not a symlink.
is_safe_manifest_file() {
    path=$1
    if ! is_safe_manifest_path "$path"; then
        return 1
    fi
    if ! is_safe_manifest_path_symlinks "$path"; then
        return 1
    fi
    return 0
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
        if ! is_safe_manifest_path "$path"; then
            emit "$sev" "$category" "$path is not a safe relative file path"
        elif [ -L "$path" ] && is_allowed_file_symlink "$path"; then
            if [ ! -f "$path" ]; then
                emit "$sev" "$category" "missing file: $path"
            fi
        elif ! is_safe_manifest_path_symlinks "$path"; then
            symlink_sev=$(severity_of symlinkSafety)
            emit_path_message "$symlink_sev" symlinkSafety fileNotAllowed 'symlink file is not allowed: {path}' "$path"
        elif [ ! -f "$path" ]; then
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
        if ! is_safe_manifest_path "$path"; then
            emit "$sev" "$category" "$path is not a safe relative directory path"
        elif ! is_safe_manifest_path_symlinks "$path"; then
            symlink_sev=$(severity_of symlinkSafety)
            emit_path_message "$symlink_sev" symlinkSafety directoryNotAllowed 'symlink directory is not allowed: {path}' "$path"
        elif [ ! -d "$path" ]; then
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
        if ! is_safe_manifest_path "$directory"; then
            emit "$sev" "$category" "$directory is not a safe relative directory path"
        elif ! is_safe_manifest_path_symlinks "$directory"; then
            symlink_sev=$(severity_of symlinkSafety)
            emit_path_message "$symlink_sev" symlinkSafety directoryNotAllowed 'symlink directory is not allowed: {path}' "$directory"
        elif [ -d "$directory" ]; then
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
        if [ -z "$hook" ]; then
            emit "$sev" "$category" "$hook is not a safe relative hook path"
            continue
        fi
        if ! is_safe_manifest_path "$hook"; then
            emit "$sev" "$category" "$hook is not a safe relative hook path"
            continue
        fi
        if ! is_safe_manifest_path_symlinks "$hook"; then
            symlink_sev=$(severity_of symlinkSafety)
            emit_path_message "$symlink_sev" symlinkSafety fileNotAllowed 'symlink file is not allowed: {path}' "$hook"
            continue
        fi
        if [ -f "$hook" ]; then
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
        if [ -z "$hook" ]; then
            emit "$sev" "$category" "$hook is not a safe relative hook path"
            continue
        fi
        if ! is_safe_manifest_path "$hook"; then
            emit "$sev" "$category" "$hook is not a safe relative hook path"
            continue
        fi
        if ! is_safe_manifest_path_symlinks "$hook"; then
            symlink_sev=$(severity_of symlinkSafety)
            emit_path_message "$symlink_sev" symlinkSafety fileNotAllowed 'symlink file is not allowed: {path}' "$hook"
            continue
        fi
        if [ -f "$hook" ] && [ ! -x "$hook" ]; then
            emit "$sev" "$category" "$hook must be executable"
        fi
    done
}

# Read the Harness validation command marker from a generated hook.
#
# @param hook Generated hook path.
# @return Writes the declared validation command, or an empty string.
declared_hook_command() {
    hook=$1
    if ! is_safe_manifest_file "$hook"; then
        return 0
    fi
    if [ ! -f "$hook" ]; then
        return 0
    fi
    sed -n 's/^# Harness validation command: //p' "$hook" | sed -n '1p'
}

# Validate that generated hooks declare and run supported commands.
#
# @return Emits findings for unsupported or missing hook commands.
check_hook_command() {
    category=hookCommand
    exists=$(manifest_string "1 if isinstance(M.get('$category'), dict) else 0")
    if [ "$exists" -ne 1 ]; then
        return 0
    fi
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    sev=$(severity_of "$category")
    pre_push=$(manifest_string "M['$category']['parameters']['prePushHook']")
    pre_commit=$(manifest_string "M['$category']['parameters']['preCommitHook']")
    if ! is_safe_manifest_path "$pre_push"; then
        emit "$sev" "$category" "$pre_push is not a safe relative hook path"
    elif ! is_safe_manifest_path_symlinks "$pre_push"; then
        symlink_sev=$(severity_of symlinkSafety)
        emit_path_message "$symlink_sev" symlinkSafety fileNotAllowed 'symlink file is not allowed: {path}' "$pre_push"
    else
        declared=$(declared_hook_command "$pre_push")
        if [ -f "$pre_push" ] && [ -z "$declared" ]; then
            emit "$sev" "$category" "$pre_push must declare Harness validation command"
        elif [ -n "$declared" ]; then
            allowed=0
            manifest_query "M['$category']['parameters']['allowedCommands']" | while IFS= read -r command; do
                if [ "$command" = "$declared" ]; then
                    printf '%s\n' allowed >"$findings_file.allowed"
                fi
            done
            if [ -f "$findings_file.allowed" ]; then
                rm -f "$findings_file.allowed"
                allowed=1
            fi
            if [ "$allowed" -ne 1 ]; then
                emit "$sev" "$category" "$pre_push declares unsupported validation command: $declared"
            fi
            if ! grep -Fxq "$declared" "$pre_push"; then
                emit "$sev" "$category" "$pre_push must run the declared validation command"
            fi
        fi
    fi
    if ! is_safe_manifest_path "$pre_commit"; then
        emit "$sev" "$category" "$pre_commit is not a safe relative hook path"
    elif ! is_safe_manifest_path_symlinks "$pre_commit"; then
        symlink_sev=$(severity_of symlinkSafety)
        emit_path_message "$symlink_sev" symlinkSafety fileNotAllowed 'symlink file is not allowed: {path}' "$pre_commit"
    elif [ -f "$pre_commit" ]; then
        manifest_query "M['$category']['parameters']['allowedCommands']" | while IFS= read -r command; do
            if [ -n "$command" ] && grep -Fxq "$command" "$pre_commit"; then
                emit "$sev" "$category" "pre-commit hook must not run full stack validation commands"
            fi
        done
    fi
}

# Validate that CI files run the command declared by generated pre-push.
#
# @return Emits findings for CI command drift.
check_ci_hook_command_parity() {
    category=ciHookCommandParity
    exists=$(manifest_string "1 if isinstance(M.get('$category'), dict) else 0")
    if [ "$exists" -ne 1 ]; then
        return 0
    fi
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    sev=$(severity_of "$category")
    reference_hook=$(manifest_string "M['$category']['parameters']['referenceHook']")
    if ! is_safe_manifest_path "$reference_hook"; then
        emit "$sev" "$category" "$reference_hook is not a safe relative hook path"
        return 0
    fi
    if ! is_safe_manifest_path_symlinks "$reference_hook"; then
        symlink_sev=$(severity_of symlinkSafety)
        emit_path_message "$symlink_sev" symlinkSafety fileNotAllowed 'symlink file is not allowed: {path}' "$reference_hook"
        return 0
    fi
    command=$(declared_hook_command "$reference_hook")
    if [ -z "$command" ]; then
        return 0
    fi
    manifest_query "M['$category']['parameters']['ciFiles']" | while IFS= read -r ci_file; do
        if ! is_safe_manifest_path "$ci_file"; then
            emit "$sev" "$category" "$ci_file is not a safe relative CI path"
        elif ! is_safe_manifest_path_symlinks "$ci_file"; then
            symlink_sev=$(severity_of symlinkSafety)
            emit_path_message "$symlink_sev" symlinkSafety fileNotAllowed 'symlink file is not allowed: {path}' "$ci_file"
        elif [ -f "$ci_file" ] && ! grep -Fq "$command" "$ci_file"; then
            emit "$sev" "$category" "$ci_file: CI command mismatch - expected $command"
        fi
    done
}

# Validate that protected harness paths are not symlinks.
#
# @return Emits findings for symlinked scan roots and scan entries.
check_symlink_safety() {
    category=symlinkSafety
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    sev=$(severity_of "$category")
    for base in .claude docs .github AGENTS.md CLAUDE.md ARCHITECTURE.md; do
        if [ -L "$base" ]; then
            if ! is_allowed_root_contract_symlink "$base"; then
                emit_path_message "$sev" "$category" scanRootNotAllowed 'symlink scan root is not allowed: {path}' "$base"
            fi
        elif [ -d "$base" ]; then
            find "$base" -type l | while IFS= read -r symlink_path; do
                path=${symlink_path#./}
                emit_path_message "$sev" "$category" pathNotAllowed 'symlink path is not allowed: {path}' "$path"
            done
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
    symlink_sev=$(severity_of symlinkSafety)
    python3 - "$MANIFEST" "$category" "$sev" "$symlink_sev" >>"$findings_file" <<'PYEOF'
import json
import re
import sys
from pathlib import Path
manifest_path = sys.argv[1]
category = sys.argv[2]
severity = sys.argv[3]
symlink_severity = sys.argv[4]
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
    return value != '' and not value.startswith('-') and not path.is_absolute() and '..' not in path.parts

def has_symlink_component(path):
    probe = Path()
    for part in path.parts:
        probe = probe / part
        if probe.is_symlink():
            return True
    return False

def root_contract_symlink_target(value):
    path = Path(value)
    if value == 'AGENTS.md':
        if path.is_symlink() and path.readlink() == Path('CLAUDE.md') and Path('CLAUDE.md').is_file() and not Path('CLAUDE.md').is_symlink():
            return 'CLAUDE.md'
    if value == 'CLAUDE.md':
        if path.is_symlink() and path.readlink() == Path('AGENTS.md') and Path('AGENTS.md').is_file() and not Path('AGENTS.md').is_symlink():
            return 'AGENTS.md'
    return None
files = []
for base in bases:
    if not isinstance(base, str) or not is_safe_relative_root(base):
        print(f"[{severity}] {category}: {base} is not a safe relative scan root")
        continue
    base_path = Path(base)
    root_contract_target = root_contract_symlink_target(base)
    if root_contract_target in bases:
        continue
    if root_contract_target is not None:
        files.append(Path(root_contract_target))
        continue
    if has_symlink_component(base_path):
        print(f"[{symlink_severity}] symlinkSafety: symlink scan root is not allowed: {base}")
        continue
    if not base_path.exists():
        continue
    if base_path.is_file():
        files.append(base_path)
        continue
    for entry in base_path.rglob('*'):
        if entry.is_symlink():
            print(f"[{symlink_severity}] symlinkSafety: symlink scan entry is not allowed: {entry}")
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
    if ! is_safe_manifest_path "$directory"; then
        emit "$sev" "$category" "$directory is not a safe relative directory path"
    elif ! is_safe_manifest_path_symlinks "$directory"; then
        symlink_sev=$(severity_of symlinkSafety)
        emit_path_message "$symlink_sev" symlinkSafety directoryNotAllowed 'symlink directory is not allowed: {path}' "$directory"
    elif [ -d "$directory" ]; then
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
    sh_body=$(
        cat <<'BODY'
for file in "$@"; do
    output=$(shellcheck "$file" 2>&1)
    if [ -n "$output" ]; then
        printf "%s\n" "$output"
        printf "%s\n" "$file"
    fi
done
BODY
    )
    violators=$(find . -type f -name '*.sh' ! -path './.git/*' -exec sh -c "$sh_body" sh {} +)
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
check_hook_command
check_ci_hook_command_parity
check_symlink_safety
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
