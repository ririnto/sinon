#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

MANIFEST=docs/harness/manifest.json
script_dir=$(CDPATH='' cd "$(dirname "$0")" && pwd)
changed_file=$(mktemp)
trap 'rm -f "$changed_file"' EXIT

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
value = eval(expr)
if not isinstance(value, list):
    raise SystemExit(1)
for item in value:
    if not isinstance(item, str):
        raise SystemExit(1)
    print(item)
PYEOF
}

# Read a manifest list field into a file.
# Return non-zero when the query returns invalid parameters.
#
# @param category Add-on category name.
# @param expr Python expression evaluating to an iterable of strings.
# @param output_file Temporary file path to write values into.
# @return Returns 0 when values were written.
manifest_query_file() {
    query_category=$1
    query_expr=$2
    query_output_file=$3
    if manifest_query "$query_expr" >"$query_output_file"; then
        return 0
    fi
    printf '%s\n' "[harness-format] invalid $query_category parameters" >&2
    return 1
}

# Return a manifest list field file for an enabled category.
#
# @param category Add-on category name.
# @param expr Python expression evaluating to an iterable of strings.
# @return Writes the temp file path when lookup succeeds.
manifest_query_required() {
    required_category=$1
    required_expr=$2
    required_file=$(mktemp)
    if manifest_query_file "$required_category" "$required_expr" "$required_file"; then
        printf '%s\n' "$required_file"
        return 0
    fi
    rm -f "$required_file"
    return 1
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

# Resolve whether an add-on is enabled (default true).
#
# @param category Add-on category name.
# @return Writes 1 when enabled, 0 otherwise.
enabled_of() {
    enabled_category=$1
    manifest_string "1 if M.get('$enabled_category', {}).get('enabled', True) else 0"
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

# Return whether a manifest path is safe for runtime writes.
#
# @param path Manifest-controlled path to check.
# @return Returns 0 when safe.
is_safe_manifest_path_for_write() {
    path=$1
    if ! is_safe_manifest_path "$path"; then
        return 1
    fi
    if ! is_safe_manifest_path_symlinks "$path"; then
        return 1
    fi
    return 0
}

# Record a changed path once.
#
# @param path Relative path that changed.
# @return Appends the path to the change file.
record_changed() {
    changed_path=$1
    if ! grep -Fxq "$changed_path" "$changed_file"; then
        printf '%s\n' "$changed_path" >>"$changed_file"
    fi
}

# Format all shell scripts under the current directory using shfmt.
#
# @return Records changed shell script paths.
format_sh_files() {
    if ! shfmt_bin=$(command -v shfmt 2>&1); then
        printf 'warning: shfmt not in PATH; skipping shfmt formatting\n' >&2
        return 0
    fi
    shell_format_failed=0
    shell_file_list=$(mktemp)
    find . \( -path './.git' -o -path './.claude/worktrees' \) -prune -o -type f -name '*.sh' -print >"$shell_file_list"
    while IFS= read -r file; do
        before=$(cksum "$file")
        if ! "$shfmt_bin" -i 4 -ci -w "$file"; then
            shell_format_failed=1
            printf 'error: shfmt failed on %s\n' "$file" >&2
            break
        fi
        after=$(cksum "$file")
        if [ "$before" != "$after" ]; then
            record_changed "${file#./}"
        fi
    done <"$shell_file_list"
    rm -f "$shell_file_list"
    if [ "$shell_format_failed" -ne 0 ]; then
        return 1
    fi
}

# Create .gitkeep placeholders for configured empty directories.
#
# @return Records each created placeholder.
format_empty_directory_placeholders() {
    category=emptyDirectoryPlaceholders
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    directories_file=$(manifest_query_required "$category" "M['$category']['parameters']['directories']") || return 1
    while IFS= read -r directory; do
        if ! is_safe_manifest_path_for_write "$directory"; then
            continue
        fi
        if [ -d "$directory" ] && [ -z "$(find "$directory" -mindepth 1 -print -quit)" ]; then
            touch "$directory/.gitkeep"
            record_changed "$directory/.gitkeep"
        fi
    done <"$directories_file"
    rm -f "$directories_file"
}

# Repair configured hook shebang lines.
#
# @return Records each hook whose shebang changed.
format_hook_shebangs() {
    category=hookShebang
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    expected=$(manifest_string "M['$category']['parameters']['expectedShebang']")
    if [ -z "$expected" ]; then
        return 0
    fi
    hooks_file=$(manifest_query_required "$category" "M['$category']['parameters']['hooks']") || return 1
    while IFS= read -r hook; do
        if ! is_safe_manifest_path_for_write "$hook" || [ ! -f "$hook" ]; then
            continue
        fi
        first=$(sed -n '1p' "$hook")
        if [ "$first" != "$expected" ]; then
            temp_file=$(mktemp)
            printf '%s\n' "$expected" >"$temp_file"
            sed '1{/^#!/d;}' "$hook" >>"$temp_file"
            mv "$temp_file" "$hook"
            record_changed "$hook"
        fi
    done <"$hooks_file"
    rm -f "$hooks_file"
}

# Mark configured hook scripts executable.
#
# @return Records each hook whose executable bit changed.
format_hook_executable() {
    category=hookExecutable
    enabled=$(enabled_of "$category")
    if [ "$enabled" -ne 1 ]; then
        return 0
    fi
    hooks_file=$(manifest_query_required "$category" "M['$category']['parameters']['hooks']") || return 1
    while IFS= read -r hook; do
        if ! is_safe_manifest_path_for_write "$hook" || [ ! -f "$hook" ]; then
            continue
        fi
        if [ ! -x "$hook" ]; then
            chmod +x "$hook"
            record_changed "$hook"
        fi
    done <"$hooks_file"
    rm -f "$hooks_file"
}

# Print changed paths or no-op status.
#
# @return Writes formatter summary.
print_format_summary() {
    count=$(wc -l <"$changed_file" | tr -d ' ')
    if [ "$count" -gt 0 ]; then
        printf 'formatted: %s\n' "$count"
        sort "$changed_file" | while IFS= read -r path; do
            printf '  %s\n' "$path"
        done
    else
        printf '%s\n' 'no files formatted'
    fi
}

# Run the shell harness validation surface after formatting.
#
# @return Exits with the harness-check status.
check_after_format() {
    printf '%s\n' 'remaining findings after format:'
    sh "$script_dir/harness-check.sh"
}

format_sh_files
format_empty_directory_placeholders
format_hook_shebangs
format_hook_executable
print_format_summary
check_after_format
