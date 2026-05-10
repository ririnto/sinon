package com.example.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Runs Maven-native harness validation as a Maven goal. */
@Mojo(name = "harness-check")
public final class HarnessCheckMojo extends AbstractMojo {
    /** Harness configuration path relative to the project base directory. */
    @Parameter(defaultValue = "docs/harness/config.json")
    private String configPath;

    /** Maven project base directory. */
    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    private java.io.File basedir;

    /** JSON parser used for structural validation. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Executes Maven-native harness validation. */
    @Override
    public void execute() throws MojoExecutionException {
        try {
            Path config = basedir.toPath().resolve(configPath).normalize();
            ArrayList<String> errors = validateConfig(config);
            for (String error : errors) {
                getLog().error("harness validation failed: " + error);
            }
            if (!errors.isEmpty()) {
                throw new MojoExecutionException("harness validation failed");
            }
            getLog().info("harness validation passed");
        } catch (IOException error) {
            throw new MojoExecutionException("failed to read harness config", error);
        }
    }

    /** Validates the versioned harness config structure and repository-relative paths. */
    private ArrayList<String> validateConfig(Path config) throws IOException {
        ArrayList<String> errors = new ArrayList<>();
        if (!Files.isRegularFile(config)) {
            errors.add("config file is missing: " + config);
            return errors;
        }
        JsonNode root = objectMapper.readTree(Files.readString(config));
        if (!root.isObject()) {
            errors.add("config root must be a JSON object");
            return errors;
        }
        List<String> topLevel = List.of("contractVersion", "profile", "paths", "docs", "commands", "gates", "evidence", "packs", "absence");
        requireOnlyFields(root, topLevel, "top-level", errors);
        if (!errors.isEmpty()) {
            return errors;
        }
        if (!root.path("contractVersion").isInt() || root.path("contractVersion").asInt() != 1) {
            errors.add("contractVersion must be 1");
        }
        validateProfile(objectField(root, "profile", errors), errors);
        validatePaths(objectField(root, "paths", errors), errors);
        validateDocs(objectField(root, "docs", errors), errors);
        validateCommandsSection(objectField(root, "commands", errors), errors);
        JsonNode gates = objectField(root, "gates", errors);
        validateGates(gates, errors);
        validateEvidence(objectField(root, "evidence", errors), errors);
        JsonNode packs = objectField(root, "packs", errors);
        validatePacks(packs, errors);
        validateAbsence(objectField(root, "absence", errors), gates, packs, errors);
        return errors;
    }

    /** Validate the adoption profile section. */
    private void validateProfile(JsonNode profile, ArrayList<String> errors) {
        if (profile == null) {
            return;
        }
        requireFields(profile, List.of("name", "stage", "targetStage", "level"), "profile", errors);
        if (!text(profile, "name")) {
            errors.add("profile.name must be a non-empty string");
        }
        if (!nonNegativeInteger(profile, "stage")) {
            errors.add("profile.stage must be a non-negative integer");
        }
        if (!nonNegativeInteger(profile, "targetStage")) {
            errors.add("profile.targetStage must be a non-negative integer");
        }
        if (profile.path("stage").isInt() && profile.path("targetStage").isInt() && profile.path("stage").asInt() > profile.path("targetStage").asInt()) {
            errors.add("profile.stage must be less than or equal to profile.targetStage");
        }
        if (!List.of("warn", "error").contains(stringField(profile, "level"))) {
            errors.add("profile.level must be one of: warn, error");
        }
    }

    /** Validate the machine-owned path registry. */
    private void validatePaths(JsonNode paths, ArrayList<String> errors) {
        if (paths == null) {
            return;
        }
        List<String> fields = List.of("harnessRoot", "config", "scriptsRoot", "validator", "wrapper", "agentsRoot", "agentContextAlias", "agentContextAliasTarget", "githubWorkflow", "gitlabConfig", "hooksRoot", "preCommitHook", "hookInstaller");
        requireFields(paths, fields, "paths", errors);
        validatePathMapping(paths, fields, "paths", errors);
        if (!"docs/harness/config.json".equals(stringField(paths, "config"))) {
            errors.add("paths.config must be docs/harness/config.json");
        }
        if (!"docs/harness".equals(stringField(paths, "harnessRoot"))) {
            errors.add("paths.harnessRoot must be docs/harness");
        }
        if (!".claude/agents".equals(stringField(paths, "agentsRoot"))) {
            errors.add("paths.agentsRoot must be .claude/agents");
        }
        if (!"AGENTS.md".equals(stringField(paths, "agentContextAlias"))) {
            errors.add("paths.agentContextAlias must be AGENTS.md");
        }
        if (!"CLAUDE.md".equals(stringField(paths, "agentContextAliasTarget"))) {
            errors.add("paths.agentContextAliasTarget must be CLAUDE.md");
        }
    }

    /** Validate Markdown policy and workflow document roles. */
    private void validateDocs(JsonNode docs, ArrayList<String> errors) {
        if (docs == null) {
            return;
        }
        List<String> fields = List.of("agentContext", "architecture", "guardrails", "readiness", "updates");
        requireFields(docs, fields, "docs", errors);
        validatePathMapping(docs, fields, "docs", errors);
        if (!"CLAUDE.md".equals(stringField(docs, "agentContext"))) {
            errors.add("docs.agentContext must be CLAUDE.md");
        }
        if (!"ARCHITECTURE.md".equals(stringField(docs, "architecture"))) {
            errors.add("docs.architecture must be ARCHITECTURE.md");
        }
    }

    /** Validate the configured command contract. */
    private void validateCommandsSection(JsonNode commands, ArrayList<String> errors) {
        if (commands == null) {
            return;
        }
        requireFields(commands, List.of("required", "optional"), "commands", errors);
        JsonNode required = commands.path("required");
        validateCommandList(required, "commands.required", false, errors);
        validateCommandList(commands.path("optional"), "commands.optional", true, errors);
        if (required.isArray()) {
            boolean hasWrapper = false;
            for (JsonNode commandNode : required) {
                if ("sh scripts/harness/validate_harness.sh".equals(commandNode.asText())) {
                    hasWrapper = true;
                    break;
                }
            }
            if (!hasWrapper) {
                errors.add("commands.required must include sh scripts/harness/validate_harness.sh");
            }
        }
    }

    /** Validate CI and hook gate metadata. */
    private void validateGates(JsonNode gates, ArrayList<String> errors) {
        if (gates == null) {
            return;
        }
        requireFields(gates, List.of("ci", "hooks"), "gates", errors);
        JsonNode ci = objectField(gates, "ci", errors);
        if (ci != null) {
            requireFields(ci, List.of("provider", "stage", "branches"), "gates.ci", errors);
            if (!List.of("github-actions", "gitlab-ci", "none").contains(stringField(ci, "provider"))) {
                errors.add("gates.ci.provider must be one of: github-actions, gitlab-ci, none");
            }
            if (!nonNegativeInteger(ci, "stage")) {
                errors.add("gates.ci.stage must be a non-negative integer");
            }
            JsonNode branches = ci.path("branches");
            if (branches.isObject()) {
                for (String field : List.of("push", "pullRequest", "mergeRequest")) {
                    validateStringList(branches.path(field), "gates.ci.branches." + field, false, errors);
                }
            } else if (!branches.isMissingNode()) {
                errors.add("gates.ci.branches must be an object");
            }
        }
        JsonNode hooks = objectField(gates, "hooks", errors);
        if (hooks != null) {
            requireFields(hooks, List.of("enabled", "stage"), "gates.hooks", errors);
            if (!hooks.path("enabled").isBoolean()) {
                errors.add("gates.hooks.enabled must be a boolean");
            }
            if (!nonNegativeInteger(hooks, "stage")) {
                errors.add("gates.hooks.stage must be a non-negative integer");
            }
        }
    }

    /** Validate evidence ledger and generated-doc path roles. */
    private void validateEvidence(JsonNode evidence, ArrayList<String> errors) {
        if (evidence == null) {
            return;
        }
        List<String> fields = List.of("readiness", "knownViolations", "generatedDocs");
        requireFields(evidence, fields, "evidence", errors);
        validatePathMapping(evidence, fields, "evidence", errors);
    }

    /** Validate optional implementation packs. */
    private void validatePacks(JsonNode packs, ArrayList<String> errors) {
        if (packs == null) {
            return;
        }
        Iterator<String> names = packs.fieldNames();
        while (names.hasNext()) {
            String packName = names.next();
            JsonNode pack = packs.path(packName);
            if (!packName.matches("^[a-z0-9][a-z0-9-]*$")) {
                errors.add("packs." + packName + " name must use lowercase letters, digits, or hyphens");
            }
            if (!pack.isObject()) {
                errors.add("packs." + packName + " must be an object");
                continue;
            }
            if (!pack.path("enabled").isBoolean()) {
                errors.add("packs." + packName + ".enabled must be a boolean");
            }
            validateCommandList(pack.path("commands"), "packs." + packName + ".commands", true, errors);
            validatePathList(pack.path("paths"), "packs." + packName + ".paths", true, errors);
            if (pack.path("enabled").asBoolean(false)) {
                if (!pack.path("commands").isArray() || pack.path("commands").isEmpty()) {
                    errors.add("packs." + packName + ".commands must be non-empty when enabled");
                }
                if (!pack.path("paths").isArray() || pack.path("paths").isEmpty()) {
                    errors.add("packs." + packName + ".paths must be non-empty when enabled");
                }
            }
        }
    }

    /** Validate explicit absence semantics for optional surfaces. */
    private void validateAbsence(JsonNode absence, JsonNode gates, JsonNode packs, ArrayList<String> errors) {
        if (absence == null) {
            return;
        }
        List<String> fields = List.of("agentContextAlias", "targetAgents", "ci", "hooks", "packs");
        List<String> states = List.of("required", "optional", "disabled", "not-applicable", "pending-conflict");
        requireFields(absence, fields, "absence", errors);
        for (String field : fields) {
            if (absence.has(field) && !states.contains(stringField(absence, field))) {
                errors.add("absence." + field + " must be one of: " + String.join(", ", states));
            }
        }
        if (!List.of("required", "optional", "pending-conflict", "not-applicable").contains(stringField(absence, "agentContextAlias"))) {
            errors.add("absence.agentContextAlias must be one of: required, optional, pending-conflict, not-applicable");
        }
        if (!states.contains(stringField(absence, "targetAgents"))) {
            errors.add("absence.targetAgents must be one of: " + String.join(", ", states));
        }
        if (gates != null && gates.path("ci").isObject()) {
            String provider = stringField(gates.path("ci"), "provider");
            validateSurfaceAbsence(provider != null && !"none".equals(provider), stringField(absence, "ci"), "absence.ci", errors);
        }
        if (gates != null && gates.path("hooks").isObject()) {
            validateSurfaceAbsence(gates.path("hooks").path("enabled").asBoolean(false), stringField(absence, "hooks"), "absence.hooks", errors);
        }
        boolean enabledPacks = false;
        if (packs != null && packs.isObject()) {
            Iterator<JsonNode> values = packs.elements();
            while (values.hasNext()) {
                JsonNode pack = values.next();
                enabledPacks = enabledPacks || (pack.isObject() && pack.path("enabled").asBoolean(false));
            }
        }
        validateSurfaceAbsence(enabledPacks, stringField(absence, "packs"), "absence.packs", errors);
    }

    /** Append an absence-state error when a surface state contradicts its config. */
    private void validateSurfaceAbsence(boolean active, String state, String field, ArrayList<String> errors) {
        List<String> activeStates = List.of("required", "optional");
        List<String> inactiveStates = List.of("disabled", "not-applicable", "pending-conflict");
        if (active && !activeStates.contains(state)) {
            errors.add(field + " must be required or optional when active");
        }
        if (!active && !inactiveStates.contains(state)) {
            errors.add(field + " must mark the surface absent when inactive");
        }
    }

    /** Return a required object field or append a structural validation error. */
    private JsonNode objectField(JsonNode root, String field, ArrayList<String> errors) {
        JsonNode value = root.path(field);
        if (!value.isObject()) {
            errors.add(field + " must be an object");
            return null;
        }
        return value;
    }

    /** Return a string field value from a JSON object. */
    private String stringField(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    /** Return whether a field is a non-empty string. */
    private boolean text(JsonNode root, String field) {
        String value = stringField(root, field);
        return value != null && !value.isEmpty();
    }

    /** Return whether a field is a non-negative integer. */
    private boolean nonNegativeInteger(JsonNode root, String field) {
        return root.path(field).isInt() && root.path(field).asInt() >= 0;
    }

    /** Validate required object fields. */
    private void requireFields(JsonNode root, List<String> fields, String section, ArrayList<String> errors) {
        for (String field : fields) {
            if (!root.has(field)) {
                errors.add("missing " + section + " field: " + field);
            }
        }
    }

    /** Validate required and unexpected fields. */
    private void requireOnlyFields(JsonNode root, List<String> fields, String section, ArrayList<String> errors) {
        requireFields(root, fields, section, errors);
        root.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field)) {
                errors.add("unexpected top-level field: " + field);
            }
        });
    }

    /** Validate repository-relative path fields. */
    private void validatePathMapping(JsonNode root, List<String> fields, String section, ArrayList<String> errors) {
        for (String field : fields) {
            if (root.has(field)) {
                validateRepoRelativePath(stringField(root, field), section + "." + field, errors);
            }
        }
    }

    /** Validate one repository-relative path field. */
    private void validateRepoRelativePath(String value, String field, ArrayList<String> errors) {
        if (value == null || value.isEmpty() || value.contains("\\") || value.matches("^[A-Za-z]:.*") || value.startsWith("/") || value.startsWith("//") || List.of(value.split("/")).contains("..")) {
            errors.add(field + " must be a repository-relative path without '..'");
        }
    }

    /** Validate one string list field. */
    private void validateStringList(JsonNode value, String field, boolean allowEmpty, ArrayList<String> errors) {
        if (!value.isArray()) {
            errors.add(field + " must be a list of strings");
            return;
        }
        if (!allowEmpty && value.isEmpty()) {
            errors.add(field + " must be a non-empty list of strings");
            return;
        }
        for (JsonNode item : value) {
            if (!item.isTextual() || item.asText().isEmpty()) {
                errors.add(field + " must contain only non-empty strings");
                return;
            }
        }
    }

    /** Validate one path list field. */
    private void validatePathList(JsonNode value, String field, boolean allowEmpty, ArrayList<String> errors) {
        validateStringList(value, field, allowEmpty, errors);
        if (!value.isArray()) {
            return;
        }
        for (int index = 0; index < value.size(); index += 1) {
            validateRepoRelativePath(value.get(index).isTextual() ? value.get(index).asText() : null, field + "[" + index + "]", errors);
        }
    }

    /** Validate trusted command strings without executing them. */
    private void validateCommandList(JsonNode value, String field, boolean allowEmpty, ArrayList<String> errors) {
        if (!value.isArray()) {
            errors.add(field + " must be a list of commands");
            return;
        }
        if (!allowEmpty && value.isEmpty()) {
            errors.add(field + " must contain at least one command");
            return;
        }
        List<String> trustedCommands = List.of(
            "sh scripts/harness/validate_harness.sh",
            "bun scripts/harness/validate-harness.mjs",
            "./gradlew harnessCheck",
            "mvn local.harness:harness-maven-plugin:harness-check"
        );
        for (int index = 0; index < value.size(); index += 1) {
            JsonNode commandNode = value.get(index);
            if (!commandNode.isTextual()) {
                errors.add(field + " must be a list of commands");
                continue;
            }
            String command = commandNode.asText();
            if (command.matches(".*[;|&`$><\n].*")) {
                errors.add(field + "[" + index + "] contains shell metacharacters: " + command);
            } else if (!trustedCommands.contains(command)) {
                errors.add(field + "[" + index + "] command is not an allowed argv form: " + command);
            }
        }
    }
}
