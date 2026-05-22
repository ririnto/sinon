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

# Require a file to be executable.
#
# @param path Executable file path to check.
# @exit Exits with status 1 when file is not executable.
require_executable() {
  path=$1
  if [ ! -x "$path" ]; then
    printf '%s\n' "[require_executable] file must be executable: $path" >&2
    printf '%s\n' "  hint: run 'git add -u' or 'git add --chmod=+x' to fix permissions" >&2
    exit 1
  fi
}

# Require a file tree to be absent.
#
# @param path Path that must be absent.
# @param message Failure message.
# @exit Exits with status 1 when path exists.
require_absent() {
  path=$1
  message=$2
  if [ -e "$path" ]; then
    printf '%s\n' "$message: $path" >&2
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
  if ! grep -Fq "$text" "$path"; then
    printf '%s\n' "[require_text] missing required text in $path: $text" >&2
    exit 1
  fi
}

# Require a file tree to not contain a fixed string.
#
# @param text Fixed string to reject.
# @param path File or directory path to search.
# @exit Exits with status 1 when text is found.
reject_text() {
  text=$1
  path=$2
  if grep -R -F -e "$text" "$path"; then
    printf '%s\n' "[reject_text] forbidden text found under $path: $text" >&2
    exit 1
  fi
}

# Require a list of file paths to not contain a fixed string.
#
# @param text Fixed string to reject.
# @param paths File or directory paths.
# @exit Exits with status 1 when text is found.
reject_text_in_paths() {
  text=$1
  shift
  for path in "$@"; do
    reject_text "$text" "$path"
  done
}

# Reject Git hooks path setters while allowing read-only queries.
#
# @param paths File or directory paths.
# @exit Exits with status 1 when setter is found.
reject_hooks_path_setters() {
  for path in "$@"; do
    if grep -R -E 'git[[:space:]].*config[[:space:]].*core[.]hooksPath' "$path" | grep -F -v 'git config --get core.hooksPath' | grep -F -v 'git config --path --get core.hooksPath' | grep -F -v 'git config --local --get core.hooksPath'; then
      printf '%s\n' "[reject_hooks_path_setters] forbidden Git hooks path setter under $path" >&2
      exit 1
    fi
  done
}

# Print files tracked by Git.
#
# @param path File or directory path to scan.
# @return Writes full paths of tracked files.
package_files() {
  path=$1
  if [ "$path" = "$root" ]; then
    rel=.
  else
    rel=${path#"$root"/}
  fi
  git_probe=$(git -C "$root" rev-parse --is-inside-work-tree 2>&1)
  if [ -n "$git_probe" ]; then
    unset git_probe
    files=$(git -C "$root" ls-files --cached -- "$rel") || { printf '%s\n' "[package_files] cannot enumerate package files: $path" >&2; exit 1; }
    printf '%s\n' "$files" | while IFS= read -r file; do
      if [ -n "$file" ]; then
        printf '%s/%s\n' "$root" "$file"
      fi
    done
  else
    unset git_probe
    find "$path" -type f -print
  fi
}

# Require a shell file to begin with the canonical harness header.
#
# @param path Shell file path to check.
# @exit Exits with status 1 when header is invalid.
require_shell_header() {
  path=$1
  line1=$(sed -n '1p' "$path")
  line2=$(sed -n '2p' "$path")
  line3=$(sed -n '3p' "$path")
  if [ "$line1" != '#!/usr/bin/env sh' ]; then
    printf '%s\n' "[require_shell_header] missing #!/usr/bin/env sh on line 1: $path" >&2
    exit 1
  fi
  if [ "$line2" != '# -*- coding: utf-8 -*-' ]; then
    printf '%s\n' "[require_shell_header] missing utf-8 coding declaration on line 2: $path" >&2
    exit 1
  fi
  if [ "$line3" != 'set -e' ]; then
    printf '%s\n' "[require_shell_header] missing set -e on line 3: $path" >&2
    exit 1
  fi
}

# Reject any 'set -u' family usage inside a shell file.
#
# @param path Shell file path to check.
# @exit Exits with status 1 when set -u is found.
reject_set_u_in_shell() {
  path=$1
  if grep -qE '^[[:space:]]*set[[:space:]]+-[A-Za-z]*u' "$path"; then
    printf '%s\n' "[reject_set_u_in_shell] shell file must not use 'set -u': $path" >&2
    exit 1
  fi
}

# Reject bare bracket conditionals not inside an 'if' or 'while' construct.
#
# @param path Shell file path to check.
# @exit Exits with status 1 when bare bracket test is found.
reject_bare_test_in_shell() {
  path=$1
  if grep -qnE '^[[:space:]]*\[' "$path"; then
    match=$(grep -nE '^[[:space:]]*\[' "$path" | head -n 1)
    printf '%s\n' "[reject_bare_test_in_shell] bare bracket test (use 'if' instead): $path:$match" >&2
    exit 1
  fi
}

# Reject reStructuredText-style ':description:' / ':param X:' shell docstrings.
#
# @param path Shell file path to check.
# @exit Exits with status 1 when a non-JSDoc docstring shape is found.
reject_nonjsdoc_docstring_in_shell() {
  path=$1
  if grep -qnE '^#[[:space:]]*:(description|param|return)' "$path"; then
    printf '%s\n' "[reject_nonjsdoc_docstring_in_shell] shell docstrings MUST use JSDoc @param/@return form: $path" >&2
    exit 1
  fi
}

# Reject output redirects targeting /dev/null.
#
# @param path Shell file path to check.
# @exit Exits with status 1 when /dev/null redirect is found.
reject_devnull_redirect_in_shell() {
  path=$1
  if grep -qnE '([12]?>|&>|>>)[[:space:]]*/dev/null' "$path"; then
    printf '%s\n' "[reject_devnull_redirect_in_shell] must not redirect output to /dev/null: $path" >&2
    exit 1
  fi
}

# Reject bash-extension '[[ ]]' tests; require POSIX '[ ]'.
#
# @param path Shell file path to check.
# @exit Exits with status 1 when [[ ]] is found.
reject_double_bracket_in_shell() {
  path=$1
  awk '/^[[:space:]]*(if|elif|while|until)[[:space:]]+\[\[/ { print FILENAME":"NR":"$0; exit 1; }' "$path" && return 0
  printf '%s\n' "[reject_double_bracket_in_shell] uses non-POSIX '[[ ]]' (use POSIX '[ ]' or 'case'): $path" >&2
  exit 1
}

# Reject blank lines inside any function body in a shell file.
#
#     Uses a Python helper to parse the file with shfmt (AST-based) if available,
#     or falls back to the original improved heuristic that tracks brace depth
#     and heredoc state. Blank lines inside heredocs are skipped.
#
# @param path Shell file path to check.
# @exit Exits with status 1 when blank line in function is found.
reject_blank_line_in_function() {
  path=$1
  shfmt_probe=$(command -v shfmt 2>&1 || true)
  if [ -n "$shfmt_probe" ]; then
    unset shfmt_probe
    python3 - "$path" <<'PYAST'
import json
import sys
import subprocess
try:
  path = sys.argv[1]
  try:
    ast_json = subprocess.check_output(['shfmt', '-tojson'], stdin=open(path, 'rb'), stderr=subprocess.PIPE, text=True)
    ast = json.loads(ast_json)
  except subprocess.CalledProcessError:
    sys.exit(0)
  def check_func(node):
    violations = []
    if node.get('Type') == 'FuncDecl':
      body = node.get('Body', {})
      stmts = body.get('Stmts', [])
      if len(stmts) < 2:
        return violations
      for i in range(len(stmts) - 1):
        start_line = stmts[i].get('End', {}).get('Line', 0)
        end_line = stmts[i+1].get('Pos', {}).get('Line', 0)
        if start_line < end_line:
          for blank_line_num in range(start_line + 1, end_line):
            violations.append((path, blank_line_num))
    for key, val in node.items():
      if isinstance(val, dict):
        violations.extend(check_func(val))
      elif isinstance(val, list):
        for item in val:
          if isinstance(item, dict):
            violations.extend(check_func(item))
    return violations
  violations = check_func(ast)
  if violations:
    for file, line in violations:
      print(f"{file}:{line}: blank line inside function body", file=sys.stderr)
    sys.exit(1)
except Exception:
  sys.exit(0)
PYAST
    return $?
  fi
  unset shfmt_probe
  state=outside
  brace_depth=0
  heredoc_tag=
  heredoc_strip=0
  line_no=0
  while IFS= read -r line || [ -n "$line" ]; do
    line_no=$((line_no + 1))
    if [ -n "$heredoc_tag" ]; then
      compare=$line
      if [ "$heredoc_strip" -eq 1 ]; then
        compare=$(printf '%s' "$compare" | sed 's/^	*//')
      fi
      if [ "$compare" = "$heredoc_tag" ]; then
        heredoc_tag=
        heredoc_strip=0
      fi
      continue
    fi
    case "$line" in
      *'<<'*)
        opener=$(printf '%s' "$line" | sed -nE 's/.*<<(-?)[ \t]*([\x27"]?)([A-Za-z_][A-Za-z0-9_]*)\2.*/\1|\3/p')
        if [ -z "$opener" ]; then
          opener=$(printf '%s' "$line" | sed -nE "s/.*<<(-?)[ \t]*'([A-Za-z_][A-Za-z0-9_]*)'$/\1|\2/p")
        fi
        if [ -z "$opener" ]; then
          opener=$(printf '%s' "$line" | sed -nE 's/.*<<(-?)[ \t]*([A-Za-z_][A-Za-z0-9_]*)[ \t]*$/\1|\2/p')
        fi
        if [ -n "$opener" ]; then
          dash=${opener%%|*}
          tag=${opener##*|}
          heredoc_tag=$tag
          if [ "$dash" = '-' ]; then
            heredoc_strip=1
          else
            heredoc_strip=0
          fi
        fi
        ;;
    esac
    case "$line" in
      *'() {'*|*'(){')
        state=enter
        ;;
    esac
    if [ "$state" = enter ]; then
      brace_depth=1
      state=inside
      continue
    fi
    if [ "$state" = inside ]; then
      case "$line" in
        *'{'*)
          brace_depth=$((brace_depth + 1))
          ;;
      esac
      trimmed=$(printf '%s' "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
      if [ -z "$trimmed" ]; then
        printf '%s\n' "[reject_blank_line_in_function] blank line inside function body at line $line_no: $path" >&2
        exit 1
      fi
      case "$line" in
        *'}'*)
          brace_depth=$((brace_depth - 1))
          if [ "$brace_depth" -le 0 ]; then
            state=outside
            brace_depth=0
          fi
          ;;
      esac
    fi
  done < "$path"
}

# Reject docstrings missing the blank `#` separator between description and tag block.
#
# @param path Shell file path to check.
# @exit Exits with status 1 when separator is missing.
reject_missing_doc_separator_in_shell() {
  path=$1
  if awk '
    function classify(l) {
      if (l ~ /^#[[:space:]]+@[A-Za-z_][A-Za-z0-9_]*\b/) return "tag";
      if (l ~ /^#$/) return "sep";
      if (l ~ /^#[[:space:]]/) return "desc";
      return "other";
    }
    {
      cls = classify($0);
      if (cls == "tag" && prev_cls == "desc") {
        printf "%d:%s\n", NR, $0;
        exit 1;
      }
      prev_cls = cls;
    }
  ' "$path"; then
    return 0
  fi
  printf '%s\n' "[reject_missing_doc_separator_in_shell] docstring missing blank '#' separator between description and tag block: $path" >&2
  exit 1
}

# Reject shellcheck violations in a packaged shell file.
#
#     Runs `shellcheck -s sh -f gcc` against the file; fails on any violation.
#     Requires `shellcheck` in PATH; gracefully skips with a warning otherwise.
#
# @param path Shell file path to check.
# @exit Exits with status 1 when shellcheck violation is found.
reject_shellcheck_violations_in_shell() {
  path=$1
  shellcheck_probe=$(command -v shellcheck 2>&1)
  if [ -z "$shellcheck_probe" ]; then
    printf 'warning: shellcheck not in PATH; skipping shellcheck enforcement for %s\n' "$path" >&2
    unset shellcheck_probe
    return 0
  fi
  if ! shellcheck_output=$(shellcheck -s sh -f gcc "$path" 2>&1); then
    printf '%s\n' "$shellcheck_output" >&2
    printf '%s\n' "[reject_shellcheck_violations_in_shell] fails shellcheck: $path" >&2
    exit 1
  fi
  unset shellcheck_probe
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
  "$root/skills/harness-install/templates/common/docs/harness/manifest.json" \
  "$root/skills/harness-install/templates/common/docs/harness/README.md" \
  "$root/skills/harness-install/templates/common/docs/harness/git-hooks/pre-commit" \
  "$root/skills/harness-install/templates/common/docs/harness/git-hooks/pre-push" \
  "$root/skills/harness-install/templates/common/docs/generated/.gitkeep" \
  "$root/skills/harness-install/templates/common/docs/exec-plans/active/.gitkeep" \
  "$root/skills/harness-install/templates/common/docs/exec-plans/completed/.gitkeep" \
  "$root/skills/harness-install/templates/common/docs/harness/templates/docs/generated-artifact.md" \
  "$root/skills/harness-install/templates/bun/.github/workflows/harness.yml" \
  "$root/skills/harness-install/templates/bun/.gitlab-ci.yml" \
  "$root/skills/harness-install/templates/bun/docs/harness/bun/harness-validate.ts" \
  "$root/skills/harness-install/templates/gradle/.github/workflows/harness.yml" \
  "$root/skills/harness-install/templates/gradle/.gitlab-ci.yml" \
  "$root/skills/harness-install/templates/maven/.github/workflows/harness.yml" \
  "$root/skills/harness-install/templates/maven/.gitlab-ci.yml" \
  "$root/skills/harness-install/templates/uv/docs/harness/uv/harness_validate.py" \
  "$root/skills/harness-install/templates/uv/.github/workflows/harness.yml" \
  "$root/skills/harness-install/templates/uv/.gitlab-ci.yml" \
  "$root/skills/harness-install/templates/gradle/docs/harness/gradle-plugin/src/main/kotlin/ai/harness/gradle/HarnessValidationPlugin.kt" \
  "$root/skills/harness-install/templates/maven/docs/harness/maven-plugin/src/main/java/ai/harness/maven/HarnessValidateMojo.java"; do
  require_file "$path"
done

for path in \
  "$root/skills/harness-install/templates/bun/.github/workflows/harness.yml" \
  "$root/skills/harness-install/templates/bun/.gitlab-ci.yml" \
  "$root/skills/harness-install/templates/gradle/.github/workflows/harness.yml" \
  "$root/skills/harness-install/templates/gradle/.gitlab-ci.yml" \
  "$root/skills/harness-install/templates/maven/.github/workflows/harness.yml" \
  "$root/skills/harness-install/templates/maven/.gitlab-ci.yml" \
  "$root/skills/harness-install/templates/uv/.github/workflows/harness.yml" \
  "$root/skills/harness-install/templates/uv/.gitlab-ci.yml"; do
  require_text "$path" '{{validation_command}}'
done

for path in \
  "$root/agents" \
  "$root/scripts" \
  "$root/skills/harness-install/templates/common/.claude/agents" \
  "$root/skills/harness-install/templates/common/.claude/skills" \
  "$root/skills/harness-install/templates/common/docs/harness/templates/agent" \
  "$root/skills/harness-install/templates/common/docs/harness/templates/skill" \
  "$root/skills/harness-install/templates/common/docs/harness/templates/workflow" \
  "$root/skills/harness-install/templates/common/docs/harness/templates/ci" \
  "$root/skills/harness-install/templates/common/docs/harness/templates/docs"; do
  require_dir "$path"
done

require_executable "$root/scripts/plugin-self-check.sh"
require_executable "$root/skills/harness-install/scripts/install-harness.sh"
require_executable "$root/skills/harness-install/scripts/detect-stack.sh"
require_executable "$root/skills/harness-install/templates/common/docs/harness/git-hooks/pre-commit"
require_executable "$root/skills/harness-install/templates/common/docs/harness/git-hooks/pre-push"

require_absent "$root/hooks" 'top-level Claude hooks runtime surface must not be packaged'

bak_part=bak
backup_suffix=.harness.$bak_part
package_file_list=$(package_files "$root")

for packaged_file in $package_file_list; do
  if [ -z "$packaged_file" ]; then
    continue
  fi
  if [ ! -f "$packaged_file" ]; then
    continue
  fi
  first_line=$(sed -n '1p' "$packaged_file")
  if [ "$first_line" != '#!/usr/bin/env sh' ]; then
    continue
  fi
  require_shell_header "$packaged_file"
  reject_set_u_in_shell "$packaged_file"
  reject_bare_test_in_shell "$packaged_file"
  reject_double_bracket_in_shell "$packaged_file"
  reject_nonjsdoc_docstring_in_shell "$packaged_file"
  reject_devnull_redirect_in_shell "$packaged_file"
  reject_blank_line_in_function "$packaged_file"
  reject_shellcheck_violations_in_shell "$packaged_file"
  reject_missing_doc_separator_in_shell "$packaged_file"
done
if printf '%s\n' "$package_file_list" | grep -F "$backup_suffix"; then
  printf '%s\n' "[backup_artifact_check] raw v6 backup artifact must not be packaged" >&2
  exit 1
fi

require_text "$root/skills/harness-install/scripts/install-harness.sh" "# -*- coding: utf-8 -*-"

template_package_file_list=$(package_files "$root/skills/harness-install/templates")
if printf '%s\n' "$template_package_file_list" | grep -E '(^|/)(db-schema[.]md|__pycache__|target|build|bin|[.]gradle|[.]factorypath|[.]classpath|[.]project|[.]settings)(/|$)|[.]pyc$'; then
  printf '%s\n' "[generated_artifacts_check] packaged template files contain generated or IDE artifacts" >&2
  exit 1
fi

if printf '%s\n' "$package_file_list" | grep -F '/docs/harness/validate.sh'; then
  printf '%s\n' "[reject_generic_validate_script] /docs/harness/validate.sh must not be packaged; stack-specific validators only" >&2
  exit 1
fi

python3 - "$root" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
plugin = json.loads((root / ".claude-plugin/plugin.json").read_text())
manifest = json.loads((root / "skills/harness-install/templates/common/docs/harness/manifest.json").read_text())
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
lsp_json_exists = (root / ".lsp.json").is_file()
has_lsp_servers = "lspServers" in plugin
if lsp_json_exists != has_lsp_servers:
    if lsp_json_exists:
        errors.append("manifest must declare lspServers because .lsp.json exists")
    else:
        errors.append(".lsp.json target file missing for lspServers declaration")
mcp_json_exists = (root / ".mcp.json").is_file()
has_mcp_servers = "mcpServers" in plugin
if mcp_json_exists != has_mcp_servers:
    if mcp_json_exists:
        errors.append("manifest must declare mcpServers because .mcp.json exists")
    else:
        errors.append(".mcp.json target file missing for mcpServers declaration")
hooks_json_exists = (root / "hooks" / "hooks.json").is_file()
has_hooks = "hooks" in plugin
if hooks_json_exists != has_hooks:
    if hooks_json_exists:
        errors.append("manifest must declare hooks because hooks/hooks.json exists")
    else:
        errors.append("hooks/hooks.json target file missing for hooks declaration")
settings_json_exists = (root / "settings.json").is_file()
has_settings = "settings" in plugin
if settings_json_exists != has_settings:
    if settings_json_exists:
        errors.append("manifest must declare settings because settings.json exists")
    else:
        errors.append("settings.json target file missing for settings declaration")
def manifest_items(manifest, key):
    section = manifest.get(key, {})
    if isinstance(section, dict):
        items = section.get("items", [])
        return items if isinstance(items, list) else []
    if isinstance(section, list):
        return section
    return []

for group in manifest_items(manifest, "templateGroups"):
    if not (root / f"skills/harness-install/templates/common/docs/harness/templates/{group}").is_dir():
        errors.append(f"manifest template group missing: {group}")
for key in ("requiredFiles", "emptyDirectoryKeepFiles"):
    for item in manifest_items(manifest, key):
        if not (root / "skills/harness-install/templates/common" / item).is_file():
            errors.append(f"manifest {key} missing file: {item}")
for item in manifest_items(manifest, "requiredDirectories"):
    if not (root / "skills/harness-install/templates/common" / item).is_dir():
        errors.append(f"manifest requiredDirectories missing directory: {item}")
if errors:
    for error in errors:
        print(error, file=sys.stderr)
    sys.exit(1)
PY

for path in "$root"/skills/*/SKILL.md; do
  if [ -f "$path" ]; then
    require_text "$path" 'name:'
    require_text "$path" 'description:'
  fi
done
for path in "$root"/agents/*.md; do
  if [ -f "$path" ]; then
    require_text "$path" 'description:'
  fi
done

require_text "$root/README.md" 'harness-install'
require_text "$root/README.md" 'harness-validate'
require_text "$root/README.md" 'harness-evolve'
require_text "$root/README.md" 'host runtimes that load plugin agents'
require_text "$root/README.md" 'hook template'
# shellcheck disable=SC2016
require_text "$root/README.md" 'Gradle `pre-commit` runs `harnessValidate`'
# shellcheck disable=SC2016
require_text "$root/README.md" 'Gradle `pre-push` runs `check`'
require_text "$root/README.md" 'THIRD_PARTY_NOTICES.md'
require_text "$root/README.md" 'skills/harness-install/templates/common/docs/harness/git-hooks/'
require_text "$root/README.md" 'v6 archive structure'
# shellcheck disable=SC2016
require_text "$root/skills/harness-install/SKILL.md" 'Gradle pre-commit runs `harnessValidate`, Gradle pre-push runs `check`'
# shellcheck disable=SC2016
require_text "$root/skills/harness-validate/SKILL.md" 'generated `docs/harness/git-hooks/pre-push` command marker'
require_text "$root/skills/harness-validate/SKILL.md" 'Manifest drift'
require_text "$root/skills/harness-validate/SKILL.md" 'Generated artifact metadata'
require_text "$root/skills/harness-validate/SKILL.md" 'Unsupported validation command'
require_text "$root/skills/harness-install/templates/common/.claude/skills/harness-validate/SKILL.md" 'manifest drift'
require_text "$root/skills/harness-install/templates/common/.claude/skills/harness-validate/SKILL.md" 'generated-artifact metadata'
require_text "$root/skills/harness-install/templates/common/.claude/skills/harness-validate/SKILL.md" 'unsupported pre-push validation command'
# shellcheck disable=SC2016
require_text "$root/skills/harness-evolve/SKILL.md" 'active `.git/hooks/pre-commit` and `.git/hooks/pre-push` remain target repository files'

generated_doc_package_file_list=$(package_files "$root/skills/harness-install/templates/common/docs/generated")
if printf '%s\n' "$generated_doc_package_file_list" | grep -F '/db-schema.md'; then
  printf '%s\n' "harness scaffold must not install docs/generated/db-schema.md" >&2
  exit 1
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
manifest_json="$root/skills/harness-install/templates/common/docs/harness/manifest.json"
require_text "$manifest_json" 'pre-commit hook must not run full stack validation commands'
require_text "$manifest_json" 'must declare Harness validation command'
require_text "$manifest_json" 'declares unsupported validation command'
require_text "$manifest_json" 'must run the declared validation command'
require_text "$manifest_json" './gradlew check'
require_text "$manifest_json" './gradlew harnessValidate'

template_roots="
$root/skills/harness-install/templates/common
$root/skills/harness-install/templates/bun
$root/skills/harness-install/templates/gradle
$root/skills/harness-install/templates/maven
$root/skills/harness-install/templates/uv
"

for template_root in $template_roots; do
  template_root_files=$(package_files "$template_root")
  for path in $template_root_files; do
    case "$path" in
      */harness_validate.py|*/harness_check.py|*/harness-validate.ts|*/harness-check.ts|*/HarnessValidationPlugin.kt|*/HarnessCheck.kt|*/HarnessValidateMojo.java|*/HarnessCheck.java|*/manifest.json|*/manifest.schema.json)
        continue
        ;;
      */templates/common/ARCHITECTURE.md|*/templates/common/docs/DESIGN.md|*/templates/common/docs/PLANS.md|*/templates/common/docs/FRONTEND.md|*/templates/common/docs/PRODUCT_SENSE.md|*/templates/common/docs/QUALITY_SCORE.md|*/templates/common/docs/RELIABILITY.md|*/templates/common/docs/SECURITY.md|*/templates/common/docs/design-docs/core-beliefs.md|*/templates/common/docs/exec-plans/tech-debt-tracker.md|*/templates/common/docs/product-specs/*.md|*/templates/common/docs/references/*.md|*/templates/common/docs/harness/templates/*)
        continue
        ;;
      */.github/workflows/harness.yml|*/.gitlab-ci.yml)
        continue
        ;;
    esac
    if [ -f "$path" ]; then
      if grep -E '\{\{[^}]+\}\}' "$path"; then
        printf '%s\n' "[unresolved_template_tokens] unresolved template token in active asset: $path" >&2
        exit 1
      fi
    fi
  done
done

for text in 'example-' 'Describe ' 'Describe...' 'TODO' 'TBD' 'replace-with-stack-specific'; do
  for template_root in $template_roots; do
    template_root_files=$(package_files "$template_root")
    for path in $template_root_files; do
      case "$path" in
        */harness_validate.py|*/harness-validate.ts|*/HarnessValidationPlugin.kt|*/HarnessValidateMojo.java|*/manifest.json)
          continue
          ;;
        */templates/common/ARCHITECTURE.md|*/templates/common/docs/DESIGN.md|*/templates/common/docs/PLANS.md|*/templates/common/docs/FRONTEND.md|*/templates/common/docs/PRODUCT_SENSE.md|*/templates/common/docs/QUALITY_SCORE.md|*/templates/common/docs/RELIABILITY.md|*/templates/common/docs/SECURITY.md|*/templates/common/docs/design-docs/core-beliefs.md|*/templates/common/docs/exec-plans/tech-debt-tracker.md|*/templates/common/docs/product-specs/*.md|*/templates/common/docs/references/*.md|*/templates/common/docs/harness/templates/*)
          continue
          ;;
        */.github/workflows/harness.yml|*/.gitlab-ci.yml)
          continue
          ;;
      esac
      if [ -f "$path" ]; then
        if grep -F "$text" "$path"; then
          printf '%s\n' "[template_marker_check] forbidden template marker in template asset: $text" >&2
          exit 1
        fi
      fi
    done
  done
done

exit 0
