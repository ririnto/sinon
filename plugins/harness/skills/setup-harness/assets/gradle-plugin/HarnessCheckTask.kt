import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Validates the harness config shape with Gradle-native JSON parsing. */
abstract class HarnessCheckTask : DefaultTask() {
    /** Harness config file to validate. */
    @get:InputFile
    abstract val configFile: RegularFileProperty

    /** Execute Gradle-native harness validation. */
    @TaskAction
    fun validateHarness() {
        val file = configFile.get().asFile
        val errors = validateConfig(file)
        if (errors.isNotEmpty()) {
            errors.forEach { logger.error("harness validation failed: $it") }
            throw GradleException("harness validation failed")
        }
        logger.lifecycle("harness validation passed")
    }

    /** Validate the versioned harness config structure and repository-relative paths. */
    private fun validateConfig(file: File): List<String> {
        if (!file.isFile) {
            return listOf("config file is missing: ${file.path}")
        }
        val errors = mutableListOf<String>()
        val parsedRoot = Json.parseToJsonElement(file.readText())
        if (parsedRoot !is JsonObject) {
            return listOf("config root must be a JSON object")
        }
        val root = parsedRoot.jsonObject
        val requiredTopLevel = setOf("contractVersion", "profile", "paths", "docs", "commands", "gates", "evidence", "packs", "absence")
        requiredTopLevel.forEach { field ->
            if (!root.containsKey(field)) {
                errors.add("missing top-level field: $field")
            }
        }
        root.keys.filterNot(requiredTopLevel::contains).forEach { field ->
            errors.add("unexpected top-level field: $field")
        }
        if (errors.isNotEmpty()) {
            return errors
        }
        if ((root["contractVersion"] as? JsonPrimitive)?.intOrNull != 1) {
            errors.add("contractVersion must be 1")
        }
        val profile = root.objectField("profile", errors)
        validateProfile(profile, errors)
        val paths = root.objectField("paths", errors)
        validatePaths(paths, errors)
        val docs = root.objectField("docs", errors)
        validateDocs(docs, errors)
        val commands = root.objectField("commands", errors)
        validateCommandsSection(commands, errors)
        val gates = root.objectField("gates", errors)
        validateGates(gates, errors)
        val evidence = root.objectField("evidence", errors)
        validateEvidence(evidence, errors)
        val packs = root.objectField("packs", errors)
        validatePacks(packs, errors)
        val absence = root.objectField("absence", errors)
        validateAbsence(absence, gates, packs, errors)
        return errors
    }

    /** Validate the adoption profile section. */
    private fun validateProfile(profile: JsonObject?, errors: MutableList<String>) {
        if (profile == null) {
            return
        }
        profile.requireFields(listOf("name", "stage", "targetStage", "level"), "profile", errors)
        if (profile.stringField("name").isNullOrEmpty()) {
            errors.add("profile.name must be a non-empty string")
        }
        if (!profile.nonNegativeInteger("stage")) {
            errors.add("profile.stage must be a non-negative integer")
        }
        if (!profile.nonNegativeInteger("targetStage")) {
            errors.add("profile.targetStage must be a non-negative integer")
        }
        val stage = (profile["stage"] as? JsonPrimitive)?.intOrNull
        val targetStage = (profile["targetStage"] as? JsonPrimitive)?.intOrNull
        if (stage != null && targetStage != null && stage > targetStage) {
            errors.add("profile.stage must be less than or equal to profile.targetStage")
        }
        if (profile.stringField("level") !in setOf("warn", "error")) {
            errors.add("profile.level must be one of: warn, error")
        }
    }

    /** Validate the machine-owned path registry. */
    private fun validatePaths(paths: JsonObject?, errors: MutableList<String>) {
        if (paths == null) {
            return
        }
        val fields = listOf("harnessRoot", "config", "scriptsRoot", "validator", "wrapper", "agentsRoot", "agentContextAlias", "agentContextAliasTarget", "githubWorkflow", "gitlabConfig", "hooksRoot", "preCommitHook", "hookInstaller")
        paths.requireFields(fields, "paths", errors)
        validatePathMapping(paths, fields, "paths", errors)
        if (paths.stringField("config") != "docs/harness/config.json") {
            errors.add("paths.config must be docs/harness/config.json")
        }
        if (paths.stringField("harnessRoot") != "docs/harness") {
            errors.add("paths.harnessRoot must be docs/harness")
        }
        if (paths.stringField("agentsRoot") != ".claude/agents") {
            errors.add("paths.agentsRoot must be .claude/agents")
        }
        if (paths.stringField("agentContextAlias") != "AGENTS.md") {
            errors.add("paths.agentContextAlias must be AGENTS.md")
        }
        if (paths.stringField("agentContextAliasTarget") != "CLAUDE.md") {
            errors.add("paths.agentContextAliasTarget must be CLAUDE.md")
        }
    }

    /** Validate Markdown policy and workflow document roles. */
    private fun validateDocs(docs: JsonObject?, errors: MutableList<String>) {
        if (docs == null) {
            return
        }
        val fields = listOf("agentContext", "architecture", "guardrails", "readiness", "updates")
        docs.requireFields(fields, "docs", errors)
        validatePathMapping(docs, fields, "docs", errors)
        if (docs.stringField("agentContext") != "CLAUDE.md") {
            errors.add("docs.agentContext must be CLAUDE.md")
        }
        if (docs.stringField("architecture") != "ARCHITECTURE.md") {
            errors.add("docs.architecture must be ARCHITECTURE.md")
        }
    }

    /** Validate the configured command contract. */
    private fun validateCommandsSection(commands: JsonObject?, errors: MutableList<String>) {
        if (commands == null) {
            return
        }
        commands.requireFields(listOf("required", "optional"), "commands", errors)
        val required = commands["required"]
        validateCommandList(required, "commands.required", false, errors)
        validateCommandList(commands["optional"], "commands.optional", true, errors)
        val requiredCommands = required as? JsonArray
        if (requiredCommands != null && requiredCommands.none { (it as? JsonPrimitive)?.content == "sh scripts/harness/validate_harness.sh" }) {
            errors.add("commands.required must include sh scripts/harness/validate_harness.sh")
        }
    }

    /** Validate CI and hook gate metadata. */
    private fun validateGates(gates: JsonObject?, errors: MutableList<String>) {
        if (gates == null) {
            return
        }
        gates.requireFields(listOf("ci", "hooks"), "gates", errors)
        val ci = gates.objectField("ci", errors)
        if (ci != null) {
            ci.requireFields(listOf("provider", "stage", "branches"), "gates.ci", errors)
            if (ci.stringField("provider") !in setOf("github-actions", "gitlab-ci", "none")) {
                errors.add("gates.ci.provider must be one of: github-actions, gitlab-ci, none")
            }
            if (!ci.nonNegativeInteger("stage")) {
                errors.add("gates.ci.stage must be a non-negative integer")
            }
            val branches = ci["branches"] as? JsonObject
            if (branches != null) {
                listOf("push", "pullRequest", "mergeRequest").forEach { field ->
                    validateStringList(branches[field], "gates.ci.branches.$field", false, errors)
                }
            } else if (ci.containsKey("branches")) {
                errors.add("gates.ci.branches must be an object")
            }
        }
        val hooks = gates.objectField("hooks", errors)
        if (hooks != null) {
            hooks.requireFields(listOf("enabled", "stage"), "gates.hooks", errors)
            if ((hooks["enabled"] as? JsonPrimitive)?.booleanOrNull == null) {
                errors.add("gates.hooks.enabled must be a boolean")
            }
            if (!hooks.nonNegativeInteger("stage")) {
                errors.add("gates.hooks.stage must be a non-negative integer")
            }
        }
    }

    /** Validate evidence ledger and generated-doc path roles. */
    private fun validateEvidence(evidence: JsonObject?, errors: MutableList<String>) {
        if (evidence == null) {
            return
        }
        val fields = listOf("readiness", "knownViolations", "generatedDocs")
        evidence.requireFields(fields, "evidence", errors)
        validatePathMapping(evidence, fields, "evidence", errors)
    }

    /** Validate optional implementation packs. */
    private fun validatePacks(packs: JsonObject?, errors: MutableList<String>) {
        if (packs == null) {
            return
        }
        packs.forEach { packName, packElement ->
            if (!Regex("^[a-z0-9][a-z0-9-]*$").matches(packName)) {
                errors.add("packs.$packName name must use lowercase letters, digits, or hyphens")
            }
            val pack = packElement as? JsonObject
            if (pack == null) {
                errors.add("packs.$packName must be an object")
                return@forEach
            }
            if ((pack["enabled"] as? JsonPrimitive)?.booleanOrNull == null) {
                errors.add("packs.$packName.enabled must be a boolean")
            }
            validateCommandList(pack["commands"], "packs.$packName.commands", true, errors)
            validatePathList(pack["paths"], "packs.$packName.paths", true, errors)
            if ((pack["enabled"] as? JsonPrimitive)?.booleanOrNull == true) {
                if ((pack["commands"] as? JsonArray)?.isEmpty() != false) {
                    errors.add("packs.$packName.commands must be non-empty when enabled")
                }
                if ((pack["paths"] as? JsonArray)?.isEmpty() != false) {
                    errors.add("packs.$packName.paths must be non-empty when enabled")
                }
            }
        }
    }

    /** Validate explicit absence semantics for optional surfaces. */
    private fun validateAbsence(absence: JsonObject?, gates: JsonObject?, packs: JsonObject?, errors: MutableList<String>) {
        if (absence == null) {
            return
        }
        val fields = listOf("agentContextAlias", "targetAgents", "ci", "hooks", "packs")
        val states = setOf("required", "optional", "disabled", "not-applicable", "pending-conflict")
        absence.requireFields(fields, "absence", errors)
        fields.forEach { field ->
            if (absence.containsKey(field) && absence.stringField(field) !in states) {
                errors.add("absence.$field must be one of: ${states.joinToString(", ")}")
            }
        }
        if (absence.stringField("agentContextAlias") !in setOf("required", "optional", "pending-conflict", "not-applicable")) {
            errors.add("absence.agentContextAlias must be one of: required, optional, pending-conflict, not-applicable")
        }
        if (absence.stringField("targetAgents") !in states) {
            errors.add("absence.targetAgents must be one of: ${states.joinToString(", ")}")
        }
        val ci = gates?.get("ci") as? JsonObject
        if (ci != null) {
            validateSurfaceAbsence(ci.stringField("provider") != "none", absence.stringField("ci"), "absence.ci", errors)
        }
        val hooks = gates?.get("hooks") as? JsonObject
        if (hooks != null) {
            validateSurfaceAbsence((hooks["enabled"] as? JsonPrimitive)?.booleanOrNull == true, absence.stringField("hooks"), "absence.hooks", errors)
        }
        val enabledPacks = packs?.values?.any { pack ->
            val packObject = pack as? JsonObject
            (packObject?.get("enabled") as? JsonPrimitive)?.booleanOrNull == true
        } == true
        validateSurfaceAbsence(enabledPacks, absence.stringField("packs"), "absence.packs", errors)
    }

    /** Append an absence-state error when a surface state contradicts its config. */
    private fun validateSurfaceAbsence(active: Boolean, state: String?, field: String, errors: MutableList<String>) {
        if (active && state !in setOf("required", "optional")) {
            errors.add("$field must be required or optional when active")
        }
        if (!active && state !in setOf("disabled", "not-applicable", "pending-conflict")) {
            errors.add("$field must mark the surface absent when inactive")
        }
    }

    /** Return a required object field or append a structural validation error. */
    private fun JsonObject.objectField(field: String, errors: MutableList<String>): JsonObject? {
        val value = this[field]
        if (value !is JsonObject) {
            errors.add("$field must be an object")
            return null
        }
        return value
    }

    /** Return a string field value from a JSON object. */
    private fun JsonObject.stringField(field: String): String? {
        val value = this[field] ?: return null
        val primitive = value as? JsonPrimitive ?: return null
        return primitive.content
    }

    /** Return whether a field is a non-negative integer. */
    private fun JsonObject.nonNegativeInteger(field: String): Boolean {
        val value = this[field] as? JsonPrimitive ?: return false
        val integer = value.intOrNull ?: return false
        return integer >= 0
    }

    /** Validate required object fields. */
    private fun JsonObject.requireFields(fields: List<String>, section: String, errors: MutableList<String>) {
        fields.forEach { field ->
            if (!containsKey(field)) {
                errors.add("missing $section field: $field")
            }
        }
    }

    /** Validate repository-relative path fields. */
    private fun validatePathMapping(root: JsonObject, fields: List<String>, section: String, errors: MutableList<String>) {
        fields.forEach { field ->
            if (root.containsKey(field)) {
                validateRepoRelativePath(root.stringField(field), "$section.$field", errors)
            }
        }
    }

    /** Validate one repository-relative path field. */
    private fun validateRepoRelativePath(value: String?, field: String, errors: MutableList<String>) {
        if (value.isNullOrEmpty() || value.contains('\\') || Regex("^[A-Za-z]:").containsMatchIn(value) || value.startsWith("/") || value.startsWith("//") || value.split('/').contains("..")) {
            errors.add("$field must be a repository-relative path without '..'")
        }
    }

    /** Validate one string list field. */
    private fun validateStringList(value: JsonElement?, field: String, allowEmpty: Boolean, errors: MutableList<String>) {
        val items = value as? JsonArray
        if (items == null) {
            errors.add("$field must be a list of strings")
            return
        }
        if (!allowEmpty && items.isEmpty()) {
            errors.add("$field must be a non-empty list of strings")
            return
        }
        if (items.any { it !is JsonPrimitive || !it.isString || it.content.isEmpty() }) {
            errors.add("$field must contain only non-empty strings")
        }
    }

    /** Validate one path list field. */
    private fun validatePathList(value: JsonElement?, field: String, allowEmpty: Boolean, errors: MutableList<String>) {
        validateStringList(value, field, allowEmpty, errors)
        val items = value as? JsonArray ?: return
        items.map { (it as? JsonPrimitive)?.content }.forEachIndexed { index, path ->
            validateRepoRelativePath(path, "$field[$index]", errors)
        }
    }

    /** Validate trusted command strings without executing them. */
    private fun validateCommandList(value: JsonElement?, field: String, allowEmpty: Boolean, errors: MutableList<String>) {
        val commands = value as? JsonArray
        if (commands == null) {
            errors.add("$field must be a list of commands")
            return
        }
        if (!allowEmpty && commands.isEmpty()) {
            errors.add("$field must contain at least one command")
            return
        }
        val trustedCommands = setOf(
            "sh scripts/harness/validate_harness.sh",
            "bun scripts/harness/validate-harness.mjs",
            "./gradlew harnessCheck",
            "mvn local.harness:harness-maven-plugin:harness-check",
        )
        val unsafeCommandChars = setOf(';', '|', '&', '`', '$', '>', '<', '\n')
        commands.forEachIndexed { index, commandElement ->
            val command = (commandElement as? JsonPrimitive)?.content
            if (command == null) {
                errors.add("$field must be a list of commands")
            } else if (command.any(unsafeCommandChars::contains)) {
                errors.add("$field[$index] contains shell metacharacters: $command")
            } else if (command !in trustedCommands) {
                errors.add("$field[$index] command is not an allowed argv form: $command")
            }
        }
    }
}
