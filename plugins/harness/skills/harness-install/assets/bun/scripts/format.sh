#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

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

# Apply ultracite formatting and safe lint fixes.
#
# @return Exits with the ultracite fix status.
main() {
    tracked_file_list=$(mktemp)
    trap 'rm -f "$tracked_file_list"' EXIT
    write_tracked_source_files "$tracked_file_list"
    if [ ! -s "$tracked_file_list" ]; then
        echo 'ultracite: no tracked source files to format'
        return 0
    fi
    bun install --no-save
    xargs -0 bunx ultracite fix -- <"$tracked_file_list"
}

main "$@"
