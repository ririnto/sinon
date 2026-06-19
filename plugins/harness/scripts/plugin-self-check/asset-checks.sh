#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${root:?}

# Require a file to contain every given text fragment.
#
# @param path File path to inspect.
# @param text Text fragment to require.
# @return Exits non-zero when any required text is missing.
require_texts() {
  path=$1
  shift
  for text in "$@"; do
    require_text "$path" "$text"
  done
}

# Require stack gitignore files to keep common local scratch paths out of Git.
#
# @param path Gitignore path.
# @return Exits non-zero when the gitignore misses the common scratch path.
assert_stack_gitignore_scope() {
  path=$1
  require_file "$path"
  require_text "$path" ".tmp/"
}

# Require Gradle harness assets.
#
# @return Exits non-zero when required Gradle assets are missing.
assert_gradle_assets() {
  assets_root=$root/skills/harness-install/assets/gradle
  require_file "$assets_root/.gitignore"
  require_file "$assets_root/build.gradle.kts"
  require_file "$assets_root/settings.gradle.kts"
  require_file "$assets_root/scripts/worktree-post-create.sh"
  require_executable "$assets_root/scripts/worktree-post-create.sh"
  require_file "$assets_root/gradle/libs.versions.toml"
  require_file "$assets_root/buildSrc/build.gradle.kts"
  require_file "$assets_root/buildSrc/settings.gradle.kts"
  require_dir "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint"
  require_file "$assets_root/buildSrc/src/main/resources/META-INF/services/com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3"
  require_file "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/RuleSetProvider.kt"
  require_texts "$assets_root/build.gradle.kts" "checkMarkdown" "tasks.named(\"ktlintCheck\")" "markdownlint-cli2" "bun add -g markdownlint-cli2"
  require_texts "$assets_root/settings.gradle.kts" "tasks(\"ktlintCheck\")" "createHooks()"
  sh -n "$assets_root/scripts/worktree-post-create.sh"
  require_text "$assets_root/scripts/worktree-post-create.sh" "./gradlew"
  require_text "$assets_root/.editorconfig" "[{*.bash,*.sh,*.zsh}]"
  require_texts "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/RuleSetProvider.kt" "SlfDirectLoggingKtlintRule()" "TerminalBranchWhenKtlintRule()" "PublicDeclarationDocCommentKtlintRule()"
  assert_stack_gitignore_scope "$assets_root/.gitignore"
  printf '[gradle assets] OK\n' >&2
}

# Require Bun harness assets.
#
# @return Exits non-zero when required Bun assets are missing.
assert_bun_assets() {
  assets_root=$root/skills/harness-install/assets/bun
  require_file "$assets_root/.gitignore"
  require_file "$assets_root/package.json"
  require_file "$assets_root/oxlint.config.ts"
  require_file "$assets_root/oxfmt.config.ts"
  require_file "$assets_root/scripts/worktree-post-create.sh"
  require_executable "$assets_root/scripts/worktree-post-create.sh"
  require_file "$assets_root/scripts/tsdoc-plugin.ts"
  require_dir "$assets_root/.husky"
  require_file "$assets_root/.husky/pre-commit"
  require_file "$assets_root/.husky/pre-push"
  require_texts "$assets_root/package.json" '"prepare": "husky"' '"check:markdownlint-cli2": "markdownlint-cli2"' '"check:ultracite": "ultracite check"'
  require_text "$assets_root/oxlint.config.ts" 'tsdoc/require-export-tsdoc'
  sh -n "$assets_root/scripts/worktree-post-create.sh"
  require_text "$assets_root/scripts/worktree-post-create.sh" "bun install"
  require_texts "$assets_root/.husky/pre-commit" "bun typecheck" "bun run check"
  require_texts "$assets_root/.husky/pre-push" "bun typecheck" "bun run check" "bun test"
  assert_stack_gitignore_scope "$assets_root/.gitignore"
  printf '[bun assets] OK\n' >&2
}

# Require uv harness assets.
#
# @return Exits non-zero when required uv assets are missing.
assert_uv_assets() {
  assets_root=$root/skills/harness-install/assets/uv
  require_file "$assets_root/.gitignore"
  require_file "$assets_root/ruff.toml"
  require_file "$assets_root/scripts/check.py"
  require_file "$assets_root/scripts/fix.py"
  require_file "$assets_root/.pre-commit-config.yaml"
  require_texts "$assets_root/scripts/check.py" 'shutil.which("markdownlint-cli2")' "skipping Markdown linting" '"ruff>=0.15.16,<0.16.0"'
  require_texts "$assets_root/scripts/fix.py" 'shutil.which("markdownlint-cli2")' "skipping Markdown fixes" '"--fix"'
  require_texts "$assets_root/.pre-commit-config.yaml" "repo: local" "- id: lint" "- id: full-lint"
  assert_stack_gitignore_scope "$assets_root/.gitignore"
  printf '[uv assets] OK\n' >&2
}

# Require Maven harness assets.
#
# @return Exits non-zero when required Maven assets are missing.
assert_maven_assets() {
  assets_root=$root/skills/harness-install/assets/maven
  require_file "$assets_root/.gitignore"
  require_file "$assets_root/pom.xml"
  require_dir "$assets_root/.githooks"
  require_file "$assets_root/.githooks/pre-commit"
  require_file "$assets_root/.githooks/pre-push"
  sh -n "$assets_root/.githooks/pre-commit"
  sh -n "$assets_root/.githooks/pre-push"
  require_text "$assets_root/pom.xml" "<artifactId>maven-checkstyle-plugin</artifactId>"
  require_text "$assets_root/pom.xml" "<artifactId>spotless-maven-plugin</artifactId>"
  require_text "$assets_root/pom.xml" "<core.hooksPath>.githooks/</core.hooksPath>"
  require_text "$assets_root/pom.xml" "markdownlint-cli2"
  require_texts "$assets_root/.githooks/pre-commit" 'git ls-files -- "*.java"' 'spotlessFiles' "./mvnw validate -DspotlessFiles=\"\$files\""
  require_text "$assets_root/.githooks/pre-push" "./mvnw verify"
  require_text "$assets_root/.editorconfig" "[{*.bash,*.sh,*.zsh}]"
  assert_stack_gitignore_scope "$assets_root/.gitignore"
  printf '[maven assets] OK\n' >&2
}

# Require shell harness assets.
#
# @return Exits non-zero when required shell assets are missing.
assert_shell_assets() {
  assets_root=$root/skills/harness-install/assets/shell
  require_file "$assets_root/.gitignore"
  require_file "$assets_root/scripts/check.sh"
  require_file "$assets_root/scripts/fix.sh"
  require_dir "$assets_root/.githooks"
  require_file "$assets_root/.githooks/pre-commit"
  require_file "$assets_root/.githooks/pre-push"
  sh -n "$assets_root/scripts/check.sh"
  sh -n "$assets_root/scripts/fix.sh"
  sh -n "$assets_root/.githooks/pre-commit"
  sh -n "$assets_root/.githooks/pre-push"
  require_texts "$assets_root/scripts/check.sh" "shellcheck -S warning" "shfmt -d" "markdownlint-cli2"
  require_texts "$assets_root/scripts/fix.sh" "shfmt" "markdownlint-cli2" "--fix"
  assert_stack_gitignore_scope "$assets_root/.gitignore"
  printf '[shell assets] OK\n' >&2
}

# Require common target harness assets.
#
# @param common_assets_root Path to common assets root.
# @return Exits non-zero when common target assets violate harness structure.
assert_common_assets_structure() {
  common_assets_root=$1
  require_file "$common_assets_root/AGENTS.md"
  require_file "$common_assets_root/CLAUDE.md"
  require_file "$common_assets_root/ARCHITECTURE.md"
  require_file "$common_assets_root/WORKFLOW.md"
  require_file "$common_assets_root/WORKFLOW.github.md"
  require_file "$common_assets_root/WORKFLOW.gitlab.md"
  require_file "$common_assets_root/WORKFLOW.none.md"
  require_file "$common_assets_root/.editorconfig"
  require_file "$common_assets_root/.markdownlint-cli2.jsonc"
  require_file "$common_assets_root/.claude/settings.json"
  require_file "$common_assets_root/.claude/agents/implementation-agent.md"
  require_file "$common_assets_root/.claude/agents/review-agent.md"
  require_file "$common_assets_root/.claude/skills/review/SKILL.md"
  require_file "$common_assets_root/.claude/skills/validate/SKILL.md"
  require_file "$common_assets_root/scripts/exec-plan-links.ts"
  require_file "$common_assets_root/scripts/docs-root-files.ts"
  require_text "$common_assets_root/CLAUDE.md" "# CLAUDE.md"
  require_text "$common_assets_root/CLAUDE.md" "@AGENTS.md"
  require_text "$common_assets_root/.claude/settings.json" "\"\$schema\": \"https://json.schemastore.org/claude-code-settings.json\""
  require_text "$common_assets_root/.claude/settings.json" '"CLAUDE_BASH_MAINTAIN_PROJECT_WORKING_DIR": "1"'
  require_text "$common_assets_root/.claude/settings.json" '"includeCoAuthoredBy": false'
  require_text "$common_assets_root/.claude/settings.json" '"includeGitInstructions": false'
  require_text "$common_assets_root/.claude/settings.json" '"showClearContextOnPlanAccept": true'
  require_text "$common_assets_root/.claude/settings.json" "\"command\": \"sh -lc"
  require_text "$common_assets_root/.claude/settings.json" "jq -r"
  require_text "$common_assets_root/.claude/settings.json" "\$HOME/.claude/worktrees"
  require_text "$common_assets_root/.claude/settings.json" "git -C"
  require_text "$common_assets_root/.claude/settings.json" "worktree add"
  require_text "$common_assets_root/.claude/settings.json" "scripts/worktree-post-create.sh"
  require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"docs/root-files": true'
  require_text "$common_assets_root/.markdownlint-cli2.jsonc" './scripts/docs-root-files.ts'
  require_text "$common_assets_root/scripts/docs-root-files.ts" 'docs/root-files'
  require_text "$common_assets_root/WORKFLOW.github.md" "gh issue create"
  require_text "$common_assets_root/WORKFLOW.github.md" "--body-file"
  require_text "$common_assets_root/WORKFLOW.gitlab.md" "glab api --method POST projects/:fullpath/issues"
  require_text "$common_assets_root/WORKFLOW.gitlab.md" "--field description=@.tmp/issue.md"
  require_text "$common_assets_root/WORKFLOW.gitlab.md" "--field description=@.tmp/review.md"
  printf '[common assets] OK\n' >&2
}
