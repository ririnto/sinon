#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${CLAUDE_PLUGIN_ROOT:-$(CDPATH='' cd "$(dirname "$0")/../../.." && pwd)}
changed_file=$(mktemp)
trap 'rm -f "$changed_file"' EXIT

# List production shell scripts covered by repository formatting.
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

# List production Python scripts covered by repository formatting.
#
# @return Writes one path per line.
list_python_files() {
    printf '%s\n' "$root/plugins/spec-driven-development/skills/spec-driven-development/scripts/sdd.py"
    printf '%s\n' "$root/plugins/agent-capability-kit/skills/plugin-authoring/assets/lsp/example-lsp.py"
    printf '%s\n' "$root/plugins/agent-capability-kit/skills/plugin-authoring/assets/servers/example-mcp.py"
    printf '%s\n' "$root/plugins/harness/skills/harness-install/assets/uv/scripts/check.py"
    printf '%s\n' "$root/plugins/harness/skills/harness-install/assets/uv/scripts/format.py"
}

# List Markdown files covered by repository formatting.
#
# @return Writes one path per line.
list_markdown_files() {
    find "$root" -type f -name '*.md' ! -path "$root/node_modules/*" ! -path "$root/.git/*" ! -path "$root/.omo/*" ! -path "$root/.claude/worktrees/*"
}

# Count changed files after checksum comparison.
#
# @param before_file Sorted checksum file captured before formatting.
# @param after_file Sorted checksum file captured after formatting.
# @return Writes changed file count.
count_changed_checksums() {
    before_file=$1
    after_file=$2
    changed_count=$(comm -13 "$before_file" "$after_file" | wc -l | tr -d ' ')
    printf '%s\n' "$changed_count"
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

# Record one changed path if not already recorded.
#
# @param path Path of changed file.
# @return Writes path into the changed-file cache.
record_changed_file() {
    path=$1
    if ! grep -Fxq "$path" "$changed_file"; then
        printf '%s\n' "$path" >>"$changed_file"
    fi
}

# Format shell files with shfmt.
#
# Writes formatted output in-place for all production shell scripts.
# Missing shfmt tool emits warning and skips.
#
# @return Writes changed file count and exits 0 or 1.
format_shell_files() {
    if ! shfmt_bin=$(command -v shfmt 2>&1); then
        echo 'warning: shfmt not in PATH; skipping shfmt formatting' >&2
        echo '0'
        return 0
    fi
    formatted_count=0
    shell_format_failed=0
    shell_file_list=$(mktemp)
    list_shell_files >"$shell_file_list"
    while IFS= read -r path; do
        if [ -f "$path" ]; then
            before_checksum=$(cksum "$path")
            if "$shfmt_bin" -i 4 -ci -w "$path" 2>&1; then
                after_checksum=$(cksum "$path")
                if [ "$before_checksum" != "$after_checksum" ]; then
                    formatted_count=$((formatted_count + 1))
                    record_changed_file "$path"
                fi
            else
                shell_format_failed=1
                printf 'error: shfmt failed on %s\n' "$path" >&2
                break
            fi
        fi
    done <"$shell_file_list"
    rm -f "$shell_file_list"
    if [ "$shell_format_failed" -ne 0 ]; then
        return 1
    fi
    printf '%d\n' "$formatted_count"
}
# Format Markdown files with markdownlint-cli2 --fix.
#
# Writes formatted output in-place for all Markdown files.
# Missing markdownlint-cli2 tool emits warning and skips.
#
# @return Writes changed file count and exits 0 or 1.
format_markdown_files() {
    if ! markdownlint_bin=$(command -v markdownlint-cli2 2>&1); then
        echo 'warning: markdownlint-cli2 not in PATH; skipping markdown formatting' >&2
        echo '0'
        return 0
    fi
    before_file=$(mktemp)
    after_file=$(mktemp)
    list_markdown_files | xargs cksum | sort >"$before_file"
    if (cd "$root" && "$markdownlint_bin" --fix "**/*.md" "#node_modules" "#.git" "#.omo" "#.claude/worktrees" >&2); then
        list_markdown_files | xargs cksum | sort >"$after_file"
        changed_count=$(count_changed_checksums "$before_file" "$after_file")
        if [ "$changed_count" -gt 0 ]; then
            changed_markdown_file=$(mktemp)
            comm -13 "$before_file" "$after_file" >"$changed_markdown_file"
            while IFS= read -r entry; do
                changed_path=${entry#* }
                changed_path=${changed_path#* }
                record_changed_file "$changed_path"
            done <"$changed_markdown_file"
            rm -f "$changed_markdown_file"
        fi
        rm "$before_file" "$after_file"
        printf '%s\n' "$changed_count"
        return 0
    else
        rm "$before_file" "$after_file"
        echo 'error: markdownlint-cli2 --fix failed' >&2
        return 1
    fi
}

# Format Python files with ruff format.
#
# Writes formatted output in-place for all production Python scripts.
# Missing uv tool emits warning and skips.
#
# @return Writes changed file count and exits 0 or 1.
format_python_files() {
    if ! uv_bin=$(command -v uv 2>&1); then
        echo 'warning: uv not in PATH; skipping ruff formatting' >&2
        echo '0'
        return 0
    fi
    formatted_count=0
    python_format_failed=0
    python_file_list=$(mktemp)
    list_python_files >"$python_file_list"
    while IFS= read -r path; do
        if [ -f "$path" ]; then
            before_checksum=$(cksum "$path")
            if "$uv_bin" run --with ruff==0.15.14 ruff format "$path" >&2; then
                after_checksum=$(cksum "$path")
                if [ "$before_checksum" != "$after_checksum" ]; then
                    formatted_count=$((formatted_count + 1))
                    record_changed_file "$path"
                fi
            else
                python_format_failed=1
                printf 'error: ruff format failed on %s\n' "$path" >&2
                break
            fi
        fi
    done <"$python_file_list"
    rm -f "$python_file_list"
    if [ "$python_format_failed" -ne 0 ]; then
        return 1
    fi
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

shell_checked=$(list_shell_files | count_existing_files)
python_checked=$(list_python_files | count_existing_files)
markdown_checked=$(list_markdown_files | count_existing_files)
shell_noop=$((shell_checked - shell_formatted))
python_noop=$((python_checked - python_formatted))
markdown_noop=$((markdown_checked - markdown_formatted))

if [ "$shell_noop" -lt 0 ]; then
    shell_noop=0
fi
if [ "$python_noop" -lt 0 ]; then
    python_noop=0
fi
if [ "$markdown_noop" -lt 0 ]; then
    markdown_noop=0
fi

if [ "$shell_formatted" -gt 0 ] || [ "$python_formatted" -gt 0 ] || [ "$markdown_formatted" -gt 0 ]; then
    total_formatted=$((shell_formatted + python_formatted + markdown_formatted))
    printf 'formatted: %d\n' "$total_formatted"
    if [ -s "$changed_file" ]; then
        sort "$changed_file" | while IFS= read -r changed_path; do
            printf '  %s\n' "$changed_path"
        done
    fi
else
    echo 'no files formatted'
fi

printf 'no-op: %d shell file(s), %d python file(s), %d markdown file(s).\n' "$shell_noop" "$python_noop" "$markdown_noop"
echo 'remaining findings after format:'
if sh "$root/plugins/harness/scripts/harness-check.sh"; then
    check_status=0
else
    check_status=$?
fi
exit "$check_status"
