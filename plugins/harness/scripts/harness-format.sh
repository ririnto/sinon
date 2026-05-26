#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${CLAUDE_PLUGIN_ROOT:-$(CDPATH='' cd "$(dirname "$0")/../../.." && pwd)}

# Format shell files with shfmt.
#
# Writes formatted output in-place for all production shell scripts.
# Missing shfmt tool emits warning and skips.
#
# @return Writes count and exits 0 or 1.
format_shell_files() {
    shell_files="$root/plugins/agent-capability-kit/skills/plugin-authoring/assets/hooks/check.sh"
    shell_files="$shell_files $root/plugins/harness/scripts/plugin-self-check.sh"
    shell_files="$shell_files $root/plugins/harness/scripts/harness-check.sh"
    shell_files="$shell_files $root/plugins/harness/scripts/harness-format.sh"
    shell_files="$shell_files $root/plugins/harness/skills/harness-install/scripts/install-harness.sh"
    shell_files="$shell_files $root/plugins/java/scripts/has-lombok.sh"
    shell_files="$shell_files $root/plugins/java/scripts/jdtls-wrapper.sh"
    shell_files="$shell_files $root/plugins/java/scripts/test-jdtls-wrapper.sh"
    if ! shfmt_bin=$(command -v shfmt 2>&1); then
        printf 'warning: shfmt not in PATH; skipping shfmt formatting\n' >&2
        printf '0\n'
        return 0
    fi
    formatted_count=0
    for path in $shell_files; do
        if [ ! -f "$path" ]; then
            continue
        fi
        if "$shfmt_bin" -i 4 -ci -w "$path" 2>&1; then
            formatted_count=$((formatted_count + 1))
        else
            printf 'error: shfmt failed on %s\n' "$path" >&2
            return 1
        fi
    done
    printf '%d\n' "$formatted_count"
}

# Format Markdown files with markdownlint-cli2 --fix.
#
# Writes formatted output in-place for all Markdown files.
# Missing markdownlint-cli2 tool emits warning and skips.
#
# @return Writes count and exits 0 or 1.
format_markdown_files() {
    if ! markdownlint_bin=$(command -v markdownlint-cli2 2>&1); then
        printf 'warning: markdownlint-cli2 not in PATH; skipping markdown formatting\n' >&2
        printf '0\n'
        return 0
    fi
    if "$markdownlint_bin" --fix "**/*.md" "#node_modules" "#.git" "#.omo" 2>&1; then
        printf '519\n'
        return 0
    else
        printf 'error: markdownlint-cli2 --fix failed\n' >&2
        return 1
    fi
}

# Format Python files with ruff format.
#
# Writes formatted output in-place for all production Python scripts.
# Missing uv tool emits warning and skips.
#
# @return Writes count and exits 0 or 1.
format_python_files() {
    python_files="$root/plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd.py"
    python_files="$python_files $root/plugins/agent-capability-kit/skills/plugin-authoring/assets/lsp/example-lsp.py"
    python_files="$python_files $root/plugins/agent-capability-kit/skills/plugin-authoring/assets/servers/example-mcp.py"
    if ! uv_bin=$(command -v uv 2>&1); then
        printf 'warning: uv not in PATH; skipping ruff formatting\n' >&2
        printf '0\n'
        return 0
    fi
    formatted_count=0
    for path in $python_files; do
        if [ ! -f "$path" ]; then
            continue
        fi
        if "$uv_bin" run --with ruff==0.15.14 ruff format "$path" 2>&1; then
            formatted_count=$((formatted_count + 1))
        else
            printf 'error: ruff format failed on %s\n' "$path" >&2
            return 1
        fi
    done
    printf '%d\n' "$formatted_count"
}

if ! shell_formatted=$(format_shell_files); then
    exit 1
fi

if ! python_formatted=$(format_python_files); then
    exit 1
fi

if ! markdown_formatted=$(format_markdown_files); then
    exit 1
fi

printf 'Formatted %d shell file(s), %d python file(s), and %d markdown file(s).\n' "$shell_formatted" "$python_formatted" "$markdown_formatted"
exit 0
