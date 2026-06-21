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

# Require stack Claude settings and EnterWorktree setup commands.
#
# @param path Settings path.
# @param command Stack setup command.
# @return Exits non-zero when settings are missing the required hook contract.
assert_stack_settings() {
  path=$1
  command=$2
  require_file "$path"
  assert_common_settings "$path"
  require_text "$path" '"command": "codegraph init; codegraph index"'
  if [ -n "$command" ]; then
    require_text "$path" "\"command\": \"$command\""
  fi
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

# Require cross-platform OS, editor, log, and temporary-file ignores.
#
# @param path Gitignore path.
# @return Exits non-zero when common ignore rules are missing.
assert_common_gitignore_entries() {
  path=$1
  require_texts "$path" ".DS_Store" ".com.apple.timemachine.supported" ".PKInstallSandboxManager" "[Dd]esktop.ini" "*.msix" "*.lnk" ".fuse_hidden*" ".idea/" ".vscode/"
  require_texts "$path" "logs/" "log/" "*.log" "*.tmp" ".tmp/" "tmp/" "temp/" "*.local.*"
  require_text "$path" ".claude/worktrees/"
}

# Require stack-specific worktree include rules to stay portable.
#
# @param path Worktree include path.
# @return Exits non-zero when required patterns are missing or unsafe patterns are present.
assert_stack_worktreeinclude() {
  path=$1
  require_file "$path"
  require_texts "$path" ".env" ".env.*" "*.local" "*.local.*"
  for pattern in \
    ".codegraph/" \
    ".gradle/" \
    "node_modules/" \
    ".bun/" \
    ".venv/" \
    ".cache/" \
    ".mvn/"; do
    if grep -Fq -- "$pattern" "$path"; then
      printf '%s\n' "[worktreeinclude] unsafe broad copy pattern in $path: $pattern" >&2
      exit 1
    fi
  done
}

# Require installed instructions to rely on automatic AGENTS.md loading.
#
# @param path Markdown path.
# @return Exits non-zero when the document tells agents to read AGENTS.md manually.
assert_no_manual_agents_read() {
  path=$1
  pattern='(Read|Inspect) .AGENTS[.]md.|.AGENTS[.]md., .ARCHITECTURE[.]md.'
  if grep -Eq "$pattern" "$path"; then
    printf '%s\n' "[agents] do not instruct manual AGENTS.md reading: $path" >&2
    exit 1
  fi
}

# Require JVM and Eclipse-generated ignores.
#
# @param path Gitignore path.
# @return Exits non-zero when JVM or Eclipse ignore rules are missing.
assert_jvm_gitignore_entries() {
  path=$1
  require_texts "$path" "bin/" "*.class" ".metadata" ".project" ".classpath" ".settings/" ".factorypath" ".externalToolBuilders/" "*.launch"
}

# Require Node and Bun generated ignores.
#
# @param path Gitignore path.
# @return Exits non-zero when Node or Bun ignore rules are missing.
assert_node_gitignore_entries() {
  path=$1
  require_texts "$path" "node_modules/" "coverage/" "*.tsbuildinfo" "report.[0-9]*.[0-9]*.[0-9]*.[0-9]*.json"
}

# Require Python generated ignores.
#
# @param path Gitignore path.
# @return Exits non-zero when Python ignore rules are missing.
assert_python_gitignore_entries() {
  path=$1
  require_texts "$path" "__pycache__/" "*.py[codz]" "bin/" ".ruff_cache/" ".pytest_cache/" ".venv/"
}

# Require Gradle harness assets.
#
# @return Exits non-zero when required Gradle assets are missing.
assert_gradle_assets() {
  assets_root=$root/skills/harness-install/assets/gradle
  require_file "$assets_root/.gitignore"
  require_file "$assets_root/build.gradle.kts"
  require_file "$assets_root/settings.gradle.kts"
  assert_stack_settings "$assets_root/.claude/settings.json" "./gradlew help"
  assert_stack_worktreeinclude "$assets_root/.worktreeinclude"
  require_file "$assets_root/gradle/libs.versions.toml"
  require_file "$assets_root/buildSrc/build.gradle.kts"
  require_file "$assets_root/buildSrc/settings.gradle.kts"
  require_dir "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint"
  require_file "$assets_root/buildSrc/src/main/resources/META-INF/services/com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3"
  require_file "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/RuleSetProvider.kt"
  require_texts "$assets_root/build.gradle.kts" "checkMarkdown" "tasks.named(\"ktlintCheck\")" "markdownlint-cli2" "bun add -g markdownlint-cli2"
  require_texts "$assets_root/settings.gradle.kts" "tasks(\"ktlintCheck\")" "createHooks()"
  require_text "$assets_root/.editorconfig" "charset = utf-8"
  require_text "$assets_root/.editorconfig" "ij_continuation_indent_size = 4"
  require_text "$assets_root/.editorconfig" "[{*.bash,*.sh,*.zsh}]"
  require_texts "$assets_root/buildSrc/src/main/kotlin/com/ririnto/sinon/ktlint/RuleSetProvider.kt" "SlfDirectLoggingKtlintRule()" "TerminalBranchWhenKtlintRule()" "PublicDeclarationDocCommentKtlintRule()"
  assert_stack_gitignore_scope "$assets_root/.gitignore"
  assert_common_gitignore_entries "$assets_root/.gitignore"
  assert_jvm_gitignore_entries "$assets_root/.gitignore"
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
  assert_stack_settings "$assets_root/.claude/settings.json" "bun install"
  assert_stack_worktreeinclude "$assets_root/.worktreeinclude"
  require_file "$assets_root/scripts/tsdoc-plugin.ts"
  require_dir "$assets_root/.husky"
  require_file "$assets_root/.husky/pre-commit"
  require_file "$assets_root/.husky/pre-push"
  require_texts "$assets_root/package.json" '"prepare": "husky"' '"check:markdownlint-cli2": "markdownlint-cli2"' '"check:ultracite": "ultracite check"'
  require_text "$assets_root/oxlint.config.ts" 'tsdoc/require-export-tsdoc'
  require_texts "$assets_root/.husky/pre-commit" "bun typecheck" "bun run check"
  require_texts "$assets_root/.husky/pre-push" "bun typecheck" "bun run check" "bun test"
  assert_stack_gitignore_scope "$assets_root/.gitignore"
  assert_common_gitignore_entries "$assets_root/.gitignore"
  assert_node_gitignore_entries "$assets_root/.gitignore"
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
  assert_stack_settings "$assets_root/.claude/settings.json" "uv sync"
  assert_stack_worktreeinclude "$assets_root/.worktreeinclude"
  require_file "$assets_root/.pre-commit-config.yaml"
  require_texts "$assets_root/scripts/check.py" 'shutil.which("markdownlint-cli2")' "skipping Markdown linting" '"ruff>=0.15.18,<0.16.0"'
  require_texts "$assets_root/scripts/fix.py" 'shutil.which("markdownlint-cli2")' "skipping Markdown fixes" '"--fix"'
  require_texts "$assets_root/.pre-commit-config.yaml" "repo: local" "- id: lint" "- id: full-lint"
  assert_stack_gitignore_scope "$assets_root/.gitignore"
  assert_common_gitignore_entries "$assets_root/.gitignore"
  assert_python_gitignore_entries "$assets_root/.gitignore"
  printf '[uv assets] OK\n' >&2
}

# Require Maven harness assets.
#
# @return Exits non-zero when required Maven assets are missing.
assert_maven_assets() {
  assets_root=$root/skills/harness-install/assets/maven
  require_file "$assets_root/.gitignore"
  require_file "$assets_root/pom.xml"
  assert_stack_settings \
    "$assets_root/.claude/settings.json" \
    "./mvnw -q -DskipTests dependency:go-offline"
  assert_stack_worktreeinclude "$assets_root/.worktreeinclude"
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
  require_text "$assets_root/.editorconfig" "charset = utf-8"
  require_text "$assets_root/.editorconfig" "ij_continuation_indent_size = 4"
  assert_stack_gitignore_scope "$assets_root/.gitignore"
  assert_common_gitignore_entries "$assets_root/.gitignore"
  assert_jvm_gitignore_entries "$assets_root/.gitignore"
  printf '[maven assets] OK\n' >&2
}

# Require shell harness assets.
#
# @return Exits non-zero when required shell assets are missing.
assert_shell_assets() {
  assets_root=$root/skills/harness-install/assets/shell
  require_file "$assets_root/.gitignore"
  assert_stack_settings "$assets_root/.claude/settings.json" ""
  assert_stack_worktreeinclude "$assets_root/.worktreeinclude"
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
  assert_common_gitignore_entries "$assets_root/.gitignore"
  printf '[shell assets] OK\n' >&2
}

# Require common Claude settings keys.
#
# @param settings_file Path to Claude settings JSON.
# @return Exits non-zero when durable settings keys are missing.
assert_common_settings() {
  settings_file=$1
  python3 "$root/scripts/plugin-self-check/asset_checks_settings.py" "$settings_file"
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
  require_file "$common_assets_root/.mcp.json"
  require_file "$common_assets_root/.editorconfig"
  require_file "$common_assets_root/.markdownlint-cli2.jsonc"
  require_file "$common_assets_root/.codegraph/.gitignore"
  require_file "$common_assets_root/.claude/agents/implementation-agent.md"
  require_file "$common_assets_root/.claude/agents/review-agent.md"
  require_file "$common_assets_root/.claude/skills/review/SKILL.md"
  require_file "$common_assets_root/.claude/skills/validate/SKILL.md"
  assert_no_manual_agents_read "$common_assets_root/AGENTS.md"
  assert_no_manual_agents_read "$common_assets_root/.claude/agents/implementation-agent.md"
  assert_no_manual_agents_read "$common_assets_root/.claude/agents/review-agent.md"
  assert_no_manual_agents_read "$common_assets_root/.claude/skills/review/SKILL.md"
  assert_no_manual_agents_read "$common_assets_root/.claude/skills/validate/SKILL.md"
  assert_no_manual_agents_read "$common_assets_root/docs/templates/agent/AGENT.md"
  assert_no_manual_agents_read "$common_assets_root/docs/templates/skill/SKILL.md"
  require_file "$common_assets_root/scripts/no-box-drawing.ts"
  require_file "$common_assets_root/scripts/exec-plan-links.ts"
  require_file "$common_assets_root/scripts/docs-root-files.ts"
  if [ -e "$common_assets_root/docs/git-hooks" ]; then
    printf '%s\n' "[common assets] docs/git-hooks must not exist; hooks are stack assets" >&2
    exit 1
  fi
  require_text "$common_assets_root/CLAUDE.md" "# CLAUDE.md"
  require_text "$common_assets_root/CLAUDE.md" "@AGENTS.md"
  require_text "$common_assets_root/AGENTS.md" "\`.agents/skills/\` MUST be \`-> .claude/skills/\`"
  require_text "$common_assets_root/AGENTS.md" "\`.agents/agents/\` MUST NOT exist"
  require_text "$common_assets_root/AGENTS.md" "\`.codex/agents/\` MUST be \`-> .claude/agents/\`"
  require_texts "$common_assets_root/.mcp.json" '"codegraph"' '"type": "stdio"' '"command": "codegraph"' '"serve"' '"--mcp"'
  require_texts "$common_assets_root/.codegraph/.gitignore" "CodeGraph data files" "*" "!.gitignore"
  require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"docs/no-box-drawing": true'
  require_text "$common_assets_root/.markdownlint-cli2.jsonc" '"docs/root-files": true'
  require_text "$common_assets_root/.markdownlint-cli2.jsonc" './scripts/no-box-drawing.ts'
  require_text "$common_assets_root/.markdownlint-cli2.jsonc" './scripts/docs-root-files.ts'
  require_text "$common_assets_root/scripts/no-box-drawing.ts" 'docs/no-box-drawing'
  require_text "$common_assets_root/scripts/no-box-drawing.ts" '\u2500-\u257F'
  require_text "$common_assets_root/scripts/docs-root-files.ts" 'docs/root-files'
  require_text "$common_assets_root/scripts/docs-root-files.ts" 'allowedDocsDirectories'
  printf '[common assets] OK\n' >&2
}
