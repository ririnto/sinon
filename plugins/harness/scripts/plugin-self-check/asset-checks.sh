#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${root:?}

# Require a file to contain every given text fragment.
#
# @param path File path to inspect.
# @param text Text fragment to require.
require_texts() {
    path=$1
    shift
    for text; do
        require_text "$path" "$text"
    done
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
    require_file "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/ComparisonDirectionKtlintRule.kt"
    require_file "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/SlfDirectLoggingKtlintRule.kt"
    require_file "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/TerminalBranchWhenKtlintRule.kt"
    require_file "$assets_root/buildSrc/src/main/resources/META-INF/services/com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3"
    require_texts "$assets_root/build.gradle.kts" 'alias(libs.plugins.ktlint) apply false' 'alias(libs.plugins.kotlin.jvm) apply false' 'rootProject.libs.plugins.ktlint' 'libs.versions.ktlint.cli'
    reject_file_contains "$assets_root/build.gradle.kts" 'gitTrackedFiles'
    reject_file_contains "$assets_root/build.gradle.kts" 'git", "ls-files"'
    require_text "$assets_root/buildSrc/build.gradle.kts" 'alias(libs.plugins.kotlin.jvm)'
    require_text "$assets_root/buildSrc/build.gradle.kts" 'libs.versions.ktlint.cli.get()'
    require_text "$assets_root/buildSrc/settings.gradle.kts" '../gradle/libs.versions.toml'
    require_texts "$assets_root/build.gradle.kts" 'checkMarkdown' 'tasks.named("check")' 'fixMarkdown' 'kotlinFormat' 'runKtlintFormatOver' 'command -v markdownlint-cli2' 'skipping Markdown linting' 'skipping Markdown fixes' 'bun add -g markdownlint-cli2' 'outputs.upToDateWhen { false }' '"--fix"'
    reject_file_contains "$assets_root/build.gradle.kts" '"**/*.md"'
    reject_file_contains "$assets_root/build.gradle.kts" '"bunx"'
    reject_file_contains "$assets_root/build.gradle.kts" 'docs/scripts/check-markdown-links.sh'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/ExplicitPropertyTypeKtlintRule.kt" 'code:explicit-property-type'
    require_texts "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/ComparisonDirectionKtlintRule.kt" 'code:comparison-direction' 'KtSingleValueToken' 'ALLOW_AUTOCORRECT'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/SlfDirectLoggingKtlintRule.kt" 'code:slf-direct-logging'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/TerminalBranchWhenKtlintRule.kt" 'code:terminal-branch-when'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/TerminalBranchWhenKtlintRule.kt" 'RuleAutocorrectApproveHandler'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/CompanionObjectPositionKtlintRule.kt" 'position != "any"'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/KotlinTopLevelDeclarationCountKtlintRule.kt" 'takeUnless { ktFile -> ktFile.isScript() }'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/PublicDeclarationDocCommentKtlintRule.kt" 'takeUnless { ktFile -> ktFile.isScript() }'
    reject_file_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint" 'abstract class KtlintRule'
    reject_file_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint" ': KtlintRule('
    reject_file_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint" 'object : KtTreeVisitorVoid'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/RuleSetProvider.kt" 'ExplicitPropertyTypeKtlintRule()'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/RuleSetProvider.kt" 'ComparisonDirectionKtlintRule()'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/RuleSetProvider.kt" 'SlfDirectLoggingKtlintRule()'
    require_text "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/RuleSetProvider.kt" 'TerminalBranchWhenKtlintRule()'
    reject_file_contains "$assets_root/.editorconfig" "ktlint_unchecked_cast_suppression_forbidden"
    reject_file_contains "$assets_root/.editorconfig" "ktlint_unchecked_cast_suppression_allowed"
    require_text "$assets_root/.editorconfig" "ij_kotlin_allow_trailing_comma = false"
    require_text "$assets_root/.editorconfig" "ktlint_companion_object_position = top"
    reject_file_contains "$assets_root/.editorconfig" "ktlint_companion_object_position_position"
    require_text "$assets_root/.editorconfig" "ktlint_standard_no-wildcard-imports = disabled"
    require_text "$assets_root/.editorconfig" "ktlint_standard_trailing-comma-on-call-site = disabled"
    require_text "$assets_root/.editorconfig" "ktlint_standard_trailing-comma-on-declaration-site = disabled"
    require_text "$assets_root/settings.gradle.kts" 'danilopianini.gradle-pre-commit-git-hooks'
    require_text "$assets_root/settings.gradle.kts" 'preCommit'
    require_text "$assets_root/settings.gradle.kts" 'tasks("ktlintCheck")'
    require_text "$assets_root/settings.gradle.kts" 'createHooks()'
    reject_file_contains "$assets_root/build.gradle.kts" 'setupGitHooks'
    printf '[gradle assets] OK\n' >&2
}

# Require bun assets exist.
assert_bun_assets() {
    assets_root=$root/skills/harness-install/assets/bun
    require_file "$assets_root/package.json"
    require_file "$assets_root/oxlint.config.ts"
    require_file "$assets_root/oxfmt.config.ts"
    require_file "$assets_root/scripts/tsdoc-plugin.ts"
    require_texts "$assets_root/package.json" '"prepare": "husky"' '"check": "run-p check:*"' '"check:markdownlint-cli2": "markdownlint-cli2"' '"check:ultracite": "ultracite check"' '"fix": "run-p fix:*"' '"fix:markdownlint-cli2": "markdownlint-cli2 --fix"' '"fix:ultracite": "ultracite fix"'
    require_texts "$assets_root/oxlint.config.ts" 'ultracite/oxlint/core' 'jsPlugins: ["./scripts/tsdoc-plugin.ts"]' 'tsdoc/require-export-tsdoc' '"**/*.{js,jsx,mjs,cjs}"' 'jsdoc/require-param' 'jsdoc/require-returns' "ignorePatterns: core.ignorePatterns"
    reject_file_contains "$assets_root/oxlint.config.ts" "disabledRules"
    require_text "$assets_root/oxfmt.config.ts" 'ultracite/oxfmt'
    require_text "$assets_root/oxfmt.config.ts" '...ultracite'
    reject_file "$assets_root/.oxlintrc.json"
    reject_file "$assets_root/.oxfmtrc.json"
    reject_file "$assets_root/scripts/plugin.mjs"
    reject_file "$assets_root/scripts/validate-jsdoc.mjs"
    reject_file "$assets_root/scripts/typescript-public-jsdoc-plugin.mjs"
    require_dir "$assets_root/.husky"
    require_file "$assets_root/.husky/pre-commit"
    require_file "$assets_root/.husky/pre-push"
    require_texts "$assets_root/.husky/pre-commit" 'bun typecheck' 'bun run check'
    require_texts "$assets_root/.husky/pre-push" 'bun typecheck' 'bun run check' 'bun test'
    printf '[bun assets] OK\n' >&2
}

# Require uv assets exist.
assert_uv_assets() {
    assets_root=$root/skills/harness-install/assets/uv
    require_file "$assets_root/ruff.toml"
    reject_file_contains "$assets_root/ruff.toml" 'extend-select'
    require_file "$assets_root/scripts/check.py"
    require_texts "$assets_root/scripts/check.py" 'shutil.which("markdownlint-cli2")' 'skipping Markdown linting'
    reject_file_contains "$assets_root/scripts/check.py" 'docs/scripts/check-markdown-links.sh'
    require_file "$assets_root/scripts/fix.py"
    require_texts "$assets_root/scripts/fix.py" 'shutil.which("markdownlint-cli2")' 'skipping Markdown fixes' '"--fix"'
    require_texts "$assets_root/scripts/check.py" '"check",' '"--check",' '"."'
    reject_file_contains "$assets_root/scripts/check.py" 'git", "ls-files"'
    reject_file_contains "$assets_root/scripts/check.py" 'tracked_python_files'
    require_texts "$assets_root/scripts/fix.py" '"--fix",' '"format",' '"."'
    reject_file_contains "$assets_root/scripts/fix.py" 'git", "ls-files"'
    reject_file_contains "$assets_root/scripts/fix.py" 'tracked_python_files'
    reject_file_contains "$assets_root/ruff.toml" 'extend-ignore'
    reject_file_contains "$assets_root/ruff.toml" 'ignore ='
    reject_file_contains "$assets_root/ruff.toml" 'per-file-ignores'
    require_file "$assets_root/.pre-commit-config.yaml"
    require_texts "$assets_root/.pre-commit-config.yaml" 'repo: local' '- id: lint' '- id: full-lint'
    reject_file_contains "$assets_root/.pre-commit-config.yaml" 'QUICK'
    reject_file_contains "$assets_root/scripts/check.py" 'sync_git_hooks'
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
    require_text "$assets_root/pom.xml" '<checkstyleRules>'
    for module in AvoidStarImport UnusedImports NeedBraces LineLength RegexpSinglelineJava MissingJavadocType MissingJavadocMethod JavadocVariable TypeName MethodName MemberName ParameterName LocalVariableName; do require_text "$assets_root/pom.xml" "<module name=\"$module"; done
    reject_file_text "$assets_root/pom.xml" '<configLocation>'
    reject_file_text "$assets_root/pom.xml" '<suppressions>'
    reject_file_text "$assets_root/pom.xml" '<excludes>'
    reject_file_text "$assets_root/pom.xml" '<skip>'
    reject_file_text "$assets_root/pom.xml" 'failOnViolation>false</failOnViolation>'
    require_text "$assets_root/pom.xml" 'git-build-hook-maven-plugin'
    require_text "$assets_root/pom.xml" '<core.hooksPath>.githooks/</core.hooksPath>'
    require_text "$assets_root/pom.xml" 'failonerror="true"'
    require_text "$assets_root/pom.xml" 'command -v markdownlint-cli2'
    require_text "$assets_root/pom.xml" 'skipping Markdown linting'
    require_text "$assets_root/pom.xml" 'bun add -g markdownlint-cli2'
    require_text "$assets_root/pom.xml" 'format-markdown'
    require_text "$assets_root/pom.xml" '--fix'
    reject_file_contains "$assets_root/pom.xml" 'docs/scripts/check-markdown-links.sh'
    reject_file_contains "$assets_root/pom.xml" 'failonerror="false"'
    require_texts "$installer_commands" 'spotlessFiles' './mvnw validate' './mvnw validate -DspotlessFiles' "root=\$(pwd -P)" 'git ls-files -- "*.java"'
    require_text "$installer_hooks" "list_tracked_tree_files"
    reject_file_text "$installer_package" "find \"\$src_dir\" -type f"
    require_texts "$installer_commands" 'comma Java path' 'escaped Java path' 'grep -q' 'comma_file=' 'unsafe_file=' 'paste -sd, -'
    reject_file_contains "$installer_commands" "case \"\$file\" in *,*)"
    require_text "$installer_commands" "s/[][\\\\\\\\.^\$*+?{}()|]/\\\\\\\\&/g"
    reject_file_contains "$root/skills/harness-install/assets/common/.claude/skills/validate/SKILL.md" './mvnw verify'
    reject_file_contains "$assets_root/pom.xml" '<phase>verify</phase>'
    reject_file_contains "$installer_commands" 's#^#.*#'
    reject_file_contains "$assets_root/pom.xml" 'sync-git-hooks'
    require_dir "$assets_root/.githooks"
    require_file "$assets_root/.githooks/pre-commit"
    require_file "$assets_root/.githooks/pre-push"
    sh -n "$assets_root/.githooks/pre-commit"
    require_texts "$assets_root/.githooks/pre-commit" 'git ls-files -- "*.java"' 'grep -q' 'comma_file=' 'unsafe_file=' 'comma Java path' 'escaped Java path' 'paste -sd, -' "s/[][\\\\.^\$*+?{}()|]/\\\\&/g" "./mvnw validate -DspotlessFiles=\"\$files\""
    require_text "$assets_root/.githooks/pre-push" './mvnw verify'
    printf '[maven assets] OK\n' >&2
}

# Require shell assets exist.
assert_shell_assets() {
    assets_root=$root/skills/harness-install/assets/shell
    require_file "$assets_root/scripts/check.sh"
    require_file "$assets_root/scripts/fix.sh"
    reject_file "$assets_root/.editorconfig"
    reject_file "$assets_root/.shellcheckrc"
    require_texts "$assets_root/scripts/check.sh" 'git ls-files -z' 'xargs -0 shellcheck -S warning --' 'xargs -0 shfmt -d -i 4 -ci --' 'command -v markdownlint-cli2' 'skipping Markdown linting'
    reject_file_contains "$assets_root/scripts/check.sh" 'docs/scripts/check-markdown-links.sh'
    require_texts "$assets_root/scripts/fix.sh" 'git ls-files -z' 'command -v markdownlint-cli2' 'skipping Markdown fixes' '--fix' "xargs -0 \"\$shfmt_bin\" -i 4 -ci -w --" 'error: shfmt is required for shell formatting.'
    reject_file_contains "$assets_root/scripts/fix.sh" "skipping shell fixes"
    reject_file_contains "$assets_root/scripts/check.sh" 'sync_git_hooks'
    require_dir "$assets_root/.githooks"
    require_file "$assets_root/.githooks/pre-commit"
    require_file "$assets_root/.githooks/pre-push"
    reject_file_contains "$assets_root/.githooks/pre-commit" 'QUICK'
    printf '[shell assets] OK\n' >&2
}

# Require common assets to render the selected validation command and stay stack-neutral.
#
# @param common_assets_root Path to common assets root.
assert_common_assets_rendered_validation_command() {
    common_assets_root=$1
    require_file "$common_assets_root/.editorconfig"
    require_file "$common_assets_root/docs/README.md"
    require_text "$common_assets_root/docs/README.md" "{{validation_command}}"
    reject_file_text "$common_assets_root/docs/README.md" "| Gradle |"
    reject_file_text "$common_assets_root/docs/README.md" "| Maven |"
    reject_file_text "$common_assets_root/docs/README.md" "| uv |"
    reject_file_text "$common_assets_root/docs/README.md" "| Bun |"
    reject_file_text "$common_assets_root/docs/README.md" "| shell |"
    require_file "$common_assets_root/AGENTS.md"
    require_text "$common_assets_root/AGENTS.md" "{{validation_command}}"

    reject_file_text "$common_assets_root/AGENTS.md" './gradlew ktlintCheck'
    reject_file_text "$common_assets_root/AGENTS.md" 'uv run scripts/check.py'
    reject_file_text "$common_assets_root/AGENTS.md" 'bun run check'
    reject_file_text "$common_assets_root/AGENTS.md" 'sh scripts/check.sh'
    require_file "$common_assets_root/.claude/skills/validate/SKILL.md"
    # Avoid stale per-stack command matrices in common validate guidance.
    reject_file_text "$common_assets_root/.claude/skills/validate/SKILL.md" "| Stack | Command |"
    printf '[common assets] validation rendering OK\n' >&2
    require_file "$common_assets_root/.markdownlint-cli2.jsonc"
    require_file "$common_assets_root/docs/scripts/exec-plan-links.ts"
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" "\"\$schema\""
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"line-length": false'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"no-inline-html": {'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"allowed_elements": ['
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"table_allowed_elements": ['
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"siblings_only": true'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"code-block-style": {'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"style": "fenced"'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"no-emphasis-as-heading": true'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"customRules": ['
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" './docs/scripts/exec-plan-links.ts'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"docs/exec-plans/links": true'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"gitignore": ".gitignore"'
    require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"**/*.md"'
    require_text "$common_assets_root/docs/scripts/exec-plan-links.ts" 'import type { Rule } from "markdownlint@'
    require_text "$common_assets_root/docs/scripts/exec-plan-links.ts" 'parser: "none"'
    require_text "$common_assets_root/docs/scripts/exec-plan-links.ts" 'tech-debt-tracker.md'
    reject_file "$common_assets_root/docs/scripts/check-markdown-links.sh"
    reject_file_text "$root/skills/harness-install/assets" 'docs/scripts/check-markdown-links.sh'
}
