#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Synchronize tracked Git hook templates into the active hooks directory.
#
# @return Copies pre-commit and pre-push into the Git hooks directory when content differs.
sync_git_hooks() {
    if ! inside_work_tree=$(git rev-parse --is-inside-work-tree 2>&1); then
        printf '%s\n' "$inside_work_tree" >&2
        return 0
    fi
    if [ "$inside_work_tree" != true ]; then
        return 0
    fi
    hooks_dir=$(git rev-parse --git-path hooks)
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

# Write Git-tracked JavaScript and TypeScript paths.
#
# @param tracked_file_list Destination file for null-delimited paths.
# @return Writes tracked source paths to tracked_file_list.
write_tracked_source_files() {
    tracked_file_list="$1"
    if ! git ls-files -z -- '*.js' '*.jsx' '*.mjs' '*.cjs' '*.ts' '*.tsx' >"$tracked_file_list"; then
        echo 'error: git ls-files failed while listing JavaScript and TypeScript files' >&2
        return 1
    fi
}

# Run ultracite against tracked JavaScript and TypeScript files.
#
# @return Exits with the lint status.
run_ultracite_check() {
    tracked_file_list=$(mktemp)
    failures_file=$(mktemp)
    trap 'rm -f "$tracked_file_list" "$failures_file"' EXIT
    write_tracked_source_files "$tracked_file_list"
    if [ ! -s "$tracked_file_list" ]; then
        echo 'ultracite: no tracked source files to check'
        return 0
    fi
    bun install --no-save
    if ! xargs -0 bunx ultracite check -- <"$tracked_file_list"; then
        echo 'ultracite' >"$failures_file"
    fi
    if [ -s "$failures_file" ]; then
        return 1
    fi
}

# Synchronize Git hooks, run markdown-link validation, then run Ultracite.
#
# @return Exits non-zero on the first failing validation stage.
main() {
    sync_git_hooks
    sh docs/harness/scripts/check-markdown-links.sh
    run_ultracite_check
}

main "$@"
