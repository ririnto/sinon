#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${CLAUDE_PLUGIN_ROOT:-$(CDPATH='' cd "$(dirname "$0")/.." && pwd)}
SELF_CHECK_DIR=$root/scripts/plugin-self-check

# shellcheck source=plugins/harness/scripts/plugin-self-check/common.sh
. "$SELF_CHECK_DIR/common.sh"
# shellcheck source=plugins/harness/scripts/plugin-self-check/asset-checks.sh
. "$SELF_CHECK_DIR/asset-checks.sh"
# shellcheck source=plugins/harness/scripts/plugin-self-check/ci-checks.sh
. "$SELF_CHECK_DIR/ci-checks.sh"
# shellcheck source=plugins/harness/scripts/plugin-self-check/policy-checks.sh
. "$SELF_CHECK_DIR/policy-checks.sh"

printf 'Validating harness plugin native-lint end-state...\n' >&2

printf '\n--- common assets ---\n' >&2
common_assets=$root/skills/harness-install/assets/common
assert_common_assets_rendered_validation_command "$common_assets"
require_file "$common_assets/.editorconfig"
require_text "$common_assets/.editorconfig" '[{*.json,*.jsonc,*.yaml,*.yml,*.js,*.jsx,*.mjs,*.cjs,*.ts,*.tsx,*.md,*.markdown}]'
require_text "$common_assets/.editorconfig" 'indent_size = 2'
printf '[common assets] OK\n' >&2

printf '\n--- gradle stack ---\n' >&2
assert_gradle_assets
gradle_assets=$root/skills/harness-install/assets/gradle
if [ -f "$gradle_assets/.github/workflows/ktlint.yaml" ]; then
    assert_github_workflow_command "$gradle_assets/.github/workflows/ktlint.yaml" "./gradlew ktlintCheck"
fi
if [ -f "$gradle_assets/.gitlab-ci.yml" ]; then
    assert_gitlab_ci_command "$gradle_assets/.gitlab-ci.yml" "ktlint" "./gradlew ktlintCheck"
fi
reject_file_text "$gradle_assets" "harness-check"
reject_file_text "$gradle_assets" "manifest.json"

printf '\n--- maven stack ---\n' >&2
assert_maven_assets
maven_assets=$root/skills/harness-install/assets/maven
if [ -f "$maven_assets/.github/workflows/spotless.yaml" ]; then
    require_text "$maven_assets/.github/workflows/spotless.yaml" "root=\$(pwd -P)"
    require_text "$maven_assets/.github/workflows/spotless.yaml" 'git ls-files -- "*.java"'
    require_text "$maven_assets/.github/workflows/spotless.yaml" 'Java path contains comma'
    require_text "$maven_assets/.github/workflows/spotless.yaml" './mvnw validate -DspotlessFiles'
    require_text "$maven_assets/.github/workflows/spotless.yaml" 'spotlessFiles'
    reject_file_contains "$maven_assets/.github/workflows/spotless.yaml" './mvnw verify'
    reject_file_contains "$maven_assets/.github/workflows/spotless.yaml" 's#^#.*#'
    reject_file_contains "$maven_assets/.github/workflows/spotless.yaml" '>-'
    require_text "$maven_assets/.github/workflows/spotless.yaml" 'run: |-'
    printf '[GitHub workflow spotless.yaml] command OK\n' >&2
fi
if [ -f "$maven_assets/.gitlab-ci.yml" ]; then
    require_text "$maven_assets/.gitlab-ci.yml" 'spotless:'
    require_text "$maven_assets/.gitlab-ci.yml" "root=\$(pwd -P)"
    require_text "$maven_assets/.gitlab-ci.yml" 'git ls-files -- "*.java"'
    require_text "$maven_assets/.gitlab-ci.yml" 'Java path contains comma'
    require_text "$maven_assets/.gitlab-ci.yml" './mvnw validate -DspotlessFiles'
    require_text "$maven_assets/.gitlab-ci.yml" 'spotlessFiles'
    reject_file_contains "$maven_assets/.gitlab-ci.yml" './mvnw verify'
    reject_file_contains "$maven_assets/.gitlab-ci.yml" 's#^#.*#'
    reject_file_contains "$maven_assets/.gitlab-ci.yml" '>-'
    require_text "$maven_assets/.gitlab-ci.yml" '- |-'
    printf '[GitLab CI] spotless job command OK\n' >&2
fi
reject_file_text "$maven_assets" "harness-check"
reject_file_text "$maven_assets" "manifest.json"

printf '\n--- bun stack ---\n' >&2
assert_bun_assets
bun_assets=$root/skills/harness-install/assets/bun
if [ -f "$bun_assets/.github/workflows/ultracite.yaml" ]; then
    assert_github_workflow_command "$bun_assets/.github/workflows/ultracite.yaml" "bun run check"
fi
if [ -f "$bun_assets/.gitlab-ci.yml" ]; then
    assert_gitlab_ci_command "$bun_assets/.gitlab-ci.yml" "ultracite" "bun run check"
fi
reject_file_text "$bun_assets" "harness-check"
reject_file_text "$bun_assets" "manifest.json"

printf '\n--- uv stack ---\n' >&2
assert_uv_assets
uv_assets=$root/skills/harness-install/assets/uv
if [ -f "$uv_assets/.github/workflows/ruff.yaml" ]; then
    assert_github_workflow_command "$uv_assets/.github/workflows/ruff.yaml" "uv run scripts/check.py"
fi
if [ -f "$uv_assets/.gitlab-ci.yml" ]; then
    assert_gitlab_ci_command "$uv_assets/.gitlab-ci.yml" "ruff" "uv run scripts/check.py"
fi
reject_file_text "$uv_assets" "harness-check"
reject_file_text "$uv_assets" "manifest.json"

printf '\n--- shell stack ---\n' >&2
assert_shell_assets
shell_assets=$root/skills/harness-install/assets/shell
if [ -f "$shell_assets/.github/workflows/shellcheck.yaml" ]; then
    assert_github_workflow_command "$shell_assets/.github/workflows/shellcheck.yaml" "sh scripts/check.sh"
    require_text "$shell_assets/.github/workflows/shellcheck.yaml" 'shellcheck shfmt'
fi
if [ -f "$shell_assets/.gitlab-ci.yml" ]; then
    assert_gitlab_ci_command "$shell_assets/.gitlab-ci.yml" "shellcheck" "sh scripts/check.sh"
    require_text "$shell_assets/.gitlab-ci.yml" 'shellcheck shfmt'
fi
reject_file_text "$shell_assets" "harness-check"
reject_file_text "$shell_assets" "manifest.json"

printf '\n--- harness markdown discovery ---\n' >&2
require_text "$root/scripts/check.sh" "git -C"
require_text "$root/scripts/check.sh" "ls-files -z -- '*.md'"
require_text "$root/scripts/check.sh" "xargs -0"
reject_file_contains "$root/scripts/check.sh" "find "
reject_file_contains "$root/scripts/check.sh" "\"**/*.md\""
require_text "$root/scripts/check.sh" "Repository validation passed."
reject_file_contains "$root/scripts/check.sh" "Checked %d file(s)"
reject_file_contains "$root/scripts/check.sh" "%d error(s)"
require_text "$root/scripts/fix.sh" "git -C"
require_text "$root/scripts/fix.sh" "ls-files -z -- '*.md'"
require_text "$root/scripts/fix.sh" "xargs -0"
reject_file_contains "$root/scripts/fix.sh" "find "
reject_file_contains "$root/scripts/fix.sh" "\"**/*.md\""
require_text "$root/scripts/fix.sh" "fixed files:"
reject_file_contains "$root/scripts/fix.sh" "unchanged files were left as-is."
reject_file_contains "$root/scripts/fix.sh" "fixed: %d"
reject_file_contains "$root/scripts/fix.sh" "no-op: %d"
printf '[harness markdown discovery] OK\n' >&2

printf '\n--- exec-plan reference policy ---\n' >&2
assert_exec_plan_reference_policy "skills/harness-install/assets"
printf '[rejection] exec-plan path policy OK\n' >&2

printf '\n--- split module package surface ---\n' >&2
self_check_modules=$root/scripts/plugin-self-check
installer_modules=$root/skills/harness-install/scripts/install_harness
require_file "$self_check_modules/common.sh"
require_file "$self_check_modules/asset-checks.sh"
require_file "$self_check_modules/ci-checks.sh"
require_file "$self_check_modules/policy-checks.sh"
require_file "$installer_modules/__init__.py"
require_file "$installer_modules/advisory.py"
require_file "$installer_modules/cli.py"
require_file "$installer_modules/commands.py"
require_file "$installer_modules/contracts.py"
require_file "$installer_modules/errors.py"
require_file "$installer_modules/hooks.py"
require_file "$installer_modules/installer.py"
require_file "$installer_modules/models.py"
require_file "$installer_modules/operations.py"
require_file "$installer_modules/paths.py"
require_file "$installer_modules/planning.py"
require_file "$installer_modules/preview.py"
printf '[split module package surface] OK\n' >&2

printf '\n--- source size policy ---\n' >&2
assert_source_size_policy "."
printf '[source size policy] OK\n' >&2

printf '\n--- Python installer surface ---\n' >&2
require_file "$root/skills/harness-install/scripts/install-harness.py"
require_text "$root/skills/harness-install/scripts/install-harness.py" '#!/usr/bin/env -S uv run --script'
require_text "$root/skills/harness-install/scripts/install-harness.py" '# /// script'
require_text "$root/skills/harness-install/scripts/install-harness.py" 'requires-python'
require_text "$root/skills/harness-install/scripts/install_harness/cli.py" 'def parse_args'
require_text "$root/skills/harness-install/scripts/install_harness/contracts.py" 'if not self.config.force:'
require_text "$root/skills/harness-install/scripts/install_harness/contracts.py" 'root contract conflicts must be resolved before installing harness assets'
require_text "$root/skills/harness-install/scripts/install_harness/preview.py" 'def preview_install_set'
require_text "$root/skills/harness-install/scripts/install_harness/preview.py" 'def show_one_target_path'
require_text "$root/skills/harness-install/scripts/install_harness/operations.py" 'def install_one_target_path'
require_text "$root/skills/harness-install/scripts/install_harness/models.py" '{{validation_command}}'
reject_file_contains "$root/skills/harness-install/scripts/install-harness.py" 'install-harness.sh'
assert_code_style_contracts \
    "$root/skills/harness-install/scripts/install-harness.py" \
    "$root/skills/harness-install/assets/bun/scripts/tsdoc-plugin.ts"
assert_code_style_contracts \
    "$root/skills/harness-install/scripts/install_harness" \
    "$root/skills/harness-install/assets/bun/scripts/tsdoc-plugin.ts"
printf '[Python installer surface] OK\n' >&2

printf '\n--- manifest/bespoke rejection ---\n' >&2
reject_file_text "$root/skills/harness-install/assets" "manifest.json"
reject_file_text "$root/skills/harness-install/assets" "harness-check"
reject_file_text "$root/skills/harness-install/assets" "leafFunctionBlankLines"
printf '[rejection] No manifest/bespoke references in assets OK\n' >&2

printf '\n--- native tool smoke tests ---\n' >&2
smoke_test_tool "bunx" "bunx --version"
smoke_test_tool "uv" "uv --version"
smoke_test_tool "shellcheck" "shellcheck --version"
smoke_test_tool "shfmt" "shfmt --version"

printf '\nAll checks passed.\n' >&2
