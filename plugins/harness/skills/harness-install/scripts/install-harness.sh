#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

script_dir=$(CDPATH='' cd "$(dirname "$0")" && pwd)
skill_dir=$(CDPATH='' cd "$script_dir/.." && pwd)
template_dir="$skill_dir/assets"
mode=
force=0
ci_host=
target_root=${HARNESS_TARGET_ROOT:-.}
root_contract_conflicts=0

# Print command usage.
#
# @return Writes usage text to standard output.
# @exit Exits with status 0 when invoked with -h or --help.
usage() {
    cat <<'EOF'
usage: install-harness.sh --mode gradle|maven|uv|bun|shell --ci-host github|gitlab|both|none [--target DIR] [--force]

modes (required, no auto-detection):
  gradle  Projects using the Gradle build tool.
  maven   Projects using the Apache Maven build tool.
  uv      Python-family projects managed by uv (pyproject.toml + uv.lock).
  bun     Node-family projects (TypeScript/JavaScript) managed by bun (package.json, bun.lock).
  shell   Shell-script-only or Makefile-driven projects.

CI host selection (required):
  --ci-host github|gitlab|both|none  Select which CI host(s) to install. --no-ci is an alias for none.

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
        --target=*)
            target_root=${1#--target=}
            shift
            ;;
        --mode)
            if [ $# -lt 2 ]; then
                error '--mode requires gradle|maven|uv|bun|shell'
            fi
            mode=$2
            shift 2
            ;;
        --mode=*)
            mode=${1#--mode=}
            shift
            ;;
        gradle | maven | uv | bun | shell)
            mode=$1
            shift
            ;;
        --force)
            force=1
            shift
            ;;
        --ci-host)
            if [ $# -lt 2 ]; then
                error '--ci-host requires github|gitlab|both|none'
            fi
            ci_host=$2
            shift 2
            ;;
        --ci-host=*)
            ci_host=${1#--ci-host=}
            shift
            ;;
        --no-ci)
            ci_host=none
            shift
            ;;
        -h | --help)
            usage
            exit 0
            ;;
        *)
            printf '%s\n' "unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

case "$mode" in
    gradle | maven | uv | bun | shell) ;;
    '')
        printf '%s\n' '--mode is required (gradle|maven|uv|bun|shell).' >&2
        exit 2
        ;;
    *)
        printf '%s\n' "invalid mode: $mode" >&2
        exit 2
        ;;
esac
case "$ci_host" in
    github | gitlab | both | none) ;;
    '')
        printf '%s\n' '--ci-host is required (github|gitlab|both|none).' >&2
        exit 2
        ;;
    *)
        printf '%s\n' "invalid ci-host: $ci_host" >&2
        exit 2
        ;;
esac
if [ -z "$target_root" ]; then
    error 'target root must not be empty'
fi
if [ ! -d "$target_root" ]; then
    error "target root is not a directory: $target_root"
fi
target_root=$(CDPATH='' cd "$target_root" && pwd -P) || error "cannot resolve target root: $target_root"
cd "$target_root"

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
        '' | . | /* | .. | ../* | */.. | */../*) error "unsafe target path: $unsafe_path (must be relative, non-empty, no .. references)" ;;
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
            */*)
                part=${rest%%/*}
                rest=${rest#*/}
                ;;
            *)
                part=$rest
                rest=
                ;;
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
# @param seed Optional 1 to mark the file as a first-install seed (default 0).
# @return Writes copied, overwritten, or skipped path with seed-aware logs.
copy_file() {
    src=$1
    dst=$2
    seed=${3:-0}
    ensure_safe_file_destination "$dst"
    if [ -e "$dst" ] && [ "$force" -ne 1 ]; then
        if [ "$seed" -eq 1 ]; then
            printf 'skip seed (target exists): %s\n' "$dst"
        else
            printf 'keep existing: %s\n' "$dst"
        fi
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
        if [ "$seed" -eq 1 ]; then
            printf 'overwrite seed (--force): %s\n' "$dst"
        else
            printf 'overwrite (--force): %s\n' "$dst"
        fi
    else
        mv "$tmp" "$dst"
        if [ "$seed" -eq 1 ]; then
            printf 'deliver seed: %s\n' "$dst"
        else
            printf 'write: %s\n' "$dst"
        fi
    fi
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
    sed "s|{{validation_command}}|$escaped_validation_command|g" "$file" >"$tmp"
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
            AGENTS.md | CLAUDE.md | docs/harness/git-hooks/pre-commit | docs/harness/git-hooks/pre-push | target/* | */target/* | build/* | */build/* | bin/* | */bin/* | .gradle/* | */.gradle/* | .factorypath | */.factorypath | .classpath | */.classpath | .project | */.project | .settings/* | */.settings/* | __pycache__/* | */__pycache__/* | *.pyc) continue ;;
            docs/harness/templates/*) copy_file "$src" "$dst_dir/$rel" 1 ;;
            *) copy_file "$src" "$dst_dir/$rel" ;;
        esac
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
    {
        cat "$file"
        printf '\n'
        cat "$template_file"
    } >"$tmp"
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
        cat "$file" >"$tmp"
    else
        : >"$tmp"
    fi
    if [ "$has_agents" -ne 1 ]; then
        if [ -s "$tmp" ]; then
            printf '\n' >>"$tmp"
        fi
        cat "$template_dir/common/AGENTS.md" >>"$tmp"
    fi
    if [ "$has_claude" -ne 1 ] && grep -Fq '## Entry Point' "$tmp"; then
        has_claude=1
    fi
    if [ "$has_agents" -ne 1 ] && grep -Fq '# Repository Harness Contract' "$tmp"; then
        has_agents=1
    fi
    if [ "$has_claude" -ne 1 ]; then
        if [ -s "$tmp" ]; then
            printf '\n' >>"$tmp"
        fi
        cat "$template_dir/common/CLAUDE.md" >>"$tmp"
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
        AGENTS.md:CLAUDE.md | CLAUDE.md:AGENTS.md) printf '%s\n' "$target" ;;
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
    agents_exists=0
    claude_exists=0
    agents_is_symlink=0
    claude_is_symlink=0
    if [ -e AGENTS.md ] || [ -L AGENTS.md ]; then
        agents_exists=1
    fi
    if [ -e CLAUDE.md ] || [ -L CLAUDE.md ]; then
        claude_exists=1
    fi
    if [ -L AGENTS.md ]; then
        agents_is_symlink=1
    fi
    if [ -L CLAUDE.md ]; then
        claude_is_symlink=1
    fi
    agents_target=AGENTS.md
    claude_target=CLAUDE.md
    if [ "$agents_is_symlink" -eq 1 ]; then
        agents_target=$(root_contract_symlink_target AGENTS.md)
    fi
    if [ "$claude_is_symlink" -eq 1 ]; then
        claude_target=$(root_contract_symlink_target CLAUDE.md)
    fi
    if [ "$agents_target" != "$claude_target" ] && [ "$agents_exists" -eq 1 ] && [ "$claude_exists" -eq 1 ]; then
        error "root contract files diverge: AGENTS.md and CLAUDE.md point to different targets; resolve divergent root contract files before install"
    fi
    check_root_contract_conflict "$agents_target" '# Repository Harness Contract'
    check_root_contract_conflict "$claude_target" '# Entry Point'
    if [ "$root_contract_conflicts" -ne 0 ]; then
        error 'root contract conflicts must be resolved before installing harness assets'
    fi
    if [ "$agents_target" = "$claude_target" ]; then
        ensure_shared_root_contract "$agents_target"
    elif [ "$agents_exists" -eq 0 ] && [ "$claude_exists" -eq 0 ]; then
        ensure_root_contract "$claude_target" '# Entry Point' "$template_dir/common/CLAUDE.md"
        ln -s CLAUDE.md AGENTS.md
        printf '%s\n' "create symlink: AGENTS.md -> CLAUDE.md"
    elif [ "$agents_exists" -eq 0 ] && [ "$claude_exists" -eq 1 ]; then
        ensure_root_contract "$claude_target" '# Entry Point' "$template_dir/common/CLAUDE.md"
        ln -s CLAUDE.md AGENTS.md
        printf '%s\n' "create symlink: AGENTS.md -> CLAUDE.md"
    elif [ "$claude_exists" -eq 0 ] && [ "$agents_exists" -eq 1 ]; then
        ensure_root_contract "$agents_target" '# Repository Harness Contract' "$template_dir/common/AGENTS.md"
        ln -s AGENTS.md CLAUDE.md
        printf '%s\n' "create symlink: CLAUDE.md -> AGENTS.md"
    else
        ensure_root_contract "$agents_target" '# Repository Harness Contract' "$template_dir/common/AGENTS.md"
        ensure_root_contract "$claude_target" '# Entry Point' "$template_dir/common/CLAUDE.md"
    fi
}

# Ensure .agents symlink points to .claude when neither exists.
#
# @return Writes symlink creation status.
ensure_agents_symlink() {
    if [ ! -e .agents ] && [ ! -L .agents ]; then
        if [ -d .claude ]; then
            ln -s .claude .agents
            printf '%s\n' "create symlink: .agents -> .claude"
            return 0
        fi
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
            : >"$keep"
            printf '%s\n' "write: $keep"
        fi
    done
}

# Copy stack-specific templates, honoring CI host selection.
#
# @param src_dir Stack template directory path.
# @param dst_dir Target-relative destination directory path.
# @param selected_mode Resolved harness stack mode.
# @return Writes copied or skipped paths.
copy_stack_tree() {
    src_dir=$1
    dst_dir=$2
    selected_mode=$3
    if [ ! -d "$src_dir" ]; then
        return 0
    fi
    find "$src_dir" -type f | while IFS= read -r src; do
        rel=${src#"$src_dir"/}
        case "$rel" in
            target/* | */target/* | build/* | */build/* | bin/* | */bin/* | .gradle/* | */.gradle/* | .ruff_cache/* | */.ruff_cache/* | .factorypath | */.factorypath | .classpath | */.classpath | .project | */.project | .settings/* | */.settings/* | __pycache__/* | */__pycache__/* | *.pyc) continue ;;
            .gitlab-ci.yml)
                case "$ci_host" in
                    gitlab | both) ;;
                    *) continue ;;
                esac
                copy_file "$src" "$dst_dir/$rel"
                render_validation_placeholders "$dst_dir/$rel" "$validation_cmd"
                ;;
            .github/workflows/*)
                case "$ci_host" in
                    github | both) ;;
                    *) continue ;;
                esac
                workflow_name=$(workflow_name_for_mode "$selected_mode")
                copy_file "$src" "$dst_dir/.github/workflows/$workflow_name"
                render_validation_placeholders "$dst_dir/.github/workflows/$workflow_name" "$validation_cmd"
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
    } >"$tmp"
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
        append_line_once "$build_file" 'apply(plugin = "com.ririnto.sinon.harness")' 'apply(plugin = "com.ririnto.sinon.harness")'
    else
        append_line_once "$build_file" "apply plugin: 'com.ririnto.sinon.harness'" "apply plugin: 'com.ririnto.sinon.harness'"
    fi
}

# Return the GitHub workflow file name for the selected stack mode.
#
# @param selected_mode Resolved harness stack mode.
# @exit Exits via 'error' when mode is unsupported.
workflow_name_for_mode() {
    selected_mode=$1
    case "$selected_mode" in
        gradle) printf '%s\n' 'ktlint.yaml' ;;
        maven) printf '%s\n' 'spotless.yaml' ;;
        uv) printf '%s\n' 'ruff.yaml' ;;
        bun) printf '%s\n' 'ultracite.yaml' ;;
        shell) printf '%s\n' 'shellcheck.yaml' ;;
        *) error "[workflow_name] unsupported mode (must be gradle|maven|uv|bun|shell): $selected_mode" ;;
    esac
}

# Print runtime-availability advisories for the selected stack mode.
#
# @param selected_mode Resolved harness stack mode.
# @return Writes one advisory line to standard error per missing runtime, or nothing when present.
runtime_advisory_for_mode() {
    selected_mode=$1
    case "$selected_mode" in
        gradle)
            if [ ! -x ./gradlew ] && ! command -v gradle 2>&1 | grep -q .; then
                printf '%s\n' "[advisory] gradle command not found on PATH; install via SDKMAN (\`sdk install gradle\`), Homebrew (\`brew install gradle\`), or asdf (\`asdf plugin add gradle && asdf install gradle latest\`), or add a Gradle wrapper (\`gradle wrapper\`) so \`./gradlew\` is available before running validation." >&2
            fi
            ;;
        maven)
            if ! command -v mvn 2>&1 | grep -q .; then
                printf '%s\n' "[advisory] mvn command not found on PATH; install via SDKMAN (\`sdk install maven\`), Homebrew (\`brew install maven\`), or your package manager before running validation." >&2
            fi
            ;;
        uv)
            if ! command -v uv 2>&1 | grep -q .; then
                printf '%s\n' "[advisory] uv command not found on PATH; install via the official script (\`curl -LsSf https://astral.sh/uv/install.sh | sh\`) or Homebrew (\`brew install uv\`) before running validation." >&2
            fi
            ;;
        bun)
            if ! command -v bun 2>&1 | grep -q .; then
                printf '%s\n' "[advisory] bun command not found on PATH; install via the official script (\`curl -fsSL https://bun.sh/install | bash\`) or Homebrew (\`brew install oven-sh/bun/bun\`) before running validation." >&2
            fi
            ;;
        shell)
            printf '%s\n' "[advisory] shellcheck is required; install via your OS package manager (\`apt install shellcheck\` on Debian/Ubuntu, \`brew install shellcheck\` on macOS, or your distro's package manager)." >&2
            ;;
        *) error "[runtime_advisory] unsupported mode (must be gradle|maven|uv|bun|shell): $selected_mode" ;;
    esac
}

# Return the validation command for the selected stack mode.
#
# @param selected_mode Resolved harness stack mode.
# @exit Exits via 'error' when mode is unsupported.
validation_command_for_mode() {
    selected_mode=$1
    case "$selected_mode" in
        gradle) printf '%s\n' './gradlew ktlintCheck' ;;
        maven) printf '%s\n' 'mvn verify' ;;
        uv) printf '%s\n' 'uv run scripts/check.py' ;;
        bun) printf '%s\n' 'bun run check' ;;
        shell) printf '%s\n' 'sh scripts/check.sh' ;;
        *) error "[validation_command] unsupported mode (must be gradle|maven|uv|bun|shell): $selected_mode" ;;
    esac
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
        } >"$file"
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
        } >"$file"
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
        */pre-commit) write_new_pre_commit_command_hook "$tmp" "$validation_command" ;;
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

validation_cmd=$(validation_command_for_mode "$mode")
ensure_root_contracts
copy_tree "$template_dir/common" .
ensure_agents_symlink
ensure_gitkeep_paths
copy_stack_tree "$template_dir/$mode" . "$mode"

if [ "$mode" = gradle ]; then
    install_gradle
fi
install_target_hook_templates "$validation_cmd" "$validation_cmd"
printf '\n%s\n' "harness target: $target_root"
printf '%s\n' "harness mode: $mode"
printf '%s\n' "ci-host: $ci_host"
printf '%s\n' "validation command: $validation_cmd"
printf '%s\n' "pre-commit command: $validation_cmd"
runtime_advisory_for_mode "$mode"
