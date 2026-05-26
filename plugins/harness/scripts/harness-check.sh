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
            shellcheck_output=$("$shellcheck_bin" "$path" 2>&1 || true)
            if [ -n "$shellcheck_output" ]; then
                printf '%s\n' "$shellcheck_output" >&2
                error_count=$((error_count + 1))
            fi
        fi
        if ! shfmt_bin=$(command -v shfmt 2>&1); then
            printf 'warning: shfmt not in PATH; skipping shfmt for %s\n' "$path" >&2
        else
            shfmt_diff=$("$shfmt_bin" -d -i 4 -ci "$path" 2>&1 || true)
            if [ -n "$shfmt_diff" ]; then
                printf '%s\n' "$shfmt_diff" >&2
                error_count=$((error_count + 1))
            fi
        fi
    done
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
            check_output=$("$uv_bin" run --with ruff==0.15.14 ruff check "$path" 2>&1 || true)
            if [ -n "$check_output" ]; then
                printf '%s\n' "$check_output" >&2
                error_count=$((error_count + 1))
            fi
            format_check_output=$("$uv_bin" run --with ruff==0.15.14 ruff format --check "$path" 2>&1 || true)
            if [ -n "$format_check_output" ]; then
                printf '%s\n' "$format_check_output" >&2
                error_count=$((error_count + 1))
            fi
        fi
    done
    printf '%d\n' "$error_count"
}

shell_errors=$(check_shell_files)
python_errors=$(check_python_files)
error_count=$((shell_errors + python_errors))
total_checked=11
warn_count=0

printf 'Checked %d file(s). %d error(s), %d warn(s).\n' "$total_checked" "$error_count" "$warn_count"

if [ "$error_count" -gt 0 ]; then
    exit 1
fi
exit 0
