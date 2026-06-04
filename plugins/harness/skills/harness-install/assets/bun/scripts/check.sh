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
        if [ -f "$dst" ] && cmp -s "$src" "$dst"; then
            continue
        fi
        tmp=$hooks_dir/.sync-git-hooks-$$-$name
        cp "$src" "$tmp"
        chmod +x "$tmp"
        mv "$tmp" "$dst"
    done
}

# Synchronize Git hooks to the user environment, then run the native linter.
#
# @return Exits with the ultracite check status.
main() {
    sync_git_hooks || true
    bunx ultracite check
}

main "$@"
