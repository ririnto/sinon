#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Return success when a Markdown file may reference exec-plan state directories.
#
# @param path Repository-relative Markdown path.
# @return Returns 0 only for the tech-debt tracker exception.
is_exec_plan_reference_exempt() {
    path=$1
    if [ "$path" = "docs/exec-plans/tech-debt-tracker.md" ]; then
        return 0
    fi
    return 1
}

# Reject Markdown references to active/completed execution-plan state directories.
#
# @return Prints violations and returns 1 when disallowed references exist.
check_exec_plan_state_links() {
    failures_file=$(mktemp)
    markdown_file_list=$(mktemp)
    trap 'rm -f "$failures_file" "$markdown_file_list"' EXIT
    if ! git ls-files -- '*.md' >"$markdown_file_list"; then
        echo 'error: git ls-files failed while listing Markdown files' >&2
        return 1
    fi
    while IFS= read -r file; do
        if is_exec_plan_reference_exempt "$file"; then
            continue
        fi
        if grep -Eq '(^|[[(<`"[:space:]])(\.\./)*(docs/)?exec-plans/(active|completed)/' "$file"; then
            printf '%s\n' "$file" >>"$failures_file"
        fi
    done <"$markdown_file_list"
    if [ -s "$failures_file" ]; then
        echo 'error: Markdown must not reference docs/exec-plans/active/ or docs/exec-plans/completed/ outside docs/exec-plans/tech-debt-tracker.md' >&2
        sort -u "$failures_file" >&2
        return 1
    fi
    echo 'markdown links: exec-plan state references passed'
}

check_exec_plan_state_links "$@"
