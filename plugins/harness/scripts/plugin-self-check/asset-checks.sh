#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${root:?}

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
    require_text "$assets_root/build.gradle.kts" 'checkHarnessMarkdown'
    require_text "$assets_root/build.gradle.kts" 'markdownlint-cli2@0.22.1'
    reject_file_contains "$assets_root/build.gradle.kts" 'docs/harness/scripts/check-markdown-links.sh'
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
    require_file "$assets_root/scripts/tsdoc-plugin.ts"
    require_text "$assets_root/package.json" '"markdownlint": "^0.40.0"'
    require_text "$assets_root/package.json" '"markdownlint-cli2": "^0.22.1"'
    require_text "$assets_root/package.json" '"ultracite": "^7.8.1"'
    require_text "$assets_root/package.json" '"oxlint": "^1.68.0"'
    require_text "$assets_root/package.json" '"oxfmt": "^0.53.0"'
    require_text "$assets_root/oxlint.config.ts" 'ultracite/oxlint/core'
    require_text "$assets_root/oxlint.config.ts" 'jsPlugins: ["./scripts/tsdoc-plugin.ts"]'
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
    require_text "$assets_root/scripts/check.sh" 'npx -y markdownlint-cli2@0.22.1'
    reject_file_contains "$assets_root/scripts/check.sh" 'docs/harness/scripts/check-markdown-links.sh'
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
    require_file "$assets_root/scripts/check.py"
    require_text "$assets_root/scripts/check.py" 'markdownlint-cli2@0.22.1'
    reject_file_contains "$assets_root/scripts/check.py" 'docs/harness/scripts/check-markdown-links.sh'
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
    installer_package=$root/skills/harness-install/scripts/install_harness
    installer_commands=$root/skills/harness-install/scripts/install_harness/commands.py
    installer_hooks=$root/skills/harness-install/scripts/install_harness/hooks.py
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
    require_text "$assets_root/pom.xml" 'markdownlint-cli2@0.22.1'
    reject_file_contains "$assets_root/pom.xml" 'docs/harness/scripts/check-markdown-links.sh'
    reject_file_contains "$assets_root/pom.xml" 'failonerror="false"'
    require_text "$installer_commands" 'spotlessFiles'
    require_text "$installer_commands" './mvnw validate'
    require_text "$installer_commands" './mvnw validate -DspotlessFiles'
    require_text "$installer_commands" "root=\$(pwd -P)"
    require_text "$installer_commands" 'git ls-files -- "*.java"'
    require_text "$installer_hooks" "list_tracked_tree_files"
    reject_file_text "$installer_package" "find \"\$src_dir\" -type f"
    require_text "$installer_commands" 'Java path contains comma'
    require_text "$installer_commands" "case \"\$file\" in *,*)"
    require_text "$installer_commands" 'paste -sd, -'
    require_text "$installer_commands" 's/[][\\.^$*+?{}()|]/\\&/g'
    reject_file_contains "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" './mvnw verify'
    reject_file_contains "$assets_root/pom.xml" '<phase>verify</phase>'
    reject_file_contains "$installer_commands" 's#^#.*#'
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
    require_text "$assets_root/scripts/check.sh" 'npx -y markdownlint-cli2@0.22.1'
    reject_file_contains "$assets_root/scripts/check.sh" 'docs/harness/scripts/check-markdown-links.sh'
    require_text "$assets_root/scripts/fix.sh" 'git ls-files -z'
    require_text "$assets_root/scripts/fix.sh" "xargs -0 \"\$shfmt_bin\" -i 4 -ci -w --"
    require_text "$assets_root/scripts/fix.sh" 'error: shfmt is required for shell formatting.'
    reject_file_contains "$assets_root/scripts/fix.sh" "skipping shell fixes"
    printf '[shell assets] OK\n' >&2
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
    require_file "$common_assets_root/.markdownlint-cli2.jsonc"
    require_file "$common_assets_root/docs/harness/scripts/exec-plan-links.ts"
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" "\"\$schema\": \"https://raw.githubusercontent.com/DavidAnson/markdownlint-cli2/refs/tags/v0.22.1/schema/markdownlint-cli2-config-schema.json\""
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"line-length": false'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"no-inline-html": {'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"allowed_elements": ['
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"table_allowed_elements": ['
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"siblings_only": true'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"code-block-style": {'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"style": "fenced"'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"no-emphasis-as-heading": true'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"customRules": ['
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" './docs/harness/scripts/exec-plan-links.ts'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"docs/exec-plans/links": true'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"gitignore": ".gitignore"'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"**/*.md"'
    require_text "$common_assets_root/docs/harness/scripts/exec-plan-links.ts" 'import type { Rule } from "markdownlint@0.40.0"'
    require_text "$common_assets_root/docs/harness/scripts/exec-plan-links.ts" 'parser: "none"'
    require_text "$common_assets_root/docs/harness/scripts/exec-plan-links.ts" 'tech-debt-tracker.md'
    reject_file "$common_assets_root/docs/harness/scripts/check-markdown-links.sh"
    reject_file_text "$root/skills/harness-install/assets" 'docs/harness/scripts/check-markdown-links.sh'
}
