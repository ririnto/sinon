#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${root:?}

# Return a path relative to the plugin root.
#
# @param path File or directory path to normalize.
# @return Prints the plugin-root-relative path.
relative_to_root() {
    path=$1
    case "$path" in
        "$root"/*) printf '%s\n' "${path#"$root"/}" ;;
        *) printf '%s\n' "$path" ;;
    esac
}

# Require a regular file in the plugin package.
#
# @param path File path to check.
# @exit Exits with status 1 when file is missing.
require_file() {
    path=$1
    if [ -f "$path" ]; then
        return 0
    fi
    relative_path=$(relative_to_root "$path")
    if ! tracked_file=$(git -C "$root" ls-files --error-unmatch -- "$relative_path" 2>&1); then
        printf '%s\n' "[require_file] missing required file: $path" >&2
        printf '%s\n' "  hint: ensure the file is committed and at the expected path" >&2
        exit 1
    fi
    if [ -z "$tracked_file" ]; then
        printf '%s\n' "[require_file] missing required file: $path" >&2
        printf '%s\n' "  hint: ensure the file is committed and at the expected path" >&2
        exit 1
    fi
}

# Require a directory in the plugin package.
#
# @param path Directory path to check.
# @exit Exits with status 1 when directory is missing.
require_dir() {
    path=$1
    relative_path=$(relative_to_root "$path")
    tracked_entries=$(git -C "$root" ls-files -- "$relative_path")
    if [ -z "$tracked_entries" ]; then
        printf '%s\n' "[require_dir] missing required directory: $path" >&2
        printf '%s\n' "  hint: ensure the directory is created and committed" >&2
        exit 1
    fi
}

# Require a file to contain a fixed string.
#
# @param path File path to search.
# @param text Fixed string to require.
# @exit Exits with status 1 when text is not found.
require_text() {
    path=$1
    text=$2
    if ! grep -Fq -- "$text" "$path"; then
        printf '%s\n' "[require_text] missing required text in $path: $text" >&2
        exit 1
    fi
}

# Reject any occurrence of forbidden text in a file or directory tree.
#
# @param path File or directory path to search recursively.
# @param text Fixed string to reject.
# @exit Exits with status 1 when text is found.
reject_file_text() {
    path=$1
    text=$2
    matches_file=$(mktemp)
    if [ -f "$path" ]; then
        if grep -Fq -- "$text" "$path"; then
            printf '%s\n' "$path" >"$matches_file"
        fi
    else
        git -C "$root" ls-files -- "$path" | while IFS= read -r file; do
            if [ ! -f "$root/$file" ]; then
                continue
            fi
            if grep -Fq -- "$text" "$root/$file"; then
                printf '%s\n' "$file" >>"$matches_file"
            fi
        done
    fi
    if [ -s "$matches_file" ]; then
        printf '%s\n' "[reject_file_text] forbidden text in $path: $text" >&2
        cat "$matches_file" >&2
        rm -f "$matches_file"
        exit 1
    fi
    rm -f "$matches_file"
}

# Require a file to NOT exist in the plugin package.
#
# @param path File path to check.
# @exit Exits with status 1 when file exists.
reject_file() {
    path=$1
    relative_path=$(relative_to_root "$path")
    if tracked_file=$(git -C "$root" ls-files --error-unmatch -- "$relative_path" 2>&1); then
        if [ -z "$tracked_file" ]; then
            return 0
        fi
        printf '%s\n' "[reject_file] file must not exist: $path" >&2
        exit 1
    fi
}

# Require a file to NOT contain a fixed string.
#
# @param path File path to search.
# @param text Fixed string to reject.
# @exit Exits with status 1 when text is found.
reject_file_contains() {
    path=$1
    text=$2
    if grep -Fq -- "$text" "$path"; then
        printf '%s\n' "[reject_file_contains] forbidden text in $path: $text" >&2
        exit 1
    fi
}
# Reject text in a file by extended regular expression.
#
# @param path File path to search.
# @param pattern Regular expression to reject.
# @exit Exits with status 1 when pattern is found.
reject_file_regex() {
    path=$1
    pattern=$2
    matches_file=$(mktemp)
    if [ -f "$path" ]; then
        if grep -Eq -- "$pattern" "$path"; then
            grep -En -- "$pattern" "$path" >"$matches_file"
        fi
    else
        relative_path=$(relative_to_root "$path")
        git -C "$root" ls-files -- "$relative_path" | while IFS= read -r file; do
            if [ ! -f "$root/$file" ]; then
                continue
            fi
            if grep -Eq -- "$pattern" "$root/$file"; then
                grep -En -- "$pattern" "$root/$file" >>"$matches_file"
            fi
        done
    fi
    if [ -s "$matches_file" ]; then
        printf '[reject_file_regex] forbidden pattern in %s: %s\n' "$path" "$pattern" >&2
        cat "$matches_file" >&2
        rm -f "$matches_file"
        exit 1
    fi
    rm -f "$matches_file"
}

# Enforce installer script style hardening contracts.
#
# @param installer_script Path to Python installer script or package directory.
# @param bun_plugin_script Path to Bun oxlint JS plugin script.
# @exit Exits with status 1 when style contracts are violated.
assert_code_style_contracts() {
    installer_script=$1
    bun_plugin_script=$2
    reject_file_regex "$installer_script" '^[[:space:]]*(def|class)[[:space:]]+_([A-Za-z0-9][A-Za-z0-9_]*)'
    reject_file_regex "$installer_script" '^[[:space:]]*_([A-Za-z0-9][A-Za-z0-9_]*)[[:space:]]*='
    reject_file_regex "$bun_plugin_script" '^[[:space:]]*(const|let|var|function|class)[[:space:]]+_([A-Za-z0-9][A-Za-z0-9_]*)'
    reject_file_regex "$installer_script" '^[[:space:]]*"""[^"\n]+"""$'
    reject_file_text "$installer_script" "SKIP_TREE_PARTS"
    reject_file_text "$installer_script" "is_generated_or_ignored_path"
    if [ -f "$installer_script" ]; then
        python3 -m py_compile "$installer_script"
        return 0
    fi
    relative_path=$(relative_to_root "$installer_script")
    git -C "$root" ls-files -- "$relative_path" | while IFS= read -r file; do
        case "$file" in
            *.py) python3 -m py_compile "$root/$file" ;;
        esac
    done
}
