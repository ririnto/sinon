#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${CLAUDE_PLUGIN_ROOT:-$(CDPATH='' cd "$(dirname "$0")/../../.." && pwd)}

# Check shell files with shellcheck and shfmt.
#
# Runs shellcheck and shfmt on production shell scripts, collecting findings
# in structured diagnostic format and continuing on tool failure. Missing tools emit warnings.
#
# @return Accumulates error count.
check_shell_files() {
    error_count=0
    shell_files="$root/plugins/agent-capability-kit/skills/plugin-authoring/assets/hooks/check.sh"
    shell_files="$shell_files $root/plugins/harness/scripts/plugin-self-check.sh"
    shell_files="$shell_files $root/plugins/harness/scripts/harness-check.sh"
    shell_files="$shell_files $root/plugins/harness/scripts/harness-format.sh"
    shell_files="$shell_files $root/plugins/harness/skills/harness-install/scripts/install-harness.sh"
    shell_files="$shell_files $root/plugins/java/scripts/has-lombok.sh"
    shell_files="$shell_files $root/plugins/java/scripts/jdtls-wrapper.sh"
    shell_files="$shell_files $root/plugins/java/scripts/test-jdtls-wrapper.sh"
    for path in $shell_files; do
        if [ ! -f "$path" ]; then
            continue
        fi
        if ! shellcheck_bin=$(command -v shellcheck 2>&1); then
            printf 'warning: shellcheck not in PATH; skipping shellcheck for %s\n' "$path" >&2
        else
            shellcheck_output=$("$shellcheck_bin" "$path" 2>&1) && shellcheck_rc=0 || shellcheck_rc=$?
            if [ "$shellcheck_rc" -ne 0 ]; then
                printf '%s\n' "$shellcheck_output" >&2
                error_count=$((error_count + 1))
            fi
        fi
        if ! shfmt_bin=$(command -v shfmt 2>&1); then
            printf 'warning: shfmt not in PATH; skipping shfmt for %s\n' "$path" >&2
        else
            shfmt_diff=$("$shfmt_bin" -d -i 4 -ci "$path" 2>&1) && shfmt_rc=0 || shfmt_rc=$?
            if [ "$shfmt_rc" -ne 0 ]; then
                printf '%s\n' "$shfmt_diff" >&2
                error_count=$((error_count + 1))
            elif [ -n "$shfmt_diff" ]; then
                printf '%s\n' "$shfmt_diff" >&2
                error_count=$((error_count + 1))
            fi
        fi
    done
    printf '%d\n' "$error_count"
}

# Check Markdown files with markdownlint-cli2.
#
# Runs markdownlint-cli2 on all Markdown files, collecting findings in
# structured diagnostic format and continuing on tool failure. Missing tool emits warning.
#
# @return Accumulates error count.
check_markdown_files() {
    error_count=0
    if ! markdownlint_bin=$(command -v markdownlint-cli2 2>&1); then
        printf 'warning: markdownlint-cli2 not in PATH; skipping markdown linting\n' >&2
        printf '0\n'
        return
    fi
    lint_output=$("$markdownlint_bin" "**/*.md" "#node_modules" "#.git" "#.omo" 2>&1) && lint_rc=0 || lint_rc=$?
    if [ "$lint_rc" -ne 0 ]; then
        printf '%s\n' "$lint_output" >&2
        error_count=1
    fi
    printf '%d\n' "$error_count"
}

# Check Python files with ruff check and ruff format.
#
# Runs ruff check and ruff format on production Python scripts, collecting
# findings in structured diagnostic format and continuing on tool failure. Missing tools emit warnings.
#
# @return Accumulates error count.
check_python_files() {
    error_count=0
    python_files="$root/plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd.py"
    python_files="$python_files $root/plugins/agent-capability-kit/skills/plugin-authoring/assets/lsp/example-lsp.py"
    python_files="$python_files $root/plugins/agent-capability-kit/skills/plugin-authoring/assets/servers/example-mcp.py"
    for path in $python_files; do
        if [ ! -f "$path" ]; then
            continue
        fi
        if ! uv_bin=$(command -v uv 2>&1); then
            printf 'warning: uv not in PATH; skipping ruff checks for %s\n' "$path" >&2
        else
            check_output=$("$uv_bin" run --with ruff==0.15.14 ruff check "$path" 2>&1) && check_rc=0 || check_rc=$?
            if [ "$check_rc" -ne 0 ]; then
                printf '%s\n' "$check_output" >&2
                error_count=$((error_count + 1))
            fi
            format_check_output=$("$uv_bin" run --with ruff==0.15.14 ruff format --check "$path" 2>&1) && format_rc=0 || format_rc=$?
            if [ "$format_rc" -ne 0 ]; then
                printf '%s\n' "$format_check_output" >&2
                error_count=$((error_count + 1))
            fi
        fi
    done
    printf '%d\n' "$error_count"
}

shell_errors=$(check_shell_files)
python_errors=$(check_python_files)
markdown_errors=$(check_markdown_files)
error_count=$((shell_errors + python_errors + markdown_errors))
total_checked=$((11 + 519))
warn_count=0

printf 'Checked %d file(s). %d error(s), %d warn(s).\n' "$total_checked" "$error_count" "$warn_count"

if [ "$error_count" -gt 0 ]; then
    exit 1
fi
exit 0
