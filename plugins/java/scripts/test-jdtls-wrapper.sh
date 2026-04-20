#!/bin/sh
set -eu

# :description: Test suite for jdtls-wrapper.sh Lombok selection logic.
#     Creates fake workspaces and asserts correct -javaagent injection behavior
#     across Maven, Gradle, plain, multimodule, classpath, incompatible, missing-jar,
#     unsafe-path, and disabled scenarios.

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
wrapper_path="${script_dir}/jdtls-wrapper.sh"
resolver_path="${script_dir}/has-lombok.sh"
temp_dir=$(mktemp -d)
trap 'rm -rf "${temp_dir}"' EXIT INT TERM

fake_bin="${temp_dir}/bin"
mkdir -p "${fake_bin}"

cat >"${fake_bin}/jdtls" <<'EOF'
#!/bin/sh
printf '%s\n' "${JDK_JAVA_OPTIONS:-}" > "${JDTLS_CAPTURE_DIR}/jdk_java_options.txt"
printf '%s\n' "$*" > "${JDTLS_CAPTURE_DIR}/args.txt"
EOF
chmod +x "${fake_bin}/jdtls"

lombok_jar="${temp_dir}/lombok.jar"
: >"${lombok_jar}"

assert_contains() {
    # :description: Assert that a file contains a literal string.
    # :param needle: String to search for.
    # :param haystack_file: Path to the file to search.
    needle="$1"
    haystack_file="$2"
    if ! grep -Fq -- "${needle}" "${haystack_file}"; then
        printf '%s\n' "ASSERTION FAILED: expected ${haystack_file} to contain ${needle}" >&2
        exit 1
    fi
}

assert_not_contains() {
    # :description: Assert that a file does not contain a literal string.
    # :param needle: String to search for.
    # :param haystack_file: Path to the file to search.
    needle="$1"
    haystack_file="$2"
    if grep -Fq -- "${needle}" "${haystack_file}"; then
        printf '%s\n' "ASSERTION FAILED: expected ${haystack_file} to not contain ${needle}" >&2
        exit 1
    fi
}

run_case() {
    # :description: Run jdtls-wrapper.sh in a fake workspace with captured output.
    # :param case_name: Label for this test case (used in capture directory naming).
    # :param workspace_dir: Fake project directory to run from.
    # :param jar_path: Lombok jar path to set as override (empty string for no override).
    # :param support_enabled: Whether Lombok support is enabled ("true" or "false").
    # :return: Prints "capture_dir|stderr_file" path pair.
    case_name="$1"
    workspace_dir="$2"
    jar_path="$3"
    support_enabled="${4:-true}"
    capture_dir="${temp_dir}/${case_name}-capture"
    stderr_file="${temp_dir}/${case_name}.stderr"
    mkdir -p "${capture_dir}"
    (
        cd "${workspace_dir}"
        PATH="${fake_bin}:$PATH" \
            JDTLS_CAPTURE_DIR="${capture_dir}" \
            JAVA_ASSISTANT_LOMBOK_JAR="${jar_path}" \
            JAVA_ASSISTANT_LOMBOK_ENABLED="${support_enabled}" \
            "${wrapper_path}" >/dev/null 2>"${stderr_file}"
    )
    printf '%s\n' "${capture_dir}|${stderr_file}"
}

run_resolver_case() {
    # :description: Run has-lombok.sh --resolve-project-jar in a workspace.
    # :param workspace_dir: Project directory to scan.
    # :return: Prints the resolved jar path or nothing.
    workspace_dir="$1"
    (
        cd "${workspace_dir}"
        "${resolver_path}" --resolve-project-jar "${workspace_dir}"
    )
}

maven_workspace="${temp_dir}/maven-project"
mkdir -p "${maven_workspace}"
cat >"${maven_workspace}/pom.xml" <<'EOF'
<project>
  <dependencies>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
    </dependency>
  </dependencies>
</project>
EOF

gradle_workspace="${temp_dir}/gradle-project"
mkdir -p "${gradle_workspace}"
cat >"${gradle_workspace}/build.gradle.kts" <<'EOF'
dependencies {
  compileOnly("org.projectlombok:lombok:1.18.36")
  annotationProcessor("org.projectlombok:lombok:1.18.36")
}
EOF

plain_workspace="${temp_dir}/plain-project"
mkdir -p "${plain_workspace}"
cat >"${plain_workspace}/pom.xml" <<'EOF'
<project>
  <dependencies>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
    </dependency>
  </dependencies>
</project>
EOF

no_project_workspace="${temp_dir}/no-project"
mkdir -p "${no_project_workspace}"

classpath_workspace="${temp_dir}/classpath-project"
mkdir -p "${classpath_workspace}/lib"
project_lombok_jar="${classpath_workspace}/lib/lombok-1.18.36.jar"
: >"${project_lombok_jar}"
cat >"${classpath_workspace}/.classpath" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
  <classpathentry kind="lib" path="lib/lombok-1.18.36.jar"/>
</classpath>
EOF

incompatible_workspace="${temp_dir}/incompatible-project"
mkdir -p "${incompatible_workspace}/lib"
incompatible_lombok_jar="${incompatible_workspace}/lib/lombok-1.18.2.jar"
: >"${incompatible_lombok_jar}"
cat >"${incompatible_workspace}/.classpath" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
  <classpathentry kind="lib" path="lib/lombok-1.18.2.jar"/>
</classpath>
EOF

multimodule_workspace="${temp_dir}/multimodule-project"
mkdir -p "${multimodule_workspace}/app"
cat >"${multimodule_workspace}/pom.xml" <<'EOF'
<project>
  <modules>
    <module>app</module>
  </modules>
</project>
EOF
cat >"${multimodule_workspace}/app/pom.xml" <<'EOF'
<project>
  <dependencies>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
    </dependency>
  </dependencies>
</project>
EOF

maven_result=$(run_case "maven" "${maven_workspace}" "${lombok_jar}")
maven_capture_dir=${maven_result%|*}
maven_stderr_file=${maven_result#*|}
assert_contains "-javaagent:${lombok_jar}" "${maven_capture_dir}/jdk_java_options.txt"
assert_contains "Enabled Lombok support from override source" "${maven_stderr_file}"

gradle_result=$(run_case "gradle" "${gradle_workspace}" "${lombok_jar}")
gradle_capture_dir=${gradle_result%|*}
gradle_stderr_file=${gradle_result#*|}
assert_contains "-javaagent:${lombok_jar}" "${gradle_capture_dir}/jdk_java_options.txt"
assert_contains "Enabled Lombok support from override source" "${gradle_stderr_file}"

plain_result=$(run_case "plain" "${plain_workspace}" "")
plain_capture_dir=${plain_result%|*}
assert_not_contains "-javaagent:" "${plain_capture_dir}/jdk_java_options.txt"

no_project_result=$(run_case "no-project" "${no_project_workspace}" "")
no_project_capture_dir=${no_project_result%|*}
assert_not_contains "-javaagent:" "${no_project_capture_dir}/jdk_java_options.txt"

multimodule_result=$(run_case "multimodule" "${multimodule_workspace}" "${lombok_jar}")
multimodule_capture_dir=${multimodule_result%|*}
assert_contains "-javaagent:${lombok_jar}" "${multimodule_capture_dir}/jdk_java_options.txt"

classpath_result=$(run_case "classpath" "${classpath_workspace}" "")
classpath_capture_dir=${classpath_result%|*}
classpath_stderr_file=${classpath_result#*|}
assert_contains "-javaagent:${project_lombok_jar}" "${classpath_capture_dir}/jdk_java_options.txt"
assert_contains "Enabled Lombok support from project source" "${classpath_stderr_file}"

resolver_output=$(run_resolver_case "${classpath_workspace}")
if [ "${resolver_output}" != "${project_lombok_jar}" ]; then
    printf '%s\n' "ASSERTION FAILED: expected resolver to return ${project_lombok_jar}, got ${resolver_output}" >&2
    exit 1
fi

incompatible_result=$(run_case "incompatible" "${incompatible_workspace}" "")
incompatible_capture_dir=${incompatible_result%|*}
assert_not_contains "-javaagent:" "${incompatible_capture_dir}/jdk_java_options.txt"

missing_result=$(run_case "missing-jar" "${maven_workspace}" "${temp_dir}/missing-lombok.jar")
missing_capture_dir=${missing_result%|*}
missing_stderr_file=${missing_result#*|}
assert_not_contains "-javaagent:" "${missing_capture_dir}/jdk_java_options.txt"
assert_contains "Ignoring override and continuing with automatic selection" "${missing_stderr_file}"

unsafe_path_result=$(run_case "unsafe-path" "${maven_workspace}" "${temp_dir}/unsafe lombok.jar")
unsafe_path_capture_dir=${unsafe_path_result%|*}
unsafe_path_stderr_file=${unsafe_path_result#*|}
assert_not_contains "-javaagent:" "${unsafe_path_capture_dir}/jdk_java_options.txt"
assert_contains "must be a single filesystem token without whitespace" "${unsafe_path_stderr_file}"

disabled_result=$(run_case "disabled" "${no_project_workspace}" "" "false")
disabled_capture_dir=${disabled_result%|*}
assert_not_contains "-javaagent:" "${disabled_capture_dir}/jdk_java_options.txt"

printf '%s\n' 'OK'
