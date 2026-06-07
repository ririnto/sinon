#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Reject Markdown references to removable execution-plan files from durable docs.
#     Durable docs outside docs/exec-plans/ MUST NOT link to dated exec-plan
#     state files. Link the durable tech-debt tracker or design/product docs
#     instead.
#
# @return Prints violations and returns 1 when disallowed references exist.
check_exec_plan_state_links() {
    grep_rc=0
    violations_file=$(mktemp)
    trap 'rm -f "$violations_file"' EXIT
    if git grep -En '(^|[[(<`"[:space:]])[0-9]{4}-[0-9]{2}-[0-9]{2}-[a-z0-9][a-z0-9-]*\.md([^a-zA-Z0-9_.-]|$)' -- '*.md' ':(exclude)docs/exec-plans/**' >"$violations_file"; then
        grep_rc=0
    else
        grep_rc=$?
        if [ "$grep_rc" -gt 1 ]; then
            echo 'error: git grep failed while checking Markdown links' >&2
            return 1
        fi
    fi
    if [ -s "$violations_file" ]; then
        remaining_file=$(mktemp)
        trap 'rm -f "$violations_file" "$remaining_file"' EXIT
        if grep -v 'tech-debt-tracker\.md' "$violations_file" >"$remaining_file"; then
            grep_rc=0
        else
            grep_rc=$?
            if [ "$grep_rc" -gt 1 ]; then
                echo 'error: grep failed while filtering tech-debt tracker references' >&2
                return 1
            fi
        fi
        if [ -s "$remaining_file" ]; then
            echo 'error: Durable Markdown must not reference removable exec-plans/(active|completed)/ state files; link docs/exec-plans/tech-debt-tracker.md or durable design/product docs instead' >&2
            cut -d: -f1 "$remaining_file" | sort -u >&2
            return 1
        fi
    fi
    echo 'markdown links: removable exec-plan references passed'
}

check_exec_plan_state_links "$@"
