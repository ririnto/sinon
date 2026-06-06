#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${CLAUDE_PLUGIN_ROOT:-$(CDPATH='' cd "$(dirname "$0")/.." && pwd)}

# Return a path relative to the plugin root.
#
# @param path File or directory path to normalize.
# @return Prints the plugin-root-relative path.
relative_to_root() {
    path=$1
    case "$path" in
        "$root"/*) printf '%s\n' "${path#"$root"/}" ;;
        *) printf '%s\n' "$path" ;;
    esac
}

# Require a regular file in the plugin package.
#
# @param path File path to check.
# @exit Exits with status 1 when file is missing.
require_file() {
    path=$1
    relative_path=$(relative_to_root "$path")
    if ! tracked_file=$(git -C "$root" ls-files --error-unmatch -- "$relative_path" 2>&1); then
        printf '%s\n' "[require_file] missing required file: $path" >&2
        printf '%s\n' "  hint: ensure the file is committed and at the expected path" >&2
        exit 1
    fi
    if [ -z "$tracked_file" ]; then
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
    relative_path=$(relative_to_root "$path")
    tracked_entries=$(git -C "$root" ls-files -- "$relative_path")
    if [ -z "$tracked_entries" ]; then
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
    matches_file=$(mktemp)
    if [ -f "$path" ]; then
        if grep -Fq -- "$text" "$path"; then
            printf '%s\n' "$path" >"$matches_file"
        fi
    else
        git -C "$root" ls-files -- "$path" | while IFS= read -r file; do
            if [ ! -f "$root/$file" ]; then
                continue
            fi
            if grep -Fq -- "$text" "$root/$file"; then
                printf '%s\n' "$file" >>"$matches_file"
            fi
        done
    fi
    if [ -s "$matches_file" ]; then
        printf '%s\n' "[reject_file_text] forbidden text in $path: $text" >&2
        cat "$matches_file" >&2
        rm -f "$matches_file"
        exit 1
    fi
    rm -f "$matches_file"
}

# Require a file to NOT exist in the plugin package.
#
# @param path File path to check.
# @exit Exits with status 1 when file exists.
reject_file() {
    path=$1
    relative_path=$(relative_to_root "$path")
    if tracked_file=$(git -C "$root" ls-files --error-unmatch -- "$relative_path" 2>&1); then
        if [ -z "$tracked_file" ]; then
            return 0
        fi
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
# Reject text in a file by extended regular expression.
#
# @param path File path to search.
# @param pattern Regular expression to reject.
# @exit Exits with status 1 when pattern is found.
reject_file_regex() {
    path=$1
    pattern=$2
    if grep -Eq -- "$pattern" "$path"; then
        printf '[reject_file_regex] forbidden pattern in %s: %s\n' "$path" "$pattern" >&2
        grep -En -- "$pattern" "$path" >&2
        exit 1
    fi
}

# Enforce installer script style hardening contracts.
#
# @param installer_script Path to Python installer script.
# @param bun_plugin_script Path to Bun oxlint JS plugin script.
# @exit Exits with status 1 when style contracts are violated.
assert_code_style_contracts() {
    installer_script=$1
    bun_plugin_script=$2

    # No leading underscore declarations in public implementation files.
    # Language-reserved dunder identifiers (e.g. __init__, __name__) are allowed;
    # the ban targets user-defined single-underscore prefixes. Standalone _ is allowed.
    reject_file_regex "$installer_script" '^[[:space:]]*(def|class)[[:space:]]+_([A-Za-z0-9][A-Za-z0-9_]*)'
    reject_file_regex "$installer_script" '^[[:space:]]*_([A-Za-z0-9][A-Za-z0-9_]*)[[:space:]]*='
    reject_file_regex "$bun_plugin_script" '^[[:space:]]*(const|let|var|function|class)[[:space:]]+_([A-Za-z0-9][A-Za-z0-9_]*)'

    # Disallow one-line triple-quoted docstrings in the installer script.
    reject_file_regex "$installer_script" '^[[:space:]]*"""[^"\n]+"""$'

    # No static SKIP_TREE_PARTS-style path filtering; git ls-files handles it.
    reject_file_contains "$installer_script" "SKIP_TREE_PARTS"
    reject_file_contains "$installer_script" "is_generated_or_ignored_path"

    # Sanity-check Python syntax.
    python3 -m py_compile "$installer_script"
}

# Require gradle buildSrc assets exist.
assert_gradle_assets() {
    assets_root=$root/skills/harness-install/assets/gradle
    require_file "$assets_root/build.gradle.kts"
    require_file "$assets_root/settings.gradle.kts"
    require_file "$assets_root/gradle/libs.versions.toml"
    require_file "$assets_root/buildSrc/settings.gradle.kts"
    require_file "$assets_root/buildSrc/build.gradle.kts"
    require_dir "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint"
    require_file "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/ExplicitPropertyTypeKtlintRule.kt"
    require_file "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/TerminalBranchWhenKtlintRule.kt"
    require_file "$assets_root/buildSrc/src/main/resources/META-INF/services/com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3"
    require_text "$assets_root/build.gradle.kts" 'gitTrackedFiles("*.kt", "*.kts")'
    require_text "$assets_root/build.gradle.kts" 'alias(libs.plugins.ktlint) apply false'
    require_text "$assets_root/build.gradle.kts" 'alias(libs.plugins.kotlin.jvm) apply false'
    require_text "$assets_root/build.gradle.kts" 'rootProject.libs.plugins.ktlint'
    require_text "$assets_root/build.gradle.kts" 'libs.versions.ktlint.cli'
    require_text "$assets_root/buildSrc/build.gradle.kts" 'alias(libs.plugins.kotlin.jvm)'
    require_text "$assets_root/buildSrc/build.gradle.kts" 'libs.versions.ktlint.cli.get()'
    require_text "$assets_root/buildSrc/settings.gradle.kts" '../gradle/libs.versions.toml'
    require_text "$assets_root/build.gradle.kts" 'checkHarnessMarkdownLinks'
    require_text "$assets_root/build.gradle.kts" 'docs/harness/scripts/check-markdown-links.sh'
    require_text "$assets_root/gradle/libs.versions.toml" 'kotlin = "2.4.0"'
    require_text "$assets_root/gradle/libs.versions.toml" 'ktlint = "14.2.0"'
    require_text "$assets_root/gradle/libs.versions.toml" 'ktlint-cli = "1.8.0"'
    require_text "$assets_root/build.gradle.kts" '"rev-parse"'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/ExplicitPropertyTypeKtlintRule.kt" 'code:explicit-property-type'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/TerminalBranchWhenKtlintRule.kt" 'code:terminal-branch-when'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/TerminalBranchWhenKtlintRule.kt" 'hasFinalElseBranch'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/RuleSetProvider.kt" 'ExplicitPropertyTypeKtlintRule()'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/RuleSetProvider.kt" 'TerminalBranchWhenKtlintRule()'
    require_text "$assets_root/build.gradle.kts" 'docs/harness/git-hooks'
    reject_file_contains "$assets_root/.editorconfig" "ktlint_unchecked_cast_suppression_forbidden"
    reject_file_contains "$assets_root/.editorconfig" "ktlint_unchecked_cast_suppression_allowed"
    require_text "$assets_root/.editorconfig" "ij_kotlin_allow_trailing_comma = false"
    require_text "$assets_root/.editorconfig" "ktlint_standard_no-wildcard-imports = disabled"
    printf '[gradle assets] OK\n' >&2
}

# Require bun assets exist.
assert_bun_assets() {
    assets_root=$root/skills/harness-install/assets/bun
    require_file "$assets_root/package.json"
    require_file "$assets_root/oxlint.config.ts"
    require_file "$assets_root/oxfmt.config.ts"
    require_file "$assets_root/scripts/check.sh"
    require_file "$assets_root/scripts/fix.sh"
    require_file "$assets_root/scripts/tsdoc-plugin.mjs"
    require_text "$assets_root/package.json" '"ultracite": "^7.8.1"'
    require_text "$assets_root/package.json" '"oxlint": "^1.68.0"'
    require_text "$assets_root/package.json" '"oxfmt": "^0.53.0"'
    require_text "$assets_root/oxlint.config.ts" 'ultracite/oxlint/core'
    require_text "$assets_root/oxlint.config.ts" 'jsPlugins: ["./scripts/tsdoc-plugin.mjs"]'
    require_text "$assets_root/oxlint.config.ts" 'tsdoc/require-export-tsdoc'
    require_text "$assets_root/oxlint.config.ts" '"**/*.{js,jsx,mjs,cjs}"'
    require_text "$assets_root/oxlint.config.ts" 'jsdoc/require-param'
    require_text "$assets_root/oxlint.config.ts" 'jsdoc/require-returns'
    require_text "$assets_root/oxlint.config.ts" "ignorePatterns: core.ignorePatterns"
    reject_file_contains "$assets_root/oxlint.config.ts" "disabledRules"
    require_text "$assets_root/oxfmt.config.ts" 'ultracite/oxfmt'
    require_text "$assets_root/oxfmt.config.ts" '...ultracite'
    require_text "$assets_root/scripts/check.sh" 'git ls-files -z'
    require_text "$assets_root/scripts/check.sh" 'bun install --no-save'
    require_text "$assets_root/scripts/check.sh" 'bunx ultracite check --'
    require_text "$assets_root/scripts/check.sh" 'docs/harness/scripts/check-markdown-links.sh'
    require_text "$assets_root/scripts/check.sh" "[ -L \"\$dst\" ]"
    require_text "$assets_root/package.json" '"fix": "sh scripts/fix.sh"'
    require_text "$assets_root/scripts/fix.sh" 'git ls-files -z'
    require_text "$assets_root/scripts/fix.sh" 'bun install --no-save'
    require_text "$assets_root/scripts/fix.sh" 'bunx ultracite fix --'
    reject_file "$assets_root/.oxlintrc.json"
    reject_file "$assets_root/.oxfmtrc.json"
    reject_file "$assets_root/scripts/plugin.mjs"
    reject_file "$assets_root/scripts/validate-jsdoc.mjs"
    reject_file "$assets_root/scripts/typescript-public-jsdoc-plugin.mjs"
    reject_file_contains "$assets_root/scripts/check.sh" 'bunx oxlint'
    reject_file_contains "$assets_root/scripts/check.sh" 'bunx oxfmt'
    reject_file_contains "$assets_root/scripts/fix.sh" 'bunx oxlint'
    reject_file_contains "$assets_root/scripts/fix.sh" 'bunx oxfmt'
    printf '[bun assets] OK\n' >&2
}

# Require uv assets exist.
assert_uv_assets() {
    assets_root=$root/skills/harness-install/assets/uv
    require_file "$assets_root/ruff.toml"
    reject_file_contains "$assets_root/ruff.toml" 'extend-select'
    require_text "$assets_root/scripts/check.py" 'docs/harness/scripts/check-markdown-links.sh'
    require_file "$assets_root/scripts/check.py"
    require_file "$assets_root/scripts/fix.py"
    require_text "$assets_root/scripts/check.py" '--git-path'
    require_text "$assets_root/scripts/check.py" 'git", "ls-files", "-z"'
    require_text "$assets_root/scripts/check.py" 'ruff>=0.15.16,<0.16.0'
    require_text "$assets_root/scripts/check.py" '"check",'
    require_text "$assets_root/scripts/check.py" '"--",'
    require_text "$assets_root/scripts/fix.py" 'git", "ls-files", "-z"'
    require_text "$assets_root/scripts/fix.py" 'ruff>=0.15.16,<0.16.0'
    require_text "$assets_root/scripts/fix.py" '"format",'
    require_text "$assets_root/scripts/fix.py" '"--",'
    reject_file_contains "$assets_root/ruff.toml" 'extend-ignore'
    reject_file_contains "$assets_root/ruff.toml" 'ignore ='
    reject_file_contains "$assets_root/ruff.toml" 'per-file-ignores'
    printf '[uv assets] OK\n' >&2
}

# Require maven assets exist.
assert_maven_assets() {
    assets_root=$root/skills/harness-install/assets/maven
    require_file "$assets_root/pom.xml"
    reject_file "$assets_root/config/checkstyle/checkstyle.xml"
    require_text "$assets_root/pom.xml" '<artifactId>maven-checkstyle-plugin</artifactId>'
    require_text "$assets_root/pom.xml" '<artifactId>checkstyle</artifactId>'
    require_text "$assets_root/pom.xml" '<version>13.5.0</version>'
    require_text "$assets_root/pom.xml" '<checkstyleRules>'
    require_text "$assets_root/pom.xml" '<module name="AvoidStarImport"/>'
    require_text "$assets_root/pom.xml" '<module name="UnusedImports"/>'
    require_text "$assets_root/pom.xml" '<module name="NeedBraces"/>'
    reject_file_text "$assets_root/pom.xml" '<configLocation>'
    reject_file_text "$assets_root/pom.xml" '<suppressions>'
    reject_file_text "$assets_root/pom.xml" '<excludes>'
    reject_file_text "$assets_root/pom.xml" '<skip>'
    reject_file_text "$assets_root/pom.xml" 'failOnViolation>false</failOnViolation>'
    require_text "$assets_root/pom.xml" 'sync-git-hooks'
    require_text "$assets_root/pom.xml" 'git rev-parse --git-path hooks'
    require_text "$assets_root/pom.xml" 'cmp -s'
    require_text "$assets_root/pom.xml" "if [ -L &quot;\$dst&quot; ]; then continue; fi"
    require_text "$assets_root/pom.xml" 'failonerror="true"'
    require_text "$assets_root/pom.xml" 'docs/harness/scripts/check-markdown-links.sh'
    reject_file_contains "$assets_root/pom.xml" 'failonerror="false"'
    require_text "$root/skills/harness-install/scripts/install-harness.py" 'spotlessFiles'
    require_text "$root/skills/harness-install/scripts/install-harness.py" './mvnw validate'
    require_text "$root/skills/harness-install/scripts/install-harness.py" './mvnw validate -DspotlessFiles'
    require_text "$root/skills/harness-install/scripts/install-harness.py" "root=\$(pwd -P)"
    require_text "$root/skills/harness-install/scripts/install-harness.py" 'git ls-files -- "*.java"'
    require_text "$root/skills/harness-install/scripts/install-harness.py" "list_tracked_tree_files"
    reject_file_contains "$root/skills/harness-install/scripts/install-harness.py" "find \"\$src_dir\" -type f"
    require_text "$root/skills/harness-install/scripts/install-harness.py" 'Java path contains comma'
    require_text "$root/skills/harness-install/scripts/install-harness.py" "case \\\"\$file\\\" in *,*)"
    require_text "$root/skills/harness-install/scripts/install-harness.py" 'paste -sd, -'
    require_text "$root/skills/harness-install/scripts/install-harness.py" 's/[][\\.^$*+?{}()|]/\\&/g'
    reject_file_contains "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" './mvnw verify'
    reject_file_contains "$assets_root/pom.xml" '<phase>verify</phase>'
    reject_file_contains "$root/skills/harness-install/scripts/install-harness.py" 's#^#.*#'
    printf '[maven assets] OK\n' >&2
}

# Require shell assets exist.
assert_shell_assets() {
    assets_root=$root/skills/harness-install/assets/shell
    require_file "$assets_root/scripts/check.sh"
    require_file "$assets_root/scripts/fix.sh"
    reject_file "$assets_root/.editorconfig"
    reject_file "$assets_root/.shellcheckrc"
    require_text "$assets_root/scripts/check.sh" 'git rev-parse --git-path hooks'
    require_text "$assets_root/scripts/check.sh" 'git ls-files -z'
    require_text "$assets_root/scripts/check.sh" 'xargs -0 shellcheck -S warning --'
    require_text "$assets_root/scripts/check.sh" 'xargs -0 shfmt -d -i 4 -ci --'
    require_text "$assets_root/scripts/check.sh" "[ -L \"\$dst\" ]"
    require_text "$assets_root/scripts/check.sh" 'docs/harness/scripts/check-markdown-links.sh'
    require_text "$assets_root/scripts/fix.sh" 'git ls-files -z'
    require_text "$assets_root/scripts/fix.sh" "xargs -0 \"\$shfmt_bin\" -i 4 -ci -w --"
    require_text "$assets_root/scripts/fix.sh" 'error: shfmt is required for shell formatting.'
    reject_file_contains "$assets_root/scripts/fix.sh" "skipping shell fixes"
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
    if ! tool_path=$(command -v "$tool_name" 2>&1); then
        printf 'note: %s not in PATH; skipping smoke test\n' "$tool_name" >&2
        return 0
    fi
    if [ -z "$tool_path" ]; then
        printf 'note: %s not in PATH; skipping smoke test\n' "$tool_name" >&2
        return 0
    fi
    if "$tool_name" --version; then
        printf '[smoke test] %s OK\n' "$tool_name" >&2
        return 0
    fi
    return 0
}

# Require common assets to render the selected validation command and stay stack-neutral.
#
# @param common_assets_root Path to common assets root.
assert_common_assets_rendered_validation_command() {
    common_assets_root=$1
    require_file "$common_assets_root/.editorconfig"
    require_file "$common_assets_root/docs/harness/README.md"
    require_text "$common_assets_root/docs/harness/README.md" "{{validation_command}}"
    reject_file_text "$common_assets_root/docs/harness/README.md" "| Gradle |"
    reject_file_text "$common_assets_root/docs/harness/README.md" "| Maven |"
    reject_file_text "$common_assets_root/docs/harness/README.md" "| uv |"
    reject_file_text "$common_assets_root/docs/harness/README.md" "| Bun |"
    reject_file_text "$common_assets_root/docs/harness/README.md" "| shell |"
    require_file "$common_assets_root/AGENTS.md"
    require_text "$common_assets_root/AGENTS.md" "{{validation_command}}"

    reject_file_text "$common_assets_root/AGENTS.md" './gradlew ktlintCheck'
    reject_file_text "$common_assets_root/AGENTS.md" 'uv run scripts/check.py'
    reject_file_text "$common_assets_root/AGENTS.md" 'bun run check'
    reject_file_text "$common_assets_root/AGENTS.md" 'sh scripts/check.sh'
    require_file "$common_assets_root/.claude/skills/harness-validate/SKILL.md"
    # Avoid stale per-stack command matrices in common validate guidance.
    reject_file_text "$common_assets_root/.claude/skills/harness-validate/SKILL.md" "| Stack | Command |"
    printf '[common assets] validation rendering OK\n' >&2
    require_file "$common_assets_root/docs/harness/scripts/check-markdown-links.sh"
    require_text "$common_assets_root/docs/harness/scripts/check-markdown-links.sh" "docs/exec-plans/tech-debt-tracker.md"
    require_text "$common_assets_root/docs/harness/scripts/check-markdown-links.sh" "exec-plans/(active|completed)/"
}
# Reject references to execution-plan active/completed directories outside the tracker.
#
# @param assets_root Path to the install asset tree.
# @exit Exits with status 1 when disallowed references are found.
assert_exec_plan_reference_policy() {
    assets_root=$1
    matches_file=$(mktemp)
    git -C "$root" ls-files -- "$assets_root" | while IFS= read -r file; do
        if [ -z "$file" ]; then
            continue
        fi
        if [ "$file" = "$assets_root/common/docs/exec-plans/tech-debt-tracker.md" ]; then
            continue
        fi
        if [ "$file" = "$assets_root/common/docs/harness/scripts/check-markdown-links.sh" ]; then
            continue
        fi
        if [ ! -f "$root/$file" ]; then
            continue
        fi
        if grep -Fq -- "docs/exec-plans/active/" "$root/$file" ||
            grep -Fq -- "docs/exec-plans/completed/" "$root/$file"; then
            printf '%s\n' "$file" >>"$matches_file"
        fi
    done

    if [ -s "$matches_file" ]; then
        printf '[assert_exec_plan_reference_policy] disallowed docs/exec-plans/{active,completed} references in assets:\n' >&2
        cat "$matches_file" >&2
        rm -f "$matches_file"
        exit 1
    fi
    rm -f "$matches_file"
}

printf 'Validating harness plugin native-lint end-state...\n' >&2

# Validate common assets.
printf '\n--- common assets ---\n' >&2
common_assets=$root/skills/harness-install/assets/common
assert_common_assets_rendered_validation_command "$common_assets"
require_file "$common_assets/.editorconfig"
require_text "$common_assets/.editorconfig" '[{*.json,*.jsonc,*.yaml,*.yml,*.js,*.jsx,*.mjs,*.cjs,*.ts,*.tsx,*.md,*.markdown}]'
require_text "$common_assets/.editorconfig" 'indent_size = 2'
printf '[common assets] OK\n' >&2

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
    require_text "$maven_assets/.github/workflows/spotless.yaml" "root=\$(pwd -P)"
    require_text "$maven_assets/.github/workflows/spotless.yaml" 'git ls-files -- "*.java"'
    require_text "$maven_assets/.github/workflows/spotless.yaml" 'Java path contains comma'
    require_text "$maven_assets/.github/workflows/spotless.yaml" './mvnw validate -DspotlessFiles'
    require_text "$maven_assets/.github/workflows/spotless.yaml" 'spotlessFiles'
    reject_file_contains "$maven_assets/.github/workflows/spotless.yaml" './mvnw verify'
    reject_file_contains "$maven_assets/.github/workflows/spotless.yaml" 's#^#.*#'
    reject_file_contains "$maven_assets/.github/workflows/spotless.yaml" '>-'
    require_text "$maven_assets/.github/workflows/spotless.yaml" 'run: |-'
    printf '[GitHub workflow spotless.yaml] command OK\n' >&2
fi
if [ -f "$maven_assets/.gitlab-ci.yml" ]; then
    require_text "$maven_assets/.gitlab-ci.yml" 'spotless:'
    require_text "$maven_assets/.gitlab-ci.yml" "root=\$(pwd -P)"
    require_text "$maven_assets/.gitlab-ci.yml" 'git ls-files -- "*.java"'
    require_text "$maven_assets/.gitlab-ci.yml" 'Java path contains comma'
    require_text "$maven_assets/.gitlab-ci.yml" './mvnw validate -DspotlessFiles'
    require_text "$maven_assets/.gitlab-ci.yml" 'spotlessFiles'
    reject_file_contains "$maven_assets/.gitlab-ci.yml" './mvnw verify'
    reject_file_contains "$maven_assets/.gitlab-ci.yml" 's#^#.*#'
    reject_file_contains "$maven_assets/.gitlab-ci.yml" '>-'
    require_text "$maven_assets/.gitlab-ci.yml" '- |-'
    printf '[GitLab CI] spotless job command OK\n' >&2
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
    require_text "$shell_assets/.github/workflows/shellcheck.yaml" 'shellcheck shfmt'
fi
if [ -f "$shell_assets/.gitlab-ci.yml" ]; then
    assert_gitlab_ci_command "$shell_assets/.gitlab-ci.yml" "shellcheck" "sh scripts/check.sh"
    require_text "$shell_assets/.gitlab-ci.yml" 'shellcheck shfmt'
fi
reject_file_text "$shell_assets" "harness-check"
reject_file_text "$shell_assets" "manifest.json"

# Validate harness check/fix scripts use git ls-files for Markdown.
printf '\n--- harness markdown discovery ---\n' >&2
require_text "$root/scripts/check.sh" "git -C"
require_text "$root/scripts/check.sh" "ls-files -z -- '*.md'"
require_text "$root/scripts/check.sh" "xargs -0"
reject_file_contains "$root/scripts/check.sh" "find "
reject_file_contains "$root/scripts/check.sh" "\"**/*.md\""
require_text "$root/scripts/check.sh" "Repository validation passed."
reject_file_contains "$root/scripts/check.sh" "Checked %d file(s)"
reject_file_contains "$root/scripts/check.sh" "%d error(s)"
require_text "$root/scripts/fix.sh" "git -C"
require_text "$root/scripts/fix.sh" "ls-files -z -- '*.md'"
require_text "$root/scripts/fix.sh" "xargs -0"
reject_file_contains "$root/scripts/fix.sh" "find "
reject_file_contains "$root/scripts/fix.sh" "\"**/*.md\""
require_text "$root/scripts/fix.sh" "fixed files:"
reject_file_contains "$root/scripts/fix.sh" "unchanged files were left as-is."
reject_file_contains "$root/scripts/fix.sh" "fixed: %d"
reject_file_contains "$root/scripts/fix.sh" "no-op: %d"
printf '[harness markdown discovery] OK\n' >&2

# Enforce docs/exec-plans path policy for packaged assets.
printf '\n--- exec-plan reference policy ---\n' >&2
assert_exec_plan_reference_policy "skills/harness-install/assets"
printf '[rejection] exec-plan path policy OK\n' >&2
# Validate Python installer surface.
printf '\n--- Python installer surface ---\n' >&2
require_file "$root/skills/harness-install/scripts/install-harness.py"
require_text "$root/skills/harness-install/scripts/install-harness.py" '#!/usr/bin/env -S uv run --script'
require_text "$root/skills/harness-install/scripts/install-harness.py" '# /// script'
require_text "$root/skills/harness-install/scripts/install-harness.py" 'requires-python'
require_text "$root/skills/harness-install/scripts/install-harness.py" 'def parse_args'
require_text "$root/skills/harness-install/scripts/install-harness.py" 'if not self.config.force:'
require_text "$root/skills/harness-install/scripts/install-harness.py" 'root contract conflicts must be resolved before installing harness assets'
require_text "$root/skills/harness-install/scripts/install-harness.py" 'def preview_install_set'
require_text "$root/skills/harness-install/scripts/install-harness.py" 'def show_one_target_path'
require_text "$root/skills/harness-install/scripts/install-harness.py" 'def install_one_target_path'
require_text "$root/skills/harness-install/scripts/install-harness.py" '{{validation_command}}'
reject_file_contains "$root/skills/harness-install/scripts/install-harness.py" 'install-harness.sh'
assert_code_style_contracts \
    "$root/skills/harness-install/scripts/install-harness.py" \
    "$root/skills/harness-install/assets/bun/scripts/tsdoc-plugin.mjs"
printf '[Python installer surface] OK\n' >&2

# Reject stale manifest/bespoke references throughout assets.
printf '\n--- manifest/bespoke rejection ---\n' >&2
reject_file_text "$root/skills/harness-install/assets" "manifest.json"
reject_file_text "$root/skills/harness-install/assets" "harness-check"
reject_file_text "$root/skills/harness-install/assets" "leafFunctionBlankLines"
printf '[rejection] No manifest/bespoke references in assets OK\n' >&2

# Smoke tests for native tools available in this plugin checkout (optional, graceful skip).
printf '\n--- native tool smoke tests ---\n' >&2
smoke_test_tool "bunx" "bunx --version"
smoke_test_tool "uv" "uv --version"
smoke_test_tool "shellcheck" "shellcheck --version"
smoke_test_tool "shfmt" "shfmt --version"

printf '\nAll checks passed.\n' >&2
