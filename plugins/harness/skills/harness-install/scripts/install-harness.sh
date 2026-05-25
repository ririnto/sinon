#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

script_dir=$(CDPATH='' cd "$(dirname "$0")" && pwd)
skill_dir=$(CDPATH='' cd "$script_dir/.." && pwd)
template_dir="$skill_dir/assets"
mode=
hooks=none
force=0
ci=1
target_root=${HARNESS_TARGET_ROOT:-.}
root_contract_conflicts=0

# Print command usage.
#
# @return Writes usage text to standard output.
# @exit Exits with status 0 when invoked with -h or --help.
usage() {
  cat <<'EOF'
usage: install-harness.sh --mode gradle|maven|uv|bun|shell [--target DIR] [--hooks none|copy] [--force] [--no-ci]

modes (required, no auto-detection):
  gradle  Projects using the Gradle build tool.
  maven   Projects using the Apache Maven build tool.
  uv      Python-family projects managed by uv (pyproject.toml + uv.lock).
  bun     Node-family projects (TypeScript/JavaScript) managed by bun (package.json, bun.lock).
  shell   Shell-script-only or Makefile-driven projects. Requires python3 on PATH for manifest parsing.

HARNESS_TARGET_ROOT may be used instead of --target.
EOF
}

# Print an error and exit.
#
# @param message Error message.
# @exit Exits with status 1.
error() {
  message=$1
  printf '%s\n' "[error] $message" >&2
  exit 1
}

while [ $# -gt 0 ]; do
  case "$1" in
    --target)
      if [ $# -lt 2 ]; then
        error '--target requires a directory'
      fi
      target_root=$2
      shift 2
      ;;
    --target=*) target_root=${1#--target=}; shift ;;
    --mode)
      if [ $# -lt 2 ]; then
        error '--mode requires gradle|maven|uv|bun|shell'
      fi
      mode=$2
      shift 2
      ;;
    --mode=*) mode=${1#--mode=}; shift ;;
    gradle|maven|uv|bun|shell) mode=$1; shift ;;
    --hooks)
      if [ $# -lt 2 ]; then
        error '--hooks requires none|copy'
      fi
      hooks=$2
      shift 2
      ;;
    --hooks=*) hooks=${1#--hooks=}; shift ;;
    --force) force=1; shift ;;
    --no-ci) ci=0; shift ;;
    -h|--help) usage; exit 0 ;;
    *) printf '%s\n' "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

case "$mode" in
  gradle|maven|uv|bun|shell) ;;
  '') printf '%s\n' '--mode is required (gradle|maven|uv|bun|shell).' >&2; exit 2 ;;
  *) printf '%s\n' "invalid mode: $mode" >&2; exit 2 ;;
esac
case "$hooks" in copy|none) ;; *) printf '%s\n' "invalid hooks mode: $hooks" >&2; exit 2 ;; esac
if [ -z "$target_root" ]; then
  error 'target root must not be empty'
fi
if [ ! -d "$target_root" ]; then
  error "target root is not a directory: $target_root"
fi
target_root=$(CDPATH='' cd "$target_root" && pwd -P) || error "cannot resolve target root: $target_root"
cd "$target_root"

if ! git_probe=$(git rev-parse --is-inside-work-tree 2>&1); then
  unset git_probe
  printf '%s\n' 'warning: target root is not a Git worktree; git hook activation will be skipped' >&2
fi


# Reject a path that is not a safe target-relative path.
#
# @param path Repository-relative path.
# @exit Exits via 'error' when path is unsafe.
reject_unsafe_relative_path() {
  unsafe_path=$1
  case "$unsafe_path" in
    ./*) unsafe_path=${unsafe_path#./} ;;
  esac
  case "$unsafe_path" in
    ''|.|/*|..|../*|*/..|*/../*) error "unsafe target path: $unsafe_path (must be relative, non-empty, no .. references)" ;;
  esac
}

# Ensure a destination parent has no symlink components.
#
# @param path Repository-relative destination path.
# @exit Exits via 'error' when symlink or non-directory component is found.
ensure_safe_parent_dir() {
  parent_path=$1
  dir=$(dirname "$parent_path")
  case "$dir" in ./*) dir=${dir#./} ;; esac
  if [ "$dir" = . ]; then
    return 0
  fi
  reject_unsafe_relative_path "$dir"
  current=
  rest=$dir
  while [ -n "$rest" ]; do
    case "$rest" in
      */*) part=${rest%%/*}; rest=${rest#*/} ;;
      *) part=$rest; rest= ;;
    esac
    if [ -z "$part" ]; then
      continue
    fi
    current=${current:+$current/}$part
    if [ -L "$current" ]; then
      error "[safe_parent] refusing symlink directory component: $current"
    fi
    if [ -e "$current" ] && [ ! -d "$current" ]; then
      error "[safe_parent] parent component is not a directory: $current"
    fi
  done
  mkdir -p "$dir"
}

# Ensure a file destination can be written safely.
#
# @param path Repository-relative destination path.
# @exit Exits via 'error' when destination is unsafe.
ensure_safe_file_destination() {
  file_path=$1
  case "$file_path" in ./*) file_path=${file_path#./} ;; esac
  reject_unsafe_relative_path "$file_path"
  ensure_safe_parent_dir "$file_path"
  if [ -L "$file_path" ]; then
    error "[safe_destination] refusing symlink file destination: $file_path"
  fi
  if [ -d "$file_path" ]; then
    error "[safe_destination] refusing directory file destination: $file_path"
  fi
}

# Copy one file while preserving executable permission.
#
# @param src Source file path.
# @param dst Target-relative destination file path.
# @return Writes copied, overwritten, or skipped path.
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
    error "[copy_file] temporary destination already exists: $tmp (cleanup or retry)"
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

# Copy the selected stack manifest over the common template when safe.
#
# @param src Selected stack manifest source path.
# @param dst Target-relative manifest destination path.
# @return Writes copied, overwritten, or skipped path.
copy_stack_manifest() {
  src=$1
  dst=$2
  common_manifest=$template_dir/common/docs/harness/manifest.json
  ensure_safe_file_destination "$dst"
  if [ -e "$dst" ] && [ "$force" -ne 1 ]; then
    if cmp -s "$dst" "$common_manifest"; then
      tmp=$(dirname "$dst")/.harness-tmp-$$-$(basename "$dst")
      ensure_safe_file_destination "$tmp"
      if [ -e "$tmp" ]; then
        error "[copy_stack_manifest] temporary destination already exists: $tmp (cleanup or retry)"
      fi
      cp "$src" "$tmp"
      mv "$tmp" "$dst"
      printf '%s\n' "overwrite selected stack manifest: $dst"
      return 0
    fi
  fi
  copy_file "$src" "$dst"
}

# Escape text for use as a sed replacement value.
#
# @param value Raw replacement text.
# @return Prints escaped replacement text.
escape_sed_replacement() {
  value=$1
  printf '%s' "$value" | sed 's/[\\&|]/\\&/g'
}

# Replace validation command placeholders in a copied target file.
#
# @param file Target-relative file path.
# @param validation_command Validation command selected at install time.
# @return Writes rendered file content when placeholders are present.
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

# Copy all files from one directory tree except root contracts.
#
# @param src_dir Source directory path.
# @param dst_dir Target-relative destination directory path.
# @return Writes copied or skipped paths.
copy_tree() {
  src_dir=$1
  dst_dir=$2
  if [ ! -d "$src_dir" ]; then
    return 0
  fi
  find "$src_dir" -type f | while IFS= read -r src; do
    rel=${src#"$src_dir"/}
    case "$rel" in
      AGENTS.md|CLAUDE.md|docs/harness/manifest.json|docs/harness/git-hooks/pre-commit|docs/harness/git-hooks/pre-push|target/*|*/target/*|build/*|*/build/*|bin/*|*/bin/*|.gradle/*|*/.gradle/*|.factorypath|*/.factorypath|.classpath|*/.classpath|.project|*/.project|.settings/*|*/.settings/*|__pycache__/*|*/__pycache__/*|*.pyc) continue ;;
    esac
    copy_file "$src" "$dst_dir/$rel"
  done
}

# Create, skip, or force-update one root contract file.
#
# @param file Target root contract file.
# @param marker Marker string used for idempotency.
# @param template_file Template file path.
# @return Writes create, update, skip, or conflict status.
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

# Create, skip, or force-update a shared root contract file atomically.
#
# @param file Shared target root contract file.
# @return Writes create, update, or skip status.
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
  if [ -e "$file" ] && grep -Fq '## Entry Point' "$file"; then
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
  if [ "$has_claude" -ne 1 ] && grep -Fq '## Entry Point' "$tmp"; then
    has_claude=1
  fi
  if [ "$has_agents" -ne 1 ] && grep -Fq '# Repository Harness Contract' "$tmp"; then
    has_agents=1
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

# Return a supported root-contract symlink target.
#
# @param file Root contract symlink path.
# @exit Exits via 'error' when symlink is unsupported.
root_contract_symlink_target() {
  file=$1
  target=$(readlink "$file")
  case "$file:$target" in
    AGENTS.md:CLAUDE.md|CLAUDE.md:AGENTS.md) printf '%s\n' "$target" ;;
    *) error "[root_contract_symlink] unsupported symlink target (must be AGENTS.md <-> CLAUDE.md): $file -> $target" ;;
  esac
}

# Record a root contract conflict before any root contracts are written.
#
# @param file Target root contract file.
# @param marker Marker string used for idempotency.
# @return Writes conflict status when the file needs explicit force.
check_root_contract_conflict() {
  file=$1
  marker=$2
  ensure_safe_file_destination "$file"
  if [ -e "$file" ] && ! grep -Fq "$marker" "$file" && [ "$force" -ne 1 ]; then
    printf '%s\n' "conflict root contract: $file lacks marker $marker; rerun with --force to append" >&2
    root_contract_conflicts=1
  fi
}

# Ensure existing root files contain the minimum harness contract.
#
# @return Writes root contract status.
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
  check_root_contract_conflict "$claude_target" '# Entry Point'
  if [ "$root_contract_conflicts" -ne 0 ]; then
    error 'root contract conflicts must be resolved before installing harness assets'
  fi
  if [ "$agents_target" = "$claude_target" ]; then
    ensure_shared_root_contract "$agents_target"
  else
    ensure_root_contract "$agents_target" '# Repository Harness Contract' "$template_dir/common/AGENTS.md"
    ensure_root_contract "$claude_target" '# Entry Point' "$template_dir/common/CLAUDE.md"
  fi
}

# Preserve required empty repository paths with .gitkeep files.
#
# @return Writes created .gitkeep paths.
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

# Copy stack-specific templates, honoring CI inclusion settings.
#
# @param src_dir Stack template directory path.
# @param dst_dir Target-relative destination directory path.
# @return Writes copied or skipped paths.
copy_stack_tree() {
  src_dir=$1
  dst_dir=$2
  if [ ! -d "$src_dir" ]; then
    return 0
  fi
  find "$src_dir" -type f | while IFS= read -r src; do
    rel=${src#"$src_dir"/}
    case "$rel" in
      target/*|*/target/*|build/*|*/build/*|bin/*|*/bin/*|.gradle/*|*/.gradle/*|.ruff_cache/*|*/.ruff_cache/*|.factorypath|*/.factorypath|.classpath|*/.classpath|.project|*/.project|.settings/*|*/.settings/*|__pycache__/*|*/__pycache__/*|*.pyc) continue ;;
      .gitlab-ci.yml)
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
      .github/workflows/harness.yml)
        copy_file "$src" "$dst_dir/$rel"
        render_validation_placeholders "$dst_dir/$rel" "$pre_push_cmd"
        ;;
      .gitlab-ci.yml)
        copy_file "$src" "$dst_dir/$rel"
        render_validation_placeholders "$dst_dir/$rel" "$pre_push_cmd"
        ;;
      runtime/*)
        copy_file "$src" "$dst_dir/docs/harness/$mode/${rel#runtime/}"
        ;;
      docs/harness/manifest.json)
        copy_stack_manifest "$src" "$dst_dir/$rel"
        ;;
      *) copy_file "$src" "$dst_dir/$rel" ;;
    esac
  done
}

# Append one line to a file when a marker is absent.
#
# @param file Target-relative file path.
# @param marker Marker string used for idempotency.
# @param line Line to append.
# @return Writes appended or skipped status.
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

# Install Gradle plugin wiring into the root build file.
#
# The harness plugin lives in `buildSrc/`, which Gradle picks up automatically
# without any `settings.gradle.kts` change. Only the root `build.gradle.kts`
# needs to apply the plugin id.
#
# @return Writes modified Gradle integration files.
install_gradle() {
  build_file=build.gradle.kts
  if [ -f build.gradle ] && [ ! -f build.gradle.kts ]; then
    build_file=build.gradle
  fi
  if [ "$build_file" = build.gradle.kts ]; then
    append_line_once "$build_file" 'apply(plugin = "ai.harness.validation")' 'apply(plugin = "ai.harness.validation")'
  else
    append_line_once "$build_file" "apply plugin: 'ai.harness.validation'" "apply plugin: 'ai.harness.validation'"
  fi
}

# Return success when local .git hooks can be modified safely.
#
# @return Returns non-zero after writing a warning when hooks cannot be modified.
ensure_git_dir_for_hooks() {
  if [ ! -e .git ]; then
    printf '%s\n' 'skip git hook install: .git not found' >&2
    return 1
  fi
  if [ -L .git ]; then
    printf '%s\n' 'skip git hook install: .git is a symlink' >&2
    return 1
  fi
  if ! git_probe=$(git rev-parse --is-inside-work-tree 2>&1); then
    unset git_probe
    printf '%s\n' 'skip git hook install: not inside a Git worktree' >&2
    return 1
  fi
  return 0
}

# Resolve the worktree-aware Git hooks directory.
#
# @return Writes the absolute hooks directory path, or returns non-zero with a warning.
resolve_git_hooks_dir() {
  if ! common_dir=$(git rev-parse --git-common-dir 2>&1); then
    printf '%s\n' 'skip git hook install: cannot resolve git common dir' >&2
    return 1
  fi
  case "$common_dir" in
    /*) ;;
    *) common_dir=$target_root/$common_dir ;;
  esac
  common_dir=$(normalize_absolute_path "$common_dir")
  if [ ! -d "$common_dir" ]; then
    printf '%s\n' "[resolve_git_hooks] skip git hook install: $common_dir not found" >&2
    return 1
  fi
  hooks_dir=$common_dir/hooks
  if [ ! -d "$hooks_dir" ]; then
    mkdir -p "$hooks_dir" || {
      printf '%s\n' "[resolve_git_hooks] skip git hook install: cannot create $hooks_dir" >&2
      return 1
    }
  fi
  printf '%s\n' "$hooks_dir"
}

# Ensure a generated Git hook destination outside the worktree is safe.
#
# @param hook_file Absolute Git hook destination path.
# @exit Exits via 'error' when destination is unsafe.
ensure_safe_hook_destination() {
  hook_file=$1
  case "$hook_file" in
    /*) ;;
    *) error "[safe_hook_destination] hook destination must be an absolute path: $hook_file" ;;
  esac
  parent=$(dirname "$hook_file")
  name=$(basename "$hook_file")
  case "$name" in
    pre-commit|pre-push) ;;
    *) error "[safe_hook_destination] unsupported hook name (must be pre-commit or pre-push): $name" ;;
  esac
  case "$parent" in
    */hooks) ;;
    *) error "[safe_hook_destination] refusing hook destination outside a hooks directory: $hook_file" ;;
  esac
  if [ -L "$hook_file" ]; then
    error "[safe_hook_destination] refusing symlink hook destination: $hook_file"
  fi
  if [ -d "$hook_file" ]; then
    error "[safe_hook_destination] refusing directory hook destination: $hook_file"
  fi
}

# Return the target repository Git hooks path when one is configured.
#
# @return Writes the configured hooks path or nothing.
configured_hooks_path() {
  # shellcheck disable=SC2034
  if ! git_probe=$(git rev-parse --is-inside-work-tree 2>&1); then
    unset git_probe
    return 0
  fi
  if git config --path --get core.hooksPath; then
    return 0
  fi
  return 0
}

# Normalize a hooks path for comparison without requiring it to exist.
#
# @param path Configured Git hooks path.
# @return Writes a target-relative normalized path when possible.
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

# Strip trailing slashes while preserving root.
#
# @param path Path to normalize.
# @return Writes path without trailing slashes.
strip_trailing_slashes() {
  slash_path=$1
  while [ "$slash_path" != / ]; do
    case "$slash_path" in
      */) slash_path=${slash_path%/} ;;
      *) break ;;
    esac
  done
  printf '%s\n' "$slash_path"
}

# Normalize an absolute path lexically without requiring it to exist.
#
# @param path Absolute or target-relative path.
# @return Writes a normalized absolute path.
normalize_absolute_path() {
  absolute_path=$1
  case "$absolute_path" in
    /*) ;;
    *) absolute_path=$target_root/$absolute_path ;;
  esac
  absolute_path=$(strip_trailing_slashes "$absolute_path")
  normalized_path=
  rest=${absolute_path#/}
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
  printf '/%s\n' "$normalized_path"
}

# Return a configured hooks path as an absolute lexical path.
#
# @param path Configured Git hooks path.
# @return Writes an absolute lexical path.
absolute_hooks_path() {
  hooks_path=$1
  case "$hooks_path" in
    '') return 1 ;;
    /*) normalize_absolute_path "$hooks_path" ;;
    *) normalize_absolute_path "$target_root/$hooks_path" ;;
  esac
}

# Resolve existing symlink components in a hooks path without requiring the final
#     target to exist.
#
# @param path Configured Git hooks path.
# @return Writes a normalized absolute path after lexical symlink expansion.
resolve_hooks_path_lexically() {
  current_path=$(absolute_hooks_path "$1") || return 1
  iterations=0
  while [ "$iterations" -lt 40 ]; do
    iterations=$((iterations + 1))
    resolved_parts=
    rest=${current_path#/}
    replaced=0
    while [ -n "$rest" ]; do
      case "$rest" in
        */*) part=${rest%%/*}; rest=${rest#*/} ;;
        *) part=$rest; rest= ;;
      esac
      if [ -z "$part" ]; then
        continue
      fi
      candidate=/${resolved_parts:+$resolved_parts/}$part
      if [ -L "$candidate" ]; then
        link_target=$(readlink "$candidate") || return 1
        case "$link_target" in
          /*) next_path=$link_target ;;
          *) next_path=$(dirname "$candidate")/$link_target ;;
        esac
        if [ -n "$rest" ]; then
          next_path=$next_path/$rest
        fi
        current_path=$(normalize_absolute_path "$next_path")
        replaced=1
        break
      fi
      resolved_parts=${resolved_parts:+$resolved_parts/}$part
    done
    if [ "$replaced" -eq 0 ]; then
      printf '%s\n' "$current_path"
      return 0
    fi
  done
  error '[resolve_hooks_path] configured hooks path contains too many symlink expansions (circular links?)'
}

# Resolve an existing configured hooks path through symlinks.
#
# @param path Configured Git hooks path.
# @return Writes the physical absolute path when it exists as a directory.
resolve_existing_hooks_path() {
  candidate=$(absolute_hooks_path "$1") || return 1
  if [ ! -d "$candidate" ]; then
    return 1
  fi
  CDPATH='' cd "$candidate" && pwd -P
}

# Fail when existing Git config would make hook activation ambiguous.
#
# @exit Exits via 'error' when configured hooks would bypass or duplicate activation.
ensure_hook_activation_policy() {
  configured_path=$(configured_hooks_path)
  case "$configured_path" in
    '') return 0 ;;
  esac
  normalized_path=$(normalize_hooks_path "$configured_path")
  harness_hooks_path=$(normalize_absolute_path "$target_root/docs/harness/git-hooks")
  symlink_path=$(resolve_hooks_path_lexically "$configured_path" || true)
  resolved_path=$(resolve_existing_hooks_path "$configured_path" || true)
  points_at_harness_hooks=0
  case "$normalized_path" in
    docs/harness/git-hooks) points_at_harness_hooks=1 ;;
  esac
  if [ -n "$symlink_path" ] && [ "$symlink_path" = "$harness_hooks_path" ]; then
    points_at_harness_hooks=1
  fi
  if [ -n "$resolved_path" ] && [ "$resolved_path" = "$harness_hooks_path" ]; then
    points_at_harness_hooks=1
  fi
  if [ "$points_at_harness_hooks" -eq 1 ]; then
    if [ "$hooks" = copy ]; then
      error '[hook_activation_policy] target Git config points hooks at docs/harness/git-hooks; --hooks copy would write to the worktree hooks directory instead. Re-run with --hooks none to refresh harness-tracked hooks in place.'
    fi
    return 0
  fi
  if [ "$hooks" = copy ]; then
    error "[hook_activation_policy] target Git config uses hooks path $configured_path; --hooks copy would not activate the worktree hooks. Either unset core.hooksPath or re-run with --hooks none."
  fi
}

# Return the validation command for the selected stack mode.
#
# @param selected_mode Resolved harness stack mode.
# @exit Exits via 'error' when mode is unsupported.
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
    maven) printf '%s\n' 'mvn -q -f harness-maven-plugin/pom.xml install ai.harness:harness-maven-plugin:0.1.0:validate' ;;
    uv) printf '%s\n' 'uv run --script docs/harness/uv/harness_validate.py' ;;
    bun) printf '%s\n' 'bun --install=fallback run docs/harness/bun/harness-validate.ts' ;;
    shell) printf '%s\n' 'sh docs/harness/shell/harness-validate.sh' ;;
    *) error "[validation_command] unsupported mode (must be gradle|maven|uv|bun|shell): $selected_mode" ;;
  esac
}

# Return the generated pre-commit command for modes that run one.
#
# @param selected_mode Resolved harness stack mode.
# @exit Exits via 'error' when mode is unsupported.
pre_commit_command_for_mode() {
  selected_mode=$1
  case "$selected_mode" in
    gradle)
      if [ -x ./gradlew ]; then
        printf '%s\n' './gradlew harnessValidate'
      else
        printf '%s\n' 'gradle harnessValidate'
      fi
      ;;
    maven|uv|bun|shell) ;;
    *) error "[pre_commit_command] unsupported mode (must be gradle|maven|uv|bun|shell): $selected_mode" ;;
  esac
}

# Return the generated pre-push and CI command for the selected stack mode.
#
# @param selected_mode Resolved harness stack mode.
# @exit Exits via 'error' when mode is unsupported.
pre_push_command_for_mode() {
  selected_mode=$1
  case "$selected_mode" in
    gradle)
      if [ -x ./gradlew ]; then
        printf '%s\n' './gradlew check'
      else
        printf '%s\n' 'gradle check'
      fi
      ;;
    maven|uv|bun|shell) validation_command_for_mode "$selected_mode" ;;
    *) error "[pre_push_command] unsupported mode (must be gradle|maven|uv|bun|shell): $selected_mode" ;;
  esac
}

# Write a lightweight generated pre-commit compliance hook.
#
# @param file Target-relative hook path.
# @exit Exits via 'error' when destination already exists or hook write fails.
write_new_pre_commit_hook() {
  file=$1
  ensure_safe_file_destination "$file"
  (
    set -C
    cat > "$file" <<'HOOK'
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
# Harness generated hook: pre-commit
# Harness stage: compliance
set -e
require_file() {
  if [ ! -f "$1" ]; then
    printf '%s\n' "harness pre-commit: missing required file: $1" >&2
    exit 1
  fi
}
require_executable_hook() {
  hook=$1
  marker=$2
  stage=$3
  require_file "$hook"
  if [ ! -x "$hook" ]; then
    printf '%s\n' "[require_executable_hook] hook must be executable: $hook" >&2
    exit 1
  fi
  first_line=$(sed -n '1p' "$hook")
  if [ "$first_line" != '#!/usr/bin/env sh' ]; then
    printf '%s\n' "[require_executable_hook] hook must use #!/usr/bin/env sh: $hook" >&2
    exit 1
  fi
  second_line=$(sed -n '2p' "$hook")
  if [ "$second_line" != '# -*- coding: utf-8 -*-' ]; then
    printf '%s\n' "[require_executable_hook] hook must declare utf-8 coding on line 2: $hook" >&2
    exit 1
  fi
  grep -Fq "$marker" "$hook" || { printf '%s\n' "[require_executable_hook] hook missing generated marker: $hook" >&2; exit 1; }
  grep -Fq "$stage" "$hook" || { printf '%s\n' "[require_executable_hook] hook missing stage marker: $hook" >&2; exit 1; }
  placeholder='packaged placeholder is replaced during harness'
  placeholder="$placeholder installation"
  if grep -Fq "$placeholder" "$hook"; then
    printf '%s\n' "[require_executable_hook] hook still contains packaged placeholder text: $hook" >&2
    exit 1
  fi
}
require_file AGENTS.md
require_file CLAUDE.md
require_file ARCHITECTURE.md
require_file docs/harness/manifest.json
require_executable_hook docs/harness/git-hooks/pre-commit 'Harness generated hook: pre-commit' 'Harness stage: compliance'
require_executable_hook docs/harness/git-hooks/pre-push 'Harness generated hook: pre-push' 'Harness stage: full-validation'
validation_command=$(sed -n 's/^# Harness validation command: //p' docs/harness/git-hooks/pre-push | head -n 1)
if [ -z "$validation_command" ]; then
  printf '%s\n' "[pre_commit_hook] pre-push hook missing validation command marker" >&2
  exit 1
fi
if [ -f .github/workflows/harness.yml ] && ! grep -Fq "$validation_command" .github/workflows/harness.yml; then
  printf '%s\n' "[pre_commit_hook] .github/workflows/harness.yml does not match pre-push validation command" >&2
  exit 1
fi
if [ -f .gitlab-ci.yml ] && ! grep -Fq "$validation_command" .gitlab-ci.yml; then
  printf '%s\n' "[pre_commit_hook] .gitlab-ci.yml does not match pre-push validation command" >&2
  exit 1
fi
HOOK
  ) || error "[write_pre_commit_hook] temporary hook destination already exists: $file (cleanup or retry)"
  chmod +x "$file"
}

# Write a generated pre-commit hook that runs a harness validation command.
#
# @param file Target-relative hook path.
# @param validation_command Pre-commit validation command selected at install time.
# @exit Exits via 'error' when destination already exists or hook write fails.
write_new_pre_commit_command_hook() {
  file=$1
  validation_command=$2
  ensure_safe_file_destination "$file"
  (
    set -C
    {
      printf '%s\n' '#!/usr/bin/env sh'
      printf '%s\n' '# -*- coding: utf-8 -*-'
      printf '%s\n' '# Harness generated hook: pre-commit'
      printf '%s\n' '# Harness stage: harness-validation'
      printf '%s\n' "# Harness validation command: $validation_command"
      printf '%s\n' 'set -e'
      printf '\n'
      printf '%s\n' "$validation_command"
    } > "$file"
  ) || error "[write_pre_commit_command_hook] temporary hook destination already exists: $file (cleanup or retry)"
  chmod +x "$file"
}

# Write a generated pre-push hook that runs the selected final check command.
#
# @param file Target-relative hook path.
# @param validation_command Validation command selected at install time.
# @exit Exits via 'error' when destination already exists or hook write fails.
write_new_pre_push_hook() {
  file=$1
  validation_command=$2
  ensure_safe_file_destination "$file"
  (
    set -C
    {
      printf '%s\n' '#!/usr/bin/env sh'
      printf '%s\n' '# -*- coding: utf-8 -*-'
      printf '%s\n' '# Harness generated hook: pre-push'
      printf '%s\n' '# Harness stage: full-validation'
      printf '%s\n' "# Harness validation command: $validation_command"
      printf '%s\n' 'set -e'
      printf '\n'
      printf '%s\n' "$validation_command"
    } > "$file"
  ) || error "[write_pre_push_hook] temporary hook destination already exists: $file (cleanup or retry)"
  chmod +x "$file"
}

# Return success when an existing generated hook can be refreshed without force.
#
# @param file Target-relative hook path.
# @param marker Generated hook marker.
# @return Returns zero for managed generated hooks.
is_managed_generated_hook() {
  file=$1
  marker=$2
  if [ ! -f "$file" ]; then
    return 1
  fi
  grep -Fq "$marker" "$file"
}

# Install one target-owned generated hook template.
#
# @param dst Target-relative hook path.
# @param marker Generated hook marker.
# @param stage_label Human-readable hook stage.
# @param validation_command Validation command selected at install time.
# @exit Exits via 'error' when hook type is unsupported or placeholder detected.
install_one_target_hook_template() {
  dst=$1
  marker=$2
  stage_label=$3
  validation_command=$4
  ensure_safe_file_destination "$dst"
  if [ -e "$dst" ] && [ "$force" -ne 1 ] && ! is_managed_generated_hook "$dst" "$marker"; then
    if grep -Fq 'packaged placeholder is replaced during harness installation' "$dst"; then
      error "[install_target_hook_template] existing harness hook placeholder is not selected-mode content: $dst; rerun with --force to replace"
    fi
    printf '%s\n' "keep existing: $dst"
    return 0
  fi
  tmp=$(dirname "$dst")/.harness-tmp-$$-$(basename "$dst")
  ensure_safe_file_destination "$tmp"
  case "$dst" in
    */pre-commit)
      if [ -n "$validation_command" ]; then
        write_new_pre_commit_command_hook "$tmp" "$validation_command"
      else
        write_new_pre_commit_hook "$tmp"
      fi
      ;;
    */pre-push) write_new_pre_push_hook "$tmp" "$validation_command" ;;
    *) error "[install_target_hook_template] unsupported harness hook template (must be pre-commit or pre-push): $dst" ;;
  esac
  if [ -e "$dst" ]; then
    mv "$tmp" "$dst"
    if [ "$force" -eq 1 ]; then
      printf '%s\n' "overwrite (--force): $dst"
    else
      printf '%s\n' "refresh generated $stage_label hook: $dst"
    fi
  else
    mv "$tmp" "$dst"
    printf '%s\n' "write: $dst"
  fi
}

# Install target-owned harness hook templates.
#
# @param validation_command Validation command selected at install time.
# @return Writes or skips target hook templates.
install_target_hook_templates() {
  pre_commit_command=$1
  pre_push_command=$2
  install_one_target_hook_template docs/harness/git-hooks/pre-commit 'Harness generated hook: pre-commit' pre-commit "$pre_commit_command"
  install_one_target_hook_template docs/harness/git-hooks/pre-push 'Harness generated hook: pre-push' pre-push "$pre_push_command"
}

# Copy one generated hook into .git/hooks.
#
# @param name Hook basename.
# @exit Exits via 'error' when hook source is invalid.
validate_generated_hook_source_for_copy() {
  name=$1
  src=docs/harness/git-hooks/$name
  case "$name" in
    pre-commit) stage='Harness stage:' ;;
    pre-push) stage='Harness stage: full-validation' ;;
    *) error "unsupported git hook copy: $name" ;;
  esac
  require_file_marker="Harness generated hook: $name"
  if [ ! -f "$src" ]; then
    error "[validate_hook_source] missing generated hook source: $src"
  fi
  grep -Fq "$require_file_marker" "$src" || error "[validate_hook_source] refusing to copy non-generated hook source: $src; rerun with --force to regenerate"
  grep -Fq "$stage" "$src" || error "[validate_hook_source] refusing to copy hook source with wrong stage: $src; rerun with --force to regenerate"
  if grep -Fq 'packaged placeholder is replaced during harness installation' "$src"; then
    error "[validate_hook_source] refusing to copy placeholder hook source: $src; rerun with --force to regenerate"
  fi
}

# Validate all hook sources that copy mode will activate before writing any active
#     hook.
#
# @param hooks_dir Absolute worktree hooks directory.
# @exit Exits via 'error' before partial writes when source is invalid.
preflight_git_hook_copy_sources() {
  hooks_dir=$1
  for name in pre-commit pre-push; do
    dst=$hooks_dir/$name
    ensure_safe_hook_destination "$dst"
    if [ -e "$dst" ] && [ "$force" -ne 1 ]; then
      continue
    fi
    validate_generated_hook_source_for_copy "$name"
  done
}

# Copy one generated hook into the worktree-aware hooks directory.
#
# @param name Hook basename.
# @param hooks_dir Absolute worktree hooks directory.
# @exit Exits via 'error' when copy or permission operations fail.
copy_one_git_hook() {
  name=$1
  hooks_dir=$2
  src=docs/harness/git-hooks/$name
  dst=$hooks_dir/$name
  ensure_safe_hook_destination "$dst"
  if [ -e "$dst" ] && [ "$force" -ne 1 ]; then
    printf '%s\n' "keep existing hook: $dst; rerun with --force to replace"
    return 0
  fi
  had_dst=0
  if [ -e "$dst" ]; then
    had_dst=1
  fi
  tmp_dir=$hooks_dir/.harness-tmp-$$-$name.dir
  if [ -e "$tmp_dir" ]; then
    error "[copy_git_hook] temporary hook directory already exists: $tmp_dir (cleanup or retry)"
  fi
  mkdir "$tmp_dir"
  tmp=$tmp_dir/$name
  cp "$src" "$tmp" || { rm -f "$tmp"; rmdir "$tmp_dir"; error "[copy_git_hook] failed to copy generated hook: $src"; }
  chmod +x "$tmp" || { rm -f "$tmp"; rmdir "$tmp_dir"; error "[copy_git_hook] failed to mark hook executable: $tmp"; }
  mv "$tmp" "$dst" || { rm -f "$tmp"; rmdir "$tmp_dir"; error "[copy_git_hook] failed to install git hook: $dst"; }
  rmdir "$tmp_dir"
  if [ "$force" -eq 1 ] && [ "$had_dst" -eq 1 ]; then
    printf '%s\n' "install git hook (--force): $dst"
  else
    printf '%s\n' "install git hook: $dst"
  fi
}

# Copy generated pre-commit and pre-push hooks into the worktree-aware hooks
#     directory.
#
# @return Writes hook installation status.
install_git_hook_copy() {
  ensure_git_dir_for_hooks || return 0
  hooks_dir=$(resolve_git_hooks_dir) || return 0
  printf '%s\n' "git hooks directory: $hooks_dir"
  preflight_git_hook_copy_sources "$hooks_dir"
  copy_one_git_hook pre-commit "$hooks_dir"
  copy_one_git_hook pre-push "$hooks_dir"
}

cmd=$(validation_command_for_mode "$mode")
pre_commit_cmd=$(pre_commit_command_for_mode "$mode")
pre_push_cmd=$(pre_push_command_for_mode "$mode")
ensure_hook_activation_policy
ensure_root_contracts
copy_tree "$template_dir/common" .
ensure_gitkeep_paths
copy_stack_tree "$template_dir/$mode" .

if [ "$mode" = gradle ]; then
  install_gradle
fi
install_target_hook_templates "$pre_commit_cmd" "$pre_push_cmd"
case "$hooks" in copy) install_git_hook_copy ;; none) printf '%s\n' 'skip git hook install' ;; esac
printf '\n%s\n' "harness target: $target_root"
printf '%s\n' "harness mode: $mode"
printf '%s\n' "validation command: $cmd"
printf '%s\n' "pre-commit command: ${pre_commit_cmd:-harness compliance}"
printf '%s\n' "pre-push command: $pre_push_cmd"
