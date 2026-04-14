#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
lombok_override_jar="${JAVA_ASSISTANT_LOMBOK_JAR:-${JDK_ASSISTANT_LOMBOK_JAR:-${LOMBOK_JAR:-}}}"
lombok_support_enabled="${JAVA_ASSISTANT_LOMBOK_ENABLED:-${JDK_ASSISTANT_LOMBOK_ENABLED:-true}}"

is_safe_path_token() {
	case "$1" in
	*" "* | *"	"* | *"
"*)
		return 1
		;;
	esac
	return 0
}

is_lombok_support_enabled() {
	case "${lombok_support_enabled}" in
	0 | false | FALSE | False | off | OFF | Off | no | NO | No)
		return 1
		;;
	esac
	return 0
}

find_project_root() {
	search_dir="${PWD}"
	while [ "${search_dir}" != "/" ]; do
		if [ -f "${search_dir}/pom.xml" ] || [ -f "${search_dir}/build.gradle" ] || [ -f "${search_dir}/build.gradle.kts" ] || [ -f "${search_dir}/.classpath" ] || [ -f "${search_dir}/.factorypath" ] || [ -f "${search_dir}/settings.gradle" ] || [ -f "${search_dir}/settings.gradle.kts" ] || [ -f "${search_dir}/gradle/libs.versions.toml" ]; then
			printf '%s\n' "${search_dir}"
			return 0
		fi
		search_dir=$(dirname "${search_dir}")
	done
	return 1
}

resolve_project_lombok_jar() {
	"${script_dir}/has-lombok.sh" --resolve-project-jar "$1" 2>/dev/null || true
}

strip_existing_lombok_agents() {
	cleaned_options=""
	for current_arg in ${JDK_JAVA_OPTIONS:-}; do
		case "${current_arg}" in
		-javaagent:*lombok*.jar)
			continue
			;;
		esac
		if [ -n "${cleaned_options}" ]; then
			cleaned_options="${cleaned_options} ${current_arg}"
		else
			cleaned_options="${current_arg}"
		fi
	done
	JDK_JAVA_OPTIONS=${cleaned_options}
}

select_lombok_jar() {
	project_root="$1"

	if [ -n "${lombok_override_jar}" ]; then
		if ! is_safe_path_token "${lombok_override_jar}"; then
			warn_optional_lombok_support "java: Lombok jar path must be a single filesystem token without whitespace. Ignoring override and continuing with automatic selection."
		elif [ -f "${lombok_override_jar}" ]; then
			printf '%s|%s\n' "override" "${lombok_override_jar}"
			return 0
		else
			warn_optional_lombok_support "java: Lombok override jar was not found at ${lombok_override_jar}. Ignoring override and continuing with automatic selection."
		fi
	fi

	if [ -n "${project_root}" ]; then
		project_lombok_jar=$(resolve_project_lombok_jar "${project_root}")
		if [ -n "${project_lombok_jar}" ]; then
			printf '%s|%s\n' "project" "${project_lombok_jar}"
			return 0
		fi
	fi

	return 1
}

warn_optional_lombok_support() {
	printf '%s\n' "$1" >&2
}

maybe_enable_lombok_agent() {
	if ! is_lombok_support_enabled; then
		return 0
	fi

	project_root=""
	if detected_project_root=$(find_project_root); then
		project_root="${detected_project_root}"
	fi

	selected_lombok=$(select_lombok_jar "${project_root}" || true)
	if [ -z "${selected_lombok}" ]; then
		return 0
	fi

	selected_source=${selected_lombok%%|*}
	selected_jar=${selected_lombok#*|}

	if ! is_safe_path_token "${selected_jar}"; then
		warn_optional_lombok_support "java: Selected Lombok jar path must be a single filesystem token without whitespace. Starting jdtls without Lombok support."
		return 0
	fi

	if [ ! -f "${selected_jar}" ]; then
		warn_optional_lombok_support "java: Selected Lombok source (${selected_source}) was not found at ${selected_jar}. Starting jdtls without Lombok support."
		return 0
	fi

	strip_existing_lombok_agents
	selected_agent="-javaagent:${selected_jar}"

	if [ -n "${JDK_JAVA_OPTIONS:-}" ]; then
		export JDK_JAVA_OPTIONS="${selected_agent} ${JDK_JAVA_OPTIONS}"
	else
		export JDK_JAVA_OPTIONS="${selected_agent}"
	fi

	warn_optional_lombok_support "java: Enabled Lombok support from ${selected_source} source (${selected_jar})."
}

if ! command -v jdtls >/dev/null 2>&1; then
	printf '%s\n' 'java: jdtls executable not found on PATH. Install jdtls first.' >&2
	exit 127
fi

maybe_enable_lombok_agent

exec jdtls "$@"
