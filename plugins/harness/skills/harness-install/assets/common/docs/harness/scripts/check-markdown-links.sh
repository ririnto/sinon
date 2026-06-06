#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Return success when a Markdown file belongs to execution-plan state.
#
# @param path Repository-relative Markdown path.
# @return Returns 0 only for execution-plan files.
is_exec_plan_reference_exempt() {
    path=$1
    case "$path" in
        docs/exec-plans/*)
            return 0
            ;;
    esac
    return 1
}

# Return success when a line references the durable tech-debt tracker.
#
# @param line Markdown line content.
# @return Returns 0 for tech-debt tracker references.
is_tech_debt_tracker_reference() {
    line=$1
    if printf '%s\n' "$line" | grep -Eq '(^|[[(<`"[:space:]])(\.\./)*(docs/)?exec-plans/tech-debt-tracker\.md'; then
        return 0
    fi
    return 1
}

# Reject Markdown references to removable execution-plan files from durable docs.
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
        if [ ! -f "$file" ]; then
            continue
        fi
        if is_exec_plan_reference_exempt "$file"; then
            continue
        fi
        while IFS= read -r line; do
            if is_tech_debt_tracker_reference "$line"; then
                continue
            fi
            if printf '%s\n' "$line" | grep -Eq '(^|[[(<`"[:space:]])[0-9]{4}-[0-9]{2}-[0-9]{2}-[a-z0-9][a-z0-9-]*\.md([^a-zA-Z0-9_.-]|$)'; then
                printf '%s\n' "$file" >>"$failures_file"
                break
            fi
        done <"$file"
    done <"$markdown_file_list"
    if [ -s "$failures_file" ]; then
        echo 'error: Durable Markdown must not reference removable exec-plans/(active|completed)/ state files; link docs/exec-plans/tech-debt-tracker.md or durable design/product docs instead' >&2
        sort -u "$failures_file" >&2
        return 1
    fi
    echo 'markdown links: removable exec-plan references passed'
}

check_exec_plan_state_links "$@"
