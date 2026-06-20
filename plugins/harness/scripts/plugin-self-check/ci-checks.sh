#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Require CI workflow file contains correct check command.
#
# @param workflow_file Path to GitHub workflow file.
# @param expected_command Expected validation command.
assert_github_workflow_command() {
  workflow_file=$1
  expected_command=$2
  require_text "$workflow_file" "run: $expected_command"
  printf '[GitHub workflow %s] command OK\n' "$(basename "$workflow_file")" >&2
}

# Require GitHub workflow uses the expected action ref.
#
# @param workflow_file Path to GitHub workflow file.
# @param expected_action Expected action reference.
assert_github_workflow_action() {
  workflow_file=$1
  expected_action=$2
  expected_line="      - uses: $expected_action"
  if ! grep -Fxq "$expected_line" "$workflow_file"; then
    printf '[GitHub workflow %s] missing action %s\n' "$(basename "$workflow_file")" "$expected_action" >&2
    exit 1
  fi
  printf '[GitHub workflow %s] action %s OK\n' "$(basename "$workflow_file")" "$expected_action" >&2
}

# Require GitLab CI file contains correct check command and job name.
#
# @param ci_file Path to .gitlab-ci.yml file.
# @param expected_job_name Expected job name.
# @param expected_command Expected validation command.
assert_gitlab_ci_command() {
  ci_file=$1
  expected_job_name=$2
  expected_command=$3
  require_text "$ci_file" "$expected_job_name:"
  require_text "$ci_file" "- $expected_command"
  printf '[GitLab CI] %s job command OK\n' "$expected_job_name" >&2
}
