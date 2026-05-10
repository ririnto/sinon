#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# :description: Full harness installer for target repositories.
#     Inspects the target, copies stage-appropriate assets, installs validators
#     and hooks, and runs validation. Non-destructive: reports conflicts instead
#     of overwriting. Every installed artifact can be added, modified, or deleted
#     to fit the target repository.
# :param target_dir: Target repository root (default: current directory).
# :param stage: Target install stage 1-4 (default: 1).
# :param dry_run: Print plan without writing files (default: false).

TARGET_DIR="${1:-.}"
STAGE="${2:-1}"
DRY_RUN="${3:-false}"

CONFIG_PATH="docs/harness/config.json"
HARNESS_ROOT="docs/harness"
SCRIPTS_ROOT="scripts/harness"
ASSETS_DIR="$(CDPATH= cd "$(dirname "$0")" && pwd)"
TARGET_DIR="$(CDPATH= cd "$TARGET_DIR" && pwd)"

# :description: Return the configured CI provider from a config file.
# :param config_file: Harness config path to inspect.
read_ci_provider() {
    config_file="$1"
    if ! [ -f "$config_file" ]; then
        return 0
    fi
    while IFS= read -r line; do
        case "$line" in
            *'"provider"'*)
                provider_value="${line#*:}"
                provider_value="$(printf '%s' "$provider_value" | tr -d ' ",' )"
                printf '%s\n' "$provider_value"
                return 0
                ;;
        esac
    done < "$config_file"
}

# :description: Install exactly the CI surface selected by the harness config.
install_configured_ci() {
    provider="$(read_ci_provider "${TARGET_DIR}/${CONFIG_PATH}")"
    if [ -z "$provider" ]; then
        provider="$(read_ci_provider "${ASSETS_DIR}/config.json")"
    fi
    case "$provider" in
        github-actions)
            ensure_dir ".github/workflows"
            install_no_overwrite "${ASSETS_DIR}/github-actions.yml" ".github/workflows/harness-checks.yml"
            ;;
        gitlab-ci)
            install_no_overwrite "${ASSETS_DIR}/gitlab-ci.yml" ".gitlab-ci.yml"
            ;;
        none | disabled | "")
            printf '  skip   CI templates (provider: %s)\n' "${provider:-none}" >&2
            ;;
        *)
            echo "error: unsupported CI provider: $provider" >&2
            exit 1
            ;;
    esac
}

# :description: Print a step header to stderr.
step() {
    printf '\n==> %s\n' "$1" >&2
}

# :description: Check whether a path exists in the target dir.
target_exists() {
    [ -e "${TARGET_DIR}/${1}" ] || [ -L "${TARGET_DIR}/${1}" ]
}

# :description: Refuse absolute paths and parent traversal before target writes.
# :param relative_path: Target-owned repository-relative path.
reject_unsafe_relative_path() {
    _path="$1"
    case "$_path" in
        "" | /* | .. | ../* | */.. | */../*)
            echo "error: unsafe target path: $_path" >&2
            exit 1
            ;;
    esac
}

# :description: Refuse existing symlink directory components before mkdir or copy.
# :param relative_dir: Target-owned repository-relative directory path.
assert_safe_directory_path() {
    _dir="$1"
    reject_unsafe_relative_path "$_dir"
    if [ "$_dir" = "." ]; then
        return 0
    fi
    _remaining="$_dir"
    _current=""
    while [ -n "$_remaining" ]; do
        case "$_remaining" in
            */*)
                _part="${_remaining%%/*}"
                _remaining="${_remaining#*/}"
                ;;
            *)
                _part="$_remaining"
                _remaining=""
                ;;
        esac
        if [ -z "$_part" ] || [ "$_part" = "." ]; then
            continue
        fi
        _current="${_current:+$_current/}$_part"
        if [ -L "${TARGET_DIR}/$_current" ]; then
            echo "error: refusing symlink directory component: $_current" >&2
            exit 1
        fi
        if [ -e "${TARGET_DIR}/$_current" ] && ! [ -d "${TARGET_DIR}/$_current" ]; then
            echo "error: directory path component is not a directory: $_current" >&2
            exit 1
        fi
    done
}

# :description: Refuse unsafe file destinations before copy, chmod, or symlink writes.
# :param relative_path: Target-owned repository-relative file path.
assert_safe_file_destination() {
    _path="$1"
    reject_unsafe_relative_path "$_path"
    if [ -L "${TARGET_DIR}/$_path" ]; then
        echo "error: refusing symlink file destination: $_path" >&2
        exit 1
    fi
    if [ -d "${TARGET_DIR}/$_path" ]; then
        echo "error: refusing directory file destination: $_path" >&2
        exit 1
    fi
    _parent_dir="$(dirname "$_path")"
    assert_safe_directory_path "$_parent_dir"
}

# :description: Create a target directory after checking for symlink components.
# :param relative_dir: Target-owned repository-relative directory path.
safe_mkdir_p() {
    _dir="$1"
    assert_safe_directory_path "$_dir"
    mkdir -p "${TARGET_DIR}/$_dir"
}

# :description: Copy an asset atomically after checking file and parent destinations.
# :param source_path: Source file path.
# :param relative_path: Target-owned repository-relative destination path.
copy_file_safe() {
    _src="$1"
    _dst="$2"
    assert_safe_file_destination "$_dst"
    safe_mkdir_p "$(dirname "$_dst")"
    _tmp="${TARGET_DIR}/$(dirname "$_dst")/.harness-tmp-$$-$(basename "$_dst")"
    rm -f "$_tmp"
    cp "$_src" "$_tmp"
    if [ -L "${TARGET_DIR}/$_dst" ] || [ -d "${TARGET_DIR}/$_dst" ]; then
        rm -f "$_tmp"
        echo "error: refusing unsafe file destination: $_dst" >&2
        exit 1
    fi
    mv -f "$_tmp" "${TARGET_DIR}/$_dst"
}

# :description: Copy an asset atomically with executable mode set before rename.
# :param source_path: Source file path.
# :param relative_path: Target-owned repository-relative destination path.
copy_executable_file_safe() {
    _src="$1"
    _dst="$2"
    assert_safe_file_destination "$_dst"
    safe_mkdir_p "$(dirname "$_dst")"
    _tmp="${TARGET_DIR}/$(dirname "$_dst")/.harness-tmp-$$-$(basename "$_dst")"
    rm -f "$_tmp"
    cp "$_src" "$_tmp"
    chmod +x "$_tmp"
    if [ -L "${TARGET_DIR}/$_dst" ] || [ -d "${TARGET_DIR}/$_dst" ]; then
        rm -f "$_tmp"
        echo "error: refusing unsafe file destination: $_dst" >&2
        exit 1
    fi
    mv -f "$_tmp" "${TARGET_DIR}/$_dst"
}

# :description: Copy an asset to the target only if the target does not exist.
install_no_overwrite() {
    _src="$1"
    _dst="$2"
    assert_safe_file_destination "$_dst"
    if target_exists "$_dst"; then
        printf '  skip   %s (exists)\n' "$_dst" >&2
        return 0
    fi
    if [ "$DRY_RUN" = "true" ]; then
        printf '  create  %s (dry-run)\n' "$_dst" >&2
        return 0
    fi
    copy_file_safe "$_src" "$_dst"
    printf '  create  %s\n' "$_dst" >&2
}

# :description: Copy an executable asset to the target only if it does not exist.
# :param source_path: Source file path.
# :param relative_path: Target-owned repository-relative destination path.
install_executable_no_overwrite() {
    _src="$1"
    _dst="$2"
    assert_safe_file_destination "$_dst"
    if target_exists "$_dst"; then
        printf '  skip   %s (exists)\n' "$_dst" >&2
        return 0
    fi
    if [ "$DRY_RUN" = "true" ]; then
        printf '  create  %s (dry-run)\n' "$_dst" >&2
        return 0
    fi
    copy_executable_file_safe "$_src" "$_dst"
    printf '  create  %s\n' "$_dst" >&2
}

# :description: Ensure a target directory exists.
ensure_dir() {
    assert_safe_directory_path "$1"
    if target_exists "$1"; then
        return 0
    fi
    if [ "$DRY_RUN" = "true" ]; then
        printf '  mkdir   %s (dry-run)\n' "$1" >&2
        return 0
    fi
    safe_mkdir_p "$1"
    printf '  mkdir   %s\n' "$1" >&2
}

# :description: Create the compatibility AGENTS.md symlink without replacing anything.
create_agent_context_alias() {
    if target_exists "AGENTS.md"; then
        return 0
    fi
    assert_safe_file_destination "AGENTS.md"
    if [ "$DRY_RUN" = "true" ]; then
        printf '  symlink AGENTS.md -> CLAUDE.md (dry-run)\n' >&2
        return 0
    fi
    (cd "$TARGET_DIR" && ln -s CLAUDE.md AGENTS.md)
    printf '  symlink AGENTS.md -> CLAUDE.md\n' >&2
}

step "Inspecting target repository"

case "$STAGE" in
    1 | 2 | 3 | 4) ;;
    *)
        echo "error: stage must be 1, 2, 3, or 4" >&2
        exit 1
        ;;
esac

if ! [ -d "${TARGET_DIR}/.git" ]; then
    echo "error: ${TARGET_DIR} is not inside a git repository" >&2
    exit 1
fi

if target_exists "$CONFIG_PATH"; then
    echo "Found existing config at ${CONFIG_PATH}"
else
    echo "No existing config - will install from asset template"
fi

step "Stage ${STAGE}: Installing harness assets"

if [ "$STAGE" -ge 1 ]; then
    step "Installing config and root instruction docs"

    ensure_dir "$HARNESS_ROOT"
    install_no_overwrite "${ASSETS_DIR}/config.json" "$CONFIG_PATH"
    install_no_overwrite "${ASSETS_DIR}/templates/CLAUDE.md" "CLAUDE.md"

    create_agent_context_alias

    ensure_dir "docs/design-docs"
    ensure_dir "docs/exec-plans/active"
    ensure_dir "docs/exec-plans/completed"
    ensure_dir "docs/generated"
    ensure_dir "docs/product-specs"
    ensure_dir "docs/references"

    install_no_overwrite "${ASSETS_DIR}/templates/ARCHITECTURE.md" "ARCHITECTURE.md"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/harness/guardrails.md" "docs/harness/guardrails.md"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/harness/readiness.md" "docs/harness/readiness.md"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/harness/updates.md" "docs/harness/updates.md"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/harness/known-violations.md" "docs/harness/known-violations.md"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/.gitignore" "docs/.gitignore"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/design-docs/index.md" "docs/design-docs/index.md"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/design-docs/core-beliefs.md" "docs/design-docs/core-beliefs.md"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/exec-plans/active/.gitkeep" "docs/exec-plans/active/.gitkeep"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/exec-plans/completed/.gitkeep" "docs/exec-plans/completed/.gitkeep"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/exec-plans/tech-debt-tracker.md" "docs/exec-plans/tech-debt-tracker.md"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/generated/db-schema.md" "docs/generated/db-schema.md"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/product-specs/index.md" "docs/product-specs/index.md"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/product-specs/new-user-onboarding.md" "docs/product-specs/new-user-onboarding.md"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/references/design-system-reference-llms.txt" "docs/references/design-system-reference-llms.txt"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/references/nixpacks-llms.txt" "docs/references/nixpacks-llms.txt"
    install_no_overwrite "${ASSETS_DIR}/templates/docs/references/uv-llms.txt" "docs/references/uv-llms.txt"

    for _topic in DESIGN FRONTEND PLANS PRODUCT_SENSE QUALITY_SCORE RELIABILITY SECURITY; do
        install_no_overwrite "${ASSETS_DIR}/templates/docs/${_topic}.md" "docs/${_topic}.md"
    done

    ensure_dir "$SCRIPTS_ROOT"
    SCRIPTS_SOURCE="$(CDPATH= cd "$(dirname "$0")/../scripts" && pwd)"
    install_no_overwrite "${SCRIPTS_SOURCE}/validate_harness.py" "$SCRIPTS_ROOT/validate_harness.py"
    install_executable_no_overwrite "${SCRIPTS_SOURCE}/validate_harness.sh" "$SCRIPTS_ROOT/validate_harness.sh"
fi

if [ "$STAGE" -ge 2 ]; then
    step "Installing git hook installer"
    install_executable_no_overwrite "${ASSETS_DIR}/setup-hooks.sh" "$SCRIPTS_ROOT/setup-hooks.sh"
fi

if [ "$STAGE" -ge 3 ]; then
    step "Installing CI templates"
    install_configured_ci
fi

if [ "$STAGE" -ge 4 ]; then
    step "Installing target-owned agent scaffolds"
    ensure_dir ".claude/agents"
    for agent_src in "${ASSETS_DIR}/agents"/*.md; do
        if [ -f "$agent_src" ]; then
            _agent_name="$(basename "$agent_src")"
            install_no_overwrite "$agent_src" ".claude/agents/${_agent_name}"
        fi
    done
fi

step "Installation summary"

echo "" >&2
echo "Harness installation complete for stage ${STAGE}." >&2
echo "Config: ${TARGET_DIR}/${CONFIG_PATH}" >&2
echo "Validator: sh ${SCRIPTS_ROOT}/validate_harness.sh" >&2
echo "" >&2
echo "Every installed file can be added, modified, or deleted to fit" >&2
echo "the target repository. The harness config is the source of truth" >&2
echo "for all paths, commands, gates, and absence policies." >&2
echo "" >&2

if [ "$DRY_RUN" != "true" ] && [ -x "${TARGET_DIR}/${SCRIPTS_ROOT}/validate_harness.sh" ]; then
    step "Running validation"
    (cd "$TARGET_DIR" && sh "${SCRIPTS_ROOT}/validate_harness.sh") || {
        echo "warning: validation reported findings (non-blocking)" >&2
    }
fi

echo "Done." >&2
