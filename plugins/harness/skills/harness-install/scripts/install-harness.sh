#!/usr/bin/env sh
set -e

script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
skill_dir=$(CDPATH= cd "$script_dir/.." && pwd)
template_dir="$skill_dir/templates"
mode=auto
hooks=none
force=0
ci=1
target_root=${HARNESS_TARGET_ROOT:-.}
root_contract_conflicts=0

# :description: Print command usage.
# :return: Writes usage text to standard output.
usage() {
  cat <<'EOF'
usage: install-harness.sh [--target DIR] [--mode auto|gradle|maven|uv|bun] [--hooks none|copy] [--force] [--no-ci]

HARNESS_TARGET_ROOT may be used instead of --target.
EOF
}

# :description: Print an error and exit.
# :param message: Error message.
# :return: Exits with status 1.
error() {
  message=$1
  printf '%s\n' "error: $message" >&2
  exit 1
}

while [ $# -gt 0 ]; do
  case "$1" in
    --target) [ $# -ge 2 ] || error '--target requires a directory'; target_root=$2; shift 2 ;;
    --target=*) target_root=${1#--target=}; shift ;;
    --mode) [ $# -ge 2 ] || error '--mode requires auto|gradle|maven|uv|bun'; mode=$2; shift 2 ;;
    --mode=*) mode=${1#--mode=}; shift ;;
    auto|gradle|maven|uv|bun) mode=$1; shift ;;
    --hooks) [ $# -ge 2 ] || error '--hooks requires none|copy'; hooks=$2; shift 2 ;;
    --hooks=*) hooks=${1#--hooks=}; shift ;;
    --force) force=1; shift ;;
    --no-ci) ci=0; shift ;;
    -h|--help) usage; exit 0 ;;
    *) printf '%s\n' "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

case "$mode" in auto|gradle|maven|uv|bun) ;; *) printf '%s\n' "invalid mode: $mode" >&2; exit 2 ;; esac
case "$hooks" in copy|none) ;; *) printf '%s\n' "invalid hooks mode: $hooks" >&2; exit 2 ;; esac
[ -n "$target_root" ] || error 'target root must not be empty'
[ -d "$target_root" ] || error "target root is not a directory: $target_root"
target_root=$(CDPATH= cd "$target_root" && pwd) || error "cannot resolve target root: $target_root"
cd "$target_root"

if [ ! -d .git ]; then
  printf '%s\n' 'warning: target root has no .git directory; git hook activation will be skipped' >&2
fi

if [ "$mode" = auto ]; then
  mode=$(sh "$script_dir/detect-stack.sh")
fi
if [ "$mode" = unknown ]; then
  printf '%s\n' 'could not detect repository stack; pass --mode gradle|maven|uv|bun' >&2
  exit 2
fi

# :description: Reject a path that is not a safe target-relative path.
# :param path: Repository-relative path.
# :return: Exits when the path is unsafe.
reject_unsafe_relative_path() {
  unsafe_path=$1
  case "$unsafe_path" in
    ./*) unsafe_path=${unsafe_path#./} ;;
  esac
  case "$unsafe_path" in
    ''|.|/*|..|../*|*/..|*/../*) error "unsafe target path: $unsafe_path" ;;
  esac
}

# :description: Ensure a destination parent has no symlink components.
# :param path: Repository-relative destination path.
# :return: Creates the parent directory when it is safe.
ensure_safe_parent_dir() {
  parent_path=$1
  dir=$(dirname "$parent_path")
  case "$dir" in ./*) dir=${dir#./} ;; esac
  [ "$dir" = . ] && return 0
  reject_unsafe_relative_path "$dir"
  current=
  rest=$dir
  while [ -n "$rest" ]; do
    case "$rest" in
      */*) part=${rest%%/*}; rest=${rest#*/} ;;
      *) part=$rest; rest= ;;
    esac
    [ -n "$part" ] || continue
    current=${current:+$current/}$part
    if [ -L "$current" ]; then
      error "refusing symlink directory component: $current"
    fi
    if [ -e "$current" ] && [ ! -d "$current" ]; then
      error "parent component is not a directory: $current"
    fi
  done
  mkdir -p "$dir"
}

# :description: Ensure a file destination can be written safely.
# :param path: Repository-relative destination path.
# :return: Creates the parent directory when it is safe.
ensure_safe_file_destination() {
  file_path=$1
  case "$file_path" in ./*) file_path=${file_path#./} ;; esac
  reject_unsafe_relative_path "$file_path"
  ensure_safe_parent_dir "$file_path"
  if [ -L "$file_path" ]; then
    error "refusing symlink file destination: $file_path"
  fi
  if [ -d "$file_path" ]; then
    error "refusing directory file destination: $file_path"
  fi
}

# :description: Copy one file while preserving executable permission.
# :param src: Source file path.
# :param dst: Target-relative destination file path.
# :return: Writes copied, overwritten, or skipped path.
copy_file() {
  src=$1
  dst=$2
  ensure_safe_file_destination "$dst"
  if [ -e "$dst" ] && [ "$force" -ne 1 ]; then
    printf '%s\n' "keep existing: $dst"
    return 0
  fi
  tmp=$(dirname "$dst")/.harness-tmp-$$-$(basename "$dst")
  ensure_safe_file_destination "$tmp"
  if [ -e "$tmp" ]; then
    error "temporary destination already exists: $tmp"
  fi
  cp "$src" "$tmp"
  if [ -x "$src" ]; then
    chmod +x "$tmp"
  fi
  if [ -e "$dst" ]; then
    mv "$tmp" "$dst"
    printf '%s\n' "overwrite (--force): $dst"
  else
    mv "$tmp" "$dst"
    printf '%s\n' "write: $dst"
  fi
}

# :description: Escape text for use as a sed replacement value.
# :param value: Raw replacement text.
# :return: Prints escaped replacement text.
escape_sed_replacement() {
  value=$1
  printf '%s' "$value" | sed 's/[\\&|]/\\&/g'
}

# :description: Replace validation command placeholders in a copied target file.
# :param file: Target-relative file path.
# :param validation_command: Validation command selected at install time.
# :return: Writes rendered file content when placeholders are present.
render_validation_placeholders() {
  file=$1
  validation_command=$2
  if [ ! -f "$file" ] || ! grep -Fq '{{validation_command}}' "$file"; then
    return 0
  fi
  tmp=$(dirname "$file")/.harness-tmp-$$-$(basename "$file")
  ensure_safe_file_destination "$tmp"
  escaped_validation_command=$(escape_sed_replacement "$validation_command")
  sed "s|{{validation_command}}|$escaped_validation_command|g" "$file" > "$tmp"
  mv "$tmp" "$file"
  printf '%s\n' "render validation command: $file"
}

# :description: Copy all files from one directory tree except root contracts.
# :param src_dir: Source directory path.
# :param dst_dir: Target-relative destination directory path.
# :return: Writes copied or skipped paths.
copy_tree() {
  src_dir=$1
  dst_dir=$2
  [ -d "$src_dir" ] || return 0
  find "$src_dir" -type f | while IFS= read -r src; do
    rel=${src#"$src_dir"/}
    case "$rel" in
      AGENTS.md|CLAUDE.md|.claude/harness/git-hooks/pre-commit|target/*|*/target/*|build/*|*/build/*|bin/*|*/bin/*|.gradle/*|*/.gradle/*|.factorypath|*/.factorypath|.classpath|*/.classpath|.project|*/.project|.settings/*|*/.settings/*|__pycache__/*|*/__pycache__/*|*.pyc) continue ;;
    esac
    copy_file "$src" "$dst_dir/$rel"
  done
}

# :description: Create, skip, or force-update one root contract file.
# :param file: Target root contract file.
# :param marker: Marker string used for idempotency.
# :param template_file: Template file path.
# :return: Writes create, update, skip, or conflict status.
ensure_root_contract() {
  file=$1
  marker=$2
  template_file=$3
  ensure_safe_file_destination "$file"
  if [ ! -e "$file" ]; then
    tmp=$file.harness.tmp.$$
    ensure_safe_file_destination "$tmp"
    cp "$template_file" "$tmp"
    mv "$tmp" "$file"
    printf '%s\n' "create root contract: $file"
    return 0
  fi
  if grep -Fq "$marker" "$file"; then
    printf '%s\n' "skip root contract: $file"
    return 0
  fi
  if [ "$force" -ne 1 ]; then
    printf '%s\n' "conflict root contract: $file lacks marker $marker; rerun with --force to append" >&2
    root_contract_conflicts=1
    return 0
  fi
  tmp=$file.harness.tmp.$$
  ensure_safe_file_destination "$tmp"
  { cat "$file"; printf '\n'; cat "$template_file"; } > "$tmp"
  mv "$tmp" "$file"
  printf '%s\n' "update root contract (--force): $file"
}


# :description: Create, skip, or force-update a shared root contract file atomically.
# :param file: Shared target root contract file.
# :return: Writes create, update, or skip status.
ensure_shared_root_contract() {
  file=$1
  ensure_safe_file_destination "$file"
  had_file=0
  has_agents=0
  has_claude=0
  if [ -e "$file" ]; then
    had_file=1
  fi
  if [ -e "$file" ] && grep -Fq '# Repository Harness Contract' "$file"; then
    has_agents=1
  fi
  if [ -e "$file" ] && grep -Fq '# Claude Code Entry Point' "$file"; then
    has_claude=1
  fi
  if [ "$has_agents" -eq 1 ] && [ "$has_claude" -eq 1 ]; then
    printf '%s\n' "skip shared root contract: $file"
    return 0
  fi
  tmp=$file.harness.tmp.$$
  ensure_safe_file_destination "$tmp"
  if [ -e "$file" ]; then
    cat "$file" > "$tmp"
  else
    : > "$tmp"
  fi
  if [ "$has_agents" -ne 1 ]; then
    if [ -s "$tmp" ]; then
      printf '\n' >> "$tmp"
    fi
    cat "$template_dir/common/AGENTS.md" >> "$tmp"
  fi
  if [ "$has_claude" -ne 1 ]; then
    if [ -s "$tmp" ]; then
      printf '\n' >> "$tmp"
    fi
    cat "$template_dir/common/CLAUDE.md" >> "$tmp"
  fi
  mv "$tmp" "$file"
  if [ "$had_file" -eq 1 ]; then
    printf '%s\n' "update shared root contract (--force): $file"
  else
    printf '%s\n' "create shared root contract: $file"
  fi
}

# :description: Return a supported root-contract symlink target.
# :param file: Root contract symlink path.
# :return: Writes the canonical target or exits on unsupported symlink.
root_contract_symlink_target() {
  file=$1
  target=$(readlink "$file")
  case "$file:$target" in
    AGENTS.md:CLAUDE.md|CLAUDE.md:AGENTS.md) printf '%s\n' "$target" ;;
    *) error "unsupported root contract symlink: $file -> $target" ;;
  esac
}

# :description: Record a root contract conflict before any root contracts are written.
# :param file: Target root contract file.
# :param marker: Marker string used for idempotency.
# :return: Writes conflict status when the file needs explicit force.
check_root_contract_conflict() {
  file=$1
  marker=$2
  ensure_safe_file_destination "$file"
  if [ -e "$file" ] && ! grep -Fq "$marker" "$file" && [ "$force" -ne 1 ]; then
    printf '%s\n' "conflict root contract: $file lacks marker $marker; rerun with --force to append" >&2
    root_contract_conflicts=1
  fi
}

# :description: Ensure existing root files contain the minimum harness contract.
# :return: Writes root contract status.
ensure_root_contracts() {
  agents_target=AGENTS.md
  claude_target=CLAUDE.md
  if [ -L AGENTS.md ]; then
    agents_target=$(root_contract_symlink_target AGENTS.md)
  fi
  if [ -L CLAUDE.md ]; then
    claude_target=$(root_contract_symlink_target CLAUDE.md)
  fi
  check_root_contract_conflict "$agents_target" '# Repository Harness Contract'
  check_root_contract_conflict "$claude_target" '# Claude Code Entry Point'
  if [ "$root_contract_conflicts" -ne 0 ]; then
    error 'root contract conflicts must be resolved before installing harness assets'
  fi
  if [ "$agents_target" = "$claude_target" ]; then
    ensure_shared_root_contract "$agents_target"
  else
    ensure_root_contract "$agents_target" '# Repository Harness Contract' "$template_dir/common/AGENTS.md"
    ensure_root_contract "$claude_target" '# Claude Code Entry Point' "$template_dir/common/CLAUDE.md"
  fi
}

# :description: Preserve required empty repository paths with .gitkeep files.
# :return: Writes created .gitkeep paths.
ensure_gitkeep_paths() {
  for dir in docs/exec-plans/active docs/exec-plans/completed docs/generated; do
    keep=$dir/.gitkeep
    ensure_safe_file_destination "$keep"
    if [ ! -e "$keep" ]; then
      : > "$keep"
      printf '%s\n' "write: $keep"
    fi
  done
}

# :description: Copy stack-specific templates, honoring CI inclusion settings.
# :param src_dir: Stack template directory path.
# :param dst_dir: Target-relative destination directory path.
# :return: Writes copied or skipped paths.
copy_stack_tree() {
  src_dir=$1
  dst_dir=$2
  [ -d "$src_dir" ] || return 0
  find "$src_dir" -type f | while IFS= read -r src; do
    rel=${src#"$src_dir"/}
    case "$rel" in
      target/*|*/target/*|build/*|*/build/*|bin/*|*/bin/*|.gradle/*|*/.gradle/*|.factorypath|*/.factorypath|.classpath|*/.classpath|.project|*/.project|.settings/*|*/.settings/*|__pycache__/*|*/__pycache__/*|*.pyc) continue ;;
      .gitlab-ci.yml.tmpl)
        if [ "$ci" -ne 1 ]; then
          continue
        fi
        ;;
      .github/*)
        if [ "$ci" -ne 1 ]; then
          continue
        fi
        ;;
    esac
    case "$rel" in
      .github/workflows/harness.yml.tmpl)
        dst_rel=.github/workflows/harness.yml
        copy_file "$src" "$dst_dir/$dst_rel"
        render_validation_placeholders "$dst_dir/$dst_rel" "$cmd"
        ;;
      .gitlab-ci.yml.tmpl)
        dst_rel=.gitlab-ci.yml
        copy_file "$src" "$dst_dir/$dst_rel"
        render_validation_placeholders "$dst_dir/$dst_rel" "$cmd"
        ;;
      *) copy_file "$src" "$dst_dir/$rel" ;;
    esac
  done
}

# :description: Append one line to a file when a marker is absent.
# :param file: Target-relative file path.
# :param marker: Marker string used for idempotency.
# :param line: Line to append.
# :return: Writes appended or skipped status.
append_line_once() {
  file=$1
  marker=$2
  line=$3
  ensure_safe_file_destination "$file"
  if [ -f "$file" ] && grep -Fq "$marker" "$file"; then
    printf '%s\n' "keep existing marker: $file $marker"
    return 0
  fi
  tmp=$file.harness.tmp.$$
  ensure_safe_file_destination "$tmp"
  {
    if [ -f "$file" ]; then
      cat "$file"
      printf '\n'
    fi
    printf '%s\n' "$line"
  } > "$tmp"
  mv "$tmp" "$file"
  printf '%s\n' "append line: $file"
}

# :description: Ensure Gradle settings includes the local harness plugin build.
# :param file: Gradle settings file path.
# :return: Writes modified status.
ensure_gradle_settings_include() {
  file=$1
  include='includeBuild(".claude/harness/gradle-plugin")'
  marker='.claude/harness/gradle-plugin'
  ensure_safe_file_destination "$file"
  if [ -f "$file" ] && grep -Fq "$marker" "$file"; then
    printf '%s\n' "keep existing Gradle plugin include: $file"
    return 0
  fi
  tmp=$file.harness.tmp.$$
  ensure_safe_file_destination "$tmp"
  {
    printf 'pluginManagement {\n    %s\n}\n\n' "$include"
    if [ -f "$file" ]; then
      cat "$file"
    fi
  } > "$tmp"
  mv "$tmp" "$file"
  printf '%s\n' "prepend Gradle plugin include: $file"
}

# :description: Install Gradle plugin wiring into settings and root build files.
# :return: Writes modified Gradle integration files.
install_gradle() {
  settings_file=settings.gradle.kts
  build_file=build.gradle.kts
  if [ -f settings.gradle ] && [ ! -f settings.gradle.kts ]; then
    settings_file=settings.gradle
  fi
  if [ -f build.gradle ] && [ ! -f build.gradle.kts ]; then
    build_file=build.gradle
  fi
  ensure_gradle_settings_include "$settings_file"
  if [ "$build_file" = build.gradle.kts ]; then
    append_line_once "$build_file" 'apply(plugin = "ai.harness.validation")' 'apply(plugin = "ai.harness.validation")'
  else
    append_line_once "$build_file" "apply plugin: 'ai.harness.validation'" "apply plugin: 'ai.harness.validation'"
  fi
}

# :description: Return success when local .git hooks can be modified safely.
# :return: Returns non-zero after writing a warning when hooks cannot be modified.
ensure_git_dir_for_hooks() {
  if [ ! -d .git ]; then
    printf '%s\n' 'skip git hook install: .git directory not found' >&2
    return 1
  fi
  if [ -L .git ]; then
    printf '%s\n' 'skip git hook install: .git is a symlink' >&2
    return 1
  fi
  return 0
}

# :description: Return the target-local Git hooks path when one is configured.
# :return: Writes the configured hooks path or nothing.
configured_hooks_path() {
  if [ ! -d .git ] || [ -L .git ]; then
    return 0
  fi
  if git config --get core.hooksPath; then
    return 0
  fi
  return 0
}

# :description: Normalize a hooks path for comparison without requiring it to exist.
# :param path: Configured Git hooks path.
# :return: Writes a target-relative normalized path when possible.
normalize_hooks_path() {
  hooks_path=$1
  case "$hooks_path" in
    "$target_root") hooks_path=. ;;
    "$target_root"/) hooks_path=. ;;
    "$target_root"/*) hooks_path=${hooks_path#"$target_root"/} ;;
  esac
  normalized_path=
  rest=$hooks_path
  while [ -n "$rest" ]; do
    case "$rest" in
      */*) part=${rest%%/*}; rest=${rest#*/} ;;
      *) part=$rest; rest= ;;
    esac
    case "$part" in
      ''|.) continue ;;
      ..)
        case "$normalized_path" in
          */*) normalized_path=${normalized_path%/*} ;;
          *) normalized_path= ;;
        esac
        ;;
      *) normalized_path=${normalized_path:+$normalized_path/}$part ;;
    esac
  done
  printf '%s\n' "$normalized_path"
}

# :description: Fail when existing Git config would make hook activation ambiguous.
# :return: Exits when configured hooks would bypass or activate generated hooks unexpectedly.
ensure_hook_activation_policy() {
  configured_path=$(configured_hooks_path)
  normalized_path=$(normalize_hooks_path "$configured_path")
  case "$configured_path" in
    '') return 0 ;;
  esac
  case "$normalized_path" in
    .claude/harness/git-hooks)
      error 'target Git config already points hooks at .claude/harness/git-hooks; refusing to install an executable harness hook without explicit config cleanup'
      ;;
  esac
  if [ "$hooks" = copy ]; then
    error "target Git config uses hooks path $configured_path; --hooks copy would not activate .git/hooks/pre-commit"
  fi
}

# :description: Return the validation command for the selected stack mode.
# :param selected_mode: Resolved harness stack mode.
# :return: Writes one shell command to standard output.
validation_command_for_mode() {
  selected_mode=$1
  case "$selected_mode" in
    gradle)
      if [ -x ./gradlew ]; then
        printf '%s\n' './gradlew harnessValidate'
      else
        printf '%s\n' 'gradle harnessValidate'
      fi
      ;;
    maven) printf '%s\n' 'mvn -q -f .claude/harness/maven-plugin/pom.xml install && mvn -q ai.harness:harness-maven-plugin:0.1.0:validate' ;;
    uv) printf '%s\n' 'uv run python .claude/harness/uv/harness_validate.py' ;;
    bun) printf '%s\n' 'bun run .claude/harness/bun/harness-validate.ts' ;;
    *) error "unsupported mode for validation command: $selected_mode" ;;
  esac
}

# :description: Write a new fixed-command pre-commit hook.
# :param file: Target-relative hook path.
# :param validation_command: Validation command selected at install time.
# :return: Writes an executable hook file.
write_new_pre_commit_hook() {
  file=$1
  validation_command=$2
  ensure_safe_file_destination "$file"
  (
    set -C
    {
      printf '%s\n' '#!/usr/bin/env sh'
      printf '%s\n' 'set -e'
      printf '\n'
      printf '%s\n' "$validation_command"
    } > "$file"
  ) || error "temporary hook destination already exists: $file"
  chmod +x "$file"
}

# :description: Install the target-owned harness pre-commit template.
# :param validation_command: Validation command selected at install time.
# :return: Writes or skips the target hook template.
install_target_hook_template() {
  validation_command=$1
  dst=.claude/harness/git-hooks/pre-commit
  ensure_safe_file_destination "$dst"
  if [ -e "$dst" ] && [ "$force" -ne 1 ]; then
    if grep -Fq 'packaged placeholder is replaced during harness installation' "$dst"; then
      error "existing harness hook placeholder is not selected-mode content: $dst; rerun with --force to replace"
    fi
    printf '%s\n' "keep existing: $dst"
    return 0
  fi
  tmp=$(dirname "$dst")/.harness-tmp-$$-$(basename "$dst")
  ensure_safe_file_destination "$tmp"
  write_new_pre_commit_hook "$tmp" "$validation_command"
  if [ -e "$dst" ]; then
    mv "$tmp" "$dst"
    printf '%s\n' "overwrite (--force): $dst"
  else
    mv "$tmp" "$dst"
    printf '%s\n' "write: $dst"
  fi
}

# :description: Copy the generated pre-commit hook into .git/hooks.
# :param validation_command: Validation command selected at install time.
# :return: Writes hook installation status.
install_git_hook_copy() {
  validation_command=$1
  ensure_git_dir_for_hooks || return 0
  dst=.git/hooks/pre-commit
  ensure_safe_file_destination "$dst"
  if [ -e "$dst" ] && [ "$force" -ne 1 ]; then
    printf '%s\n' "keep existing hook: $dst; rerun with --force to replace"
    return 0
  fi
  tmp=$(dirname "$dst")/.harness-tmp-$$-$(basename "$dst")
  ensure_safe_file_destination "$tmp"
  write_new_pre_commit_hook "$tmp" "$validation_command"
  mv "$tmp" "$dst"
  printf '%s\n' "install git hook: $dst"
}

cmd=$(validation_command_for_mode "$mode")
ensure_hook_activation_policy
ensure_root_contracts
copy_tree "$template_dir/common" .
ensure_gitkeep_paths
copy_stack_tree "$template_dir/$mode" .

if [ "$mode" = gradle ]; then
  install_gradle
fi
install_target_hook_template "$cmd"
case "$hooks" in copy) install_git_hook_copy "$cmd" ;; none) printf '%s\n' 'skip git hook install' ;; esac
printf '\n%s\n' "harness target: $target_root"
printf '%s\n' "harness mode: $mode"
printf '%s\n' "validation command: $cmd"
