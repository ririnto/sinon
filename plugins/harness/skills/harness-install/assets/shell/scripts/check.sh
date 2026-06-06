#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Synchronize tracked Git hook templates into the active hooks directory.
#
# @return Copies pre-commit and pre-push into the Git hooks directory when content differs.
sync_git_hooks() {
    if ! hooks_dir=$(git rev-parse --git-path hooks 2>&1); then
        return 0
    fi
    if [ ! -d "$hooks_dir" ]; then
        mkdir -p "$hooks_dir"
    fi
    for name in pre-commit pre-push; do
        src=docs/harness/git-hooks/$name
        if [ ! -f "$src" ]; then
            continue
        fi
        dst=$hooks_dir/$name
        if [ -L "$dst" ]; then
            continue
        fi
        if [ -f "$dst" ] && cmp -s "$src" "$dst"; then
            continue
        fi
        tmp=$hooks_dir/.sync-git-hooks-$$-$name
        cp "$src" "$tmp"
        chmod +x "$tmp"
        mv "$tmp" "$dst"
    done
}

# Write Git-tracked shell scripts.
#
# @param shell_file_list Destination file for null-delimited paths.
# @return Writes tracked shell paths to shell_file_list.
write_tracked_shell_files() {
    shell_file_list="$1"
    if ! git ls-files -z -- '*.sh' >"$shell_file_list"; then
        echo 'error: git ls-files failed while listing shell files' >&2
        return 1
    fi
}

# Synchronize Git hooks, then validate tracked shell scripts using shellcheck and shfmt.
#
# @return Exits with 0 when all scripts pass lint and format checks, 1 on violations.
main() {
    sync_git_hooks
    sh docs/harness/scripts/check-markdown-links.sh
    failures_file=$(mktemp)
    shell_file_list=$(mktemp)
    trap 'rm -f "$failures_file" "$shell_file_list"' EXIT
    write_tracked_shell_files "$shell_file_list"
    if [ ! -s "$shell_file_list" ]; then
        echo 'shellcheck: no tracked shell files to check'
        return 0
    fi
    if ! xargs -0 shellcheck -S warning -- <"$shell_file_list" 2>&1; then
        echo 'shellcheck' >"$failures_file"
    fi
    if ! xargs -0 shfmt -d -i 4 -ci -- <"$shell_file_list" 2>&1; then
        echo 'shfmt' >"$failures_file"
    fi
    if [ -s "$failures_file" ]; then
        exit 1
    fi
    echo 'shellcheck and shfmt: all scripts passed'
}

main "$@"
