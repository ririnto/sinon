#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Minimum Lombok version accepted for Java 25-compatible project-resolved jars.
COMPATIBLE_VERSION="1.18.40"
mode="uses-lombok"

case "${1:-}" in
--resolve-project-jar)
  mode="resolve-project-jar"
  shift
  ;;
--uses-lombok)
  shift
  ;;
esac

project_root=${1:-$PWD}

# Check whether a single build file declares a Lombok dependency.
#     Requires grep with -E (GNU grep on Linux and BSD grep on macOS both support -E).
#
# @param file_path Path to the build file to inspect.
# @return 0 if Lombok dependency is found, 1 otherwise.
contains_lombok_dependency() {
  file_path="$1"
  if [ -f "${file_path}" ]; then
    case "${file_path}" in
    */pom.xml)
      grep -Eq 'org\.projectlombok|<artifactId>lombok</artifactId>' "${file_path}"
      ;;
    */build.gradle | */build.gradle.kts)
      grep -Eq 'org\.projectlombok:lombok|io\.freefair\.lombok|annotationProcessor[^[:cntrl:]]*lombok|compileOnly[^[:cntrl:]]*lombok|testAnnotationProcessor[^[:cntrl:]]*lombok|testCompileOnly[^[:cntrl:]]*lombok|kapt[^[:cntrl:]]*lombok' "${file_path}"
      ;;
    */gradle/libs.versions.toml)
      grep -Eq 'org\.projectlombok|module\s*=\s*"org\.projectlombok:lombok"|^lombok\s*=|\blombok\b' "${file_path}"
      ;;
    */.classpath | */.factorypath)
      grep -Eq 'org\.projectlombok|lombok' "${file_path}"
      ;;
    *)
      false
      ;;
    esac
  else
    false
  fi
}

# Recursively find all build-system metadata files under a directory.
#
# @return Prints one file path per line, excluding .git/, node_modules/, build/, target/.
find_project_files() {
  find "${project_root}" \
    -type f \
    \( -name pom.xml -o -name build.gradle -o -name build.gradle.kts -o -path '*/gradle/libs.versions.toml' -o -name .classpath -o -name .factorypath \) \
    -not -path '*/.git/*' \
    -not -path '*/node_modules/*' \
    -not -path '*/build/*' \
    -not -path '*/target/*' \
    -print
}

# Resolve a candidate jar path to an absolute filesystem path.
#
# @param candidate_path Raw path string from metadata (may be relative or file:-prefixed).
# @param source_file File that contains the candidate path for resolving relatives.
# @return Prints the resolved absolute path if the file exists, or returns 1.
resolve_candidate_jar_path() {
  candidate_path="$1"
  source_file="$2"
  source_dir=$(dirname "${source_file}")
  case "${candidate_path}" in
  file:/*)
    candidate_path=${candidate_path#file:}
    ;;
  esac
  case "${candidate_path}" in
  /*)
    resolved_path="${candidate_path}"
    ;;
  [A-Za-z]:[\\/]*)
    resolved_path="${candidate_path}"
    ;;
  *)
    resolved_path="${source_dir}/${candidate_path}"
    ;;
  esac
  candidate_status=1
  if [ -f "${resolved_path}" ]; then
    printf '%s\n' "${resolved_path}"
    candidate_status=0
  fi
  return "${candidate_status}"
}

# Extract the version string from a lombok jar filename.
#
# @param jar_path Path to a lombok-<version>.jar file.
# @return Prints the version string, or nothing if the pattern does not match.
version_from_lombok_path() {
  basename "$1" | sed -n '
    /^lombok-[0-9][0-9A-Za-z._-]*\.jar$/ {
      s/^lombok-//
      s/\.jar$//
      p
    }'
}

# Find a compatible Lombok jar path from one metadata file.
#
# @param metadata_file Build metadata file that may reference Lombok jars.
# @return Prints the resolved jar path on success, or returns 1 if none found.
resolve_lombok_jar_from_metadata_file() {
  metadata_file="$1"
  metadata_status=1
  if candidate_paths=$(grep -Eo '([A-Za-z]:[\\/][^"[:space:]]*|[^"[:space:]]*/)?lombok-[0-9][^"[:space:]]*\.jar' "${metadata_file}"); then
    while [ "${metadata_status}" -ne 0 ] && IFS= read -r candidate_path; do
      if resolved_path=$(resolve_candidate_jar_path "${candidate_path}" "${metadata_file}"); then
        candidate_version=$(version_from_lombok_path "${resolved_path}")
        if [ -n "${candidate_version}" ] && is_compatible_lombok_version "${candidate_version}"; then
          printf '%s\n' "${resolved_path}"
          metadata_status=0
        fi
      fi
    done <<EOF
${candidate_paths}
EOF
  fi
  return "${metadata_status}"
}

# Check whether a Lombok version meets the minimum compatibility threshold.
#
# @param current_version Version string to check.
# @exit 0 if compatible, 1 if below minimum.
is_compatible_lombok_version() {
  current_version="$1"
  awk -v current="${current_version}" -v minimum="${COMPATIBLE_VERSION}" '
        function parse(version, values,   parts, count, i, parsed) {
            count = split(version, parts, /[^0-9]+/)
            parsed = 0
            for (i = 1; i <= count; i++) {
                if (parts[i] != "") {
                    values[++parsed] = parts[i] + 0
                }
            }
            return parsed
        }
        BEGIN {
            current_count = parse(current, current_values)
            minimum_count = parse(minimum, minimum_values)
            max_count = current_count > minimum_count ? current_count : minimum_count
            for (i = 1; i <= max_count; i++) {
                current_value = i in current_values ? current_values[i] : 0
                minimum_value = i in minimum_values ? minimum_values[i] : 0
                if (current_value > minimum_value) {
                    exit 0
                }
                if (current_value < minimum_value) {
                    exit 1
                }
            }
            exit 0
        }'
}

# Check whether the project root or any nested module uses Lombok.
#
# @return 0 if Lombok usage is detected anywhere in the project, 1 otherwise.
project_uses_lombok() {
  usage_status=1
  if contains_lombok_dependency "${project_root}/pom.xml" ||
    contains_lombok_dependency "${project_root}/build.gradle" ||
    contains_lombok_dependency "${project_root}/build.gradle.kts" ||
    contains_lombok_dependency "${project_root}/gradle/libs.versions.toml" ||
    contains_lombok_dependency "${project_root}/.classpath" ||
    contains_lombok_dependency "${project_root}/.factorypath"; then
    usage_status=0
  else
    while [ "${usage_status}" -ne 0 ] && IFS= read -r candidate_file; do
      if contains_lombok_dependency "${candidate_file}"; then
        usage_status=0
      fi
    done <<EOF
$(find_project_files)
EOF
  fi
  return "${usage_status}"
}

# Find a compatible Lombok jar path from project metadata files.
#     Searches .classpath/.factorypath first, then all discovered build files.
#
# @return Prints the resolved jar path on success, or returns 1 if none found.
resolve_project_lombok_jar() {
  project_jar_status=1
  for metadata_file in "${project_root}/.classpath" "${project_root}/.factorypath"; do
    if [ "${project_jar_status}" -ne 0 ] && [ -f "${metadata_file}" ] && resolve_lombok_jar_from_metadata_file "${metadata_file}"; then
      project_jar_status=0
    fi
  done
  if [ "${project_jar_status}" -ne 0 ]; then
    while [ "${project_jar_status}" -ne 0 ] && IFS= read -r metadata_file; do
      case "${metadata_file}" in
      */.classpath | */.factorypath)
        if resolve_lombok_jar_from_metadata_file "${metadata_file}"; then
          project_jar_status=0
        fi
        ;;
      esac
    done <<EOF
$(find_project_files)
EOF
  fi
  return "${project_jar_status}"
}

if [ ! -d "${project_root}" ]; then
  exit 1
fi

case "${mode}" in
resolve-project-jar)
  resolve_project_lombok_jar
  ;;
uses-lombok)
  project_uses_lombok
  ;;
esac
