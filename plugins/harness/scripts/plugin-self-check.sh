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

# Require a path to not be a symbolic link.
#
# @param path Path to check.
# @exit Exits with status 1 when path is a symbolic link.
require_not_symlink() {
    path=$1
    if [ -L "$path" ]; then
        printf '%s\n' "[require_not_symlink] path must not be a symbolic link: $path" >&2
        exit 1
    fi
}

# Require a path to not be a symbolic link unless it is the packaged root contract link.
#
# @param path Path to check.
# @exit Exits with status 1 when path is an unsupported symbolic link.
require_not_symlink_or_common_root_contract() {
    path=$1
    if [ ! -L "$path" ]; then
        return 0
    fi
    case "$path" in
        "$root/skills/harness-install/assets/common/AGENTS.md")
            target=$(readlink "$path")
            if [ "$target" = CLAUDE.md ] && [ -f "$root/skills/harness-install/assets/common/CLAUDE.md" ] && [ ! -L "$root/skills/harness-install/assets/common/CLAUDE.md" ]; then
                return 0
            fi
            ;;
        "$root/skills/harness-install/assets/common/CLAUDE.md")
            target=$(readlink "$path")
            if [ "$target" = AGENTS.md ] && [ -f "$root/skills/harness-install/assets/common/AGENTS.md" ] && [ ! -L "$root/skills/harness-install/assets/common/AGENTS.md" ]; then
                return 0
            fi
            ;;
    esac
    printf '%s\n' "[require_not_symlink] path must not be a symbolic link: $path" >&2
    exit 1
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

# Require a Markdown file to contain a heading at a specific level.
#
# @param path Markdown file path to inspect.
# @param level Markdown heading level number.
# @param title Heading title without hash marks.
# @exit Exits with status 1 when the heading is missing.
require_markdown_heading() {
    path=$1
    level=$2
    title=$3
    marks=$(printf '%*s' "$level" '' | tr ' ' '#')
    heading="$marks $title"
    if ! grep -Fxq "$heading" "$path"; then
        printf '%s\n' "[require_markdown_heading] missing heading in $path: $heading" >&2
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

# Reject unresolved template tokens in prose text of Markdown-like documents.
#
# @param path Markdown or text document path to inspect.
# @exit Exits with status 1 when prose contains an unresolved template token.
reject_unresolved_template_tokens_in_document() {
    path=$1
    if ! python3 - "$path" <<'PYEOF'; then
import re
import sys
from pathlib import Path
path = Path(sys.argv[1])
try:
    text = path.read_text(encoding="utf-8")
except UnicodeDecodeError:
    raise SystemExit(0)
stripped_lines = []
in_fence = False
fence_marker = ""
for line in text.splitlines():
    fence_match = re.match(r" {0,3}(`{3,}|~{3,})", line)
    if fence_match:
        marker = fence_match.group(1)[0]
        if not in_fence:
            in_fence = True
            fence_marker = marker
        elif marker == fence_marker:
            in_fence = False
        stripped_lines.append("")
        continue
    if in_fence:
        stripped_lines.append("")
        continue
    stripped_lines.append(re.sub(r"`+[^`\n]*`+", "", line))
if re.search(r"\{\{[^}]+\}\}", "\n".join(stripped_lines)):
    raise SystemExit(1)
PYEOF
        printf '%s\n' "[unresolved_template_tokens] unresolved template token in active document: $path" >&2
        exit 1
    fi
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

# Print files tracked by Git plus untracked package files.
#
# @param path File or directory path to scan.
# @return Writes full paths of tracked and untracked files.
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
        files=$(git -C "$root" ls-files --cached --others --exclude-standard -- "$rel") || {
            printf '%s\n' "[package_files] cannot enumerate package files: $path" >&2
            exit 1
        }
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
    if python3 - "$path" <<'PYEOF'; then
import sys
from pathlib import Path
path = Path(sys.argv[1])
heredoc = None
for lineno, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
    line = raw_line
    if heredoc is not None:
        if line.strip() == heredoc:
            heredoc = None
        continue
    if "<<" in line:
        import re
        match = re.search(r"<<-?\s*['\"]?([A-Za-z_][A-Za-z0-9_]*)['\"]?", line)
        if match:
            heredoc = match.group(1)
    stripped = []
    quote = None
    escaped = False
    for char in line:
        if escaped:
            escaped = False
            if quote is None:
                stripped.append(" ")
            continue
        if quote == '"':
            if char == "\\":
                escaped = True
                stripped.append(" ")
            elif char == '"':
                quote = None
                stripped.append(" ")
            else:
                stripped.append(" ")
            continue
        if quote == "'":
            if char == "'":
                quote = None
            stripped.append(" ")
            continue
        if char == "#":
            break
        if char in ("'", '"'):
            quote = char
            stripped.append(" ")
            continue
        stripped.append(char)
    code = "".join(stripped)
    if "[[" in code or "]]" in code:
        print(f"{path}:{lineno}:{raw_line}", file=sys.stderr)
        raise SystemExit(1)
PYEOF
        return 0
    fi
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
            *'() {'* | *'(){')
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
    done <"$path"
}

# Reject docstrings missing the blank `#` separator between description and tag block.
#
# @param path Shell file path to check.
# @exit Exits with status 1 when separator is missing.
reject_missing_doc_separator_in_shell() {
    path=$1
    if awk '
    function classify(l) {
      if (l ~ /^#[[:space:]]+@[A-Za-z_][A-Za-z0-9_]*([[:space:]]|$)/) return "tag";
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

# Reject Markdown opening fences that lack a language specifier.
#
# @param path Markdown file path to check.
# @exit Exits with status 1 when an opening fence has no language.
require_markdown_fence_language() {
    path=$1
    if ! python3 - "$path" <<'PYFENCE'; then
import re
import sys
from pathlib import Path
path = Path(sys.argv[1])
try:
    text = path.read_text(encoding="utf-8")
except UnicodeDecodeError:
    raise SystemExit(0)
in_fence = False
fence_marker = ""
for lineno, line in enumerate(text.splitlines(), 1):
    fence_match = re.match(r" {0,3}(`{3,}|~{3,})(.*)", line)
    if fence_match:
        marker_char = fence_match.group(1)[0]
        info_string = fence_match.group(2).strip()
        if not in_fence:
            in_fence = True
            fence_marker = marker_char
            if not info_string:
                print(f"{path}:{lineno}: opening fence missing language specifier", file=sys.stderr)
                raise SystemExit(1)
        elif marker_char == fence_marker:
            in_fence = False
PYFENCE
        printf '%s\n' "[require_markdown_fence_language] opening fence missing language specifier: $path" >&2
        exit 1
    fi
}

# Reject Markdown opening fences not preceded by a blank line.
#
# @param path Markdown file path to check.
# @exit Exits with status 1 when an opening fence lacks a preceding blank line.
require_markdown_blank_before_fence() {
    path=$1
    if ! python3 - "$path" <<'PYFENCE'; then
import re
import sys
from pathlib import Path
path = Path(sys.argv[1])
try:
    text = path.read_text(encoding="utf-8")
except UnicodeDecodeError:
    raise SystemExit(0)
lines = text.splitlines()
in_fence = False
fence_marker = ""
for lineno, line in enumerate(lines, 1):
    fence_match = re.match(r" {0,3}(`{3,}|~{3,})", line)
    if fence_match:
        marker_char = fence_match.group(1)[0]
        if not in_fence:
            if lineno > 1:
                prev = lines[lineno - 2].strip()
                if prev != "":
                    print(f"{path}:{lineno}: opening fence not preceded by blank line", file=sys.stderr)
                    raise SystemExit(1)
            in_fence = True
            fence_marker = marker_char
        elif marker_char == fence_marker:
            in_fence = False
PYFENCE
        printf '%s\n' "[require_markdown_blank_before_fence] opening fence not preceded by blank line: $path" >&2
        exit 1
    fi
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
    if ! shellcheck_path=$(command -v shellcheck 2>&1); then
        printf 'warning: shellcheck not in PATH; skipping shellcheck enforcement for %s\n' "$path" >&2
        unset shellcheck_path
        return 0
    fi
    if ! shellcheck_output=$("$shellcheck_path" -s sh -f gcc "$path" 2>&1); then
        printf '%s\n' "$shellcheck_output" >&2
        printf '%s\n' "[reject_shellcheck_violations_in_shell] fails shellcheck: $path" >&2
        exit 1
    fi
    unset shellcheck_path
}

# Match the canonical fixture finding prefix.
#
# @constant FIXTURE_CANONICAL_FINDING_PREFIX_REGEX
FIXTURE_CANONICAL_FINDING_PREFIX_REGEX='^[^:]+:[0-9]+:[0-9]+ \[(ERROR|WARN|INFO)\] [A-Za-z][A-Za-z0-9]*: .+'

# Create a temporary fixture directory for self-check assertions.
#
# @return Writes the temporary directory path.
# @exit Exits with status 1 when the directory cannot be created.
fixture_create_temp_dir() {
    mktemp -d /tmp/harness-self-check-XXXXXXXXXX
}

# Remove a temporary fixture directory.
#
# @param temp_dir Temporary directory path to remove.
# @return Returns 0 when cleanup succeeds.
fixture_remove_temp_dir() {
    temp_dir=$1
    rm -rf "$temp_dir"
}

# Write fixture file content under a temporary directory.
#
# @param temp_dir Temporary directory root.
# @param relative_path Fixture-relative file path.
# @param content File content to write.
# @return Returns 0 when the file is written.
fixture_write_file() {
    temp_dir=$1
    relative_path=$2
    content=$3
    file_path=$temp_dir/$relative_path
    parent_dir=$(dirname "$file_path")
    mkdir -p "$parent_dir"
    printf '%s' "$content" >"$file_path"
}

# Copy a stack runtime into a temporary fixture directory.
#
# @param temp_dir Temporary directory root.
# @param stack_name Stack name: bun, uv, gradle, maven, or shell.
# @return Returns 0 when the runtime is copied.
fixture_copy_runtime() {
    temp_dir=$1
    stack_name=$2
    case "$stack_name" in
        bun | shell | uv)
            source_dir=$root/skills/harness-install/assets/$stack_name/runtime
            mkdir -p "$temp_dir"
            cp -R "$source_dir"/. "$temp_dir"/
            if [ "$stack_name" = bun ]; then
                if [ -f "$root/skills/harness-install/assets/$stack_name/.oxfmtrc.json" ]; then
                    cp "$root/skills/harness-install/assets/$stack_name/.oxfmtrc.json" "$temp_dir/"
                fi
                if [ -f "$root/skills/harness-install/assets/$stack_name/.oxlintrc.json" ]; then
                    cp "$root/skills/harness-install/assets/$stack_name/.oxlintrc.json" "$temp_dir/"
                fi
                if [ -d "$root/skills/harness-install/assets/$stack_name/docs" ]; then
                    mkdir -p "$temp_dir/docs"
                    cp -R "$root/skills/harness-install/assets/$stack_name/docs/." "$temp_dir/docs/"
                fi
                if [ -d "$temp_dir/oxlint-plugins" ]; then
                    mkdir -p "$temp_dir/docs/harness/bun"
                    mv "$temp_dir/oxlint-plugins" "$temp_dir/docs/harness/bun/"
                fi
            fi
            ;;
        gradle)
            source_dir=$root/skills/harness-install/assets/gradle/buildSrc
            mkdir -p "$temp_dir/buildSrc"
            cp -R "$source_dir"/. "$temp_dir/buildSrc"/
            ;;
        maven)
            source_dir=$root/skills/harness-install/assets/maven
            mkdir -p "$temp_dir"
            cp -R "$source_dir"/. "$temp_dir"/
            ;;
        *)
            printf '%s\n' "[fixture_copy_runtime] unsupported stack: $stack_name" >&2
            return 1
            ;;
    esac
}

# Write a minimal harness manifest into a temporary fixture directory.
#
# @param temp_dir Temporary directory root.
# @param manifest_json_string Manifest JSON content.
# @return Returns 0 when the manifest is written.
fixture_write_manifest() {
    temp_dir=$1
    manifest_json_string=$2
    fixture_write_file "$temp_dir" docs/harness/manifest.json "$manifest_json_string"
}

# Run a command inside a temporary fixture directory and capture output.
#
# @param temp_dir Temporary directory root.
# @param command_string Shell command string to evaluate.
# @return Returns the command exit code and sets fixture_stdout and fixture_stderr.
fixture_run_command() {
    temp_dir=$1
    command_string=$2
    fixture_exit_code=0
    fixture_stderr_path=$(mktemp /tmp/harness-self-check-stderr-XXXXXXXXXX)
    if fixture_stdout=$(cd "$temp_dir" && eval "$command_string" 2>"$fixture_stderr_path"); then
        fixture_exit_code=0
    else
        fixture_exit_code=$?
    fi
    fixture_stderr=$(cat "$fixture_stderr_path")
    rm -f "$fixture_stderr_path"
    : "$fixture_stdout" "$fixture_stderr"
    return "$fixture_exit_code"
}

# Assert that an actual exit code matches the expected value.
#
# @param actual Actual exit code.
# @param expected Expected exit code.
# @param label Assertion label.
# @return Returns 0 when the exit code matches.
fixture_assert_exit_code() {
    actual=$1
    expected=$2
    label=$3
    if [ "$actual" = "$expected" ]; then
        printf '%s\n' "PASS: $label"
        return 0
    fi
    printf '%s\n' "FAIL: $label expected exit $expected, got $actual" >&2
    return 1
}

# Assert that captured output contains a fixed substring.
#
# @param haystack Captured output text.
# @param needle Required fixed substring.
# @param label Assertion label.
# @return Returns 0 when the substring is present.
fixture_assert_output_contains() {
    haystack=$1
    needle=$2
    label=$3
    case "$haystack" in
        *"$needle"*)
            printf '%s\n' "PASS: $label"
            return 0
            ;;
        *)
            printf '%s\n' "FAIL: $label missing output substring: $needle" >&2
            return 1
            ;;
    esac
}

# Assert that captured output matches a regular expression.
#
# @param haystack Captured output text.
# @param pattern Required extended regular expression.
# @param label Assertion label.
# @return Returns 0 when the pattern matches.
fixture_assert_output_matches() {
    haystack=$1
    pattern=$2
    label=$3
    if printf '%s\n' "$haystack" | grep -Eq "$pattern"; then
        printf '%s\n' "PASS: $label"
        return 0
    fi
    printf '%s\n' "FAIL: $label missing output pattern: $pattern" >&2
    return 1
}

# Return a stable checksum for a fixture file.
#
# @param path File path to checksum.
# @return Writes the cksum numeric digest and size.
fixture_file_checksum() {
    path=$1
    cksum "$path" | awk '{ print $1 ":" $2 }'
}

# Assert that a fixture file checksum changed.
#
# @param before Checksum captured before the action.
# @param after Checksum captured after the action.
# @param label Assertion label.
# @return Returns 0 when the checksums differ.
fixture_assert_checksum_changed() {
    before=$1
    after=$2
    label=$3
    if [ "$before" != "$after" ]; then
        printf '%s\n' "PASS: $label"
        return 0
    fi
    printf '%s\n' "FAIL: $label expected checksum to change" >&2
    return 1
}

# Assert that a fixture file checksum stayed unchanged.
#
# @param before Checksum captured before the action.
# @param after Checksum captured after the action.
# @param label Assertion label.
# @return Returns 0 when the checksums match.
fixture_assert_checksum_unchanged() {
    before=$1
    after=$2
    label=$3
    if [ "$before" = "$after" ]; then
        printf '%s\n' "PASS: $label"
        return 0
    fi
    printf '%s\n' "FAIL: $label expected checksum to stay unchanged" >&2
    return 1
}

# Assert that the first format run changed one fixture file and reported it.
#
# @param before Checksum captured before formatting.
# @param after Checksum captured after formatting.
# @param haystack Captured formatter output text.
# @param relative_path Fixture-relative file path that should be reported.
# @param label Assertion label prefix.
# @return Returns 0 when formatter output and checksum prove a first-run edit.
# shellcheck disable=SC2329
fixture_assert_format_changed() {
    before=$1
    after=$2
    haystack=$3
    relative_path=$4
    label=$5
    fixture_assert_checksum_changed "$before" "$after" "$label changes fixture file" || return 1
    fixture_assert_output_contains "$haystack" 'formatted: 1' "$label reports one formatted file" || return 1
    fixture_assert_output_contains "$haystack" "$relative_path" "$label reports modified path"
}

# Assert that the second format run made no fixture file changes.
#
# @param before Checksum captured before formatting.
# @param after Checksum captured after formatting.
# @param haystack Captured formatter output text.
# @param label Assertion label prefix.
# @return Returns 0 when formatter output and checksum prove idempotence.
fixture_assert_format_unchanged() {
    before=$1
    after=$2
    haystack=$3
    label=$4
    fixture_assert_checksum_unchanged "$before" "$after" "$label leaves fixture file unchanged" || return 1
    fixture_assert_output_contains "$haystack" 'no files formatted' "$label reports no-op format"
}

# Verify shell formatting reports edits and remaining validation findings.
#
#     Requires `shfmt` in PATH. Gracefully skips with a warning when shfmt is unavailable.
#
# @return Returns 0 on success or when shfmt is missing.
# @exit Exits with status 1 when the shell format fixture fails.
fixture_assert_shell_format_check_after_format() {
    if ! shfmt_path=$(command -v shfmt 2>&1); then
        printf 'warning: shfmt not in PATH; skipping shell format fixture check\n' >&2
        return 0
    fi
    : "$shfmt_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" shell
    mkdir -p "$temp_dir/docs/harness/shell"
    cp "$temp_dir/harness-check.sh" "$temp_dir/docs/harness/shell/harness-check.sh"
    cp "$temp_dir/harness-format.sh" "$temp_dir/docs/harness/shell/harness-format.sh"
    fixture_write_manifest "$temp_dir" '{"name":"shell-format-fixture","filePresence":{"enabled":true,"severity":"ERROR","parameters":{"paths":["required.md"]}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"hookCommand":{"enabled":false,"parameters":{"prePushHook":"","preCommitHook":"","allowedCommands":[],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":false,"parameters":{"ciFiles":[],"referenceHook":"docs/harness/git-hooks/pre-push"}},"symlinkSafety":{"enabled":false,"parameters":{"allowedSymlinkPairs":[]}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"shellcheck":{"enabled":false,"parameters":{}}}'
    fixture_write_file "$temp_dir" fixture.sh '#!/usr/bin/env sh
if [ 1 -eq 1 ]; then
printf "%s\n" "fixture"
fi
'
    fixture_before_checksum=$(fixture_file_checksum "$temp_dir/fixture.sh")
    if fixture_run_command "$temp_dir" 'sh docs/harness/shell/harness-format.sh'; then
        printf '%s\n' '[fixture_assert_shell_format_check_after_format] expected first format to report remaining missing file' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    fixture_after_first_checksum=$(fixture_file_checksum "$temp_dir/fixture.sh")
    if ! fixture_assertion_output=$(fixture_assert_checksum_changed "$fixture_before_checksum" "$fixture_after_first_checksum" 'shell format first run changes fixture file' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'formatted:' 'shell format first run reports formatted files' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'fixture.sh' 'shell format first run reports fixture path' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'remaining findings after format:' 'shell format reports remaining findings' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" '[ERROR] filePresence: missing file: required.md' 'shell format reports check error' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" 'sh docs/harness/shell/harness-format.sh'; then
        printf '%s\n' '[fixture_assert_shell_format_check_after_format] expected second format to report remaining missing file' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    fixture_after_second_checksum=$(fixture_file_checksum "$temp_dir/fixture.sh")
    if ! fixture_assertion_output=$(fixture_assert_format_unchanged "$fixture_after_first_checksum" "$fixture_after_second_checksum" "$fixture_combined_output" 'shell format second run' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}
# Verify shell formatter fails on malformed list parameters.
#
# @exit Exits with status 1 when formatter fails to reject malformed parameters.
fixture_assert_shell_format_malformed_manifest() {
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" shell
    mkdir -p "$temp_dir/docs/harness/shell"
    cp "$temp_dir/harness-check.sh" "$temp_dir/docs/harness/shell/harness-check.sh"
    cp "$temp_dir/harness-format.sh" "$temp_dir/docs/harness/shell/harness-format.sh"
    fixture_write_file "$temp_dir" docs/harness/shell/harness-check.sh '#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e
exit 0
'
    fixture_write_manifest "$temp_dir" '{"name":"shell-format-malformed-fixture","emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":true,"parameters":{"hooks":"not-an-array"}}}'
    fixture_write_file "$temp_dir" docs/harness/git-hooks/pre-commit '#!/usr/bin/env sh
echo "fixture pre-commit hook"
'
    fixture_before_checksum=$(fixture_file_checksum "$temp_dir/docs/harness/git-hooks/pre-commit")
    if fixture_run_command "$temp_dir" 'sh docs/harness/shell/harness-format.sh'; then
        printf '%s\n' '[fixture_assert_shell_format_malformed_manifest] expected malformed manifest to fail' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'invalid hookExecutable parameters' 'shell format malformed manifest error' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_after_checksum=$(fixture_file_checksum "$temp_dir/docs/harness/git-hooks/pre-commit")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'shell format malformed manifest leaves hook file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}

# Assert that captured output contains a canonical finding for a runtime fixture.
#
# @param haystack Captured output text.
# @param relative_path_pattern Fixture-relative file path extended regular expression.
# @param category Finding category identifier.
# @param label Assertion label.
# @return Returns 0 when the output has the canonical prefix and expected rule.
fixture_assert_canonical_finding_prefix() {
    haystack=$1
    relative_path_pattern=$2
    category=$3
    label=$4
    pattern="^$relative_path_pattern:[0-9]+:[0-9]+ \\[(ERROR|WARN|INFO)\\] $category: .+"
    fixture_assert_output_matches "$haystack" "$FIXTURE_CANONICAL_FINDING_PREFIX_REGEX" "$label canonical shape" || return 1
    fixture_assert_output_matches "$haystack" "$pattern" "$label"
}

# Verify fixture helpers without adding user-visible output.
#
# @exit Exits with status 1 when a fixture helper fails.
fixture_self_check_helpers() {
    temp_dir=$(fixture_create_temp_dir)
    fixture_write_file "$temp_dir" docs/harness/fixture.txt 'fixture helper ok'
    fixture_write_manifest "$temp_dir" '{"name":"fixture"}'
    fixture_copy_runtime "$temp_dir/runtime-uv" uv
    if fixture_run_command "$temp_dir" 'printf "%s\n" "fixture helper ok"'; then
        :
    else
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_exit_code "$fixture_exit_code" 0 'fixture command exit' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stdout" 'fixture helper ok' 'fixture command output' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_matches "$fixture_stdout" '^fixture helper ok$' 'fixture command output pattern' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    : "$fixture_assertion_output"
    fixture_remove_temp_dir "$temp_dir"
}

# Verify shell runtime rejects malformed enabled manifest parameters.
#
# @exit Exits with status 1 when malformed manifest does not fail visibly.
fixture_assert_shell_malformed_manifest() {
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" shell
    fixture_write_manifest "$temp_dir" '{"name":"shell-malformed-fixture","filePresence":{"enabled":true,"severity":"ERROR","parameters":{"paths":"not-an-array"}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"hookCommand":{"enabled":false,"parameters":{"prePushHook":"","preCommitHook":"","allowedCommands":[],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":false,"parameters":{"ciFiles":[],"referenceHook":"docs/harness/git-hooks/pre-push"}},"symlinkSafety":{"enabled":false,"parameters":{"allowedSymlinkPairs":[]}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"shellcheck":{"enabled":false,"parameters":{}}}'
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        printf '%s\n' '[fixture_assert_shell_malformed_manifest] expected malformed manifest to fail' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'invalid filePresence parameters' 'shell malformed manifest filePresence error' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}

# Verify shell runtime excludes .claude/worktrees from format, check, and symlink scans.
#
# @exit Exits with status 1 when worktree content is not excluded.
fixture_assert_shell_worktree_excluded() {
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" shell
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
{"name":"shell-worktree-exclusion-fixture","filePresence":{"enabled":false,"parameters":{"paths":[]}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"hookCommand":{"enabled":false,"parameters":{"prePushHook":"","preCommitHook":"","allowedCommands":[],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":false,"parameters":{"ciFiles":[],"referenceHook":"docs/harness/git-hooks/pre-push"}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"symlinkSafety":{"enabled":true,"severity":"ERROR","messages":{"fileNotAllowed":"symlink file is not allowed: {path}","directoryNotAllowed":"symlink directory is not allowed: {path}","scanRootNotAllowed":"symlink scan root is not allowed: {path}","scanEntryNotAllowed":"symlink scan entry is not allowed: {path}","pathNotAllowed":"symlink path is not allowed: {path}"}},"shellcheck":{"enabled":false,"parameters":{}}}
JSONEOF
    )"
    mkdir -p "$temp_dir/.claude/worktrees/abc1234"
    ln -s ../../docs "$temp_dir/.claude/worktrees/abc1234/docs-link"
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        :
    else
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stdout" 'Harness validation passed' 'shell worktree exclusion passed' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! shfmt_path=$(command -v shfmt 2>&1); then
        printf 'warning: shfmt not in PATH; skipping shell runtime format worktree exclusion check\n' >&2
    else
        : "$shfmt_path"
        worktree_formatter_file="$temp_dir/.claude/worktrees/abc1234/format-fixture.sh"
        fixture_write_file "$temp_dir" ".claude/worktrees/abc1234/format-fixture.sh" '#!/usr/bin/env sh
if [ 1 -eq 1 ];then
printf "%s\n" "shell runtime format worktree fixture"
fi
'
        fixture_before_formatter_checksum=$(fixture_file_checksum "$worktree_formatter_file")
        mkdir -p "$temp_dir/docs/harness/shell"
        cp "$temp_dir/harness-check.sh" "$temp_dir/docs/harness/shell/harness-check.sh"
        cp "$temp_dir/harness-format.sh" "$temp_dir/docs/harness/shell/harness-format.sh"
        if fixture_run_command "$temp_dir" 'sh docs/harness/shell/harness-format.sh'; then
            :
        else
            printf '%s\n' "$fixture_stdout" >&2
            printf '%s\n' "$fixture_stderr" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
        fi
        worktree_formatter_after_checksum=$(fixture_file_checksum "$worktree_formatter_file")
        if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_formatter_checksum" "$worktree_formatter_after_checksum" 'shell worktree excluded from formatter scan' 2>&1); then
            printf '%s\n' "$fixture_assertion_output" >&2
            printf '%s\n' "$fixture_stdout" >&2
            printf '%s\n' "$fixture_stderr" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
        fi
    fi
    if ! shellcheck_path=$(command -v shellcheck 2>&1); then
        printf 'warning: shellcheck not in PATH; skipping shell runtime shellcheck worktree exclusion check\n' >&2
    else
        : "$shellcheck_path"
        worktree_shellcheck_file=".claude/worktrees/abc1234/shellcheck-violation.sh"
        fixture_write_file "$temp_dir" "$worktree_shellcheck_file" "$(
            cat <<'SHELLCHECK_FIXTURE'
#!/usr/bin/env sh
echo "\$unset_variable"
SHELLCHECK_FIXTURE
        )"
        fixture_write_manifest "$temp_dir" "$(
            cat <<'JSONEOF'
{"name":"shell-worktree-exclusion-fixture","filePresence":{"enabled":false,"parameters":{"paths":[]}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"hookCommand":{"enabled":false,"parameters":{"prePushHook":"","preCommitHook":"","allowedCommands":[],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":false,"parameters":{"ciFiles":[],"referenceHook":"docs/harness/git-hooks/pre-push"}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"symlinkSafety":{"enabled":true,"severity":"ERROR","messages":{"fileNotAllowed":"symlink file is not allowed: {path}","directoryNotAllowed":"symlink directory is not allowed: {path}","scanRootNotAllowed":"symlink scan root is not allowed: {path}","scanEntryNotAllowed":"symlink scan entry is not allowed: {path}","pathNotAllowed":"symlink path is not allowed: {path}"}},"shellcheck":{"enabled":true,"severity":"ERROR","parameters":{}}}
JSONEOF
        )"
        if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
            :
        else
            printf '%s\n' "$fixture_stdout" >&2
            printf '%s\n' "$fixture_stderr" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
        fi
        if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stdout" 'Harness validation passed' 'shell worktree shellcheck exclusion passed' 2>&1); then
            printf '%s\n' "$fixture_assertion_output" >&2
            printf '%s\n' "$fixture_stdout" >&2
            printf '%s\n' "$fixture_stderr" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
        fi
        if printf '%s' "$fixture_stdout$fixture_stderr" | grep -Fq "$worktree_shellcheck_file"; then
            printf '%s\n' "[fixture_assert_shell_worktree_excluded] expected shellcheck scan to ignore worktree file: $worktree_shellcheck_file" >&2
            printf '%s\n' "$fixture_stdout" >&2
            printf '%s\n' "$fixture_stderr" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
        fi
    fi
    fixture_remove_temp_dir "$temp_dir"
}

# Verify build-tool hook mode prints commands for supported stacks and rejects unsupported ones.
#
# @exit Exits with status 1 when build-tool mode safety fails.
fixture_assert_build_tool_hook_mode() {
    temp_dir=$(fixture_create_temp_dir)
    mkdir -p "$temp_dir/.git"
    fixture_write_file "$temp_dir/settings.gradle.kts" 'rootProject.name = "build-tool-fixture"'
    if ! fixture_run_command "$temp_dir" "sh \"$root/skills/harness-install/scripts/install-harness.sh\" --mode gradle --hooks build-tool --target ."; then
        printf '%s\n' '[fixture_assert_build_tool_hook_mode] expected gradle build-tool to succeed with print-only' >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stdout" '-Pharness.gitHooks=true' 'gradle build-tool prints activation command' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    pom_xml=$(
        cat <<'POM_XML'
<project></project>
POM_XML
    )
    fixture_write_file "$temp_dir" pom.xml "$pom_xml"
    if ! fixture_run_command "$temp_dir" "sh \"$root/skills/harness-install/scripts/install-harness.sh\" --mode maven --hooks build-tool --target ."; then
        printf '%s\n' '[fixture_assert_build_tool_hook_mode] expected maven build-tool to succeed with print-only' >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stdout" '-Dharness.gitHooks=true' 'maven build-tool prints activation command' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" "sh \"$root/skills/harness-install/scripts/install-harness.sh\" --mode shell --hooks build-tool --target ."; then
        printf '%s\n' '[fixture_assert_build_tool_hook_mode] expected shell build-tool to be rejected' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'build-tool is only supported for gradle and maven' 'shell build-tool rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}

# Verify shell runtime rejects required-file, required-directory, and hook symlinks.
#
# @exit Exits with status 1 when symlink violations are not reported.
fixture_assert_shell_symlink_safety() {
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" shell
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
{"name":"shell-symlink-fixture","filePresence":{"enabled":true,"severity":"ERROR","parameters":{"paths":["required-file.md","docs/harness/git-hooks/pre-commit","docs/harness/git-hooks/pre-push"]}},"directoryPresence":{"enabled":true,"severity":"ERROR","parameters":{"paths":["required-dir"]}},"hookShebang":{"enabled":true,"severity":"ERROR","parameters":{"hooks":["docs/harness/git-hooks/pre-commit","docs/harness/git-hooks/pre-push"],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":true,"severity":"ERROR","parameters":{"hooks":["docs/harness/git-hooks/pre-commit","docs/harness/git-hooks/pre-push"]}},"symlinkSafety":{"enabled":true,"severity":"ERROR","messages":{"fileNotAllowed":"symlink file is not allowed: {path}","directoryNotAllowed":"symlink directory is not allowed: {path}","scanRootNotAllowed":"symlink scan root is not allowed: {path}","scanEntryNotAllowed":"symlink scan entry is not allowed: {path}","pathNotAllowed":"symlink path is not allowed: {path}"},"parameters":{"allowedSymlinkPairs":[["AGENTS.md","CLAUDE.md"]]}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"shellcheck":{"enabled":false,"parameters":{}}}
JSONEOF
    )"
    fixture_write_file "$temp_dir" target-file.md 'fixture target'
    fixture_write_file "$temp_dir" target-hook '#!/usr/bin/env sh
printf "%s\n" "fixture hook"
'
    chmod +x "$temp_dir/target-hook"
    mkdir -p "$temp_dir/target-dir" "$temp_dir/docs/harness/git-hooks"
    ln -s target-file.md "$temp_dir/required-file.md"
    ln -s target-dir "$temp_dir/required-dir"
    ln -s ../../../target-hook "$temp_dir/docs/harness/git-hooks/pre-commit"
    ln -s ../../../target-hook "$temp_dir/docs/harness/git-hooks/pre-push"
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        printf '%s\n' '[fixture_assert_shell_symlink_safety] expected shell harness-check to reject symlinks' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'symlink file is not allowed: required-file.md' 'shell required file symlink rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'symlink directory is not allowed: required-dir' 'shell required directory symlink rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'symlink file is not allowed: docs/harness/git-hooks/pre-commit' 'shell pre-commit symlink rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'symlink file is not allowed: docs/harness/git-hooks/pre-push' 'shell pre-push symlink rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}

# Verify shell scaffold scanning allows the installed root contract symlink.
#
# @exit Exits with status 1 when a valid AGENTS.md / CLAUDE.md symlink fails.
fixture_assert_shell_root_contract_scaffold_symlink() {
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" shell
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
{"name":"shell-root-contract-symlink-fixture","filePresence":{"enabled":false,"parameters":{"paths":[]}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"hookCommand":{"enabled":false,"parameters":{"prePushHook":"","preCommitHook":"","allowedCommands":[],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":false,"parameters":{"ciFiles":[],"referenceHook":"docs/harness/git-hooks/pre-push"}},"symlinkSafety":{"enabled":true,"severity":"ERROR","messages":{"fileNotAllowed":"symlink file is not allowed: {path}","directoryNotAllowed":"symlink directory is not allowed: {path}","scanRootNotAllowed":"symlink scan root is not allowed: {path}","scanEntryNotAllowed":"symlink scan entry is not allowed: {path}","pathNotAllowed":"symlink path is not allowed: {path}"},"parameters":{"allowedSymlinkPairs":[["AGENTS.md","CLAUDE.md"]]}},"scaffoldLeaks":{"enabled":true,"severity":"ERROR","parameters":{"scope":{"bases":["AGENTS.md","CLAUDE.md"],"extensions":["md"]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"shellcheck":{"enabled":false,"parameters":{}}}
JSONEOF
    )"
    fixture_write_file "$temp_dir" CLAUDE.md '# Entry Point
'
    ln -s CLAUDE.md "$temp_dir/AGENTS.md"
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        :
    else
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stdout" 'Harness validation passed' 'shell root contract symlink scaffold pass' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_write_manifest "$temp_dir" '{"name":"shell-root-contract-symlink-target-fixture","filePresence":{"enabled":false,"parameters":{"paths":[]}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"hookCommand":{"enabled":false,"parameters":{"prePushHook":"","preCommitHook":"","allowedCommands":[],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":false,"parameters":{"ciFiles":[],"referenceHook":"docs/harness/git-hooks/pre-push"}},"symlinkSafety":{"enabled":true,"severity":"ERROR","messages":{"fileNotAllowed":"symlink file is not allowed: {path}","directoryNotAllowed":"symlink directory is not allowed: {path}","scanRootNotAllowed":"symlink scan root is not allowed: {path}","scanEntryNotAllowed":"symlink scan entry is not allowed: {path}","pathNotAllowed":"symlink path is not allowed: {path}"},"parameters":{"allowedSymlinkPairs":[["AGENTS.md","CLAUDE.md"]]}},"scaffoldLeaks":{"enabled":true,"severity":"ERROR","parameters":{"scope":{"bases":["AGENTS.md"],"extensions":["md"]},"patterns":[{"pattern":"\\{\\{","label":"unresolved template token"}]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"shellcheck":{"enabled":false,"parameters":{}}}'
    fixture_write_file "$temp_dir" CLAUDE.md '# Entry Point
{{ unresolved }}
'
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        printf '%s\n' '[fixture_assert_shell_root_contract_scaffold_symlink] expected symlink target scaffold leak to fail' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'scaffoldLeaks: unresolved template token in active asset: CLAUDE.md' 'shell root contract symlink target scan' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}
# Verify install repairs a dangling AGENTS.md -> CLAUDE.md alias safely.
#
# @exit Exits with status 1 when repair is incorrect or partial.
fixture_assert_install_root_contract_dangling_agents_alias() {
    temp_dir=$(fixture_create_temp_dir)
    ln -s CLAUDE.md "$temp_dir/AGENTS.md"
    if fixture_run_command "$temp_dir" "sh \"$root/skills/harness-install/scripts/install-harness.sh\" --mode shell --target ."; then
        :
    else
        printf '%s\n' '[fixture_assert_install_root_contract_dangling_agents_alias] expected install to succeed with dangling AGENTS.md alias' >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stdout" 'create shared root contract: CLAUDE.md' 'install dangling AGENTS alias creates shared CLAUDE contract' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if printf '%s' "$fixture_stdout$fixture_stderr" | grep -Fq 'create symlink: CLAUDE.md -> AGENTS.md'; then
        printf '%s\n' '[fixture_assert_install_root_contract_dangling_agents_alias] unexpected CLAUDE symlink creation after dangling AGENTS alias' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if [ ! -L "$temp_dir/AGENTS.md" ] || [ "$(readlink "$temp_dir/AGENTS.md")" != "CLAUDE.md" ]; then
        printf '%s\n' '[fixture_assert_install_root_contract_dangling_agents_alias] expected AGENTS.md to remain a symlink to CLAUDE.md' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if [ ! -f "$temp_dir/CLAUDE.md" ] || [ -L "$temp_dir/CLAUDE.md" ]; then
        printf '%s\n' '[fixture_assert_install_root_contract_dangling_agents_alias] expected CLAUDE.md to become a real root-contract file' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! grep -Fq '# Repository Harness Contract' "$temp_dir/CLAUDE.md"; then
        printf '%s\n' '[fixture_assert_install_root_contract_dangling_agents_alias] CLAUDE.md missing Repository Harness Contract marker' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! grep -Fq '## Entry Point' "$temp_dir/CLAUDE.md"; then
        printf '%s\n' '[fixture_assert_install_root_contract_dangling_agents_alias] CLAUDE.md missing Entry Point marker' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}

# Verify install repairs a dangling CLAUDE.md -> AGENTS.md alias safely.
#
# @exit Exits with status 1 when repair is incorrect or partial.
fixture_assert_install_root_contract_dangling_claude_alias() {
    temp_dir=$(fixture_create_temp_dir)
    ln -s AGENTS.md "$temp_dir/CLAUDE.md"
    if fixture_run_command "$temp_dir" "sh \"$root/skills/harness-install/scripts/install-harness.sh\" --mode shell --target ."; then
        :
    else
        printf '%s\n' '[fixture_assert_install_root_contract_dangling_claude_alias] expected install to succeed with dangling CLAUDE.md alias' >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stdout" 'create shared root contract: AGENTS.md' 'install dangling CLAUDE alias creates shared AGENTS contract' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if printf '%s' "$fixture_stdout$fixture_stderr" | grep -Fq 'create symlink: AGENTS.md -> CLAUDE.md'; then
        printf '%s\n' '[fixture_assert_install_root_contract_dangling_claude_alias] unexpected AGENTS symlink creation after dangling CLAUDE alias' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if [ ! -L "$temp_dir/CLAUDE.md" ] || [ "$(readlink "$temp_dir/CLAUDE.md")" != "AGENTS.md" ]; then
        printf '%s\n' '[fixture_assert_install_root_contract_dangling_claude_alias] expected CLAUDE.md to remain a symlink to AGENTS.md' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if [ ! -f "$temp_dir/AGENTS.md" ] || [ -L "$temp_dir/AGENTS.md" ]; then
        printf '%s\n' '[fixture_assert_install_root_contract_dangling_claude_alias] expected AGENTS.md to become a real root-contract file' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! grep -Fq '# Repository Harness Contract' "$temp_dir/AGENTS.md"; then
        printf '%s\n' '[fixture_assert_install_root_contract_dangling_claude_alias] AGENTS.md missing Repository Harness Contract marker' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! grep -Fq '## Entry Point' "$temp_dir/AGENTS.md"; then
        printf '%s\n' '[fixture_assert_install_root_contract_dangling_claude_alias] AGENTS.md missing Entry Point marker' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}

# Verify install rejects both real AGENTS.md and CLAUDE.md files as divergent contracts.
#
# @exit Exits with status 1 when divergent real root contracts are partially rewritten.
fixture_assert_install_root_contract_real_files_rejected() {
    temp_dir=$(fixture_create_temp_dir)
    fixture_write_file "$temp_dir" AGENTS.md '# Repository Harness Contract'
    fixture_write_file "$temp_dir" CLAUDE.md '# Entry Point'
    agents_checksum_before=$(fixture_file_checksum "$temp_dir/AGENTS.md")
    claude_checksum_before=$(fixture_file_checksum "$temp_dir/CLAUDE.md")
    if fixture_run_command "$temp_dir" "sh \"$root/skills/harness-install/scripts/install-harness.sh\" --mode shell --target ."; then
        printf '%s\n' '[fixture_assert_install_root_contract_real_files_rejected] expected install to reject separate real AGENTS.md and CLAUDE.md files' >&2
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'resolve divergent root contract files before install' 'install rejects divergent real root contracts' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if [ -L "$temp_dir/AGENTS.md" ] || [ ! -f "$temp_dir/AGENTS.md" ]; then
        printf '%s\n' '[fixture_assert_install_root_contract_real_files_rejected] expected AGENTS.md to remain a real file' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if [ -L "$temp_dir/CLAUDE.md" ] || [ ! -f "$temp_dir/CLAUDE.md" ]; then
        printf '%s\n' '[fixture_assert_install_root_contract_real_files_rejected] expected CLAUDE.md to remain a real file' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if [ "$(fixture_file_checksum "$temp_dir/AGENTS.md")" != "$agents_checksum_before" ]; then
        printf '%s\n' '[fixture_assert_install_root_contract_real_files_rejected] expected AGENTS.md to remain unchanged' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if [ "$(fixture_file_checksum "$temp_dir/CLAUDE.md")" != "$claude_checksum_before" ]; then
        printf '%s\n' '[fixture_assert_install_root_contract_real_files_rejected] expected CLAUDE.md to remain unchanged' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}

# Smoke-check the shell stack runtime rejects unsafe manifest-controlled hook paths.
#
# @exit Exits with status 1 when unsafe hook paths are not rejected.
smoke_check_shell_unsafe_hook_paths() {
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" shell
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
{"name":"shell-unsafe-hook-fixture","filePresence":{"enabled":true,"severity":"ERROR","parameters":{"paths":["../required.md","-required.md","linked/required.md"]}},"directoryPresence":{"enabled":true,"severity":"ERROR","parameters":{"paths":["../required-dir","-required-dir","linked/required-dir"]}},"emptyDirectoryPlaceholders":{"enabled":true,"severity":"ERROR","parameters":{"directories":["../empty-dir","-empty-dir","linked/empty-dir"]}},"hookShebang":{"enabled":true,"severity":"ERROR","parameters":{"hooks":["../escape.sh","-flag.sh","linked/pre-push",""],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":true,"severity":"ERROR","parameters":{"hooks":["../escape.sh","-flag.sh","linked/pre-push",""]}},"hookCommand":{"enabled":false,"parameters":{"prePushHook":"","preCommitHook":"","allowedCommands":[],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":true,"severity":"ERROR","parameters":{"ciFiles":[],"referenceHook":"../outside.sh"}},"symlinkSafety":{"enabled":false,"parameters":{"allowedSymlinkPairs":[]}},"scaffoldLeaks":{"enabled":true,"severity":"ERROR","parameters":{"scope":{"bases":["../docs","-docs","linked","safe-docs"],"extensions":["md","sh"]},"patterns":[]}},"uncheckedTasks":{"enabled":true,"severity":"ERROR","parameters":{"directory":"../docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"shellcheck":{"enabled":false,"parameters":{}}}
JSONEOF
    )"
    mkdir -p "$temp_dir/outside"
    fixture_write_file "$temp_dir" outside/pre-push '#!/usr/bin/env sh
printf "%s\n" "outside"
'
    ln -s outside "$temp_dir/linked"
    mkdir -p "$temp_dir/safe-docs"
    ln -s ../outside/pre-push "$temp_dir/safe-docs/link.sh"
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        printf '%s\n' '[smoke_check_shell_unsafe_hook_paths] expected unsafe hook paths to fail' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" '../escape.sh is not a safe relative hook path' 'shell unsafe hookShebang parent traversal rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" '-flag.sh is not a safe relative hook path' 'shell unsafe hookShebang leading-dash rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'symlink file is not allowed: linked/pre-push' 'shell unsafe hookShebang parent symlink rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'hookShebang:  is not a safe relative hook path' 'shell unsafe hookShebang empty path rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'filePresence: ../required.md is not a safe relative file path' 'shell unsafe filePresence parent traversal rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'directoryPresence: -required-dir is not a safe relative directory path' 'shell unsafe directoryPresence leading-dash rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'symlink directory is not allowed: linked/empty-dir' 'shell unsafe emptyDirectoryPlaceholders symlink rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'scaffoldLeaks: ../docs is not a safe relative scan root' 'shell unsafe scaffoldLeaks scan root rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'symlink scan root is not allowed: linked' 'shell unsafe scaffoldLeaks symlink scan root rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'symlink scan entry is not allowed: safe-docs/link.sh' 'shell unsafe scaffoldLeaks symlink scan entry rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'uncheckedTasks: ../docs/exec-plans/completed is not a safe relative directory path' 'shell unsafe uncheckedTasks directory rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" '../outside.sh is not a safe relative hook path' 'shell unsafe ciHookCommandParity referenceHook rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_write_manifest "$temp_dir" '{"name":"shell-unsafe-ci-reference-fixture","filePresence":{"enabled":false,"parameters":{"paths":[]}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"hookCommand":{"enabled":false,"parameters":{"prePushHook":"","preCommitHook":"","allowedCommands":[],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":true,"severity":"ERROR","parameters":{"ciFiles":[],"referenceHook":"linked/pre-push"}},"symlinkSafety":{"enabled":false,"parameters":{"allowedSymlinkPairs":[]}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"shellcheck":{"enabled":false,"parameters":{}}}'
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        printf '%s\n' '[smoke_check_shell_unsafe_hook_paths] expected unsafe CI reference hook symlink to fail' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'symlink file is not allowed: linked/pre-push' 'shell unsafe ciHookCommandParity referenceHook symlink rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_write_file "$temp_dir" docs/harness/git-hooks/pre-push '#!/usr/bin/env sh
# Harness validation command: sh docs/harness/shell/harness-check.sh
sh docs/harness/shell/harness-check.sh
'
    fixture_write_manifest "$temp_dir" '{"name":"shell-unsafe-ci-files-fixture","filePresence":{"enabled":false,"parameters":{"paths":[]}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"hookCommand":{"enabled":false,"parameters":{"prePushHook":"","preCommitHook":"","allowedCommands":[],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":true,"severity":"ERROR","parameters":{"ciFiles":["../ci.yml","-ci.yml","linked/ci.yml"],"referenceHook":"docs/harness/git-hooks/pre-push"}},"symlinkSafety":{"enabled":false,"parameters":{"allowedSymlinkPairs":[]}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"shellcheck":{"enabled":false,"parameters":{}}}'
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        printf '%s\n' '[smoke_check_shell_unsafe_hook_paths] expected unsafe CI paths to fail' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" '../ci.yml is not a safe relative CI path' 'shell unsafe ciHookCommandParity CI parent traversal rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'symlink file is not allowed: linked/ci.yml' 'shell unsafe ciHookCommandParity CI symlink rejection' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    printf '%s\n' 'shell runtime: unsafe hook path rejection OK'
    fixture_remove_temp_dir "$temp_dir"
}

# Smoke-check the shell stack runtime with command parity fixtures.
#
# @exit Exits with status 1 when shell runtime command parity fails.
smoke_check_shell_runtime() {
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" shell
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
{"name":"shell-smoke-fixture","filePresence":{"enabled":false,"parameters":{"paths":[]}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":true,"severity":"ERROR","parameters":{"hooks":["docs/harness/git-hooks/pre-commit","docs/harness/git-hooks/pre-push"],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":true,"severity":"ERROR","parameters":{"hooks":["docs/harness/git-hooks/pre-commit","docs/harness/git-hooks/pre-push"]}},"hookCommand":{"enabled":true,"severity":"ERROR","parameters":{"prePushHook":"docs/harness/git-hooks/pre-push","preCommitHook":"docs/harness/git-hooks/pre-commit","allowedCommands":["sh docs/harness/shell/harness-check.sh"],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":true,"severity":"WARN","parameters":{"ciFiles":[".github/workflows/harness.yml",".gitlab-ci.yml"],"referenceHook":"docs/harness/git-hooks/pre-push"}},"symlinkSafety":{"enabled":true,"severity":"ERROR","messages":{"fileNotAllowed":"symlink file is not allowed: {path}","directoryNotAllowed":"symlink directory is not allowed: {path}","scanRootNotAllowed":"symlink scan root is not allowed: {path}","scanEntryNotAllowed":"symlink scan entry is not allowed: {path}","pathNotAllowed":"symlink path is not allowed: {path}"},"parameters":{"allowedSymlinkPairs":[["AGENTS.md","CLAUDE.md"]]}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"shellcheck":{"enabled":false,"parameters":{}}}
JSONEOF
    )"
    fixture_write_file "$temp_dir" docs/harness/git-hooks/pre-commit '#!/usr/bin/env sh
# Harness generated hook: pre-commit
# Harness stage: compliance
printf "%s\n" "compliance"
'
    fixture_write_file "$temp_dir" docs/harness/git-hooks/pre-push '#!/usr/bin/env sh
# Harness generated hook: pre-push
# Harness stage: full-validation
# Harness validation command: sh docs/harness/shell/harness-check.sh
sh docs/harness/shell/harness-check.sh
'
    chmod +x "$temp_dir/docs/harness/git-hooks/pre-commit" "$temp_dir/docs/harness/git-hooks/pre-push"
    fixture_write_file "$temp_dir" .github/workflows/harness.yml 'run: sh docs/harness/shell/harness-check.sh
'
    fixture_write_file "$temp_dir" .gitlab-ci.yml 'script: sh docs/harness/shell/harness-check.sh
'
    mkdir -p "$temp_dir/docs/harness/shell"
    cp "$temp_dir/harness-check.sh" "$temp_dir/docs/harness/shell/harness-check.sh"
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        :
    else
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stdout" 'Harness validation passed' 'shell runtime smoke valid parity' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_write_file "$temp_dir" docs/harness/git-hooks/pre-push '#!/usr/bin/env sh
# Harness generated hook: pre-push
# Harness stage: full-validation
# Harness validation command: sh wrong.sh
sh wrong.sh
'
    chmod +x "$temp_dir/docs/harness/git-hooks/pre-push"
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        printf '%s\n' '[smoke_check_shell_runtime] expected unsupported shell command to fail' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" 'declares unsupported validation command: sh wrong.sh' 'shell runtime smoke unsupported command' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_write_file "$temp_dir" docs/harness/git-hooks/pre-push '#!/usr/bin/env sh
# Harness generated hook: pre-push
# Harness stage: full-validation
# Harness validation command: sh docs/harness/shell/harness-check.sh
sh docs/harness/shell/harness-check.sh
'
    chmod +x "$temp_dir/docs/harness/git-hooks/pre-push"
    fixture_write_file "$temp_dir" .github/workflows/harness.yml 'run: sh unrelated.sh
'
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        :
    else
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" '[WARN] ciHookCommandParity' 'shell runtime smoke CI warning' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_write_manifest "$temp_dir" '{"name":"shell-optional-fixture","filePresence":{"enabled":false,"parameters":{"paths":[]}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"symlinkSafety":{"enabled":false,"parameters":{"allowedSymlinkPairs":[]}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"shellcheck":{"enabled":false,"parameters":{}}}'
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        :
    else
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_write_manifest "$temp_dir" '{"name":"shell-unsafe-fixture","filePresence":{"enabled":false,"parameters":{"paths":[]}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"hookCommand":{"enabled":true,"severity":"ERROR","parameters":{"prePushHook":"../outside","preCommitHook":"docs/harness/git-hooks/pre-commit","allowedCommands":["sh docs/harness/shell/harness-check.sh"],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":false,"parameters":{"ciFiles":[],"referenceHook":"docs/harness/git-hooks/pre-push"}},"symlinkSafety":{"enabled":false,"parameters":{"allowedSymlinkPairs":[]}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"shellcheck":{"enabled":false,"parameters":{}}}'
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        printf '%s\n' '[smoke_check_shell_runtime] expected unsafe shell hook path to fail' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stderr" '../outside is not a safe relative hook path' 'shell runtime smoke unsafe hook path' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    printf '%s\n' 'shell runtime: harness-check.sh OK'
    fixture_remove_temp_dir "$temp_dir"
}


# Verify uv ruff runtime accepts clean code with no wildcardImport violations.
#
#     Requires `uv` in PATH. Gracefully skips with a warning when uv is unavailable.
#
# @return Returns 0 on success or when uv is missing.
# @exit Exits with status 1 when clean code unexpectedly produces errors.
fixture_assert_uv_ruff_clean() {
    if ! uv_path=$(command -v uv 2>&1); then
        printf 'warning: uv not in PATH; skipping uv ruff clean fixture\n' >&2
        return 0
    fi
    : "$uv_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" uv
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
{"name":"uv-ruff-clean-fixture","wildcardImport":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["py"],"includePaths":[],"excludePaths":[]}}}
JSONEOF
    )"
    mkdir -p "$temp_dir/src"
    cat > "$temp_dir/src/clean.py" <<'PYEOF'
from os import getcwd
def get_cwd() -> str:
    return getcwd()
PYEOF
    if fixture_run_command "$temp_dir" "uv run --quiet --with libcst \"$temp_dir/harness_check.py\""; then
        printf 'fixture_assert_uv_ruff_clean passed: compliant code produced no errors\n' >&2
        fixture_remove_temp_dir "$temp_dir"
        return 0
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if printf '%s' "$fixture_combined_output" | grep -q 'wildcardImport'; then
        printf 'fixture_assert_uv_ruff_clean failed: compliant code unexpectedly reported wildcardImport\n' >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    printf 'fixture_assert_uv_ruff_clean failed: compliant code unexpectedly produced errors\n' >&2
    printf '%s\n' "$fixture_combined_output" >&2
    fixture_remove_temp_dir "$temp_dir"
    exit 1
}

# Verify uv ruff runtime detects wildcardImport violations.
#
#     Requires `uv` in PATH. Gracefully skips with a warning when uv is unavailable.
#
# @return Returns 0 on success or when uv is missing.
# @exit Exits with status 1 when violation is not detected.
fixture_assert_uv_ruff_detects() {
    if ! uv_path=$(command -v uv 2>&1); then
        printf 'warning: uv not in PATH; skipping uv ruff detects fixture\n' >&2
        return 0
    fi
    : "$uv_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" uv
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
{"name":"uv-ruff-detects-fixture","wildcardImport":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["py"],"includePaths":[],"excludePaths":[]}}}
JSONEOF
    )"
    mkdir -p "$temp_dir/src"
    cat > "$temp_dir/src/dirty.py" <<'PYEOF'
from os import *
def get_cwd() -> str:
    return getcwd()
PYEOF
    if fixture_run_command "$temp_dir" "uv run --quiet --with libcst \"$temp_dir/harness_check.py\""; then
        printf 'fixture_assert_uv_ruff_detects unexpectedly succeeded\n' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! printf '%s' "$fixture_combined_output" | grep -q 'wildcardImport'; then
        printf 'fixture_assert_uv_ruff_detects failed: wildcardImport not detected\n' >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! printf '%s' "$fixture_combined_output" | grep -q '\[ERROR\]'; then
        printf 'fixture_assert_uv_ruff_detects failed: ERROR severity not reported\n' >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    printf 'fixture_assert_uv_ruff_detects passed: wildcardImport violation detected\n' >&2
    fixture_remove_temp_dir "$temp_dir"
    return 0
}

# Verify Bun AST source roots cannot escape the target root.
#
#     Requires `bun` in PATH. Gracefully skips with a warning when bun is
#     unavailable.
#
# @return Returns 0 on success or when bun is missing.
# @exit Exits with status 1 when an unsafe source root is accepted or not reported.
fixture_assert_bun_source_root_safety() {
    if ! bun_path=$(command -v bun 2>&1); then
        printf 'warning: bun not in PATH; skipping bun source-root safety fixture check\n' >&2
        return 0
    fi
    if ! bunx_path=$(command -v bunx 2>&1); then
        printf 'warning: bunx not provisioned; skipping bun source-root safety fixture check\n' >&2
        return 0
    fi
    : "$bun_path" "$bunx_path"
    fixture_root=$(fixture_create_temp_dir)
    target_dir=$fixture_root/target
    fixture_copy_runtime "$target_dir" bun
    fixture_write_manifest "$target_dir" '{"name":"bun-source-root-safety-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["../outside"],"extensions":["ts"],"includePaths":[],"excludePaths":[]}}}'
    fixture_write_file "$fixture_root" outside/OutsideFixture.ts 'export function unsafe(value: number): boolean {
    return value > 1;
}
'
    fixture_before_checksum=$(fixture_file_checksum "$fixture_root/outside/OutsideFixture.ts")
    if fixture_run_command "$target_dir" "bun \"$target_dir/harness-format.ts\""; then
        :
    else
        :
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    fixture_after_checksum=$(fixture_file_checksum "$fixture_root/outside/OutsideFixture.ts")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'bun unsafe source root leaves outside file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_write_file "$fixture_root" outside/OutsideFixture.ts 'export function unsafe(value: number): boolean {
    return value > 1;
}
'
    fixture_before_checksum=$(fixture_file_checksum "$fixture_root/outside/OutsideFixture.ts")
    if fixture_run_command "$target_dir" "bun \"$target_dir/harness-check.ts\""; then
        :
    else
        :
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    fixture_after_checksum=$(fixture_file_checksum "$fixture_root/outside/OutsideFixture.ts")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'bun unsafe source root check leaves outside file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    if printf '%s' "$fixture_combined_output" | grep -Fq 'OutsideFixture'; then
        printf '%s\n' '[fixture_assert_bun_source_root_safety] outside file was scanned' >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_remove_temp_dir "$fixture_root"
}

# Verify Bun runtime rejects manifest paths through symlink components.
#
#     Creates a symlink directory inside the target root pointing outside,
#     then runs harness-check and harness-format with a sourceRoot that
#     traverses the symlink. Proves the runtime never reads files reached
#     through a symlink component.
#
#     Requires `bun` in PATH. Gracefully skips with a warning when bun is
#     unavailable.
#
# @return Returns 0 on success or when bun is missing.
# @exit Exits with status 1 when a file behind a symlink component is read.
fixture_assert_bun_symlink_component_safety() {
    if ! bun_path=$(command -v bun 2>&1); then
        printf 'warning: bun not in PATH; skipping bun symlink component safety fixture check\n' >&2
        return 0
    fi
    if ! bunx_path=$(command -v bunx 2>&1); then
        printf 'warning: bunx not provisioned; skipping bun symlink component safety fixture check\n' >&2
        return 0
    fi
    : "$bun_path" "$bunx_path"
    fixture_root=$(fixture_create_temp_dir)
    target_dir=$fixture_root/target
    fixture_copy_runtime "$target_dir" bun
    fixture_write_file "$fixture_root" outside/LinkedFixture.ts 'export function unsafe(value: number): boolean {
    return value > 1;
}
'
    mkdir -p "$fixture_root/outside-real"
    fixture_write_file "$fixture_root" outside-real/LinkedFixture2.ts 'export function unsafe(value: number): boolean {
    return value > 1;
}
'
    ln -s "$fixture_root/outside-real" "$target_dir/linked-outside"
    fixture_write_manifest "$target_dir" '{"name":"bun-symlink-component-safety-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["linked-outside"],"extensions":["ts"],"includePaths":[],"excludePaths":[]}}}'
    linked_outside_checksum_before=$(fixture_file_checksum "$fixture_root/outside-real/LinkedFixture2.ts")
    if fixture_run_command "$target_dir" "bun \"$target_dir/harness-format.ts\""; then
        :
    else
        :
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    linked_outside_checksum_after=$(fixture_file_checksum "$fixture_root/outside-real/LinkedFixture2.ts")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$linked_outside_checksum_before" "$linked_outside_checksum_after" 'bun symlink component source root leaves outside file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    if fixture_run_command "$target_dir" "bun \"$target_dir/harness-check.ts\""; then
        :
    else
        :
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    linked_outside_checksum_after_check=$(fixture_file_checksum "$fixture_root/outside-real/LinkedFixture2.ts")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$linked_outside_checksum_before" "$linked_outside_checksum_after_check" 'bun symlink component source root check leaves outside file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    if printf '%s' "$fixture_combined_output" | grep -Fq 'LinkedFixture2'; then
        printf '%s\n' '[fixture_assert_bun_symlink_component_safety] symlink component file was scanned' >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_remove_temp_dir "$fixture_root"
}

# Verify Bun oxlint runtime produces zero findings on compliant code.
#
#     Compliant code: single exit (no early return), rethrow in catch (no silent catch),
#     multiline doc comments, documented exports, no console usage, no > or >= operators,
#     no leading underscore, no namespace imports, no @ts- comments.
#
#     Requires `bun` and `bunx` in PATH. Gracefully skips with a warning when either is unavailable.
#
# @return Returns 0 on success or when bun/bunx is missing.
# @exit Exits with status 1 when compliant code unexpectedly produces errors.
fixture_assert_bun_oxlint_clean() {
    if ! bun_path=$(command -v bun 2>&1); then
        printf 'warning: bun not in PATH; skipping bun oxlint clean fixture\n' >&2
        return 0
    fi
    if ! bunx_path=$(command -v bunx 2>&1); then
        printf 'warning: bunx not provisioned; skipping bun oxlint clean fixture\n' >&2
        return 0
    fi
    : "$bun_path" "$bunx_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" bun
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
{"name":"bun-oxlint-clean-fixture","multilineDocStyle":{"enabled":true,"severity":"WARN","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[],"docStyleMode":"multiline"}},"publicDeclarationDocComment":{"enabled":true,"severity":"WARN","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[],"visibility":["export"]}},"unstructuredLogging":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[]}},"ifStatementBraces":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[]}},"leadingUnderscore":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[]}},"wildcardImport":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[]}},"uncheckedCastSuppression":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[]}}}
JSONEOF
    )"
    mkdir -p "$temp_dir/src"
    cat > "$temp_dir/src/clean.ts" <<'TSEOF'
/**
 * Return the smaller of two numbers using a single exit.
 */
export function smaller(a: number, b: number): number {
    const result = a < b ? a : b;
    return result;
}

/**
 * Run a task and rethrow any failure.
 */
export function runTask(task: () => void): void {
    try {
        task();
    } catch (error) {
        throw error;
    }
}
TSEOF
    if fixture_run_command "$temp_dir" "bun \"$temp_dir/harness-check.ts\""; then
        printf 'fixture_assert_bun_oxlint_clean passed: compliant code produced no errors\n' >&2
        fixture_remove_temp_dir "$temp_dir"
        return 0
    fi
    printf 'fixture_assert_bun_oxlint_clean failed: compliant code unexpectedly produced errors\n' >&2
    printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr" >&2
    fixture_remove_temp_dir "$temp_dir"
    exit 1
}

# Verify Bun oxlint runtime detects all 8 harness categories.
#
#     Writes a source file that violates each of the 8 oxlint-owned categories
#     and asserts that harness-check reports all 8 categories.
#
#     Requires `bun` and `bunx` in PATH. Gracefully skips with a warning when either is unavailable.
#
# @return Returns 0 on success or when bun/bunx is missing.
# @exit Exits with status 1 when any category is not detected.
fixture_assert_bun_oxlint_detects() {
    if ! bun_path=$(command -v bun 2>&1); then
        printf 'warning: bun not in PATH; skipping bun oxlint detects fixture\n' >&2
        return 0
    fi
    if ! bunx_path=$(command -v bunx 2>&1); then
        printf 'warning: bunx not provisioned; skipping bun oxlint detects fixture\n' >&2
        return 0
    fi
    : "$bun_path" "$bunx_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" bun
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
{"name":"bun-oxlint-detects-fixture","multilineDocStyle":{"enabled":true,"severity":"WARN","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[],"docStyleMode":"multiline"}},"publicDeclarationDocComment":{"enabled":true,"severity":"WARN","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[],"visibility":["export"]}},"unstructuredLogging":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[]}},"ifStatementBraces":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[]}},"leadingUnderscore":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[]}},"wildcardImport":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[]}},"uncheckedCastSuppression":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[]}}}
JSONEOF
    )"
    mkdir -p "$temp_dir/src"
    cat > "$temp_dir/src/violations.ts" <<'TSEOF'
/** Violation: multilineDocStyle uses single-line JSDoc */
export function singleLineDoc(): void {}

export function undocumentedFunc(): void {}

/**
 * Violation: unstructuredLogging uses console.
 */
export function loggingViolation(): void {
    console.log("test");
}

/**
 * Violation: ifStatementBraces missing braces.
 */
export function ifViolation(flag: boolean): number {
    if (flag)
        return 1;
    return 2;
}

/**
 * Violation: leadingUnderscore uses leading underscore.
 */
export function _privateFunc(): void {}

/**
 * Violation: wildcardImport uses namespace import.
 */
import * as ns from "module";

/**
 * Violation: uncheckedCastSuppression uses @ts-ignore.
 */
export function castViolation(): any {
    const x: unknown = {};
    return x as string; // @ts-ignore
}
TSEOF
    if fixture_run_command "$temp_dir" "bun \"$temp_dir/harness-check.ts\""; then
        printf 'fixture_assert_bun_oxlint_detects unexpectedly succeeded\n' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    detected_categories=0
    missing_categories=""
    if printf '%s' "$fixture_combined_output" | grep -qE 'multilineDocStyle'; then
        detected_categories=$((detected_categories + 1))
    else
        missing_categories="$missing_categories multilineDocStyle"
    fi
    if printf '%s' "$fixture_combined_output" | grep -qE 'publicDeclarationDocComment'; then
        detected_categories=$((detected_categories + 1))
    else
        missing_categories="$missing_categories publicDeclarationDocComment"
    fi
    if printf '%s' "$fixture_combined_output" | grep -qE 'unstructuredLogging'; then
        detected_categories=$((detected_categories + 1))
    else
        missing_categories="$missing_categories unstructuredLogging"
    fi
    if printf '%s' "$fixture_combined_output" | grep -qE 'ifStatementBraces'; then
        detected_categories=$((detected_categories + 1))
    else
        missing_categories="$missing_categories ifStatementBraces"
    fi
    if printf '%s' "$fixture_combined_output" | grep -qE 'leadingUnderscore'; then
        detected_categories=$((detected_categories + 1))
    else
        missing_categories="$missing_categories leadingUnderscore"
    fi
    if printf '%s' "$fixture_combined_output" | grep -qE 'wildcardImport'; then
        detected_categories=$((detected_categories + 1))
    else
        missing_categories="$missing_categories wildcardImport"
    fi
    if printf '%s' "$fixture_combined_output" | grep -qE 'uncheckedCastSuppression'; then
        detected_categories=$((detected_categories + 1))
    else
        missing_categories="$missing_categories uncheckedCastSuppression"
    fi
    if [ "$detected_categories" -ne 7 ]; then
        printf 'fixture_assert_bun_oxlint_detects: only detected %d of 8 categories\n' "$detected_categories" >&2
        printf 'missing:%s\n' "$missing_categories" >&2
        printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    printf 'fixture_assert_bun_oxlint_detects passed: all 7 categories detected\n' >&2
    fixture_remove_temp_dir "$temp_dir"
    return 0
}

# Verify uv AST source roots cannot escape the target root.
#
#     Requires `uv` in PATH. Gracefully skips with a warning when uv is
#     unavailable.
#
# @return Returns 0 on success or when uv is missing.
# @exit Exits with status 1 when an unsafe source root is accepted or not reported.
fixture_assert_uv_source_root_safety() {
    if ! uv_path=$(command -v uv 2>&1); then
        printf 'warning: uv not in PATH; skipping uv source-root safety fixture check\n' >&2
        return 0
    fi
    : "$uv_path"
    fixture_root=$(fixture_create_temp_dir)
    target_dir=$fixture_root/target
    fixture_copy_runtime "$target_dir" uv
    fixture_write_manifest "$target_dir" '{"name":"uv-source-root-safety-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["../outside"],"extensions":["py"],"includePaths":[],"excludePaths":[]}}}'
    fixture_write_file "$fixture_root" outside/OutsideFixture.py 'def unsafe(value: int) -> bool:
    return value > 1
'
    fixture_before_checksum=$(fixture_file_checksum "$fixture_root/outside/OutsideFixture.py")
    if fixture_run_command "$target_dir" "uv run --quiet --with libcst \"$target_dir/harness_format.py\""; then
        :
    else
        :
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    fixture_after_checksum=$(fixture_file_checksum "$fixture_root/outside/OutsideFixture.py")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'uv unsafe source root leaves outside file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_remove_temp_dir "$fixture_root"
}

# Verify uv runtime prunes .claude/worktrees when sourceRoots include broad globs.
#
#     Requires `uv` in PATH. Gracefully skips with a warning when uv is
#     unavailable.
#
# @return Returns 0 on success or when uv is missing.
# @exit Exits with status 1 when worktree content is scanned or mutated.
fixture_assert_uv_worktree_excluded() {
    if ! uv_path=$(command -v uv 2>&1); then
        printf 'warning: uv not in PATH; skipping uv worktree exclusion fixture check\n' >&2
        return 0
    fi
    : "$uv_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" uv
    fixture_write_manifest "$temp_dir" '{"name":"uv-worktree-exclusion-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["py"],"includePaths":[],"excludePaths":[]}}}'
    fixture_write_file "$temp_dir" src/GoodFixture.py 'def safe(value: int) -> bool:
    return value > 1
'
    mkdir -p "$temp_dir/.claude/worktrees/abc1234/src"
    fixture_write_file "$temp_dir" ".claude/worktrees/abc1234/src/WorktreeFixture.py" 'def bad(value: int) -> bool:
    return value > 1
'
    worktree_file=$temp_dir/.claude/worktrees/abc1234/src/WorktreeFixture.py
    worktree_before_checksum=$(fixture_file_checksum "$worktree_file")
    if fixture_run_command "$temp_dir" "uv run --quiet --with libcst \"$temp_dir/harness_check.py\""; then
        :
    else
        :
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'publicDeclarationDocComment' 'uv worktree exclusion reports legitimate finding' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if printf '%s' "$fixture_combined_output" | grep -Fq 'WorktreeFixture.py'; then
        printf '%s\n' '[fixture_assert_uv_worktree_excluded] worktree file was scanned during check' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" "uv run --quiet --with libcst \"$temp_dir/harness_format.py\""; then
        :
    else
        :
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    worktree_after_checksum=$(fixture_file_checksum "$worktree_file")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$worktree_before_checksum" "$worktree_after_checksum" 'uv worktree file unchanged by format' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}

# Verify uv hookShebang validation and formatting reports instead of NameError.
#
#     Requires `uv` in PATH. Gracefully skips with a warning when uv is
#     unavailable.
#
# @return Returns 0 on success or when uv is missing.
# @exit Exits with status 1 when the uv hookShebang fixture fails.
fixture_assert_uv_hook_shebang() {
    if ! uv_path=$(command -v uv 2>&1); then
        printf 'warning: uv not in PATH; skipping uv hookShebang fixture check\n' >&2
        return 0
    fi
    : "$uv_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" uv
    fixture_write_manifest "$temp_dir" '{"name":"uv-hook-shebang-fixture","hookShebang":{"enabled":true,"severity":"ERROR","messages":{"default":"{hook} must start with {expectedShebang}"},"parameters":{"hooks":["docs/harness/git-hooks/pre-commit"],"expectedShebang":"#!/usr/bin/env sh"}}}'
    fixture_write_file "$temp_dir" docs/harness/git-hooks/pre-commit 'echo "wrong shebang hook"
'
    fixture_before_checksum=$(fixture_file_checksum "$temp_dir/docs/harness/git-hooks/pre-commit")
    if fixture_run_command "$temp_dir" "uv run --quiet --with libcst \"$temp_dir/harness_check.py\""; then
        :
    else
        :
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_canonical_finding_prefix "$fixture_stdout" 'docs/harness/git-hooks/pre-commit' 'hookShebang' 'uv hookShebang reports canonical finding' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" "uv run --quiet --with libcst \"$temp_dir/harness_format.py\""; then
        :
    else
        :
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    fixture_after_checksum=$(fixture_file_checksum "$temp_dir/docs/harness/git-hooks/pre-commit")
    if ! fixture_assertion_output=$(fixture_assert_checksum_changed "$fixture_before_checksum" "$fixture_after_checksum" 'uv hookShebang formatter inserts expected shebang' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! grep -Fq '#!/usr/bin/env sh' "$temp_dir/docs/harness/git-hooks/pre-commit"; then
        printf '%s\n' '[fixture_assert_uv_hook_shebang] expected shebang not found after format' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" "uv run --quiet --with libcst \"$temp_dir/harness_format.py\""; then
        :
    else
        :
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    fixture_after_second_checksum=$(fixture_file_checksum "$temp_dir/docs/harness/git-hooks/pre-commit")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_after_checksum" "$fixture_after_second_checksum" 'uv hookShebang second format is idempotent' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}

# Verify uv read/stat helpers cannot read or accept files outside root or
# through symlink components. Outside file content must remain unchanged.
#
#     Requires `uv` in PATH. Gracefully skips with a warning when uv is
#     unavailable.
#
# @return Returns 0 on success or when uv is missing.
# @exit Exits with status 1 when an unsafe path is accepted or outside content changes.
fixture_assert_uv_unsafe_manifest_paths() {
    if ! uv_path=$(command -v uv 2>&1); then
        printf 'warning: uv not in PATH; skipping uv unsafe manifest path fixture check\n' >&2
        return 0
    fi
    : "$uv_path"
    fixture_root=$(fixture_create_temp_dir)
    target_dir=$fixture_root/target
    fixture_copy_runtime "$target_dir" uv
    fixture_write_manifest "$target_dir" "$(
        cat <<'JSONEOF'
{"name":"uv-unsafe-path-fixture","filePresence":{"enabled":true,"severity":"ERROR","parameters":{"paths":["../outside.md","-unsafe.md","linked/target.md"]}},"hookShebang":{"enabled":true,"severity":"ERROR","parameters":{"hooks":["docs/harness/git-hooks/pre-commit","../outside.sh","linked/hook.sh"],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":true,"severity":"ERROR","parameters":{"hooks":["docs/harness/git-hooks/pre-commit","../outside.sh","linked/hook.sh"]}}}
JSONEOF
    )"
    fixture_write_file "$fixture_root" outside.md 'outside content'
    fixture_write_file "$fixture_root" outside.sh '#!/usr/bin/env sh
echo "outside"
'
    chmod +x "$fixture_root/outside.sh"
    mkdir -p "$target_dir/docs/harness/git-hooks"
    fixture_write_file "$target_dir" docs/harness/git-hooks/pre-commit '#!/usr/bin/env sh
echo "valid hook"
'
    chmod +x "$target_dir/docs/harness/git-hooks/pre-commit"
    ln -s "$fixture_root/outside.md" "$target_dir/linked.md"
    mkdir -p "$target_dir/linked"
    ln -s "$fixture_root/outside.sh" "$target_dir/linked/hook.sh"
    ln -s "$fixture_root/outside.md" "$target_dir/linked/target.md"
    outside_before_checksum=$(fixture_file_checksum "$fixture_root/outside.md")
    outside_sh_before_checksum=$(fixture_file_checksum "$fixture_root/outside.sh")
    if fixture_run_command "$target_dir" "uv run --quiet --with libcst \"$target_dir/harness_check.py\""; then
        :
    else
        :
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'filePresence' 'uv unsafe path reports filePresence' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    if fixture_run_command "$target_dir" "uv run --quiet --with libcst \"$target_dir/harness_format.py\""; then
        :
    else
        :
    fi
    outside_after_checksum=$(fixture_file_checksum "$fixture_root/outside.md")
    outside_sh_after_checksum=$(fixture_file_checksum "$fixture_root/outside.sh")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$outside_before_checksum" "$outside_after_checksum" 'uv unsafe path leaves outside file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$outside_sh_before_checksum" "$outside_sh_after_checksum" 'uv unsafe path leaves outside hook unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_remove_temp_dir "$fixture_root"
}

# Verify Gradle AST findings render canonical file:line:column prefixes.
#
#     Requires `gradle` in PATH. Gracefully skips with a warning when gradle is unavailable.
#
# @return Returns 0 on success or when gradle is missing.
# @exit Exits with status 1 when the Gradle location fixture fails.
fixture_assert_gradle_location() {
    if ! gradle_path=$(command -v gradle 2>&1); then
        printf 'warning: gradle not in PATH; skipping gradle location fixture check\n' >&2
        return 0
    fi
    : "$gradle_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" gradle
    fixture_write_file "$temp_dir" settings.gradle.kts 'rootProject.name = "gradle-location-fixture"'
    fixture_write_file "$temp_dir" build.gradle.kts "$(
        cat <<'KOTLINEOF'
plugins {
    id("com.ririnto.sinon.harness")
}

repositories {
    mavenCentral()
}
KOTLINEOF
    )"
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
  {"name":"gradle-location-fixture","filePresence":{"enabled":true,"severity":"ERROR","paths":["MISSING.md"],"parameters":{}},"ifStatementBraces":{"enabled":true,"severity":"ERROR","messages":{"default":"if/else without braces; wrap the body in `{ ... }`"},"parameters":{"sourceRoots":["buildSrc/src/main/kotlin"],"extensions":["kt"],"includePaths":[],"excludePaths":[]}},"importOverFqn":{"enabled":true,"severity":"ERROR","messages":{"default":"fully qualified name `{name}` used inline; add an import and use the simple name"},"parameters":{"sourceRoots":["buildSrc/src/main/kotlin"],"extensions":["kt"],"includePaths":[],"excludePaths":[],"allowedFqnPatterns":[]}},"uncheckedCastSuppression":{"enabled":true,"severity":"ERROR","messages":{"default":"avoid suppression of forbidden tokens (`{snippet}`); refactor to type-safe cast or explicit handling"},"parameters":{"sourceRoots":["src/main/kotlin"],"extensions":["kt"],"includePaths":[],"excludePaths":[],"forbiddenSuppressions":["UNCHECKED_CAST"],"allowedSuppressions":[]}},"implicitLambdaIt":{"enabled":false},"publicDeclarationDocComment":{"enabled":false},"wildcardImport":{"enabled":false}}
JSONEOF
    )"
    fixture_write_file "$temp_dir" buildSrc/src/main/kotlin/fixture/LocationFixture.kt "$(
        cat <<'KOTLINEOF'
package fixture

class LocationFixture {
    fun later(flag: Boolean): String {
        if (flag) return "value"
        return "other"
    }
}
KOTLINEOF
    )"
    fixture_write_file "$temp_dir" src/main/kotlin/fixture/SuppressionFixture.kt "$(
        cat <<'KOTLINEOF'
package fixture

class SuppressionFixture {
    @Suppress(value = ["UNCHECKED_CAST"])
    fun unsafeCast(value: Any): List<String> {
        return value as List<String>
    }
}
KOTLINEOF
    )"
    fixture_write_file "$temp_dir" buildSrc/src/main/kotlin/fixture/FqnFixture.kt "$(
        cat <<'KOTLINEOF'
package fixture

class FqnFixture {
    val names: java.util.List<String>? = null

    fun create(): Any {
        return java.util.ArrayList<String>()
    }
}
KOTLINEOF
    )"
    if fixture_run_command "$temp_dir" 'gradle --console=plain --no-daemon harnessCheck'; then
        printf '%s\n' '[fixture_assert_gradle_location] expected harnessCheck to report if statement braces' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_canonical_finding_prefix "$fixture_combined_output" 'buildSrc/src/main/kotlin/fixture/LocationFixture[.]kt' 'ifStatementBraces' 'gradle AST canonical finding prefix' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" '[ERROR] filePresence: missing file: MISSING.md' 'gradle repository-level finding' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_canonical_finding_prefix "$fixture_combined_output" 'src/main/kotlin/fixture/SuppressionFixture[.]kt' 'uncheckedCastSuppression' 'gradle unchecked suppression structural fixture' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_canonical_finding_prefix "$fixture_combined_output" 'buildSrc/src/main/kotlin/fixture/FqnFixture[.]kt' 'importOverFqn' 'gradle import-over-fqn type and call fixture' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    for name in 'java.util.List' 'java.util.ArrayList'; do
        if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" "$name" "gradle import-over-fqn reports $name" 2>&1); then
            printf '%s\n' "$fixture_assertion_output" >&2
            printf '%s\n' "$fixture_combined_output" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
        fi
    done
    if printf '%s\n' "$fixture_combined_output" | grep -Eq '^[^:]+:0:0'; then
        printf '%s\n' '[fixture_assert_gradle_location] repository-level finding rendered a fabricated :0:0 location' >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    : "$fixture_assertion_output"
    fixture_remove_temp_dir "$temp_dir"
}


# Verify Gradle AST source roots cannot escape the target root.
#
#     Requires `gradle` in PATH. Gracefully skips with a warning when gradle is unavailable.
#
# @return Returns 0 on success or when gradle is missing.
# @exit Exits with status 1 when an unsafe source root is accepted or not reported.
fixture_assert_gradle_source_root_safety() {
    if ! gradle_path=$(command -v gradle 2>&1); then
        printf 'warning: gradle not in PATH; skipping gradle source-root safety fixture check\n' >&2
        return 0
    fi
    : "$gradle_path"
    fixture_root=$(fixture_create_temp_dir)
    target_dir=$fixture_root/target
    fixture_copy_runtime "$target_dir" gradle
    fixture_write_file "$target_dir" settings.gradle.kts 'rootProject.name = "gradle-source-root-safety-fixture"'
    fixture_write_file "$target_dir" build.gradle.kts 'plugins { id("com.ririnto.sinon.harness") }

repositories { mavenCentral() }
'
    fixture_write_manifest "$target_dir" '{"name":"gradle-source-root-safety-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["../outside"],"extensions":["kt"],"includePaths":[],"excludePaths":[]}}}'
    fixture_write_file "$fixture_root" outside/OutsideFixture.kt 'package outside

fun undocumented(left: Int): Boolean {
    return left > 1
}'
    fixture_before_checksum=$(fixture_file_checksum "$fixture_root/outside/OutsideFixture.kt")
    if fixture_run_command "$target_dir" 'gradle --console=plain --no-daemon harnessFormat'; then
        printf '%s\n' "[fixture_assert_gradle_source_root_safety] expected harnessFormat to fail on unsafe source roots"
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    fixture_after_checksum=$(fixture_file_checksum "$fixture_root/outside/OutsideFixture.kt")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'gradle unsafe source root leaves outside file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'source root traversal is not allowed: ../outside' 'gradle unsafe source root reports traversal finding' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_remove_temp_dir "$fixture_root"
}

# Verify Gradle parse-error detection reports errors and skips rule scanning.
#
#     Requires `gradle` in PATH. Gracefully skips with a warning when gradle is unavailable.
#
# @return Returns 0 on success or when gradle is missing.
# @exit Exits with status 1 when the Gradle parse-error fixture fails.
fixture_assert_gradle_parse_error() {
    if ! gradle_path=$(command -v gradle 2>&1); then
        printf 'warning: gradle not in PATH; skipping gradle parse-error fixture check\n' >&2
        return 0
    fi
    : "$gradle_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" gradle
    fixture_write_file "$temp_dir" settings.gradle.kts 'rootProject.name = "gradle-parse-error-fixture"'
    fixture_write_file "$temp_dir" build.gradle.kts 'plugins { id("com.ririnto.sinon.harness") }

repositories { mavenCentral() }
'
    fixture_write_manifest "$temp_dir" '{"name":"gradle-parse-error-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src/harness-fixtures/kotlin/fixture"],"extensions":["kt"],"includePaths":[],"excludePaths":[]}}}'
    fixture_write_file "$temp_dir" src/harness-fixtures/kotlin/fixture/BrokenFixture.kt 'package fixture

class BrokenFixture {
    fun broken( {
        return 1 > 2
    }
}
'
    if fixture_run_command "$temp_dir" 'gradle --console=plain --no-daemon harnessCheck'; then
        printf '%s\n' '[fixture_assert_gradle_parse_error] expected harnessCheck to fail on parse error' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_canonical_finding_prefix "$fixture_combined_output" 'src/harness-fixtures/kotlin/fixture/BrokenFixture[.]kt' 'parseError' 'gradle parse error finding' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" 'gradle --console=plain --no-daemon harnessFormat'; then
        printf '%s\n' '[fixture_assert_gradle_parse_error] expected harnessFormat to fail due to parse errors' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    fixture_before_checksum=$(fixture_file_checksum "$temp_dir/src/harness-fixtures/kotlin/fixture/BrokenFixture.kt")
    if ! grep -Fq 'return 1 > 2' "$temp_dir/src/harness-fixtures/kotlin/fixture/BrokenFixture.kt"; then
        printf '%s\n' '[fixture_assert_gradle_parse_error] malformed file was unexpectedly formatted' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_after_checksum=$(fixture_file_checksum "$temp_dir/src/harness-fixtures/kotlin/fixture/BrokenFixture.kt")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'gradle parse error file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}

# Verify Maven import-over-FQN detection covers JavaParser type references.
#
#     Requires `mvn` in PATH. Gracefully skips with a warning when mvn is unavailable.
#
# @return Returns 0 on success or when mvn is missing.
# @exit Exits with status 1 when the Maven import-over-FQN fixture fails.
fixture_assert_maven_import_over_fqn() {
    if ! mvn_path=$(command -v mvn 2>&1); then
        printf 'warning: mvn not in PATH; skipping maven import-over-fqn fixture check\n' >&2
        return 0
    fi
    : "$mvn_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" maven
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
{"name":"maven-import-over-fqn-fixture","importOverFqn":{"enabled":true,"severity":"ERROR","messages":{"default":"fully qualified name `{name}` used inline; add an import and use the simple name"},"parameters":{"sourceRoots":["src/main/java"],"extensions":["java"],"includePaths":[],"excludePaths":[],"allowedFqnPatterns":[]}}}
JSONEOF
    )"
    fixture_write_file "$temp_dir" src/main/java/fixture/ImportFixture.java "$(
        cat <<'JAVAEOF'
package fixture;

final class ImportFixture {
    private java.util.List<String> names;
    private java.util.Optional<java.util.Set<String>> values;
    private Runnable task = java.lang.Thread::yield;

    java.util.TreeMap<String, String> createTree() {
        return null;
    }

    Object createHash() {
        return new java.util.HashMap<>();
    }
}
JAVAEOF
    )"
    if fixture_run_command "$temp_dir" 'mvn -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:check'; then
        printf '%s\n' '[fixture_assert_maven_import_over_fqn] expected harnessCheck to report inline fully qualified type references' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_canonical_finding_prefix "$fixture_combined_output" 'src/main/java/fixture/ImportFixture[.]java' 'importOverFqn' 'maven import-over-fqn type reference fixture' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    for name in 'java.util.List' 'java.util.Optional' 'java.util.Set' 'java.lang.Thread' 'java.util.TreeMap' 'java.util.HashMap'; do
        if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" "$name" "maven import-over-fqn reports $name" 2>&1); then
            printf '%s\n' "$fixture_assertion_output" >&2
            printf '%s\n' "$fixture_combined_output" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
        fi
    done
    : "$fixture_assertion_output"
    fixture_remove_temp_dir "$temp_dir"
}

# Verify Maven AST source roots cannot escape the target root.
#
#     Requires `mvn` in PATH. Gracefully skips with a warning when mvn is unavailable.
#
# @return Returns 0 on success or when mvn is missing.
# @exit Exits with status 1 when an unsafe source root is accepted or not reported.
fixture_assert_maven_source_root_safety() {
    if ! mvn_path=$(command -v mvn 2>&1); then
        printf 'warning: mvn not in PATH; skipping maven source-root safety fixture check\n' >&2
        return 0
    fi
    : "$mvn_path"
    fixture_root=$(fixture_create_temp_dir)
    target_dir=$fixture_root/target
    fixture_copy_runtime "$target_dir" maven
    fixture_write_file "$target_dir" docs/harness/manifest.json '{"name":"maven-source-root-safety-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["../outside"],"extensions":["java"],"includePaths":[],"excludePaths":[]}}}'
    fixture_write_file "$fixture_root" outside/OutsideFixture.java 'package outside;

public class OutsideFixture {
}'
    fixture_before_checksum=$(fixture_file_checksum "$fixture_root/outside/OutsideFixture.java")
    if fixture_run_command "$target_dir" 'mvn -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:format'; then
        printf '%s\n' "[fixture_assert_maven_source_root_safety] expected harnessFormat to fail on unsafe source roots"
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    fixture_after_checksum=$(fixture_file_checksum "$fixture_root/outside/OutsideFixture.java")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'maven unsafe source root leaves outside file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'source root traversal is not allowed: ../outside' 'maven unsafe source root reports traversal finding' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_write_manifest "$target_dir" '{"name":"maven-source-root-safety-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src/main/{java"],"extensions":["java"],"includePaths":[],"excludePaths":[]}}}'
    fixture_before_checksum=$(fixture_file_checksum "$fixture_root/outside/OutsideFixture.java")
    if fixture_run_command "$target_dir" 'mvn -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:format'; then
        printf '%s\n' "[fixture_assert_maven_source_root_safety] expected harnessFormat to fail on invalid source roots"
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'invalid glob source root pattern: src/main/{java' 'maven invalid glob source root reports finding during format' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_after_checksum=$(fixture_file_checksum "$fixture_root/outside/OutsideFixture.java")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'maven invalid glob source root leaves outside file unchanged in format' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    if fixture_run_command "$target_dir" 'mvn -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:check'; then
        printf '%s\n' "[fixture_assert_maven_source_root_safety] expected harnessCheck to fail on invalid source roots"
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'invalid glob source root pattern: src/main/{java' 'maven invalid glob source root reports finding during check' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_remove_temp_dir "$fixture_root"
}

# Verify shell runtime prunes .claude/worktrees from traversal rather than filtering output.
#
#     Creates nested worktree content with files that would produce findings if traversed:
#     a no-read-permission directory (format), a shellcheck violation, and a symlink target.
#     Restores permissions before cleanup. Proves the runtime never walks into .claude/worktrees.
#
# @exit Exits with status 1 when worktree content is traversed by any scan path.
fixture_assert_shell_worktree_pruned() {
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" shell
    fixture_write_manifest "$temp_dir" '{"name":"shell-worktree-pruned-fixture","filePresence":{"enabled":false,"parameters":{"paths":[]}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"hookCommand":{"enabled":false,"parameters":{"prePushHook":"","preCommitHook":"","allowedCommands":[],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":false,"parameters":{"ciFiles":[],"referenceHook":"docs/harness/git-hooks/pre-push"}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"symlinkSafety":{"enabled":true,"severity":"ERROR","messages":{"fileNotAllowed":"symlink file is not allowed: {path}","directoryNotAllowed":"symlink directory is not allowed: {path}","allowedSymlinkPairs":[]}},"shellcheck":{"enabled":true,"severity":"WARN","parameters":{"paths":["docs/harness/git-hooks/*.sh"]}}}'
    worktree_nested=$temp_dir/.claude/worktrees/deep/nested
    mkdir -p "$worktree_nested"
    fixture_write_file "$temp_dir" ".claude/worktrees/deep/nested/bad-hook.sh" '#!/usr/bin/env sh
echo "bad worktree hook"
'
    chmod 000 "$worktree_nested"
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        :
    else
        chmod 755 "$worktree_nested"
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    chmod 755 "$worktree_nested"
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stdout" 'Harness validation passed' 'shell worktree pruned check passed' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if printf '%s' "$fixture_stdout$fixture_stderr" | grep -Fq 'bad-hook.sh'; then
        printf '%s\n' "[fixture_assert_shell_worktree_pruned] worktree file was traversed during check" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! shfmt_path=$(command -v shfmt 2>&1); then
        printf 'warning: shfmt not in PATH; skipping shell worktree format prune check\n' >&2
    else
        : "$shfmt_path"
        fixture_write_file "$temp_dir" ".claude/worktrees/deep/nested/format-bad.sh" '#!/usr/bin/env sh
if [ 1 -eq 1 ];then
printf "bad"
fi
'
        worktree_file=$temp_dir/.claude/worktrees/deep/nested/format-bad.sh
        fixture_before_checksum=$(fixture_file_checksum "$worktree_file")
        mkdir -p "$temp_dir/docs/harness/shell"
        cp "$temp_dir/harness-check.sh" "$temp_dir/docs/harness/shell/harness-check.sh"
        cp "$temp_dir/harness-format.sh" "$temp_dir/docs/harness/shell/harness-format.sh"
        if fixture_run_command "$temp_dir" 'sh docs/harness/shell/harness-format.sh'; then
            :
        else
            printf '%s\n' "$fixture_stdout" >&2
            printf '%s\n' "$fixture_stderr" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
        fi
        fixture_after_checksum=$(fixture_file_checksum "$worktree_file")
        if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'shell worktree format pruned file unchanged' 2>&1); then
            printf '%s\n' "$fixture_assertion_output" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
        fi
    fi
    if ! shellcheck_path=$(command -v shellcheck 2>&1); then
        printf 'warning: shellcheck not in PATH; skipping shell worktree shellcheck prune check\n' >&2
    else
        : "$shellcheck_path"
        fixture_write_file "$temp_dir" ".claude/worktrees/deep/nested/sc-violation.sh" "$(
            cat <<'SCVIOLATION_FIXTURE'
#!/usr/bin/env sh
echo "$unset_variable"
SCVIOLATION_FIXTURE
        )"
        fixture_write_manifest "$temp_dir" '{"name":"shell-worktree-pruned-fixture","filePresence":{"enabled":false,"parameters":{"paths":[]}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"hookCommand":{"enabled":false,"parameters":{"prePushHook":"","preCommitHook":"","allowedCommands":[],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":false,"parameters":{"ciFiles":[],"referenceHook":"docs/harness/git-hooks/pre-push"}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"symlinkSafety":{"enabled":false,"parameters":{"allowedSymlinkPairs":[]}},"shellcheck":{"enabled":true,"severity":"WARN","parameters":{"paths":[]}}}'
        if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
            :
        else
            printf '%s\n' "$fixture_stdout" >&2
            printf '%s\n' "$fixture_stderr" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
        fi
        if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_stdout" 'Harness validation passed' 'shell worktree shellcheck pruned check passed' 2>&1); then
            printf '%s\n' "$fixture_assertion_output" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
        fi
        if printf '%s' "$fixture_stdout$fixture_stderr" | grep -Fq 'sc-violation.sh'; then
            printf '%s\n' "[fixture_assert_shell_worktree_pruned] worktree file was traversed during shellcheck" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
        fi
    fi
    if printf '%s' "$fixture_stdout$fixture_stderr" | grep -Fq '.claude/worktrees'; then
        printf '%s\n' "[fixture_assert_shell_worktree_pruned] worktree path leaked into output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}
# Verify Gradle malformed includePaths and excludePaths render findings and do not crash.
#
#     Requires `gradle` in PATH. Gracefully skips with a warning when gradle is unavailable.
#
# @return Returns 0 on success or when gradle is missing.
# @exit Exits with status 1 when malformed globs crash or produce no findings.
fixture_assert_gradle_malformed_include_exclude() {
    if ! gradle_path=$(command -v gradle 2>&1); then
        printf 'warning: gradle not in PATH; skipping gradle malformed include/exclude fixture check\n' >&2
        return 0
    fi
    : "$gradle_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" gradle
    fixture_write_file "$temp_dir" settings.gradle.kts 'rootProject.name = "gradle-malformed-include-exclude-fixture"'
    fixture_write_file "$temp_dir" build.gradle.kts 'plugins { id("com.ririnto.sinon.harness") }

repositories { mavenCentral() }
'
    fixture_write_manifest "$temp_dir" '{"name":"gradle-malformed-include-exclude-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["buildSrc/src/main/kotlin/fixture"],"extensions":["kt"],"includePaths":["[["],"excludePaths":["[[["]}}}'
    fixture_write_file "$temp_dir" buildSrc/src/main/kotlin/fixture/MalformedGlobFixture.kt 'package fixture

fun undocumented(left: Int): Boolean {
    return left > 1
}'
    if fixture_run_command "$temp_dir" 'gradle --console=plain --no-daemon harnessCheck'; then
        printf '%s\n' '[fixture_assert_gradle_malformed_include_exclude] expected harnessCheck to fail on malformed globs' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'invalid includePaths glob pattern: [[' 'gradle malformed includePaths finding during check' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'invalid excludePaths glob pattern: [[[' 'gradle malformed excludePaths finding during check' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" 'gradle --console=plain --no-daemon harnessFormat'; then
        printf '%s\n' '[fixture_assert_gradle_malformed_include_exclude] expected harnessFormat to fail on malformed globs' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'invalid includePaths glob pattern: [[' 'gradle malformed includePaths finding during format' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'invalid excludePaths glob pattern: [[[' 'gradle malformed excludePaths finding during format' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_before_checksum=$(fixture_file_checksum "$temp_dir/buildSrc/src/main/kotlin/fixture/MalformedGlobFixture.kt")
    fixture_after_checksum=$(fixture_file_checksum "$temp_dir/buildSrc/src/main/kotlin/fixture/MalformedGlobFixture.kt")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'gradle malformed glob leaves source file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}
# Verify Gradle runtime prunes .claude/worktrees when sourceRoots use broad globs.
#
#     Requires `gradle` in PATH. Gracefully skips with a warning when gradle is unavailable.
#
# @return Returns 0 on success or when gradle is missing.
# @exit Exits with status 1 when worktree content is scanned or mutated.
fixture_assert_gradle_worktree_excluded() {
    if ! gradle_path=$(command -v gradle 2>&1); then
        printf 'warning: gradle not in PATH; skipping gradle worktree exclusion fixture check\n' >&2
        return 0
    fi
    : "$gradle_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" gradle
    fixture_write_file "$temp_dir" settings.gradle.kts 'rootProject.name = "gradle-worktree-exclusion-fixture"'
    fixture_write_file "$temp_dir" build.gradle.kts 'plugins { id("com.ririnto.sinon.harness") }

repositories { mavenCentral() }
'
    fixture_write_manifest "$temp_dir" '{"name":"gradle-worktree-exclusion-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["app/src/main/kotlin",".claude/worktrees/**/src/main/kotlin"],"extensions":["kt"],"includePaths":[],"excludePaths":[]}}}'
    fixture_write_file "$temp_dir" app/src/main/kotlin/fixture/GoodFixture.kt 'package fixture

fun undocumented(left: Int): Boolean {
    return left > 1
}'
    mkdir -p "$temp_dir/.claude/worktrees/abc1234/src/main/kotlin"
    fixture_write_file "$temp_dir" ".claude/worktrees/abc1234/src/main/kotlin/WorktreeFixture.kt" 'package fixture

fun alsoUndocumented(left: Int): Boolean {
    return left > 1
}'
    worktree_file=$temp_dir/.claude/worktrees/abc1234/src/main/kotlin/WorktreeFixture.kt
    worktree_before_checksum=$(fixture_file_checksum "$worktree_file")
    if fixture_run_command "$temp_dir" 'gradle --console=plain --no-daemon harnessCheck'; then
        printf '%s\n' '[fixture_assert_gradle_worktree_excluded] expected harnessCheck to report findings in legit source' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'publicDeclarationDocComment' 'gradle worktree exclusion reports legitimate finding' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if printf '%s' "$fixture_combined_output" | grep -Fq 'WorktreeFixture.kt'; then
        printf '%s\n' '[fixture_assert_gradle_worktree_excluded] worktree file was scanned during check' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" 'gradle --console=plain --no-daemon harnessFormat'; then
        :
    else
        :
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    worktree_after_checksum=$(fixture_file_checksum "$worktree_file")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$worktree_before_checksum" "$worktree_after_checksum" 'gradle worktree file unchanged by format' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}
# Verify Maven malformed includePaths and excludePaths render findings and do not crash.
#
#     Requires `mvn` in PATH. Gracefully skips with a warning when mvn is unavailable.
#
# @return Returns 0 on success or when mvn is missing.
# @exit Exits with status 1 when malformed globs crash or produce no findings.
fixture_assert_maven_malformed_include_exclude() {
    if ! mvn_path=$(command -v mvn 2>&1); then
        printf 'warning: mvn not in PATH; skipping maven malformed include/exclude fixture check\n' >&2
        return 0
    fi
    : "$mvn_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" maven
    fixture_write_manifest "$temp_dir" '{"name":"maven-malformed-include-exclude-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src/main/java"],"extensions":["java"],"includePaths":["[["],"excludePaths":["[[["]}}}'
    fixture_write_file "$temp_dir" src/main/java/fixture/MalformedGlobFixture.java 'package fixture;

public class MalformedGlobFixture {
}'
    if fixture_run_command "$temp_dir" 'mvn -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:check'; then
        printf '%s\n' '[fixture_assert_maven_malformed_include_exclude] expected harnessCheck to fail on malformed globs' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'invalid includePaths glob pattern: [[' 'maven malformed includePaths finding during check' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'invalid excludePaths glob pattern: [[[' 'maven malformed excludePaths finding during check' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" 'mvn -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:format'; then
        printf '%s\n' '[fixture_assert_maven_malformed_include_exclude] expected harnessFormat to fail on malformed globs' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'invalid includePaths glob pattern: [[' 'maven malformed includePaths finding during format' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'invalid excludePaths glob pattern: [[[' 'maven malformed excludePaths finding during format' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_before_checksum=$(fixture_file_checksum "$temp_dir/src/main/java/fixture/MalformedGlobFixture.java")
    fixture_after_checksum=$(fixture_file_checksum "$temp_dir/src/main/java/fixture/MalformedGlobFixture.java")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'maven malformed glob leaves source file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}
# Verify Maven runtime prunes .claude/worktrees when sourceRoots use broad globs.
#
#     Requires `mvn` in PATH. Gracefully skips with a warning when mvn is unavailable.
#
# @return Returns 0 on success or when mvn is missing.
# @exit Exits with status 1 when worktree content is scanned or mutated.
fixture_assert_maven_worktree_excluded() {
    if ! mvn_path=$(command -v mvn 2>&1); then
        printf 'warning: mvn not in PATH; skipping maven worktree exclusion fixture check\n' >&2
        return 0
    fi
    : "$mvn_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" maven
    fixture_write_manifest "$temp_dir" '{"name":"maven-worktree-exclusion-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src/main/java"],"extensions":["java"],"includePaths":[],"excludePaths":[]}}}'
    fixture_write_file "$temp_dir" src/main/java/fixture/GoodFixture.java 'package fixture;

public class GoodFixture {
}'
    mkdir -p "$temp_dir/.claude/worktrees/abc1234/src/main/java"
    fixture_write_file "$temp_dir" ".claude/worktrees/abc1234/src/main/java/WorktreeFixture.java" 'package fixture;

public class WorktreeFixture {
}'
    worktree_file=$temp_dir/.claude/worktrees/abc1234/src/main/java/WorktreeFixture.java
    worktree_before_checksum=$(fixture_file_checksum "$worktree_file")
    if fixture_run_command "$temp_dir" 'mvn -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:check'; then
        printf '%s\n' '[fixture_assert_maven_worktree_excluded] expected harnessCheck to report findings in legit source' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'publicDeclarationDocComment' 'maven worktree exclusion reports legitimate finding' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if printf '%s' "$fixture_combined_output" | grep -Fq 'WorktreeFixture.java'; then
        printf '%s\n' '[fixture_assert_maven_worktree_excluded] worktree file was scanned during check' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" 'mvn -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:format'; then
        :
    else
        :
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    worktree_after_checksum=$(fixture_file_checksum "$worktree_file")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$worktree_before_checksum" "$worktree_after_checksum" 'maven worktree file unchanged by format' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}
# Verify Maven parse-error detection reports findings and skips formatting.
#
#     Requires `mvn` in PATH. Gracefully skips with a warning when mvn is unavailable.
#
# @return Returns 0 on success or when mvn is missing.
# @exit Exits with status 1 when parse errors crash or files are mutated.
fixture_assert_maven_parse_error() {
    if ! mvn_path=$(command -v mvn 2>&1); then
        printf 'warning: mvn not in PATH; skipping maven parse-error fixture check\n' >&2
        return 0
    fi
    : "$mvn_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" maven
    fixture_write_manifest "$temp_dir" '{"name":"maven-parse-error-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src/main/java"],"extensions":["java"],"includePaths":[],"excludePaths":[]}}}'
    fixture_write_file "$temp_dir" src/main/java/fixture/BrokenFixture.java 'package fixture;

class BrokenFixture {
    boolean broken( {
        return 1 > 2;
    }
}'
    if fixture_run_command "$temp_dir" 'mvn -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:check'; then
        printf '%s\n' '[fixture_assert_maven_parse_error] expected harnessCheck to fail on parse error' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'parseError' 'maven parse error finding during check' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_before_checksum=$(fixture_file_checksum "$temp_dir/src/main/java/fixture/BrokenFixture.java")
    if fixture_run_command "$temp_dir" 'mvn -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:format'; then
        :
    else
        :
    fi
    fixture_after_checksum=$(fixture_file_checksum "$temp_dir/src/main/java/fixture/BrokenFixture.java")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'maven parse error file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! grep -Fq 'return 1 > 2;' "$temp_dir/src/main/java/fixture/BrokenFixture.java"; then
        printf '%s\n' '[fixture_assert_maven_parse_error] malformed file was unexpectedly formatted' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}
# Verify Gradle hook formatter does not mutate outside-root hook paths.
#
#     Requires `gradle` in PATH. Gracefully skips with a warning when gradle is unavailable.
#
# @return Returns 0 on success or when gradle is missing.
# @exit Exits with status 1 when outside-root hook file is mutated.
fixture_assert_gradle_hook_format_safety() {
    if ! gradle_path=$(command -v gradle 2>&1); then
        printf 'warning: gradle not in PATH; skipping gradle hook format safety fixture check\n' >&2
        return 0
    fi
    : "$gradle_path"
    fixture_root=$(fixture_create_temp_dir)
    target_dir=$fixture_root/target
    fixture_copy_runtime "$target_dir" gradle
    fixture_write_file "$target_dir" settings.gradle.kts 'rootProject.name = "gradle-hook-format-safety-fixture"'
    fixture_write_file "$target_dir" build.gradle.kts 'plugins { id("com.ririnto.sinon.harness") }

repositories { mavenCentral() }
'
    fixture_write_manifest "$target_dir" '{"name":"gradle-hook-format-safety-fixture","hookShebang":{"enabled":true,"severity":"ERROR","messages":{"default":"{hook} must start with #!/usr/bin/env sh"},"parameters":{"hooks":["../outside-hook.sh"],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}}}'
    fixture_write_file "$fixture_root" outside-hook.sh 'echo "outside hook without shebang"
'
    fixture_before_checksum=$(fixture_file_checksum "$fixture_root/outside-hook.sh")
    if fixture_run_command "$target_dir" 'gradle --console=plain --no-daemon harnessFormat'; then
        :
    else
        :
    fi
    fixture_after_checksum=$(fixture_file_checksum "$fixture_root/outside-hook.sh")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'gradle hook format safety leaves outside file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_remove_temp_dir "$fixture_root"
}
# Verify Maven hook formatter does not mutate outside-root hook paths.
#
#     Requires `mvn` in PATH. Gracefully skips with a warning when mvn is unavailable.
#
# @return Returns 0 on success or when mvn is missing.
# @exit Exits with status 1 when outside-root hook file is mutated.
fixture_assert_maven_hook_format_safety() {
    if ! mvn_path=$(command -v mvn 2>&1); then
        printf 'warning: mvn not in PATH; skipping maven hook format safety fixture check\n' >&2
        return 0
    fi
    : "$mvn_path"
    fixture_root=$(fixture_create_temp_dir)
    target_dir=$fixture_root/target
    fixture_copy_runtime "$target_dir" maven
    fixture_write_manifest "$target_dir" '{"name":"maven-hook-format-safety-fixture","hookShebang":{"enabled":true,"severity":"ERROR","messages":{"default":"{hook} must start with #!/usr/bin/env sh"},"parameters":{"hooks":["../outside-hook.sh"],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}}}'
    fixture_write_file "$fixture_root" outside-hook.sh 'echo "outside hook without shebang"
'
    fixture_before_checksum=$(fixture_file_checksum "$fixture_root/outside-hook.sh")
    if fixture_run_command "$target_dir" 'mvn -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:format'; then
        :
    else
        :
    fi
    fixture_after_checksum=$(fixture_file_checksum "$fixture_root/outside-hook.sh")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'maven hook format safety leaves outside file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_remove_temp_dir "$fixture_root"
}
# Verify reject_missing_doc_separator_in_shell catches missing separator.
#
# @exit Exits with status 1 when a docstring without separator passes.
fixture_assert_doc_separator_rejection() {
    temp_dir=$(fixture_create_temp_dir)
    doc_description='# Do something useful.'
    doc_param='# @param name The name.'
    doc_separator='#'

    bad_doc_content=$(
        printf '%s\n' '#!/usr/bin/env sh'
        printf '%s\n' '# -*- coding: utf-8 -*-'
        printf '%s\n' 'set -e'
        printf '%s\n' "$doc_description"
        printf '%s\n' "$doc_param"
        printf '%s\n' 'do_thing() {'
        printf '%s\n' "    printf \"%s\\n\" \"\\$name\""
        printf '%s\n' '}'
    )
    fixture_write_file "$temp_dir" bad-docstring.sh "$bad_doc_content"

    _=$(reject_missing_doc_separator_in_shell "$temp_dir/bad-docstring.sh" 2>&1) && {
        printf '%s\n' '[fixture_assert_doc_separator_rejection] expected missing separator to be rejected' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    }

    good_doc_content=$(
        printf '%s\n' '#!/usr/bin/env sh'
        printf '%s\n' '# -*- coding: utf-8 -*-'
        printf '%s\n' 'set -e'
        printf '%s\n' "$doc_description"
        printf '%s\n' "$doc_separator"
        printf '%s\n' "$doc_param"
        printf '%s\n' 'do_thing() {'
        printf '%s\n' "    printf \"%s\\n\" \"\\$name\""
        printf '%s\n' '}'
    )
    fixture_write_file "$temp_dir" good-docstring.sh "$good_doc_content"

    reject_missing_doc_separator_in_shell "$temp_dir/good-docstring.sh"
    fixture_remove_temp_dir "$temp_dir"
}
# Verify reject_double_bracket_in_shell rejects standalone and embedded [[.
#
# @exit Exits with status 1 when standalone or embedded [[ passes.
fixture_assert_double_bracket_rejection() {
    temp_dir=$(fixture_create_temp_dir)
    fixture_write_file "$temp_dir" standalone-bracket.sh "$(
        cat <<'STANDALONEEOF'
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e
if [[ -f x ]]; then
    printf "%s\n" "bad"
fi
STANDALONEEOF
    )"
    _=$(reject_double_bracket_in_shell "$temp_dir/standalone-bracket.sh" 2>&1) && {
        printf '%s\n' '[fixture_assert_double_bracket_rejection] expected standalone [[ to be rejected' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    }
    bracket_line='[[ -f x ]] && printf "%s\n" "bad"'
    fixture_write_file "$temp_dir" and-list-bracket.sh "$(
        printf '%s\n%s\n%s\n%s\n' '#!/usr/bin/env sh' '# -*- coding: utf-8 -*-' 'set -e' "$bracket_line"
    )"
    _=$(reject_double_bracket_in_shell "$temp_dir/and-list-bracket.sh" 2>&1) && {
        printf '%s\n' '[fixture_assert_double_bracket_rejection] expected &&-style [[ to be rejected' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    }
    fixture_write_file "$temp_dir" comment-bracket.sh "$(
        cat <<'COMMEOF'
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e
# echo "[[ should be ignored in comments"
COMMEOF
    )"
    reject_double_bracket_in_shell "$temp_dir/comment-bracket.sh"
    fixture_write_file "$temp_dir" string-bracket.sh "$(
        cat <<'STREOF'
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e
printf "%s\n" "[[ should be ignored in strings]]"
STREOF
    )"
    reject_double_bracket_in_shell "$temp_dir/string-bracket.sh"
    fixture_remove_temp_dir "$temp_dir"
}
# Verify Gradle hook rules read from parameters.hooks when declared.
#
#     Requires `gradle` in PATH. Gracefully skips with a warning when gradle is unavailable.
#
# @return Returns 0 on success or when gradle is missing.
# @exit Exits with status 1 when hook rule parameters.hooks is not exercised.
fixture_assert_gradle_hook_from_parameters() {
    if ! gradle_path=$(command -v gradle 2>&1); then
        printf 'warning: gradle not in PATH; skipping gradle hook parameters fixture check\n' >&2
        return 0
    fi
    : "$gradle_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" gradle
    fixture_write_file "$temp_dir" settings.gradle.kts 'rootProject.name = "gradle-hook-params-fixture"'
    fixture_write_file "$temp_dir" build.gradle.kts 'plugins { id("com.ririnto.sinon.harness") }
repositories { mavenCentral() }
'
    fixture_write_manifest "$temp_dir" '{"name":"gradle-hook-params-fixture","hookShebang":{"enabled":true,"severity":"ERROR","messages":{"default":"{hook} must start with #!/usr/bin/env sh"},"parameters":{"hooks":["docs/harness/git-hooks/pre-commit"],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":true,"severity":"ERROR","parameters":{"hooks":["docs/harness/git-hooks/pre-commit"]}}}'
    fixture_write_file "$temp_dir" docs/harness/git-hooks/pre-commit '#!/usr/bin/env sh
echo "ok"
'
    chmod +x "$temp_dir/docs/harness/git-hooks/pre-commit"
    if fixture_run_command "$temp_dir" 'gradle --console=plain --no-daemon harnessCheck'; then
        :
    else
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}
# Verify Maven worktree symlink under .claude/worktrees is excluded from violation reports.
#
#     Requires `mvn` in PATH. Gracefully skips with a warning when mvn is unavailable.
#
# @return Returns 0 on success or when mvn is missing.
# @exit Exits with status 1 when worktree symlink triggers Maven harness violation.
fixture_assert_maven_worktree_symlink_excluded() {
    if ! mvn_path=$(command -v mvn 2>&1); then
        printf 'warning: mvn not in PATH; skipping maven worktree symlink exclusion fixture check\n' >&2
        return 0
    fi
    : "$mvn_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" maven
    fixture_write_manifest "$temp_dir" '{"name":"maven-worktree-symlink-fixture","publicDeclarationDocComment":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src/main/java"],"extensions":["java"],"includePaths":[],"excludePaths":[]}}}'
    fixture_write_file "$temp_dir" src/main/java/fixture/Ok.java 'package fixture;
final class Ok {
    boolean safe() { return true; }
}
'
    mkdir -p "$temp_dir/.claude/worktrees/abc1234/src/main/java"
    ln -s "$temp_dir/src/main/java/Ok.java" "$temp_dir/.claude/worktrees/abc1234/src/main/java/Link.java"
    if fixture_run_command "$temp_dir" 'mvn -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:check'; then
        :
    else
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if printf '%s' "$fixture_combined_output" | grep -Fq '.claude/worktrees'; then
        printf '%s\n' "[fixture_assert_maven_worktree_symlink_excluded] worktree path leaked into output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}
# Verify shell formatter fail-closed preflight with malformed manifest and bad .sh.
#
# @exit Exits with status 1 when formatter does not exit non-zero or mutates files.
fixture_assert_shell_format_fail_closed_preflight() {
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" shell
    mkdir -p "$temp_dir/docs/harness/shell"
    cp "$temp_dir/harness-check.sh" "$temp_dir/docs/harness/shell/harness-check.sh"
    cp "$temp_dir/harness-format.sh" "$temp_dir/docs/harness/shell/harness-format.sh"
    fixture_write_file "$temp_dir" docs/harness/shell/harness-check.sh '#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e
exit 0
'
    fixture_write_manifest "$temp_dir" '{"name":"shell-fail-closed-fixture","emptyDirectoryPlaceholders":{"enabled":true,"parameters":{"directories":"not-an-array"}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}}}'
    fixture_write_file "$temp_dir" bad-format.sh '#!/usr/bin/env sh
if [ 1 -eq 1 ];then
printf "bad"
fi
'
    mkdir -p "$temp_dir/placeholder-dir"
    fixture_before_checksum=$(fixture_file_checksum "$temp_dir/bad-format.sh")
    if fixture_run_command "$temp_dir" 'sh docs/harness/shell/harness-format.sh'; then
        printf '%s\n' '[fixture_assert_shell_format_fail_closed_preflight] expected malformed manifest to fail' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_after_checksum=$(fixture_file_checksum "$temp_dir/bad-format.sh")
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_before_checksum" "$fixture_after_checksum" 'shell fail-closed preflight leaves bad .sh unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if [ -f "$temp_dir/placeholder-dir/.gitkeep" ]; then
        printf '%s\n' '[fixture_assert_shell_format_fail_closed_preflight] placeholder .gitkeep should not exist after failed preflight' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}
# Verify emit_path_message renders paths with special characters without sed corruption.
#
# @exit Exits with status 1 when emit_path_message output is corrupted for special paths.
fixture_assert_emit_path_message_special_chars() {
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" shell
    for test_path in 'dir&file' 'a|b' 'back\slash'; do
        rm -f "$temp_dir/$test_path"
        ln -s target "$temp_dir/$test_path"
    done
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
{"name":"special-path-message-fixture","filePresence":{"enabled":true,"severity":"ERROR","messages":{"fileNotAllowed":"symlink file is not allowed: {path}","directoryNotAllowed":"symlink directory is not allowed: {path}","scanRootNotAllowed":"symlink scan root is not allowed: {path}","scanEntryNotAllowed":"symlink scan entry is not allowed: {path}","pathNotAllowed":"symlink path is not allowed: {path}"},"parameters":{"paths":["dir&file","a|b","back\\slash"]}},"directoryPresence":{"enabled":false,"parameters":{"paths":[]}},"emptyDirectoryPlaceholders":{"enabled":false,"parameters":{"directories":[]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"hookCommand":{"enabled":false,"parameters":{"prePushHook":"","preCommitHook":"","allowedCommands":[],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":false,"parameters":{"ciFiles":[],"referenceHook":"docs/harness/git-hooks/pre-push"}},"symlinkSafety":{"enabled":true,"severity":"ERROR","messages":{"fileNotAllowed":"symlink file is not allowed: {path}","directoryNotAllowed":"symlink directory is not allowed: {path}","scanRootNotAllowed":"symlink scan root is not allowed: {path}","scanEntryNotAllowed":"symlink scan entry is not allowed: {path}","pathNotAllowed":"symlink path is not allowed: {path}"},"parameters":{"allowedSymlinkPairs":[]}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"docs/exec-plans/completed","uncheckedTaskPattern":"^\\s*-\\s*\\[ \\]\\s"}},"shellcheck":{"enabled":false,"parameters":{}}}
JSONEOF
    )"
    if fixture_run_command "$temp_dir" 'sh harness-check.sh'; then
        printf '%s\n' '[fixture_assert_emit_path_message_special_chars] expected harness-check to report special-path diagnostics' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    for test_path in 'dir&file' 'a|b' 'back\slash'; do
        expected="[ERROR] symlinkSafety: symlink file is not allowed: $test_path"
        if ! printf '%s\n' "$fixture_stderr" | grep -Fxq "$expected"; then
            printf '%s\n' "[fixture_assert_emit_path_message_special_chars] missing expected diagnostic for path '$test_path'" >&2
            printf '%s\n' "$fixture_stderr" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
        fi
    done
    fixture_remove_temp_dir "$temp_dir"
}
# Verify root harness-check warning counter accumulates warnings correctly.
#
# @exit Exits with status 1 when the warning counter does not increment.

fixture_assert_harness_check_warn_counter() {
    temp_dir=$(fixture_create_temp_dir)
    warn_bin_dir=$temp_dir/bin
    mkdir -p "$warn_bin_dir"
    for required_command in cat sh mktemp find grep python3 printf env rm; do
        required_command_path=$(command -v "$required_command")
        ln -s "$required_command_path" "$warn_bin_dir/$required_command"
    done
    shell_file=$temp_dir/plugins/agent-capability-kit/skills/plugin-authoring/assets/hooks/check.sh
    python_file=$temp_dir/plugins/harness/skills/harness-install/assets/uv/runtime/harness_check.py
    mkdir -p "$(dirname "$shell_file")"
    mkdir -p "$(dirname "$python_file")"
    : >"$shell_file"
    : >"$python_file"
    if ! fixture_run_command "$temp_dir" "PATH=$warn_bin_dir CLAUDE_PLUGIN_ROOT=\"$temp_dir\" sh \"$root/scripts/harness-check.sh\""; then
        printf '%s\n' "[fixture_assert_harness_check_warn_counter] expected harness-check to pass with warnings: $fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    summary=$(printf '%s\n' "$fixture_stdout" | grep '^Checked ')
    if [ -z "$summary" ]; then
        printf '%s\n' '[fixture_assert_harness_check_warn_counter] expected Checked summary output from harness-check' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    error_count=$(printf '%s\n' "$summary" | awk '{print $4}')
    warn_count=$(printf '%s\n' "$summary" | awk '{print $6}' | sed 's/,$//')
    case "$error_count" in
        "" | *[!0-9]*)
            printf '%s\n' "[fixture_assert_harness_check_warn_counter] expected numeric error count in summary, got: $summary" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
            ;;
    esac
    case "$warn_count" in
        "" | *[!0-9]*)
            printf '%s\n' "[fixture_assert_harness_check_warn_counter] expected numeric warn count in summary, got: $summary" >&2
            fixture_remove_temp_dir "$temp_dir"
            exit 1
            ;;
    esac
    if [ "$warn_count" -ne 4 ]; then
        printf '%s\n' "[fixture_assert_harness_check_warn_counter] expected warn count to be 4, got $warn_count" >&2
        printf '%s\n' "$summary" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if [ "$error_count" -ne 0 ]; then
        printf '%s\n' "[fixture_assert_harness_check_warn_counter] expected zero errors, got $error_count" >&2
        printf '%s\n' "$summary" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}
# Verify install --hooks copy refuses to write through a symlinked .git/hooks directory.
#
# @exit Exits with status 1 when copy mode writes through a symlinked hooks directory.
fixture_assert_install_hooks_copy_symlink_rejection() {
    temp_dir=$(fixture_create_temp_dir)
    if git_path=$(command -v git 2>&1); then
        : "$git_path"
        git -C "$temp_dir" init -q
    else
        printf 'warning: git not in PATH; skipping hooks copy symlink rejection fixture\n' >&2
        fixture_remove_temp_dir "$temp_dir"
        return 0
    fi
    rm -rf "$temp_dir/.git/hooks"
    mkdir -p "$temp_dir/real-hooks"
    ln -s "$temp_dir/real-hooks" "$temp_dir/.git/hooks"
    fixture_write_file "$temp_dir/settings.gradle.kts" 'rootProject.name = "hooks-copy-symlink-fixture"'
    fixture_write_file "$temp_dir" docs/harness/git-hooks/pre-commit '#!/usr/bin/env sh
echo "hook"
'
    fixture_write_file "$temp_dir" docs/harness/git-hooks/pre-push '#!/usr/bin/env sh
echo "hook"
'
    if fixture_run_command "$temp_dir" "sh \"$root/skills/harness-install/scripts/install-harness.sh\" --mode gradle --hooks copy --target ."; then
        :
    else
        if printf '%s' "$fixture_stderr" | grep -Fq 'refusing'; then
            fixture_remove_temp_dir "$temp_dir"
            return 0
        fi
        if printf '%s' "$fixture_stderr" | grep -Fq 'symlink'; then
            fixture_remove_temp_dir "$temp_dir"
            return 0
        fi
        if printf '%s' "$fixture_stderr" | grep -Fq 'not a directory'; then
            fixture_remove_temp_dir "$temp_dir"
            return 0
        fi
    fi
    if [ -f "$temp_dir/real-hooks/pre-commit" ]; then
        printf '%s\n' '[fixture_assert_install_hooks_copy_symlink_rejection] hook was written through symlinked .git/hooks' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_remove_temp_dir "$temp_dir"
}
# Smoke-check the uv stack runtime by importing harness_check and counting registered rules.
#
#     Requires `uv` in PATH. Loads libcst on demand via `uv run --with libcst`.
#     Gracefully skips with a warning when uv is unavailable.
#
# @return Returns 0 on success or when uv is missing.
# @exit Exits with status 1 on import failure.
smoke_check_uv_runtime() {
    if ! uv_path=$(command -v uv 2>&1); then
        printf 'warning: uv not in PATH; skipping uv runtime smoke check\n' >&2
        return 0
    fi
    : "$uv_path"
    runtime_dir="$root/skills/harness-install/assets/uv/runtime"
    if ! smoke_output=$(cd "$runtime_dir" && uv run --quiet --with libcst python3 -c 'import sys
sys.path.insert(0, ".")
import harness_check
from ruff.ruff_code_map import RUFF_CODE_TO_CATEGORY, RUFF_FIX_SAFETY, RUFF_CATEGORIES
assert set(RUFF_CODE_TO_CATEGORY.values()) <= set(RUFF_CATEGORIES), "code map category not in RUFF_CATEGORIES"
assert all(c in RUFF_FIX_SAFETY for c in RUFF_CATEGORIES), "RUFF_CATEGORIES missing fix safety"
print(f"uv runtime rules: {len(list(harness_check.HarnessCheck))}")
print(f"uv ruff categories: {len(RUFF_CATEGORIES)}")' 2>&1); then
        printf '%s\n' "$smoke_output" >&2
        printf '%s\n' "[smoke_check_uv_runtime] uv runtime failed to import: $runtime_dir" >&2
        exit 1
    fi
    printf '%s\n' "$smoke_output"
}

# Smoke-check the bun stack runtime by importing harness-check.ts and counting registered rules.
#
#     Requires `bun` in PATH. Uses dynamic import so external version-pinned npm
#     specifiers resolve through Bun's auto-install. Gracefully skips with a
#     warning when bun is unavailable.
#
# @return Returns 0 on success or when bun is missing.
# @exit Exits with status 1 on import failure.
smoke_check_bun_runtime() {
    if ! bun_path=$(command -v bun 2>&1); then
        printf 'warning: bun not in PATH; skipping bun runtime smoke check\n' >&2
        return 0
    fi
    : "$bun_path"
    runtime_dir="$root/skills/harness-install/assets/bun/runtime"
    bun_script=$(
        cat <<'JSEOF'
import("./harness-check.ts").then(m => { console.log(`bun runtime rules: ${m.HARNESS_CHECKS.length}`); })
JSEOF
    )
    if ! smoke_output=$(cd "$runtime_dir" && bun -e "$bun_script" 2>&1); then
        printf '%s\n' "$smoke_output" >&2
        printf '%s\n' "[smoke_check_bun_runtime] bun runtime failed to import: $runtime_dir" >&2
        exit 1
    fi
    printf '%s\n' "$smoke_output"
}

# Smoke-check the gradle stack runtime by compiling buildSrc Kotlin sources.
#
#     Requires `gradle` in PATH. Gracefully skips with a warning when gradle is
#     unavailable. Compilation exercises every rule class without running the
#     validator against an installed harness.
#
# @return Returns 0 on success or when gradle is missing.
# @exit Exits with status 1 on compile failure.
smoke_check_gradle_runtime() {
    if ! gradle_path=$(command -v gradle 2>&1); then
        printf 'warning: gradle not in PATH; skipping gradle runtime smoke check\n' >&2
        return 0
    fi
    : "$gradle_path"
    runtime_dir="$root/skills/harness-install/assets/gradle"
    if ! smoke_output=$(cd "$runtime_dir" && gradle --console=plain --no-daemon -q buildSrc:compileKotlin 2>&1); then
        printf '%s\n' "$smoke_output" >&2
        printf '%s\n' "[smoke_check_gradle_runtime] gradle buildSrc compile failed: $runtime_dir" >&2
        exit 1
    fi
    printf '%s\n' 'gradle runtime: buildSrc:compileKotlin OK'
}

# Smoke-check the maven stack runtime by validating the plugin POM.
#
#     Requires `mvn` in PATH. Gracefully skips with a warning when mvn is
#     unavailable. Runs `mvn validate` which exercises module wiring without
#     executing the full plugin lifecycle.
#
# @return Returns 0 on success or when mvn is missing.
# @exit Exits with status 1 on validation failure.
smoke_check_maven_runtime() {
    if ! mvn_path=$(command -v mvn 2>&1); then
        printf 'warning: mvn not in PATH; skipping maven runtime smoke check\n' >&2
        return 0
    fi
    : "$mvn_path"
    runtime_dir="$root/skills/harness-install/assets/maven/harness-maven-plugin"
    if ! smoke_output=$(cd "$runtime_dir" && mvn -q -B -ntp validate 2>&1); then
        printf '%s\n' "$smoke_output" >&2
        printf '%s\n' "[smoke_check_maven_runtime] mvn validate failed: $runtime_dir" >&2
        exit 1
    fi
    printf '%s\n' 'maven runtime: mvn validate OK'
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
    "$root/skills/harness-install/assets/common/AGENTS.md" \
    "$root/skills/harness-install/assets/common/ARCHITECTURE.md" \
    "$root/skills/harness-install/assets/common/CLAUDE.md" \
    "$root/skills/harness-install/assets/common/docs/harness/README.md" \
    "$root/skills/harness-install/assets/common/docs/harness/git-hooks/pre-commit" \
    "$root/skills/harness-install/assets/common/docs/harness/git-hooks/pre-push" \
    "$root/skills/harness-install/assets/common/docs/generated/.gitkeep" \
    "$root/skills/harness-install/assets/common/docs/exec-plans/active/.gitkeep" \
    "$root/skills/harness-install/assets/common/docs/exec-plans/completed/.gitkeep" \
    "$root/skills/harness-install/assets/common/docs/harness/templates/docs/generated-artifact.md" \
    "$root/skills/harness-install/assets/bun/.github/workflows/harness.yml" \
    "$root/skills/harness-install/assets/bun/.gitlab-ci.yml" \
    "$root/skills/harness-install/assets/bun/docs/harness/manifest.json" \
    "$root/skills/harness-install/assets/bun/runtime/harness-check.ts" \
    "$root/skills/harness-install/assets/gradle/.github/workflows/harness.yml" \
    "$root/skills/harness-install/assets/gradle/.gitlab-ci.yml" \
    "$root/skills/harness-install/assets/gradle/docs/harness/manifest.json" \
    "$root/skills/harness-install/assets/maven/.github/workflows/harness.yml" \
    "$root/skills/harness-install/assets/maven/.gitlab-ci.yml" \
    "$root/skills/harness-install/assets/maven/docs/harness/manifest.json" \
    "$root/skills/harness-install/assets/maven/harness-maven-plugin/src/main/java/com/ririnto/sinon/harness/core/Manifest.java" \
    "$root/skills/harness-install/assets/maven/harness-maven-plugin/src/main/java/com/ririnto/sinon/harness/core/DefaultManifest.java" \
    "$root/skills/harness-install/assets/maven/harness-maven-plugin/src/main/java/com/ririnto/sinon/harness/core/RuleContext.java" \
    "$root/skills/harness-install/assets/maven/harness-maven-plugin/src/main/java/com/ririnto/sinon/harness/core/DefaultRuleContext.java" \
    "$root/skills/harness-install/assets/maven/harness-maven-plugin/src/main/java/com/ririnto/sinon/harness/core/Severity.java" \
    "$root/skills/harness-install/assets/uv/runtime/harness_check.py" \
    "$root/skills/harness-install/assets/uv/docs/harness/manifest.json" \
    "$root/skills/harness-install/assets/uv/.github/workflows/harness.yml" \
    "$root/skills/harness-install/assets/uv/.gitlab-ci.yml" \
    "$root/skills/harness-install/assets/shell/.editorconfig" \
    "$root/skills/harness-install/assets/shell/.shellcheckrc" \
    "$root/skills/harness-install/assets/shell/.github/workflows/harness.yml" \
    "$root/skills/harness-install/assets/shell/.gitlab-ci.yml" \
    "$root/skills/harness-install/assets/shell/docs/harness/manifest.json" \
    "$root/skills/harness-install/assets/shell/runtime/README.md" \
    "$root/skills/harness-install/assets/shell/runtime/harness-check.sh" \
    "$root/skills/harness-install/assets/shell/runtime/harness-format.sh" \
    "$root/skills/harness-install/assets/gradle/buildSrc/src/main/kotlin/com/ririnto/sinon/harness/plugin/HarnessValidationPlugin.kt" \
    "$root/skills/harness-install/assets/gradle/buildSrc/src/main/kotlin/com/ririnto/sinon/harness/ktlint/HarnessKtlintEngine.kt" \
    "$root/skills/harness-install/assets/gradle/buildSrc/src/main/kotlin/com/ririnto/sinon/harness/ktlint/HarnessKotlinRules.kt" \
    "$root/skills/harness-install/assets/maven/harness-maven-plugin/src/main/java/com/ririnto/sinon/harness/HarnessCheckMojo.java" \
    "$root/skills/harness-install/assets/maven/harness-maven-plugin/src/main/java/com/ririnto/sinon/harness/rules/ast/ClassMemberOrderingRule.java"; do
    require_file "$path"
    require_not_symlink_or_common_root_contract "$path"
done

for path in \
    "$root/skills/harness-install/assets/bun/.github/workflows/harness.yml" \
    "$root/skills/harness-install/assets/bun/.gitlab-ci.yml" \
    "$root/skills/harness-install/assets/gradle/.github/workflows/harness.yml" \
    "$root/skills/harness-install/assets/gradle/.gitlab-ci.yml" \
    "$root/skills/harness-install/assets/maven/.github/workflows/harness.yml" \
    "$root/skills/harness-install/assets/maven/.gitlab-ci.yml" \
    "$root/skills/harness-install/assets/uv/.github/workflows/harness.yml" \
    "$root/skills/harness-install/assets/uv/.gitlab-ci.yml" \
    "$root/skills/harness-install/assets/shell/.github/workflows/harness.yml" \
    "$root/skills/harness-install/assets/shell/.gitlab-ci.yml"; do
    require_text "$path" '{{validation_command}}'
done

for path in \
    "$root/agents" \
    "$root/scripts" \
    "$root/skills/harness-install/assets/common/.claude/agents" \
    "$root/skills/harness-install/assets/common/.claude/skills" \
    "$root/skills/harness-install/assets/common/docs/harness/templates/agent" \
    "$root/skills/harness-install/assets/common/docs/harness/templates/skill" \
    "$root/skills/harness-install/assets/common/docs/harness/templates/workflow" \
    "$root/skills/harness-install/assets/common/docs/harness/templates/ci" \
    "$root/skills/harness-install/assets/common/docs/harness/templates/docs"; do
    require_dir "$path"
    require_not_symlink "$path"
done

require_executable "$root/scripts/plugin-self-check.sh"
require_executable "$root/skills/harness-install/scripts/install-harness.sh"
require_executable "$root/skills/harness-install/assets/common/docs/harness/git-hooks/pre-commit"
require_executable "$root/skills/harness-install/assets/common/docs/harness/git-hooks/pre-push"
require_executable "$root/skills/harness-install/assets/shell/runtime/harness-check.sh"
require_executable "$root/skills/harness-install/assets/shell/runtime/harness-format.sh"

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
require_text "$root/skills/harness-install/assets/maven/harness-maven-plugin/src/main/java/com/ririnto/sinon/harness/rules/HarnessCheckRule.java" 'boolean applies(RuleContext ctx)'
require_text "$root/skills/harness-install/assets/maven/harness-maven-plugin/src/main/java/com/ririnto/sinon/harness/rules/HarnessCheckRule.java" 'Collection<Finding> validate(RuleContext ctx)'
require_text "$root/skills/harness-install/assets/bun/runtime/README.md" 'Structural parity'
require_text "$root/skills/harness-install/assets/uv/runtime/README.md" 'Structural parity'
require_text "$root/skills/harness-install/assets/maven/runtime/README.md" 'Structural parity'

template_package_file_list=$(package_files "$root/skills/harness-install/assets")
if printf '%s\n' "$template_package_file_list" | grep -E '(^|/)(db-schema[.]md|__pycache__|[.]ruff_cache|target|build|bin|[.]gradle|[.]factorypath|[.]classpath|[.]project|[.]settings)(/|$)|[.]pyc$'; then
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
import re
import sys

root = pathlib.Path(sys.argv[1])
plugin = json.loads((root / ".claude-plugin/plugin.json").read_text())
manifest = json.loads((root / "skills/harness-install/assets/gradle/docs/harness/manifest.json").read_text())
install_script = (root / "skills/harness-install/scripts/install-harness.sh").read_text()
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
required_metadata_properties = {"$schema", "name", "description", "seedFiles", "generatedArtifacts", "harnessEvolution", "teamPatterns"}
for stack in ("shell", "gradle", "maven", "bun", "uv"):
    stack_schema_path = root / f"skills/harness-install/assets/{stack}/docs/harness/manifest.schema.json"
    if not stack_schema_path.is_file():
        errors.append(f"missing stack schema: {stack}/docs/harness/manifest.schema.json")
        continue
    stack_schema = json.loads(stack_schema_path.read_text())
    stack_schema_text = stack_schema_path.read_text()
    if "manifest.base.schema.json" in stack_schema_text or "manifest.code.schema.json" in stack_schema_text:
        errors.append(f"{stack} manifest schema must be self-contained")
    stack_schema_properties = set(stack_schema.get("properties", {}).keys())
    missing_metadata = required_metadata_properties - stack_schema_properties
    if missing_metadata:
        errors.append(f"{stack} manifest schema missing metadata properties: {sorted(missing_metadata)}")
if "copy_stack_manifest" not in install_script or "docs/harness/manifest.json" not in install_script:
    errors.append("installer must apply the selected stack manifest slice")
def reject_prefixed_rule_ids(value, path, inside_parameters=False):
    old_rule_id_pattern = re.compile(r"^(require|forbid)[A-Z]")
    if isinstance(value, dict):
        for key, child in value.items():
            if not inside_parameters and isinstance(key, str) and old_rule_id_pattern.match(key):
                errors.append(f"manifest canonical rule id must be neutral: {path}.{key}")
            reject_prefixed_rule_ids(child, f"{path}.{key}", inside_parameters or key == "parameters")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            reject_prefixed_rule_ids(child, f"{path}[{index}]", inside_parameters)
    elif not inside_parameters and isinstance(value, str) and old_rule_id_pattern.match(value):
        errors.append(f"manifest canonical rule reference must be neutral: {path}={value}")
reject_prefixed_rule_ids(manifest, "common manifest")
stack_expectations = {
    "gradle": {"must": {"implicitLambdaIt", "companionObjectPosition", "terminalBranchWhen"}, "forbidden_keys": {"classMemberOrdering"}},
    "maven": {"must": {"classMemberOrdering"}, "forbidden_keys": {"companionObjectPosition", "terminalBranchWhen"}},
    "uv": {"must": {"publicDeclarationDocComment"}, "forbidden_keys": {"classMemberOrdering", "companionObjectPosition", "kotlinTopLevelDeclarationCount", "terminalBranchWhen"}},
    "bun": {"must": {"publicDeclarationDocComment"}, "forbidden_keys": {"classMemberOrdering", "companionObjectPosition", "kotlinTopLevelDeclarationCount", "terminalBranchWhen"}},
    "shell": {"must": {"filePresence", "scaffoldLeaks"}, "forbidden_keys": {"classMemberOrdering", "importOverFqn", "terminalBranchWhen"}},
}
forbidden_param_keys = ("sourceRootsPerStack", "extensionsPerStack", "includePathsPerStack", "excludePathsPerStack", "visibilityPerStack")
for stack, expectation in stack_expectations.items():
    stack_manifest_path = root / f"skills/harness-install/assets/{stack}/docs/harness/manifest.json"
    if not stack_manifest_path.is_file():
        errors.append(f"missing stack manifest slice: {stack}")
        continue
    stack_manifest = json.loads(stack_manifest_path.read_text())
    reject_prefixed_rule_ids(stack_manifest, f"{stack} manifest")
    for required_key in expectation["must"]:
        if required_key not in stack_manifest:
            errors.append(f"{stack} manifest missing expected key: {required_key}")
    for forbidden in expectation["forbidden_keys"]:
        if forbidden in stack_manifest:
            errors.append(f"{stack} manifest contains non-selected stack content: {forbidden}")
    for key, value in stack_manifest.items():
        params = value.get("parameters") if isinstance(value, dict) else None
        if isinstance(params, dict):
            for legacy_key in forbidden_param_keys:
                if legacy_key in params:
                    errors.append(f"{stack} manifest {key}.parameters contains legacy *PerStack key: {legacy_key}")
def manifest_items(manifest, key):
    section = manifest.get(key, {})
    if isinstance(section, dict):
        items = section.get("items", [])
        return items if isinstance(items, list) else []
    if isinstance(section, list):
        return section
    return []

for group in manifest_items(manifest, "templateGroups"):
    if not (root / f"skills/harness-install/assets/common/docs/harness/templates/{group}").is_dir():
        errors.append(f"manifest template group missing: {group}")
for key in ("requiredFiles", "emptyDirectoryKeepFiles"):
    for item in manifest_items(manifest, key):
        asset_path = root / "skills/harness-install/assets/common" / item
        if asset_path.is_symlink():
            errors.append(f"manifest {key} path must not be a symlink: {item}")
        elif not asset_path.is_file():
            errors.append(f"manifest {key} missing file: {item}")
for item in manifest_items(manifest, "requiredDirectories"):
    asset_path = root / "skills/harness-install/assets/common" / item
    if asset_path.is_symlink():
        errors.append(f"manifest requiredDirectories path must not be a symlink: {item}")
    elif not asset_path.is_dir():
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
require_text "$root/README.md" 'hook template'
require_text "$root/README.md" "Gradle \`pre-commit\` runs \`harnessCheck\`"
require_text "$root/README.md" "Gradle \`pre-push\` runs \`check\`"
require_text "$root/README.md" 'THIRD_PARTY_NOTICES.md'
require_text "$root/README.md" 'skills/harness-install/assets/common/docs/harness/git-hooks/'
require_text "$root/README.md" 'five-stack runtime smoke checks'
require_text "$root/README.md" 'requires the selected stack mode'
require_text "$root/README.md" 'v6 archive structure'
require_markdown_heading "$root/README.md" 2 'Plugin-Owned Structural Agents'
require_markdown_heading "$root/README.md" 2 'Packaged Scripts and Assets'
require_markdown_heading "$root/README.md" 2 'Runtime Model'
require_text "$root/skills/harness-install/SKILL.md" "Gradle pre-commit runs \`harnessCheck\`, Gradle pre-push runs \`check\`"
require_text "$root/skills/harness-install/SKILL.md" "shell runtime installs \`harness-check.sh\` and \`harness-format.sh\`"
require_markdown_heading "$root/skills/harness-install/SKILL.md" 2 'Ownership Boundary'
require_markdown_heading "$root/skills/harness-install/SKILL.md" 2 'Invariants'
require_text "$root/skills/harness-validate/SKILL.md" "generated \`docs/harness/git-hooks/pre-push\` command marker"
require_markdown_heading "$root/skills/harness-validate/SKILL.md" 2 'Ownership Boundary'
require_markdown_heading "$root/skills/harness-validate/SKILL.md" 2 'Invariants'
require_text "$root/skills/harness-validate/SKILL.md" 'Manifest drift'
require_text "$root/skills/harness-validate/SKILL.md" 'Generated artifact metadata'
require_text "$root/skills/harness-validate/SKILL.md" 'Unsupported validation command'
require_text "$root/skills/harness-validate/SKILL.md" 'sh docs/harness/shell/harness-check.sh'
require_text "$root/skills/harness-validate/SKILL.md" "harness-check.sh\` and \`harness-format.sh"
require_text "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" 'manifest drift'
require_text "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" 'generated-artifact metadata'
require_text "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" 'unsupported pre-push validation command'
require_text "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" './gradlew harnessCheck'
require_text "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" 'gradle harnessCheck'
require_text "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" 'mvn -q -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:check'
require_text "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" 'uv run --script docs/harness/uv/harness_check.py'
require_text "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" 'bun --install=fallback run docs/harness/bun/harness-check.ts'
require_text "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" 'sh docs/harness/shell/harness-check.sh'
require_text "$root/skills/harness-install/assets/common/docs/harness/README.md" 'sh docs/harness/shell/harness-check.sh'
require_text "$root/skills/harness-install/assets/shell/docs/harness/manifest.json" '"hookCommand"'
require_text "$root/skills/harness-install/assets/shell/docs/harness/manifest.json" '"ciHookCommandParity"'
require_text "$root/skills/harness-install/assets/shell/docs/harness/manifest.json" 'sh docs/harness/shell/harness-check.sh'
require_text "$root/skills/harness-install/assets/shell/docs/harness/manifest.schema.json" '"hookCommand"'
require_text "$root/skills/harness-install/assets/shell/docs/harness/manifest.schema.json" '"ciHookCommandParity"'
require_text "$root/skills/harness-install/assets/shell/runtime/harness-check.sh" 'check_hook_command'
require_text "$root/skills/harness-install/assets/shell/runtime/harness-check.sh" 'check_ci_hook_command_parity'
require_text "$root/skills/harness-install/assets/shell/runtime/harness-format.sh" 'remaining findings after format:'
require_text "$root/skills/harness-install/assets/shell/runtime/README.md" 'hookCommand'
require_text "$root/skills/harness-install/assets/shell/runtime/README.md" 'ciHookCommandParity'
require_text "$root/skills/harness-install/assets/shell/runtime/README.md" 'harness-format.sh'
require_text "$root/skills/harness-install/assets/shell/.github/workflows/harness.yml" 'python3 shellcheck'
require_text "$root/skills/harness-install/assets/shell/.gitlab-ci.yml" 'python3 shellcheck'
reject_text 'harnessValidate' "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md"
reject_text 'ai.harness:harness-maven-plugin:0.1.0:validate' "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md"
reject_text 'docs/harness/uv/harness_validate.py' "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md"
reject_text 'docs/harness/bun/harness-validate.ts' "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md"
require_text "$root/skills/harness-evolve/SKILL.md" "active \`.git/hooks/pre-commit\` and \`.git/hooks/pre-push\` remain target repository files"
require_markdown_heading "$root/skills/harness-evolve/SKILL.md" 2 'Ownership Boundary'
require_markdown_heading "$root/skills/harness-evolve/SKILL.md" 2 'Cleanup Evolution'
require_markdown_heading "$root/skills/harness-evolve/SKILL.md" 2 'Invariants'

generated_doc_package_file_list=$(package_files "$root/skills/harness-install/assets/common/docs/generated")
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
reject_text_in_paths 'root agents are host-dependent' \
    "$root/README.md" \
    "$root/skills/harness-install/SKILL.md" \
    "$root/skills/harness-validate/SKILL.md" \
    "$root/skills/harness-evolve/SKILL.md"
reject_text_in_paths 'host runtimes that load plugin agents' \
    "$root/README.md" \
    "$root/skills/harness-install/SKILL.md" \
    "$root/skills/harness-validate/SKILL.md" \
    "$root/skills/harness-evolve/SKILL.md"
reject_text_in_paths 'This plugin skill owns installer guidance and orchestration only' \
    "$root/skills/harness-install/SKILL.md" \
    "$root/skills/harness-validate/SKILL.md" \
    "$root/skills/harness-evolve/SKILL.md"
reject_text_in_paths 'This plugin skill owns validation guidance only' \
    "$root/skills/harness-install/SKILL.md" \
    "$root/skills/harness-validate/SKILL.md" \
    "$root/skills/harness-evolve/SKILL.md"
reject_text_in_paths 'This plugin skill owns harness evolution guidance only' \
    "$root/skills/harness-install/SKILL.md" \
    "$root/skills/harness-validate/SKILL.md" \
    "$root/skills/harness-evolve/SKILL.md"

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
    "$root/skills/harness-install/assets/common"
reject_text_in_paths 'install_git_hook_path' \
    "$root/skills/harness-install/scripts/install-harness.sh" \
    "$root/skills/harness-install/SKILL.md"

require_text "$root/skills/harness-install/scripts/install-harness.sh" 'Harness generated hook: pre-commit'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'Harness stage: compliance'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'Harness generated hook: pre-push'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'Harness stage: full-validation'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'Harness validation command:'
require_text "$root/skills/harness-install/scripts/install-harness.sh" './gradlew check'
require_text "$root/skills/harness-install/scripts/install-harness.sh" './gradlew harnessCheck'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'resolve_existing_hooks_path'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'refusing to copy non-generated hook source'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'build-tool'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'install_git_hook_build_tool'
require_text "$root/skills/harness-install/scripts/install-harness.sh" '-Pharness.gitHooks=true'
require_text "$root/skills/harness-install/scripts/install-harness.sh" '-Dharness.gitHooks=true'
require_text "$root/skills/harness-install/scripts/install-harness.sh" './gradlew -Pharness.gitHooks=true help'
require_text "$root/skills/harness-install/scripts/install-harness.sh" 'mvn -q -f harness-maven-plugin/pom.xml -Dharness.gitHooks=true generate-sources'
require_text "$root/skills/harness-install/assets/gradle/settings.gradle.kts" 'org.danilopianini.gradle-pre-commit-git-hooks'
require_text "$root/skills/harness-install/assets/gradle/settings.gradle.kts" '2.1.17'
require_text "$root/skills/harness-install/assets/gradle/settings.gradle.kts" 'harness.gitHooks'
require_text "$root/skills/harness-install/assets/gradle/settings.gradle.kts" '"from"(file("docs/harness/git-hooks/pre-commit"))'
require_text "$root/skills/harness-install/assets/gradle/settings.gradle.kts" '"from"(file("docs/harness/git-hooks/pre-push"))'
require_text "$root/skills/harness-install/assets/maven/harness-maven-plugin/pom.xml" 'git-build-hook-maven-plugin'
require_text "$root/skills/harness-install/assets/maven/harness-maven-plugin/pom.xml" '3.6.0'
require_text "$root/skills/harness-install/assets/maven/harness-maven-plugin/pom.xml" 'core.hooksPath'
require_text "$root/skills/harness-install/SKILL.md" 'build-tool'
require_text "$root/README.md" 'build-tool'
require_text "$root/README.md" 'org.danilopianini.gradle-pre-commit-git-hooks'
require_text "$root/README.md" 'git-build-hook-maven-plugin'
manifest_json="$root/skills/harness-install/assets/gradle/docs/harness/manifest.json"
require_text "$manifest_json" 'pre-commit hook must not run full stack validation commands'
require_text "$manifest_json" 'must declare Harness validation command'
require_text "$manifest_json" 'declares unsupported validation command'
require_text "$manifest_json" 'must run the declared validation command'
require_text "$manifest_json" './gradlew check'
require_text "$manifest_json" './gradlew harnessCheck'

template_roots="
$root/skills/harness-install/assets/common
$root/skills/harness-install/assets/bun
$root/skills/harness-install/assets/gradle
$root/skills/harness-install/assets/maven
$root/skills/harness-install/assets/shell
$root/skills/harness-install/assets/uv
"

for template_root in $template_roots; do
    template_root_files=$(package_files "$template_root")
    for path in $template_root_files; do
        case "$path" in
            */harness_check.py | */harness-check.ts | */harness-check.sh | */HarnessValidationPlugin.kt | */HarnessCheck.kt | */HarnessCheckMojo.java | */HarnessCheck.java | */manifest.json | */manifest.schema.json)
                continue
                ;;
            */assets/common/ARCHITECTURE.md | */assets/common/docs/DESIGN.md | */assets/common/docs/PLANS.md | */assets/common/docs/FRONTEND.md | */assets/common/docs/PRODUCT_SENSE.md | */assets/common/docs/QUALITY_SCORE.md | */assets/common/docs/RELIABILITY.md | */assets/common/docs/SECURITY.md | */assets/common/docs/design-docs/core-beliefs.md | */assets/common/docs/exec-plans/tech-debt-tracker.md | */assets/common/docs/product-specs/*.md | */assets/common/docs/references/*.md | */assets/common/docs/harness/templates/*)
                continue
                ;;
            */.github/workflows/harness.yml | */.gitlab-ci.yml)
                continue
                ;;
        esac
        if [ -f "$path" ]; then
            case "$path" in
                *.md | *.txt)
                    reject_unresolved_template_tokens_in_document "$path"
                    ;;
            esac
            case "$path" in
                *.md)
                    require_markdown_fence_language "$path"
                    require_markdown_blank_before_fence "$path"
                    ;;
            esac
        fi
    done
done

for text in 'example-' 'Describe ' 'Describe...' 'TODO' 'TBD' 'replace-with-stack-specific'; do
    for template_root in $template_roots; do
        template_root_files=$(package_files "$template_root")
        for path in $template_root_files; do
            case "$path" in
                */harness_check.py | */harness-check.ts | */harness-check.sh | */HarnessValidationPlugin.kt | */HarnessCheckMojo.java | */manifest.json)
                    continue
                    ;;
                */assets/common/ARCHITECTURE.md | */assets/common/docs/DESIGN.md | */assets/common/docs/PLANS.md | */assets/common/docs/FRONTEND.md | */assets/common/docs/PRODUCT_SENSE.md | */assets/common/docs/QUALITY_SCORE.md | */assets/common/docs/RELIABILITY.md | */assets/common/docs/SECURITY.md | */assets/common/docs/design-docs/core-beliefs.md | */assets/common/docs/exec-plans/tech-debt-tracker.md | */assets/common/docs/product-specs/*.md | */assets/common/docs/references/*.md | */assets/common/docs/harness/templates/*)
                    continue
                    ;;
                */.github/workflows/harness.yml | */.gitlab-ci.yml)
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
fixture_self_check_helpers
fixture_assert_shell_malformed_manifest
fixture_assert_shell_worktree_excluded
fixture_assert_build_tool_hook_mode
fixture_assert_shell_symlink_safety
fixture_assert_shell_root_contract_scaffold_symlink
fixture_assert_install_root_contract_dangling_agents_alias
fixture_assert_install_root_contract_dangling_claude_alias
fixture_assert_install_root_contract_real_files_rejected
smoke_check_shell_unsafe_hook_paths
fixture_assert_shell_format_check_after_format
fixture_assert_shell_format_malformed_manifest
fixture_assert_bun_source_root_safety
fixture_assert_bun_symlink_component_safety
fixture_assert_bun_oxlint_clean
fixture_assert_bun_oxlint_detects
fixture_assert_uv_ruff_clean
fixture_assert_uv_ruff_detects
fixture_assert_uv_source_root_safety
fixture_assert_uv_worktree_excluded
fixture_assert_uv_hook_shebang
fixture_assert_uv_unsafe_manifest_paths
fixture_assert_gradle_location
fixture_assert_gradle_source_root_safety
fixture_assert_gradle_parse_error
fixture_assert_maven_import_over_fqn
fixture_assert_maven_source_root_safety
fixture_assert_shell_worktree_pruned
fixture_assert_gradle_malformed_include_exclude
fixture_assert_gradle_worktree_excluded
fixture_assert_maven_malformed_include_exclude
fixture_assert_maven_worktree_excluded
fixture_assert_maven_parse_error
fixture_assert_gradle_hook_format_safety
fixture_assert_maven_hook_format_safety
fixture_assert_doc_separator_rejection
# Verify Gradle emptyDirectoryPlaceholders formatter does not create .gitkeep outside root.
#
#     Requires `gradle` in PATH. Gracefully skips with a warning when gradle is unavailable.
#
# @return Returns 0 on success or when gradle is missing.
# @exit Exits with status 1 when .gitkeep is created outside root or format does not report safely.
fixture_assert_gradle_empty_directory_format_safety() {
    if ! gradle_path=$(command -v gradle 2>&1); then
        printf 'warning: gradle not in PATH; skipping gradle empty directory format safety fixture check\n' >&2
        return 0
    fi
    : "$gradle_path"
    fixture_root=$(fixture_create_temp_dir)
    target_dir=$fixture_root/target
    fixture_copy_runtime "$target_dir" gradle
    fixture_write_file "$target_dir" settings.gradle.kts 'rootProject.name = "gradle-empty-directory-format-safety-fixture"'
    fixture_write_file "$target_dir" build.gradle.kts 'plugins { id("com.ririnto.sinon.harness") }

repositories { mavenCentral() }
'
    fixture_write_manifest "$target_dir" '{"name":"gradle-empty-directory-format-safety-fixture","emptyDirectoryPlaceholders":{"enabled":true,"severity":"ERROR","messages":{"default":"empty directory must keep placeholder or real files: {directory}"},"parameters":{"directories":["../outside-empty","safe-empty"]}},"hookShebang":{"enabled":false,"parameters":{"hooks":[],"expectedShebang":"#!/usr/bin/env sh"}},"hookExecutable":{"enabled":false,"parameters":{"hooks":[]}},"hookCommand":{"enabled":false,"parameters":{"prePushHook":"","preCommitHook":"","allowedCommands":[],"allowedPreCommitCommands":[]}},"ciHookCommandParity":{"enabled":false,"parameters":{"ciFiles":[],"referenceHook":""}},"symlinkSafety":{"enabled":false,"parameters":{"allowedSymlinkPairs":[]}},"scaffoldLeaks":{"enabled":false,"parameters":{"scope":{"bases":[],"extensions":[]},"patterns":[]}},"uncheckedTasks":{"enabled":false,"parameters":{"directory":"","uncheckedTaskPattern":""}}}'
    mkdir -p "$target_dir/safe-empty"
    mkdir -p "$fixture_root/outside-empty"
    outside_gitkeep="$fixture_root/outside-empty/.gitkeep"
    if fixture_run_command "$target_dir" 'gradle --console=plain --no-daemon harnessFormat'; then
        :
    else
        :
    fi
    if [ -f "$outside_gitkeep" ]; then
        printf '%s\n' '[fixture_assert_gradle_empty_directory_format_safety] .gitkeep was created outside root' >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_output_contains "$fixture_combined_output" 'is not a safe relative directory path' 'gradle empty directory format reports unsafe path finding' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$fixture_root"
        exit 1
    fi
    fixture_remove_temp_dir "$fixture_root"
}
fixture_assert_double_bracket_rejection
fixture_assert_gradle_hook_from_parameters
fixture_assert_maven_worktree_symlink_excluded
fixture_assert_shell_format_fail_closed_preflight
fixture_assert_emit_path_message_special_chars
fixture_assert_harness_check_warn_counter
fixture_assert_install_hooks_copy_symlink_rejection
fixture_assert_gradle_empty_directory_format_safety
smoke_check_uv_runtime
smoke_check_bun_runtime
smoke_check_shell_runtime
smoke_check_gradle_runtime
smoke_check_maven_runtime

exit 0
