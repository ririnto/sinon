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
assert_common_assets_structure "$common_assets"
require_file "$common_assets/.editorconfig"
require_text "$common_assets/.editorconfig" '[{*.json,*.jsonc,*.yaml,*.yml,*.js,*.jsx,*.mjs,*.cjs,*.ts,*.tsx,*.md,*.markdown}]'
require_text "$common_assets/.editorconfig" '[{*.bash,*.sh,*.zsh}]'
require_text "$common_assets/.editorconfig" 'indent_size = 2'
require_text "$common_assets/.editorconfig" 'charset = utf-8'
require_text "$common_assets/.editorconfig" 'ij_continuation_indent_size = 4'
require_text "$common_assets/docs/.editorconfig" 'charset = utf-8'
printf '[common assets] OK\n' >&2

printf '\n--- gradle stack ---\n' >&2
assert_gradle_assets
gradle_assets=$root/skills/harness-install/assets/gradle
if [ -f "$gradle_assets/.github/workflows/ktlint.yaml" ]; then
  assert_github_workflow_command "$gradle_assets/.github/workflows/ktlint.yaml" "./gradlew ktlintCheck"
  assert_github_workflow_action "$gradle_assets/.github/workflows/ktlint.yaml" 'actions/checkout@v7'
  assert_github_workflow_action "$gradle_assets/.github/workflows/ktlint.yaml" 'gradle/actions/setup-gradle@v6'
fi
if [ -f "$gradle_assets/.gitlab-ci.yml" ]; then
  assert_gitlab_ci_command "$gradle_assets/.gitlab-ci.yml" "ktlint" "./gradlew ktlintCheck"
fi

printf '\n--- maven stack ---\n' >&2
assert_maven_assets
maven_assets=$root/skills/harness-install/assets/maven
if [ -f "$maven_assets/.github/workflows/spotless.yaml" ]; then
  assert_github_workflow_action "$maven_assets/.github/workflows/spotless.yaml" 'actions/checkout@v7'
  assert_github_workflow_action "$maven_assets/.github/workflows/spotless.yaml" 'actions/setup-java@v5'
  require_text "$maven_assets/.github/workflows/spotless.yaml" "root=\$(pwd -P)"
  require_text "$maven_assets/.github/workflows/spotless.yaml" 'git ls-files -- "*.java"'
  require_text "$maven_assets/.github/workflows/spotless.yaml" 'comma Java path'
  require_text "$maven_assets/.github/workflows/spotless.yaml" 'escaped Java path'
  require_text "$maven_assets/.github/workflows/spotless.yaml" './mvnw validate -DspotlessFiles'
  require_text "$maven_assets/.github/workflows/spotless.yaml" 'spotlessFiles'
  require_text "$maven_assets/.github/workflows/spotless.yaml" 'run: |-'
  printf '[GitHub workflow spotless.yaml] command OK\n' >&2
fi
if [ -f "$maven_assets/.gitlab-ci.yml" ]; then
  require_text "$maven_assets/.gitlab-ci.yml" 'spotless:'
  require_text "$maven_assets/.gitlab-ci.yml" "root=\$(pwd -P)"
  require_text "$maven_assets/.gitlab-ci.yml" 'git ls-files -- "*.java"'
  require_text "$maven_assets/.gitlab-ci.yml" 'comma Java path'
  require_text "$maven_assets/.gitlab-ci.yml" 'escaped Java path'
  require_text "$maven_assets/.gitlab-ci.yml" './mvnw validate -DspotlessFiles'
  require_text "$maven_assets/.gitlab-ci.yml" 'spotlessFiles'
  require_text "$maven_assets/.gitlab-ci.yml" '- |-'
  printf '[GitLab CI] spotless job command OK\n' >&2
fi

printf '\n--- bun stack ---\n' >&2
assert_bun_assets
bun_assets=$root/skills/harness-install/assets/bun
if [ -f "$bun_assets/.github/workflows/ultracite.yaml" ]; then
  assert_github_workflow_command "$bun_assets/.github/workflows/ultracite.yaml" "bun run check"
  assert_github_workflow_action "$bun_assets/.github/workflows/ultracite.yaml" 'actions/checkout@v7'
  assert_github_workflow_action "$bun_assets/.github/workflows/ultracite.yaml" 'oven-sh/setup-bun@v2'
fi
if [ -f "$bun_assets/.gitlab-ci.yml" ]; then
  assert_gitlab_ci_command "$bun_assets/.gitlab-ci.yml" "ultracite" "bun run check"
fi

printf '\n--- uv stack ---\n' >&2
assert_uv_assets
uv_assets=$root/skills/harness-install/assets/uv
if [ -f "$uv_assets/.github/workflows/ruff.yaml" ]; then
  assert_github_workflow_command "$uv_assets/.github/workflows/ruff.yaml" "uv run scripts/check.py"
  assert_github_workflow_action "$uv_assets/.github/workflows/ruff.yaml" 'actions/checkout@v7'
  assert_github_workflow_action "$uv_assets/.github/workflows/ruff.yaml" 'astral-sh/setup-uv@v8.2.0'
fi
if [ -f "$uv_assets/.gitlab-ci.yml" ]; then
  assert_gitlab_ci_command "$uv_assets/.gitlab-ci.yml" "ruff" "uv run scripts/check.py"
fi

printf '\n--- shell stack ---\n' >&2
assert_shell_assets
shell_assets=$root/skills/harness-install/assets/shell
if [ -f "$shell_assets/.github/workflows/shellcheck.yaml" ]; then
  assert_github_workflow_command "$shell_assets/.github/workflows/shellcheck.yaml" "sh scripts/check.sh"
  assert_github_workflow_action "$shell_assets/.github/workflows/shellcheck.yaml" 'actions/checkout@v7'
  require_text "$shell_assets/.github/workflows/shellcheck.yaml" 'shellcheck shfmt'
fi
if [ -f "$shell_assets/.gitlab-ci.yml" ]; then
  assert_gitlab_ci_command "$shell_assets/.gitlab-ci.yml" "shellcheck" "sh scripts/check.sh"
  require_text "$shell_assets/.gitlab-ci.yml" 'shellcheck shfmt'
fi

printf '\n--- harness markdown discovery ---\n' >&2
require_text "$root/scripts/check.sh" "git -C"
require_text "$root/scripts/check.sh" "ls-files -z -- '*.md'"
require_text "$root/scripts/check.sh" "xargs -0 \"\$markdownlint_bin\" <\"\$markdown_file_list\""
require_text "$root/scripts/check.sh" "check_plugin_packages"
require_text "$root/scripts/check.sh" "run_check_job check_python_lint"
require_text "$root/scripts/check.sh" "ruff>=0.15.18,<0.16.0"
require_text "$root/scripts/check.sh" "ruff check ."
require_text "$root/scripts/check.sh" "Repository validation passed."
require_text "$root/scripts/fix.sh" "git -C"
require_text "$root/scripts/fix.sh" "ls-files -z -- '*.md'"
require_text "$root/scripts/fix.sh" "xargs -0 \"\$markdownlint_bin\" --fix <\"\$markdown_file_list\""
require_text "$root/scripts/fix.sh" "fixed files:"
require_text "$root/scripts/fix.sh" "run_fix_job fix_python_files"
require_text "$root/scripts/fix.sh" "ruff>=0.15.18,<0.16.0"
require_text "$root/scripts/fix.sh" "ruff check --fix ."
printf '[harness markdown discovery] OK\n' >&2

printf '\n--- plugin self-check package surface ---\n' >&2
self_check_modules=$root/scripts/plugin-self-check
require_file "$self_check_modules/common.sh"
require_file "$self_check_modules/asset-checks.sh"
require_file "$self_check_modules/asset_checks_settings.py"
require_file "$self_check_modules/ci-checks.sh"
require_file "$self_check_modules/package-checks.py"
require_file "$self_check_modules/package_checks_common.py"
require_file "$self_check_modules/package_checks_inventory.py"
require_file "$self_check_modules/package_checks_manifest.py"
require_file "$self_check_modules/policy-checks.sh"
printf '[plugin self-check package surface] OK\n' >&2

printf '\n--- Python installer surface ---\n' >&2
require_file "$root/skills/harness-install/scripts/install-harness.py"
require_text "$root/skills/harness-install/scripts/install-harness.py" '#!/usr/bin/env -S uv run --script'
require_text "$root/skills/harness-install/scripts/install-harness.py" '# /// script'
require_text "$root/skills/harness-install/scripts/install-harness.py" 'requires-python'
require_dir "$root/skills/harness-install/scripts/install_harness"
assert_code_style_contracts \
  "$root/skills/harness-install/scripts/install-harness.py" \
  "$root/skills/harness-install/assets/bun/scripts/tsdoc-plugin.ts"
assert_code_style_contracts \
  "$root/skills/harness-install/scripts/install_harness" \
  "$root/skills/harness-install/assets/bun/scripts/tsdoc-plugin.ts"
printf '[Python installer surface] OK\n' >&2

printf '\n--- native tool smoke tests ---\n' >&2
smoke_test_tool "bun"
smoke_test_tool "uv"
smoke_test_tool "shellcheck"
smoke_test_tool "shfmt"

printf '\nAll checks passed.\n' >&2
