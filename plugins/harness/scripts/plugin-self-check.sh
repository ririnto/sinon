#!/usr/bin/env sh
set -e

root=${CLAUDE_PLUGIN_ROOT:-$(CDPATH= cd "$(dirname "$0")/.." && pwd)}

# :description: Exit with a self-check failure message.
# :param message: Failure message.
fail() {
  message=$1
  printf '%s\n' "$message" >&2
  exit 1
}

# :description: Require a regular file in the plugin package.
# :param path: File path to check.
require_file() {
  path=$1
  [ -f "$path" ] || fail "harness plugin missing required file: $path"
}

# :description: Require a directory in the plugin package.
# :param path: Directory path to check.
require_dir() {
  path=$1
  [ -d "$path" ] || fail "harness plugin missing required directory: $path"
}

# :description: Require a file to be executable.
# :param path: Executable file path to check.
require_executable() {
  path=$1
  [ -x "$path" ] || fail "harness plugin file must be executable: $path"
}

# :description: Require a file tree to be absent.
# :param path: Path that must be absent.
# :param message: Failure message.
require_absent() {
  path=$1
  message=$2
  [ ! -e "$path" ] || fail "$message: $path"
}

# :description: Require a file to contain a fixed string.
# :param path: File path to search.
# :param text: Fixed string to require.
require_text() {
  path=$1
  text=$2
  grep -Fq "$text" "$path" || fail "missing required text in $path: $text"
}

# :description: Require a file tree to not contain a fixed string.
# :param text: Fixed string to reject.
# :param path: File or directory path to search.
reject_text() {
  text=$1
  path=$2
  if grep -R -F -e "$text" "$path"; then
    fail "forbidden text found under $path: $text"
  fi
}

# :description: Require a list of file paths to not contain a fixed string.
# :param text: Fixed string to reject.
# :param paths: File or directory paths.
reject_text_in_paths() {
  text=$1
  shift
  for path in "$@"; do
    reject_text "$text" "$path"
  done
}

# :description: Reject Git hooks path setters while allowing read-only queries.
# :param paths: File or directory paths.
reject_hooks_path_setters() {
  for path in "$@"; do
    if grep -R -E 'git[[:space:]].*config[[:space:]].*core[.]hooksPath' "$path" | grep -F -v 'git config --get core.hooksPath' | grep -F -v 'git config --path --get core.hooksPath' | grep -F -v 'git config --local --get core.hooksPath'; then
      fail "forbidden Git hooks path setter under $path"
    fi
  done
}

# :description: Print files tracked by Git.
# :param path: File or directory path to scan.
package_files() {
  path=$1
  if [ "$path" = "$root" ]; then
    rel=.
  else
    rel=${path#"$root"/}
  fi
  if git -C "$root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    files=$(git -C "$root" ls-files --cached -- "$rel") || fail "cannot enumerate package files: $path"
    printf '%s\n' "$files" | while IFS= read -r file; do
      [ -n "$file" ] || continue
      printf '%s/%s\n' "$root" "$file"
    done
  else
    find "$path" -type f -print
  fi
}

for path in \
  "$root/.claude-plugin/plugin.json" \
  "$root/LICENSE" \
  "$root/README.md" \
  "$root/THIRD_PARTY_NOTICES.md" \
  "$root/scripts/plugin-self-check.sh" \
  "$root/agents/harness-architect.md" \
  "$root/agents/harness-reviewer.md" \
  "$root/agents/harness-validator.md" \
  "$root/skills/harness-install/SKILL.md" \
  "$root/skills/harness-validate/SKILL.md" \
  "$root/skills/harness-evolve/SKILL.md" \
  "$root/skills/harness-install/scripts/install-harness.sh" \
  "$root/skills/harness-install/scripts/detect-stack.sh" \
  "$root/skills/harness-install/templates/common/AGENTS.md" \
  "$root/skills/harness-install/templates/common/ARCHITECTURE.md" \
  "$root/skills/harness-install/templates/common/CLAUDE.md" \
  "$root/skills/harness-install/templates/common/.claude/harness/manifest.json" \
  "$root/skills/harness-install/templates/common/.claude/harness/README.md" \
  "$root/skills/harness-install/templates/common/.claude/harness/git-hooks/pre-commit" \
  "$root/skills/harness-install/templates/common/.claude/harness/git-hooks/pre-push" \
  "$root/skills/harness-install/templates/common/docs/generated/.gitkeep" \
  "$root/skills/harness-install/templates/common/docs/exec-plans/active/.gitkeep" \
  "$root/skills/harness-install/templates/common/docs/exec-plans/completed/.gitkeep" \
  "$root/skills/harness-install/templates/common/.claude/harness/templates/docs/generated-artifact.md.tmpl" \
  "$root/skills/harness-install/templates/bun/.github/workflows/harness.yml.tmpl" \
  "$root/skills/harness-install/templates/bun/.gitlab-ci.yml.tmpl" \
  "$root/skills/harness-install/templates/bun/.claude/harness/bun/harness-validate.ts" \
  "$root/skills/harness-install/templates/gradle/.github/workflows/harness.yml.tmpl" \
  "$root/skills/harness-install/templates/gradle/.gitlab-ci.yml.tmpl" \
  "$root/skills/harness-install/templates/maven/.github/workflows/harness.yml.tmpl" \
  "$root/skills/harness-install/templates/maven/.gitlab-ci.yml.tmpl" \
  "$root/skills/harness-install/templates/uv/.claude/harness/uv/harness_validate.py" \
  "$root/skills/harness-install/templates/uv/.github/workflows/harness.yml.tmpl" \
  "$root/skills/harness-install/templates/uv/.gitlab-ci.yml.tmpl" \
  "$root/skills/harness-install/templates/gradle/.claude/harness/gradle-plugin/src/main/kotlin/ai/harness/gradle/HarnessValidationPlugin.kt" \
  "$root/skills/harness-install/templates/maven/.claude/harness/maven-plugin/src/main/java/ai/harness/maven/HarnessValidateMojo.java"; do
  require_file "$path"
done

for path in \
  "$root/skills/harness-install/templates/bun/.github/workflows/harness.yml.tmpl" \
  "$root/skills/harness-install/templates/bun/.gitlab-ci.yml.tmpl" \
  "$root/skills/harness-install/templates/gradle/.github/workflows/harness.yml.tmpl" \
  "$root/skills/harness-install/templates/gradle/.gitlab-ci.yml.tmpl" \
  "$root/skills/harness-install/templates/maven/.github/workflows/harness.yml.tmpl" \
  "$root/skills/harness-install/templates/maven/.gitlab-ci.yml.tmpl" \
  "$root/skills/harness-install/templates/uv/.github/workflows/harness.yml.tmpl" \
  "$root/skills/harness-install/templates/uv/.gitlab-ci.yml.tmpl"; do
  require_text "$path" '{{validation_command}}'
done

for path in \
  "$root/agents" \
  "$root/scripts" \
  "$root/skills/harness-install/templates/common/.claude/agents" \
  "$root/skills/harness-install/templates/common/.claude/skills" \
  "$root/skills/harness-install/templates/common/.claude/harness/templates/agent" \
  "$root/skills/harness-install/templates/common/.claude/harness/templates/skill" \
  "$root/skills/harness-install/templates/common/.claude/harness/templates/workflow" \
  "$root/skills/harness-install/templates/common/.claude/harness/templates/ci" \
  "$root/skills/harness-install/templates/common/.claude/harness/templates/docs"; do
  require_dir "$path"
done

require_executable "$root/scripts/plugin-self-check.sh"
require_executable "$root/skills/harness-install/scripts/install-harness.sh"
require_executable "$root/skills/harness-install/scripts/detect-stack.sh"
require_executable "$root/skills/harness-install/templates/common/.claude/harness/git-hooks/pre-commit"
require_executable "$root/skills/harness-install/templates/common/.claude/harness/git-hooks/pre-push"

require_absent "$root/hooks" 'top-level Claude hooks runtime surface must not be packaged'

bak_part=bak
backup_suffix=.harness.$bak_part
package_file_list=$(package_files "$root")
if printf '%s\n' "$package_file_list" | grep -F "$backup_suffix"; then
  fail 'raw v6 backup artifact must not be packaged'
fi

template_package_file_list=$(package_files "$root/skills/harness-install/templates")
if printf '%s\n' "$template_package_file_list" | grep -E '(^|/)(db-schema[.]md|__pycache__|target|build|bin|[.]gradle|[.]factorypath|[.]classpath|[.]project|[.]settings)(/|$)|[.]pyc$'; then
  fail 'template tree contains packaged generated or IDE artifacts'
fi

if printf '%s\n' "$package_file_list" | grep -F '/.claude/harness/validate.sh'; then
  fail 'raw v6 generic harness validate.sh must not be packaged'
fi

python3 - "$root" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
plugin = json.loads((root / ".claude-plugin/plugin.json").read_text())
manifest = json.loads((root / "skills/harness-install/templates/common/.claude/harness/manifest.json").read_text())
errors = []
if plugin.get("$schema") != "https://anthropic.com/claude-code/plugin.schema.json":
    errors.append("plugin manifest schema mismatch")
if plugin.get("name") != "harness":
    errors.append("plugin manifest name must be harness")
if plugin.get("author") != {"name": "ririnto"}:
    errors.append("plugin manifest author must be ririnto object")
if plugin.get("skills") != "./skills/":
    errors.append("plugin manifest skills must be ./skills/")
if plugin.get("license") != "Apache-2.0":
    errors.append("plugin manifest license must be Apache-2.0")
if plugin.get("homepage") != "https://github.com/ririnto/sinon/tree/main/plugins/harness":
    errors.append("plugin manifest homepage mismatch")
if plugin.get("repository") != "https://github.com/ririnto/sinon":
    errors.append("plugin manifest repository mismatch")
for forbidden in ("agents", "hooks", "version", "interface"):
    if forbidden in plugin:
        errors.append(f"plugin manifest must not declare {forbidden}")
for group in manifest.get("templateGroups", []):
    if not (root / f"skills/harness-install/templates/common/.claude/harness/templates/{group}").is_dir():
        errors.append(f"manifest template group missing: {group}")
for key in ("requiredFiles", "emptyDirectoryKeepFiles"):
    for item in manifest.get(key, []):
        if not (root / "skills/harness-install/templates/common" / item).is_file():
            errors.append(f"manifest {key} missing file: {item}")
for item in manifest.get("requiredDirectories", []):
    if not (root / "skills/harness-install/templates/common" / item).is_dir():
        errors.append(f"manifest requiredDirectories missing directory: {item}")
if errors:
    for error in errors:
        print(error, file=sys.stderr)
    sys.exit(1)
PY

for path in "$root"/skills/*/SKILL.md; do
  require_text "$path" 'name:'
  require_text "$path" 'description:'
done
for path in "$root"/agents/*.md; do
  require_text "$path" 'description:'
done

require_text "$root/README.md" 'harness-install'
require_text "$root/README.md" 'harness-validate'
require_text "$root/README.md" 'harness-evolve'
require_text "$root/README.md" 'host runtimes that load plugin agents'
require_text "$root/README.md" 'hook template'
require_text "$root/README.md" 'Gradle `pre-commit` runs `harnessValidate`'
require_text "$root/README.md" 'Gradle `pre-push` runs `check`'
require_text "$root/README.md" 'THIRD_PARTY_NOTICES.md'
require_text "$root/README.md" 'skills/harness-install/templates/common/.claude/harness/git-hooks/'
require_text "$root/README.md" 'v6 archive structure'
require_text "$root/skills/harness-install/SKILL.md" 'Gradle pre-commit runs `harnessValidate`, Gradle pre-push runs `check`'
require_text "$root/skills/harness-validate/SKILL.md" 'generated `.claude/harness/git-hooks/pre-push` command marker'
require_text "$root/skills/harness-validate/SKILL.md" 'Manifest drift'
require_text "$root/skills/harness-validate/SKILL.md" 'Generated artifact metadata'
require_text "$root/skills/harness-validate/SKILL.md" 'Unsupported validation command'
require_text "$root/skills/harness-install/templates/common/.claude/skills/harness-validate/SKILL.md" 'manifest drift'
require_text "$root/skills/harness-install/templates/common/.claude/skills/harness-validate/SKILL.md" 'generated-artifact metadata'
require_text "$root/skills/harness-install/templates/common/.claude/skills/harness-validate/SKILL.md" 'unsupported pre-push validation command'
require_text "$root/skills/harness-evolve/SKILL.md" 'active `.git/hooks/pre-commit` and `.git/hooks/pre-push` remain target-local'

generated_doc_package_file_list=$(package_files "$root/skills/harness-install/templates/common/docs/generated")
if printf '%s\n' "$generated_doc_package_file_list" | grep -F '/db-schema.md'; then
  fail 'harness scaffold must not install docs/generated/db-schema.md'
fi

require_absent "$root/hooks" 'top-level Claude hooks runtime surface must not be packaged'
reject_text '"hooks"' "$root/.claude-plugin/plugin.json"
reject_text 'https://json.schemastore.org/claude-code-plugin.json' "$root/.claude-plugin/plugin.json"
reject_text 'https://json.schemastore.org/claude-code-plugin-manifest.json' "$root/.claude-plugin/plugin.json"
reject_text '"skills": "./skills"' "$root/.claude-plugin/plugin.json"
reject_text 'docs/generated/README.md' "$root/skills"
reject_text 'setup-harness' "$root/README.md"

reject_text_in_paths '--hooks install' \
  "$root/README.md" \
  "$root/skills/harness-install/scripts/install-harness.sh" \
  "$root/skills/harness-install/SKILL.md" \
  "$root/skills/harness-validate/SKILL.md"
reject_text_in_paths '--hooks path' \
  "$root/skills/harness-install/scripts/install-harness.sh" \
  "$root/skills/harness-install/SKILL.md"
reject_hooks_path_setters \
  "$root/skills/harness-install/scripts/install-harness.sh" \
  "$root/skills/harness-install/SKILL.md"
reject_text_in_paths "$backup_suffix" \
  "$root/skills/harness-install/scripts/install-harness.sh" \
  "$root/skills/harness-install/SKILL.md" \
  "$root/README.md" \
  "$root/skills/harness-install/templates/common"
reject_text_in_paths 'install_git_hook_path' \
  "$root/skills/harness-install/scripts/install-harness.sh" \
  "$root/skills/harness-install/SKILL.md"

require_text "$root/skills/harness-install/scripts/install-harness.sh" 'Harness generated hook: pre-commit'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'Harness stage: compliance'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'Harness generated hook: pre-push'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'Harness stage: full-validation'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'Harness validation command:'
require_text "$root/skills/harness-install/scripts/install-harness.sh" './gradlew check'
require_text "$root/skills/harness-install/scripts/install-harness.sh" './gradlew harnessValidate'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'resolve_existing_hooks_path'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'refusing to copy non-generated hook source'
require_text "$root/skills/harness-install/templates/uv/.claude/harness/uv/harness_validate.py" 'pre-commit hook must not run full stack validation commands'
require_text "$root/skills/harness-install/templates/bun/.claude/harness/bun/harness-validate.ts" 'pre-commit hook must not run full stack validation commands'
require_text "$root/skills/harness-install/templates/gradle/.claude/harness/gradle-plugin/src/main/kotlin/ai/harness/gradle/HarnessValidationPlugin.kt" 'pre-commit hook must declare Gradle harness validation command'
require_text "$root/skills/harness-install/templates/maven/.claude/harness/maven-plugin/src/main/java/ai/harness/maven/HarnessValidateMojo.java" 'pre-commit hook must not run full stack validation commands'
require_text "$root/skills/harness-install/templates/uv/.claude/harness/uv/harness_validate.py" 'pre-push hook declares unsupported validation command'
require_text "$root/skills/harness-install/templates/bun/.claude/harness/bun/harness-validate.ts" 'pre-push hook declares unsupported validation command'
require_text "$root/skills/harness-install/templates/gradle/.claude/harness/gradle-plugin/src/main/kotlin/ai/harness/gradle/HarnessValidationPlugin.kt" 'pre-push hook declares unsupported validation command'
require_text "$root/skills/harness-install/templates/maven/.claude/harness/maven-plugin/src/main/java/ai/harness/maven/HarnessValidateMojo.java" 'pre-push hook declares unsupported validation command'
require_text "$root/skills/harness-install/templates/gradle/.claude/harness/gradle-plugin/src/main/kotlin/ai/harness/gradle/HarnessValidationPlugin.kt" './gradlew check'
require_text "$root/skills/harness-install/templates/gradle/.claude/harness/gradle-plugin/src/main/kotlin/ai/harness/gradle/HarnessValidationPlugin.kt" './gradlew harnessValidate'
require_text "$root/skills/harness-install/templates/uv/.claude/harness/uv/harness_validate.py" 'pre-push hook must declare Harness validation command'
require_text "$root/skills/harness-install/templates/bun/.claude/harness/bun/harness-validate.ts" 'pre-push hook must declare Harness validation command'
require_text "$root/skills/harness-install/templates/gradle/.claude/harness/gradle-plugin/src/main/kotlin/ai/harness/gradle/HarnessValidationPlugin.kt" 'pre-push hook must declare Harness validation command'
require_text "$root/skills/harness-install/templates/maven/.claude/harness/maven-plugin/src/main/java/ai/harness/maven/HarnessValidateMojo.java" 'pre-push hook must declare Harness validation command'

template_roots="
$root/skills/harness-install/templates/common
$root/skills/harness-install/templates/bun
$root/skills/harness-install/templates/gradle
$root/skills/harness-install/templates/maven
$root/skills/harness-install/templates/uv
"

for template_root in $template_roots; do
  template_root_files=$(package_files "$template_root")
  for path in $(printf '%s\n' "$template_root_files" | grep -F -v '.tmpl'); do
    if grep -E '\{\{[^}]+\}\}' "$path"; then
      fail "unresolved template token outside .tmpl asset: $path"
    fi
  done
done

for text in 'example-' 'Describe ' 'Describe...' 'TODO' 'TBD' 'replace-with-stack-specific'; do
  for template_root in $template_roots; do
    template_root_files=$(package_files "$template_root")
    for path in $(printf '%s\n' "$template_root_files" | grep -F -v '.tmpl'); do
      case "$path" in
        */harness_validate.py|*/harness-validate.ts|*/HarnessValidationPlugin.kt|*/HarnessValidateMojo.java)
          continue
          ;;
      esac
      if grep -F "$text" "$path"; then
        fail "forbidden template marker in template asset: $text"
      fi
    done
  done
done

exit 0
