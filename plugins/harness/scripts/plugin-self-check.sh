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
# @param stack_name Stack name: bun, uv, or gradle.
# @return Returns 0 when the runtime is copied.
fixture_copy_runtime() {
    temp_dir=$1
    stack_name=$2
    case "$stack_name" in
        bun | uv)
            source_dir=$root/skills/harness-install/assets/$stack_name/runtime
            mkdir -p "$temp_dir"
            cp -R "$source_dir"/. "$temp_dir"/
            ;;
        gradle)
            source_dir=$root/skills/harness-install/assets/gradle/buildSrc
            mkdir -p "$temp_dir/buildSrc"
            cp -R "$source_dir"/. "$temp_dir/buildSrc"/
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

# Verify Bun formatter allowlists safe edits and remains idempotent.
#
#     Requires `bun` in PATH. Gracefully skips with a warning when bun is
#     unavailable.
#
# @return Returns 0 on success or when bun is missing.
# @exit Exits with status 1 when the Bun formatter fixture fails.
fixture_assert_bun_format() {
    if ! bun_path=$(command -v bun 2>&1); then
        printf 'warning: bun not in PATH; skipping bun format fixture check\n' >&2
        return 0
    fi
    : "$bun_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" bun
    fixture_write_file "$temp_dir" package.json '{"dependencies":{"typescript":"6.0.3"}}'
    if ! install_output=$(cd "$temp_dir" && bun install 2>&1); then
        printf '%s\n' "$install_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    ln -s typescript "$temp_dir/node_modules/typescript@6.0.3"
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
{"name":"bun-format-fixture","leafFunctionBlankLines":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[],"maxConsecutiveBlankLines":1}},"greaterThanComparison":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["ts"],"includePaths":[],"excludePaths":[]}}}
JSONEOF
    )"
    fixture_write_file "$temp_dir" src/example.ts "$(
        cat <<'TSEOF'
export function compare(value: number): number {
  const baseline = 1;


  return value > baseline ? value : baseline;
}
TSEOF
    )"
    fixture_write_file "$temp_dir" src/unsafe.ts "$(
        cat <<'TSEOF'
export function unsafe(value: number): boolean {
  return value > 1;
}
TSEOF
    )"
    fixture_target_file=$temp_dir/src/example.ts
    fixture_unsafe_file=$temp_dir/src/unsafe.ts
    fixture_before_format_checksum=$(fixture_file_checksum "$fixture_target_file")
    fixture_unsafe_before_format_checksum=$(fixture_file_checksum "$fixture_unsafe_file")
    if fixture_run_command "$temp_dir" "bun \"$temp_dir/harness-format.ts\""; then
        :
    else
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_after_first_format_checksum=$(fixture_file_checksum "$fixture_target_file")
    fixture_unsafe_after_first_format_checksum=$(fixture_file_checksum "$fixture_unsafe_file")
    if ! fixture_assertion_output=$(fixture_assert_format_changed "$fixture_before_format_checksum" "$fixture_after_first_format_checksum" "$fixture_stdout" 'src/example.ts' 'bun format first run' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_unsafe_before_format_checksum" "$fixture_unsafe_after_first_format_checksum" 'bun format leaves unsafe-only file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" "bun \"$temp_dir/harness-format.ts\""; then
        :
    else
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_after_second_format_checksum=$(fixture_file_checksum "$fixture_target_file")
    fixture_unsafe_after_second_format_checksum=$(fixture_file_checksum "$fixture_unsafe_file")
    if ! fixture_assertion_output=$(fixture_assert_format_unchanged "$fixture_after_first_format_checksum" "$fixture_after_second_format_checksum" "$fixture_stdout" 'bun format second run' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_unsafe_before_format_checksum" "$fixture_unsafe_after_second_format_checksum" 'bun second format leaves unsafe-only file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" "bun \"$temp_dir/harness-check.ts\""; then
        printf '%s\n' '[fixture_assert_bun_format] expected harness-check to report unsafe comparison' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_canonical_finding_prefix "$fixture_stdout" 'src/example[.]ts' 'greaterThanComparison' 'bun check canonical finding prefix' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! grep -Fq 'value > baseline' "$temp_dir/src/example.ts"; then
        printf '%s\n' '[fixture_assert_bun_format] unsafe greaterThanComparison edit was unexpectedly formatted' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    : "$fixture_assertion_output"
    fixture_remove_temp_dir "$temp_dir"
}

# Verify uv formatter allowlists safe edits and remains idempotent.
#
#     Requires `uv` in PATH. Gracefully skips with a warning when uv is
#     unavailable.
#
# @return Returns 0 on success or when uv is missing.
# @exit Exits with status 1 when the uv formatter fixture fails.
fixture_assert_uv_format() {
    if ! uv_path=$(command -v uv 2>&1); then
        printf 'warning: uv not in PATH; skipping uv format fixture check\n' >&2
        return 0
    fi
    : "$uv_path"
    temp_dir=$(fixture_create_temp_dir)
    fixture_copy_runtime "$temp_dir" uv
    fixture_write_manifest "$temp_dir" "$(
        cat <<'JSONEOF'
{"name":"uv-format-fixture","leafFunctionBlankLines":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["py"],"includePaths":[],"excludePaths":[],"maxConsecutiveBlankLines":1}},"greaterThanComparison":{"enabled":true,"severity":"ERROR","parameters":{"sourceRoots":["src"],"extensions":["py"],"includePaths":[],"excludePaths":[]}}}
JSONEOF
    )"
    fixture_write_file "$temp_dir" src/example.py "$(
        cat <<'PYEOF'
def compare(value: int) -> int:
    baseline = 1


    return value > baseline
PYEOF
    )"
    fixture_write_file "$temp_dir" src/unsafe.py "$(
        cat <<'PYEOF'
def unsafe(value: int) -> bool:
    return value > 1
PYEOF
    )"
    fixture_target_file=$temp_dir/src/example.py
    fixture_unsafe_file=$temp_dir/src/unsafe.py
    fixture_before_format_checksum=$(fixture_file_checksum "$fixture_target_file")
    fixture_unsafe_before_format_checksum=$(fixture_file_checksum "$fixture_unsafe_file")
    if fixture_run_command "$temp_dir" "uv run --quiet --with libcst \"$temp_dir/harness_format.py\""; then
        :
    else
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_after_first_format_checksum=$(fixture_file_checksum "$fixture_target_file")
    fixture_unsafe_after_first_format_checksum=$(fixture_file_checksum "$fixture_unsafe_file")
    if ! fixture_assertion_output=$(fixture_assert_format_changed "$fixture_before_format_checksum" "$fixture_after_first_format_checksum" "$fixture_stdout" 'src/example.py' 'uv format first run' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_unsafe_before_format_checksum" "$fixture_unsafe_after_first_format_checksum" 'uv format leaves unsafe-only file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" "uv run --quiet --with libcst \"$temp_dir/harness_format.py\""; then
        :
    else
        printf '%s\n' "$fixture_stdout" >&2
        printf '%s\n' "$fixture_stderr" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_after_second_format_checksum=$(fixture_file_checksum "$fixture_target_file")
    fixture_unsafe_after_second_format_checksum=$(fixture_file_checksum "$fixture_unsafe_file")
    if ! fixture_assertion_output=$(fixture_assert_format_unchanged "$fixture_after_first_format_checksum" "$fixture_after_second_format_checksum" "$fixture_stdout" 'uv format second run' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_checksum_unchanged "$fixture_unsafe_before_format_checksum" "$fixture_unsafe_after_second_format_checksum" 'uv second format leaves unsafe-only file unchanged' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if fixture_run_command "$temp_dir" "uv run --quiet --with libcst \"$temp_dir/harness_check.py\""; then
        printf '%s\n' '[fixture_assert_uv_format] expected harness-check to report unsafe comparison' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! fixture_assertion_output=$(fixture_assert_canonical_finding_prefix "$fixture_stdout" 'src/example[.]py' 'greaterThanComparison' 'uv check canonical finding prefix' 2>&1); then
        printf '%s\n' "$fixture_assertion_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    if ! grep -Fq 'value > baseline' "$temp_dir/src/example.py"; then
        printf '%s\n' '[fixture_assert_uv_format] unsafe greaterThanComparison edit was unexpectedly formatted' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    : "$fixture_assertion_output"
    fixture_remove_temp_dir "$temp_dir"
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
  {"name":"gradle-location-fixture","filePresence":{"enabled":true,"severity":"ERROR","paths":["MISSING.md"],"parameters":{}},"ifStatementBraces":{"enabled":false},"implicitLambdaIt":{"enabled":false},"publicDeclarationDocComment":{"enabled":false},"silentCatch":{"enabled":false},"wildcardImport":{"enabled":false},"classMemberOrdering":{"enabled":true,"severity":"ERROR","messages":{"default":"{file}:{line}: class `{className}` member `{memberName}` ({memberOverrideState}:{memberVisibility}:{memberKind}) is out of order"},"parameters":{"sourceRoots":["buildSrc/src/main/kotlin"],"extensions":["kt"],"includePaths":[],"excludePaths":[],"kindOrder":["companionObject","constProperty","fieldOrProperty","initializer","constructor","function","interface","class","enum"],"visibilityOrder":["public","protected","internal","package","private"],"overrideOrder":["override","nonOverride"]}}}
JSONEOF
    )"
    fixture_write_file "$temp_dir" buildSrc/src/main/kotlin/fixture/LocationFixture.kt "$(
        cat <<'KOTLINEOF'
package fixture

class LocationFixture {
    fun later(): String = "value"
    val earlier: String = "value"
}
KOTLINEOF
    )"
    if fixture_run_command "$temp_dir" 'gradle --console=plain --no-daemon harnessCheck'; then
        printf '%s\n' '[fixture_assert_gradle_location] expected harnessCheck to report class member ordering' >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    fixture_combined_output=$(printf '%s\n%s\n' "$fixture_stdout" "$fixture_stderr")
    if ! fixture_assertion_output=$(fixture_assert_canonical_finding_prefix "$fixture_combined_output" 'buildSrc/src/main/kotlin/fixture/LocationFixture[.]kt' 'classMemberOrdering' 'gradle AST canonical finding prefix' 2>&1); then
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
    if printf '%s\n' "$fixture_combined_output" | grep -Eq '^[^:]+:0:0'; then
        printf '%s\n' '[fixture_assert_gradle_location] repository-level finding rendered a fabricated :0:0 location' >&2
        printf '%s\n' "$fixture_combined_output" >&2
        fixture_remove_temp_dir "$temp_dir"
        exit 1
    fi
    : "$fixture_assertion_output"
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
print(f"uv runtime rules: {len(list(harness_check.HarnessCheck))}")' 2>&1); then
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
    "$root/skills/harness-install/assets/common/docs/harness/manifest.json" \
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
    "$root/skills/harness-install/assets/shell/.github/workflows/harness.yml" \
    "$root/skills/harness-install/assets/shell/.gitlab-ci.yml" \
    "$root/skills/harness-install/assets/shell/docs/harness/manifest.json" \
    "$root/skills/harness-install/assets/shell/runtime/harness-check.sh" \
    "$root/skills/harness-install/assets/gradle/buildSrc/src/main/kotlin/com/ririnto/sinon/harness/plugin/HarnessValidationPlugin.kt" \
    "$root/skills/harness-install/assets/gradle/buildSrc/src/main/kotlin/com/ririnto/sinon/harness/ast/AstFinding.kt" \
    "$root/skills/harness-install/assets/gradle/buildSrc/src/main/kotlin/com/ririnto/sinon/harness/rules/ast/ClassMemberOrderingRule.kt" \
    "$root/skills/harness-install/assets/gradle/buildSrc/src/main/kotlin/com/ririnto/sinon/harness/rules/ast/TerminalBranchWhenRule.kt" \
    "$root/skills/harness-install/assets/maven/harness-maven-plugin/src/main/java/com/ririnto/sinon/harness/HarnessCheckMojo.java" \
    "$root/skills/harness-install/assets/maven/harness-maven-plugin/src/main/java/com/ririnto/sinon/harness/rules/ast/ClassMemberOrderingRule.java"; do
    require_file "$path"
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
done

require_executable "$root/scripts/plugin-self-check.sh"
require_executable "$root/skills/harness-install/scripts/install-harness.sh"
require_executable "$root/skills/harness-install/assets/common/docs/harness/git-hooks/pre-commit"
require_executable "$root/skills/harness-install/assets/common/docs/harness/git-hooks/pre-push"
require_executable "$root/skills/harness-install/assets/shell/runtime/harness-check.sh"

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
manifest = json.loads((root / "skills/harness-install/assets/common/docs/harness/manifest.json").read_text())
manifest_base_schema_path = root / "skills/harness-install/assets/common/docs/harness/manifest.base.schema.json"
manifest_code_schema_path = root / "skills/harness-install/assets/common/docs/harness/manifest.code.schema.json"
manifest_base_schema = json.loads(manifest_base_schema_path.read_text())
manifest_code_schema = json.loads(manifest_code_schema_path.read_text())
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
base_schema_properties = set(manifest_base_schema.get("properties", {}).keys())
missing_metadata = required_metadata_properties - base_schema_properties
if missing_metadata:
    errors.append(f"manifest base schema missing metadata properties: {sorted(missing_metadata)}")
base_additional = manifest_base_schema.get("additionalProperties")
if not isinstance(base_additional, dict) or base_additional.get("$ref") != "#/$defs/addOn":
    errors.append("manifest base schema must accept unknown rules via additionalProperties addOn $ref")
code_all_of = manifest_code_schema.get("allOf")
if not isinstance(code_all_of, list) or {"$ref": "./manifest.base.schema.json"} not in code_all_of:
    errors.append("manifest code schema must extend base via allOf $ref to ./manifest.base.schema.json")
per_stack_schema_expectations = {
    "shell": "./manifest.base.schema.json",
    "gradle": "./manifest.code.schema.json",
    "maven": "./manifest.code.schema.json",
    "bun": "./manifest.code.schema.json",
    "uv": "./manifest.code.schema.json",
}
for stack, expected_parent_ref in per_stack_schema_expectations.items():
    stack_schema_path = root / f"skills/harness-install/assets/{stack}/docs/harness/manifest.schema.json"
    if not stack_schema_path.is_file():
        errors.append(f"missing stack schema: {stack}/docs/harness/manifest.schema.json")
        continue
    stack_schema = json.loads(stack_schema_path.read_text())
    stack_all_of = stack_schema.get("allOf")
    if not isinstance(stack_all_of, list) or {"$ref": expected_parent_ref} not in stack_all_of:
        errors.append(f"{stack} manifest schema must extend {expected_parent_ref} via allOf")
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
    "gradle": {"must": {"implicitLambdaIt", "classMemberOrdering", "companionObjectPosition", "terminalBranchWhen"}, "forbidden_keys": set()},
    "maven": {"must": {"classMemberOrdering"}, "forbidden_keys": {"companionObjectPosition", "terminalBranchWhen"}},
    "uv": {"must": {"greaterThanComparison"}, "forbidden_keys": {"classMemberOrdering", "companionObjectPosition", "kotlinTopLevelDeclarationCount", "terminalBranchWhen"}},
    "bun": {"must": {"greaterThanComparison"}, "forbidden_keys": {"classMemberOrdering", "companionObjectPosition", "kotlinTopLevelDeclarationCount", "terminalBranchWhen"}},
    "shell": {"must": {"filePresence", "scaffoldLeaks"}, "forbidden_keys": {"classMemberOrdering", "greaterThanComparison", "importOverFqn", "terminalBranchWhen"}},
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
        if not (root / "skills/harness-install/assets/common" / item).is_file():
            errors.append(f"manifest {key} missing file: {item}")
for item in manifest_items(manifest, "requiredDirectories"):
    if not (root / "skills/harness-install/assets/common" / item).is_dir():
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
require_text "$root/README.md" 'v6 archive structure'
require_markdown_heading "$root/README.md" 2 'Plugin-Owned Structural Agents'
require_markdown_heading "$root/README.md" 2 'Packaged Scripts and Assets'
require_markdown_heading "$root/README.md" 2 'Runtime Model'
require_text "$root/skills/harness-install/SKILL.md" "Gradle pre-commit runs \`harnessCheck\`, Gradle pre-push runs \`check\`"
require_markdown_heading "$root/skills/harness-install/SKILL.md" 2 'Ownership Boundary'
require_markdown_heading "$root/skills/harness-install/SKILL.md" 2 'Invariants'
require_text "$root/skills/harness-validate/SKILL.md" "generated \`docs/harness/git-hooks/pre-push\` command marker"
require_markdown_heading "$root/skills/harness-validate/SKILL.md" 2 'Ownership Boundary'
require_markdown_heading "$root/skills/harness-validate/SKILL.md" 2 'Invariants'
require_text "$root/skills/harness-validate/SKILL.md" 'Manifest drift'
require_text "$root/skills/harness-validate/SKILL.md" 'Generated artifact metadata'
require_text "$root/skills/harness-validate/SKILL.md" 'Unsupported validation command'
require_text "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" 'manifest drift'
require_text "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" 'generated-artifact metadata'
require_text "$root/skills/harness-install/assets/common/.claude/skills/harness-validate/SKILL.md" 'unsupported pre-push validation command'
require_text "$root/skills/harness-evolve/SKILL.md" "active \`.git/hooks/pre-commit\` and \`.git/hooks/pre-push\` remain target repository files"
require_markdown_heading "$root/skills/harness-evolve/SKILL.md" 2 'Ownership Boundary'
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
manifest_json="$root/skills/harness-install/assets/common/docs/harness/manifest.json"
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
fixture_assert_bun_format
fixture_assert_uv_format
fixture_assert_gradle_location
smoke_check_uv_runtime
smoke_check_bun_runtime
smoke_check_gradle_runtime
smoke_check_maven_runtime

exit 0
