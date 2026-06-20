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
  if [ ! -f "${file_path}" ]; then
    return 1
  fi
  case "${file_path}" in
  */pom.xml)
    grep -Eq 'org\.projectlombok|<artifactId>lombok</artifactId>' "${file_path}"
    return
    ;;
  */build.gradle | */build.gradle.kts)
    grep -Eq 'org\.projectlombok:lombok|io\.freefair\.lombok|annotationProcessor[^[:cntrl:]]*lombok|compileOnly[^[:cntrl:]]*lombok|testAnnotationProcessor[^[:cntrl:]]*lombok|testCompileOnly[^[:cntrl:]]*lombok|kapt[^[:cntrl:]]*lombok' "${file_path}"
    return
    ;;
  */gradle/libs.versions.toml)
    grep -Eq 'org\.projectlombok|module\s*=\s*"org\.projectlombok:lombok"|^lombok\s*=|\blombok\b' "${file_path}"
    return
    ;;
  */.classpath | */.factorypath)
    grep -Eq 'org\.projectlombok|lombok' "${file_path}"
    return
    ;;
  esac
  return 1
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
  if [ -f "${resolved_path}" ]; then
    printf '%s\n' "${resolved_path}"
    return 0
  fi
  return 1
}

# Extract the version string from a lombok jar filename.
#
# @param jar_path Path to a lombok-<version>.jar file.
# @return Prints the version string, or nothing if the pattern does not match.
version_from_lombok_path() {
  basename "$1" | sed -n 's/^lombok-\([0-9][0-9A-Za-z._-]*\)\.jar$/\1/p'
}

# Find a compatible Lombok jar path from one metadata file.
#
# @param metadata_file Build metadata file that may reference Lombok jars.
# @return Prints the resolved jar path on success, or returns 1 if none found.
resolve_lombok_jar_from_metadata_file() {
  metadata_file="$1"
  if ! candidate_paths=$(grep -Eo '([A-Za-z]:[\\/][^"[:space:]]*|[^"[:space:]]*/)?lombok-[0-9][^"[:space:]]*\.jar' "${metadata_file}"); then
    return 1
  fi
  while IFS= read -r candidate_path; do
    if resolved_path=$(resolve_candidate_jar_path "${candidate_path}" "${metadata_file}"); then
      candidate_version=$(version_from_lombok_path "${resolved_path}")
      if [ -n "${candidate_version}" ] && is_compatible_lombok_version "${candidate_version}"; then
        printf '%s\n' "${resolved_path}"
        return 0
      fi
    fi
  done <<EOF
${candidate_paths}
EOF
  return 1
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
  contains_lombok_dependency "${project_root}/pom.xml" && return 0
  contains_lombok_dependency "${project_root}/build.gradle" && return 0
  contains_lombok_dependency "${project_root}/build.gradle.kts" && return 0
  contains_lombok_dependency "${project_root}/gradle/libs.versions.toml" && return 0
  contains_lombok_dependency "${project_root}/.classpath" && return 0
  contains_lombok_dependency "${project_root}/.factorypath" && return 0
  while IFS= read -r candidate_file; do
    if contains_lombok_dependency "${candidate_file}"; then
      return 0
    fi
  done <<EOF
$(find_project_files)
EOF
  return 1
}

# Find a compatible Lombok jar path from project metadata files.
#     Searches .classpath/.factorypath first, then all discovered build files.
#
# @return Prints the resolved jar path on success, or returns 1 if none found.
resolve_project_lombok_jar() {
  for metadata_file in "${project_root}/.classpath" "${project_root}/.factorypath"; do
    if [ -f "${metadata_file}" ]; then
      if resolve_lombok_jar_from_metadata_file "${metadata_file}"; then
        return 0
      fi
    fi
  done
  while IFS= read -r metadata_file; do
    case "${metadata_file}" in
    */.classpath | */.factorypath)
      if resolve_lombok_jar_from_metadata_file "${metadata_file}"; then
        return 0
      fi
      ;;
    esac
  done <<EOF
$(find_project_files)
EOF
  return 1
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
