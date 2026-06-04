#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${CLAUDE_PLUGIN_ROOT:-$(CDPATH='' cd "$(dirname "$0")/../../.." && pwd)}
warn_counter_file=$(mktemp)
echo '0' >"$warn_counter_file"
# Increment the shared warning counter by one.
#
# @return Updates the warn counter file.
warn_count_increment() {
    current=$(cat "$warn_counter_file")
    current=$((current + 1))
    printf '%d\n' "$current" >"$warn_counter_file"
}

# List production shell scripts covered by repository checks.
#
# @return Writes one path per line.
list_shell_files() {
    printf '%s\n' "$root/plugins/agent-capability-kit/skills/plugin-authoring/assets/hooks/check.sh"
    printf '%s\n' "$root/plugins/agent-capability-kit/skills/plugin-authoring/assets/monitors/watch.sh"
    printf '%s\n' "$root/plugins/harness/scripts/plugin-self-check.sh"
    printf '%s\n' "$root/plugins/harness/scripts/harness-check.sh"
    printf '%s\n' "$root/plugins/harness/scripts/harness-format.sh"
    printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install-harness.sh"
    printf '%s\n' "$root/plugins/harness/skills/harness-install/assets/shell/scripts/check.sh"
    printf '%s\n' "$root/plugins/harness/skills/harness-install/assets/shell/scripts/format.sh"
    printf '%s\n' "$root/plugins/java/scripts/has-lombok.sh"
    printf '%s\n' "$root/plugins/java/scripts/jdtls-wrapper.sh"
    printf '%s\n' "$root/plugins/java/scripts/test-jdtls-wrapper.sh"
}

# List production Python scripts covered by repository checks.
#
# @return Writes one path per line.
list_python_files() {
    printf '%s\n' "$root/plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd.py"
    printf '%s\n' "$root/plugins/agent-capability-kit/skills/plugin-authoring/assets/lsp/example-lsp.py"
    printf '%s\n' "$root/plugins/agent-capability-kit/skills/plugin-authoring/assets/servers/example-mcp.py"
    printf '%s\n' "$root/plugins/harness/skills/harness-install/assets/uv/scripts/check.py"
    printf '%s\n' "$root/plugins/harness/skills/harness-install/assets/uv/scripts/format.py"
}

# List Markdown files covered by repository checks.
#
# @return Writes one path per line.
list_markdown_files() {
    find "$root" -type f -name '*.md' ! -path "$root/node_modules/*" ! -path "$root/.git/*" ! -path "$root/.omo/*" ! -path "$root/.claude/worktrees/*"
}

# Count listed paths that exist as files.
#
# @return Writes existing file count.
count_existing_files() {
    existing_count=0
    while IFS= read -r path; do
        if [ -f "$path" ]; then
            existing_count=$((existing_count + 1))
        fi
    done
    printf '%d\n' "$existing_count"
}

# Check shell files with shellcheck and shfmt.
#
# Runs shellcheck and shfmt on production shell scripts, collecting findings
# in structured diagnostic format and continuing on tool failure. Missing tools emit warnings.
#
# @return Accumulates error count.
check_shell_files() {
    error_count=0
    shellcheck_tool_checked=0
    shfmt_tool_checked=0
    for path in $(list_shell_files); do
        if [ ! -f "$path" ]; then
            continue
        fi
        if [ "$shellcheck_tool_checked" -eq 0 ]; then
            if ! shellcheck_bin=$(command -v shellcheck 2>&1); then
                printf 'warning: shellcheck not in PATH; skipping shellcheck for %s\n' "$path" >&2
                warn_count_increment
                shellcheck_bin=
            fi
            shellcheck_tool_checked=1
        fi
        if [ -n "$shellcheck_bin" ]; then
            shellcheck_output=$("$shellcheck_bin" "$path" 2>&1) && shellcheck_rc=0 || shellcheck_rc=$?
            if [ "$shellcheck_rc" -ne 0 ]; then
                printf '%s\n' "$shellcheck_output" >&2
                error_count=$((error_count + 1))
            fi
        fi
        if [ "$shfmt_tool_checked" -eq 0 ]; then
            if ! shfmt_bin=$(command -v shfmt 2>&1); then
                printf 'warning: shfmt not in PATH; skipping shfmt for %s\n' "$path" >&2
                warn_count_increment
                shfmt_bin=
            fi
            shfmt_tool_checked=1
        fi
        if [ -n "$shfmt_bin" ]; then
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
        echo 'warning: markdownlint-cli2 not in PATH; skipping markdown linting' >&2
        warn_count_increment
        echo '0'
        return
    fi
    lint_output=$(cd "$root" && "$markdownlint_bin" "**/*.md" "#node_modules" "#.git" "#.omo" "#.claude/worktrees" 2>&1) && lint_rc=0 || lint_rc=$?
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
    uv_tool_checked=0
    for path in $(list_python_files); do
        if [ ! -f "$path" ]; then
            continue
        fi
        if [ "$uv_tool_checked" -eq 0 ]; then
            if ! uv_bin=$(command -v uv 2>&1); then
                printf 'warning: uv not in PATH; skipping ruff checks for %s\n' "$path" >&2
                warn_count_increment
                uv_bin=
            fi
            uv_tool_checked=1
        fi
        if [ -n "$uv_bin" ]; then
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
warn_count=$(cat "$warn_counter_file")
rm -f "$warn_counter_file"
error_count=$((shell_errors + python_errors + markdown_errors))
shell_checked=$(list_shell_files | count_existing_files)
python_checked=$(list_python_files | count_existing_files)
markdown_checked=$(list_markdown_files | count_existing_files)
total_checked=$((shell_checked + python_checked + markdown_checked))

printf 'Checked %d file(s). %d error(s), %d warn(s).\n' "$total_checked" "$error_count" "$warn_count"

if [ "$error_count" -gt 0 ]; then
    exit 1
fi
exit 0
