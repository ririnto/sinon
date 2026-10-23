#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

script_dir=$(CDPATH='' cd -- "$(dirname "$0")" && pwd)
lombok_override_jar="${JAVA_ASSISTANT_LOMBOK_JAR:-}"
lombok_support_enabled="${JAVA_ASSISTANT_LOMBOK_ENABLED:-true}"
lombok_project_jar_enabled="${JAVA_ASSISTANT_LOMBOK_PROJECT_JAR_ENABLED:-false}"

# Check whether a path contains no whitespace or unsafe characters.
#
# @param path Filesystem path to validate.
# @return 0 if safe, 1 if whitespace or unsafe characters found.
is_safe_path_token() {
  case "$1" in
  *" "* | *"	"* | *"
"*)
    false
    ;;
  *)
    true
    ;;
  esac
}

# Check whether Lombok support is enabled from environment.
#
# @return 0 if enabled, 1 if explicitly disabled.
is_lombok_support_enabled() {
  case "${lombok_support_enabled}" in
  0 | false | FALSE | False | off | OFF | Off | no | NO | No)
    false
    ;;
  *)
    true
    ;;
  esac
}

# Check whether project-local Lombok jar discovery is enabled.
#
# @return 0 if enabled, 1 unless explicitly enabled.
is_project_lombok_jar_enabled() {
  case "${lombok_project_jar_enabled}" in
  1 | true | TRUE | True | on | ON | On | yes | YES | Yes)
    true
    ;;
  *)
    false
    ;;
  esac
}

# Walk up from PWD to find the project root directory.
#     Looks for build-system marker files (pom.xml, build.gradle, etc.).
#
# @return Prints the project root path and returns 0, or returns 1 if not found.
find_project_root() {
  search_dir="${PWD}"
  root_status=1
  while [ "${search_dir}" != "/" ] && [ "${root_status}" -ne 0 ]; do
    if [ -f "${search_dir}/pom.xml" ] || [ -f "${search_dir}/build.gradle" ] || [ -f "${search_dir}/build.gradle.kts" ] || [ -f "${search_dir}/.classpath" ] || [ -f "${search_dir}/.factorypath" ] || [ -f "${search_dir}/settings.gradle" ] || [ -f "${search_dir}/settings.gradle.kts" ] || [ -f "${search_dir}/gradle/libs.versions.toml" ]; then
      printf '%s\n' "${search_dir}"
      root_status=0
    else
      search_dir=$(dirname "${search_dir}")
    fi
  done
  return "${root_status}"
}

# Resolve the project Lombok jar path using has-lombok.sh.
#
# @param project_root Project root directory to scan.
# @return Prints the resolved jar path, or nothing if not found.
resolve_project_lombok_jar() {
  project_lombok_jar=$("${script_dir}/has-lombok.sh" --resolve-project-jar "$1") || true
  printf '%s\n' "${project_lombok_jar}"
}

#
# Remove any existing -javaagent:*lombok*.jar from JDK_JAVA_OPTIONS.
#
strip_existing_lombok_agents() {
  remaining_options=${JDK_JAVA_OPTIONS:-}
  cleaned_options=""
  current_raw=""
  current_arg=""
  quote=""
  while [ -n "${remaining_options}" ]; do
    current_char=${remaining_options%"${remaining_options#?}"}
    remaining_options=${remaining_options#?}
    if [ -n "${quote}" ]; then
      current_raw="${current_raw}${current_char}"
      if [ "${current_char}" = "${quote}" ]; then
        quote=""
      else
        current_arg="${current_arg}${current_char}"
      fi
    else
      case "${current_char}" in
      "'" | '"')
        current_raw="${current_raw}${current_char}"
        quote=${current_char}
        ;;
      [[:space:]])
        case "${current_arg}" in
        -javaagent:*lombok*.jar)
          :
          ;;
        *)
          cleaned_options="${cleaned_options}${current_raw}"
          ;;
        esac
        current_raw=""
        current_arg=""
        cleaned_options="${cleaned_options}${current_char}"
        ;;
      *)
        current_raw="${current_raw}${current_char}"
        current_arg="${current_arg}${current_char}"
        ;;
      esac
    fi
  done
  case "${current_arg}" in
  -javaagent:*lombok*.jar)
    :
    ;;
  *)
    cleaned_options="${cleaned_options}${current_raw}"
    ;;
  esac
  JDK_JAVA_OPTIONS=${cleaned_options}
}

# Select the Lombok jar from override or project resolution.
#
# @param project_root Project root directory for project-level resolution.
# @return Prints "source|jarpath" on success, or returns 1 if no jar selected.
select_lombok_jar() {
  project_root="$1"
  selection_status=1
  if [ -n "${lombok_override_jar}" ]; then
    if ! is_safe_path_token "${lombok_override_jar}"; then
      warn_optional_lombok_support "java: Lombok jar path must be a single filesystem token without whitespace. Ignoring override."
    elif [ -f "${lombok_override_jar}" ]; then
      printf '%s|%s\n' "override" "${lombok_override_jar}"
      selection_status=0
    else
      warn_optional_lombok_support "java: Lombok override jar was not found at ${lombok_override_jar}. Ignoring override."
    fi
  fi
  if [ "${selection_status}" -ne 0 ] && [ -n "${project_root}" ] && is_project_lombok_jar_enabled; then
    project_lombok_jar=$(resolve_project_lombok_jar "${project_root}")
    if [ -n "${project_lombok_jar}" ]; then
      printf '%s|%s\n' "project" "${project_lombok_jar}"
      selection_status=0
    fi
  fi
  return "${selection_status}"
}

# Print a warning message to stderr about Lombok support.
#
# @param message Warning text to print.
warn_optional_lombok_support() {
  printf '%s\n' "$1" >&2
}

# Orchestrate Lombok agent injection before launching JDTLS.
#     Selects a jar source, validates safety, strips existing agents,
#     and injects -javaagent via JDK_JAVA_OPTIONS.
maybe_enable_lombok_agent() {
  if is_lombok_support_enabled; then
    project_root=$(find_project_root) || true
    selected_lombok=$(select_lombok_jar "${project_root}") || true
    if [ -n "${selected_lombok}" ]; then
      selected_source=${selected_lombok%%|*}
      selected_jar=${selected_lombok#*|}
      if ! is_safe_path_token "${selected_jar}"; then
        warn_optional_lombok_support "java: Selected Lombok jar path must be a single filesystem token without whitespace. Starting jdtls without Lombok support."
      elif [ ! -f "${selected_jar}" ]; then
        warn_optional_lombok_support "java: Selected Lombok source (${selected_source}) was not found at ${selected_jar}. Starting jdtls without Lombok support."
      else
        strip_existing_lombok_agents
        selected_agent="-javaagent:"
        remaining_jar=${selected_jar}
        while [ -n "${remaining_jar}" ]; do
          current_char=${remaining_jar%"${remaining_jar#?}"}
          remaining_jar=${remaining_jar#?}
          case "${current_char}" in
          "'")
            selected_agent="${selected_agent}\"'\""
            ;;
          '"')
            selected_agent="${selected_agent}'\"'"
            ;;
          *)
            selected_agent="${selected_agent}${current_char}"
            ;;
          esac
        done
        export JDK_JAVA_OPTIONS="${selected_agent} ${JDK_JAVA_OPTIONS:-}"
        warn_optional_lombok_support "java: Enabled Lombok support from ${selected_source} source (${selected_jar})."
      fi
    fi
  fi
}

if ! jdtls_path=$(command -v jdtls); then
  printf '%s\n' 'java: jdtls executable not found on PATH. Install jdtls first.' >&2
  exit 127
fi

maybe_enable_lombok_agent

exec "${jdtls_path}" "$@"
