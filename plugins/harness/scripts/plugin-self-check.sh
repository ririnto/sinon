#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${CLAUDE_PLUGIN_ROOT:-$(CDPATH='' cd "$(dirname "$0")/.." && pwd)}

# Require a regular file in the plugin package.
#
# @param path File path to check.
# @exit Exits with status 1 when file is missing.
require_file() {
    path=$1
    if [ ! -f "$path" ]; then
        printf '%s\n' "[require_file] missing required file: $path" >&2
        printf '%s\n' "  hint: ensure the file is committed and at the expected path" >&2
        exit 1
    fi
}

# Require a directory in the plugin package.
#
# @param path Directory path to check.
# @exit Exits with status 1 when directory is missing.
require_dir() {
    path=$1
    if [ ! -d "$path" ]; then
        printf '%s\n' "[require_dir] missing required directory: $path" >&2
        printf '%s\n' "  hint: ensure the directory is created and committed" >&2
        exit 1
    fi
}

# Require a file to contain a fixed string.
#
# @param path File path to search.
# @param text Fixed string to require.
# @exit Exits with status 1 when text is not found.
require_text() {
    path=$1
    text=$2
    if ! grep -Fq -- "$text" "$path"; then
        printf '%s\n' "[require_text] missing required text in $path: $text" >&2
        exit 1
    fi
}

# Reject any occurrence of forbidden text in a file or directory tree.
#
# @param path File or directory path to search recursively.
# @param text Fixed string to reject.
# @exit Exits with status 1 when text is found.
reject_file_text() {
    path=$1
    text=$2
    if grep -rFq -- "$text" "$path"; then
        printf '%s\n' "[reject_file_text] forbidden text in $path: $text" >&2
        exit 1
    fi
}

# Require a file to NOT exist in the plugin package.
#
# @param path File path to check.
# @exit Exits with status 1 when file exists.
reject_file() {
    path=$1
    if [ -f "$path" ]; then
        printf '%s\n' "[reject_file] file must not exist: $path" >&2
        exit 1
    fi
}

# Require a file to NOT contain a fixed string.
#
# @param path File path to search.
# @param text Fixed string to reject.
# @exit Exits with status 1 when text is found.
reject_file_contains() {
    path=$1
    text=$2
    if grep -Fq -- "$text" "$path"; then
        printf '%s\n' "[reject_file_contains] forbidden text in $path: $text" >&2
        exit 1
    fi
}

# Require gradle buildSrc assets exist.
assert_gradle_assets() {
    assets_root=$root/skills/harness-install/assets/gradle
    require_file "$assets_root/build.gradle.kts"
    require_file "$assets_root/settings.gradle.kts"
    require_file "$assets_root/buildSrc/build.gradle.kts"
    require_dir "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint"
    require_file "$assets_root/buildSrc/src/main/resources/META-INF/services/com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3"
    require_text "$assets_root/build.gradle.kts" 'docs/harness/git-hooks'
    printf '[gradle assets] OK\n' >&2
}

# Require bun assets exist.
assert_bun_assets() {
    assets_root=$root/skills/harness-install/assets/bun
    require_file "$assets_root/package.json"
    require_file "$assets_root/oxlint.config.ts"
    require_file "$assets_root/oxfmt.config.ts"
    require_file "$assets_root/scripts/check.sh"
    require_file "$assets_root/scripts/format.sh"
    require_text "$assets_root/package.json" '"ultracite"'
    require_text "$assets_root/oxlint.config.ts" 'ultracite/oxlint/core'
    require_text "$assets_root/oxfmt.config.ts" 'ultracite/oxfmt'
    require_text "$assets_root/oxfmt.config.ts" 'ignorePatterns'
    require_text "$assets_root/scripts/check.sh" 'bunx ultracite check'
    require_text "$assets_root/scripts/format.sh" 'bunx ultracite fix'
    reject_file "$assets_root/.oxlintrc.json"
    reject_file "$assets_root/.oxfmtrc.json"
    reject_file "$assets_root/scripts/plugin.mjs"
    reject_file_contains "$assets_root/scripts/check.sh" 'bunx oxlint'
    printf '[bun assets] OK\n' >&2
}

# Require uv assets exist.
assert_uv_assets() {
    assets_root=$root/skills/harness-install/assets/uv
    require_file "$assets_root/ruff.toml"
    require_file "$assets_root/scripts/check.py"
    require_file "$assets_root/scripts/format.py"
    require_text "$assets_root/scripts/check.py" '--git-path'
    printf '[uv assets] OK\n' >&2
}

# Require maven assets exist.
assert_maven_assets() {
    assets_root=$root/skills/harness-install/assets/maven
    require_file "$assets_root/pom.xml"
    require_text "$assets_root/pom.xml" 'sync-git-hooks'
    printf '[maven assets] OK\n' >&2
}

# Require shell assets exist.
assert_shell_assets() {
    assets_root=$root/skills/harness-install/assets/shell
    require_file "$assets_root/scripts/check.sh"
    require_file "$assets_root/scripts/format.sh"
    require_file "$assets_root/.shellcheckrc"
    require_text "$assets_root/scripts/check.sh" 'git rev-parse --git-path hooks'
    printf '[shell assets] OK\n' >&2
}

# Require CI workflow file contains correct check command.
#
# @param workflow_file Path to GitHub workflow file.
# @param expected_command Expected validation command.
assert_github_workflow_command() {
    workflow_file=$1
    expected_command=$2
    require_text "$workflow_file" "run: $expected_command"
    printf '[GitHub workflow %s] command OK\n' "$(basename "$workflow_file")" >&2
}

# Require GitLab CI file contains correct check command and job name.
#
# @param ci_file Path to .gitlab-ci.yml file.
# @param expected_job_name Expected job name.
# @param expected_command Expected validation command.
assert_gitlab_ci_command() {
    ci_file=$1
    expected_job_name=$2
    expected_command=$3
    require_text "$ci_file" "$expected_job_name:"
    require_text "$ci_file" "- $expected_command"
    printf '[GitLab CI] %s job command OK\n' "$expected_job_name" >&2
}

# Smoke-check native linters.
# Gracefully skips if tool is unavailable.
smoke_test_tool() {
    tool_name=$1
    tool_command=$2
    if ! command -v "$tool_name" >/dev/null 2>&1; then
        printf 'note: %s not in PATH; skipping smoke test\n' "$tool_name" >&2
        return 0
    fi
    if eval "$tool_command"; then
        printf '[smoke test] %s OK\n' "$tool_name" >&2
        return 0
    fi
    return 0
}

printf 'Validating harness plugin native-lint end-state...\n' >&2

# Validate gradle assets and CI.
printf '\n--- gradle stack ---\n' >&2
assert_gradle_assets
gradle_assets=$root/skills/harness-install/assets/gradle
if [ -f "$gradle_assets/.github/workflows/ktlint.yaml" ]; then
    assert_github_workflow_command "$gradle_assets/.github/workflows/ktlint.yaml" "./gradlew ktlintCheck"
fi
if [ -f "$gradle_assets/.gitlab-ci.yml" ]; then
    assert_gitlab_ci_command "$gradle_assets/.gitlab-ci.yml" "ktlint" "./gradlew ktlintCheck"
fi
reject_file_text "$gradle_assets" "harness-check"
reject_file_text "$gradle_assets" "manifest.json"

# Validate maven assets and CI.
printf '\n--- maven stack ---\n' >&2
assert_maven_assets
maven_assets=$root/skills/harness-install/assets/maven
if [ -f "$maven_assets/.github/workflows/spotless.yaml" ]; then
    assert_github_workflow_command "$maven_assets/.github/workflows/spotless.yaml" "mvn verify"
fi
if [ -f "$maven_assets/.gitlab-ci.yml" ]; then
    assert_gitlab_ci_command "$maven_assets/.gitlab-ci.yml" "spotless" "mvn verify"
fi
reject_file_text "$maven_assets" "harness-check"
reject_file_text "$maven_assets" "manifest.json"

# Validate bun assets and CI.
printf '\n--- bun stack ---\n' >&2
assert_bun_assets
bun_assets=$root/skills/harness-install/assets/bun
if [ -f "$bun_assets/.github/workflows/ultracite.yaml" ]; then
    assert_github_workflow_command "$bun_assets/.github/workflows/ultracite.yaml" "bun run check"
fi
if [ -f "$bun_assets/.gitlab-ci.yml" ]; then
    assert_gitlab_ci_command "$bun_assets/.gitlab-ci.yml" "ultracite" "bun run check"
fi
reject_file_text "$bun_assets" "harness-check"
reject_file_text "$bun_assets" "manifest.json"

# Validate uv assets and CI.
printf '\n--- uv stack ---\n' >&2
assert_uv_assets
uv_assets=$root/skills/harness-install/assets/uv
if [ -f "$uv_assets/.github/workflows/ruff.yaml" ]; then
    assert_github_workflow_command "$uv_assets/.github/workflows/ruff.yaml" "uv run scripts/check.py"
fi
if [ -f "$uv_assets/.gitlab-ci.yml" ]; then
    assert_gitlab_ci_command "$uv_assets/.gitlab-ci.yml" "ruff" "uv run scripts/check.py"
fi
reject_file_text "$uv_assets" "harness-check"
reject_file_text "$uv_assets" "manifest.json"

# Validate shell assets and CI.
printf '\n--- shell stack ---\n' >&2
assert_shell_assets
shell_assets=$root/skills/harness-install/assets/shell
if [ -f "$shell_assets/.github/workflows/shellcheck.yaml" ]; then
    assert_github_workflow_command "$shell_assets/.github/workflows/shellcheck.yaml" "sh scripts/check.sh"
fi
if [ -f "$shell_assets/.gitlab-ci.yml" ]; then
    assert_gitlab_ci_command "$shell_assets/.gitlab-ci.yml" "shellcheck" "sh scripts/check.sh"
fi
reject_file_text "$shell_assets" "harness-check"
reject_file_text "$shell_assets" "manifest.json"

# Reject stale manifest/bespoke references throughout assets.
printf '\n--- manifest/bespoke rejection ---\n' >&2
reject_file_text "$root/skills/harness-install/assets" "manifest.json"
reject_file_text "$root/skills/harness-install/assets" "harness-check"
reject_file_text "$root/skills/harness-install/assets" "leafFunctionBlankLines"
printf '[rejection] No manifest/bespoke references in assets OK\n' >&2

# Smoke tests for native tools (optional, graceful skip).
printf '\n--- native tool smoke tests ---\n' >&2
smoke_test_tool "mvn" "mvn -version"
smoke_test_tool "gradle" "gradle -version"
smoke_test_tool "bunx" "bunx --version"
smoke_test_tool "uv" "uv --version"
smoke_test_tool "shellcheck" "shellcheck --version"

printf '\nAll checks passed.\n' >&2
