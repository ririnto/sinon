package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Rule that requires specific content in documentation files.
 */
public enum DocContentRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "docContent";
    }
    private static final String CATEGORY = "docContent";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final JsonNode catNode = manifest.get(CATEGORY);
        final JsonNode checksNode = catNode.get("parameters").get("checks");
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return StreamSupport.stream(checksNode.spliterator(), false)
                .flatMap(check -> validateCheck(root, check, severity).stream())
                .toList();
    }

    private List<Finding> validateCheck(Path root, JsonNode check, String severity) {
        final String failureMessage = check.get("failureMessage").asText();
        final String combined = HarnessCheckHelper.extractPaths(check.get("files")).stream()
                .map(f -> {
                    try {
                        return HarnessCheckHelper.readFile(root, root.resolve(f));
                    } catch (MojoExecutionException e) {
                        return "";
                    }
                })
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return conditionMatches(check, combined)
                ? List.of()
                : List.of(new Finding(severity, CATEGORY, failureMessage));
    }

    private boolean conditionMatches(JsonNode check, String combined) {
        final JsonNode condition = check.has("condition") ? check.get("condition") : check.get("when");
        return condition == null
                ? false
                : evaluateCondition(condition, combined);
    }

    private boolean evaluateCondition(JsonNode condition, String combined) {
        if (condition == null || condition.isNull()) {
            return false;
        }
        if (condition.isTextual()) {
            return combined.contains(condition.asText());
        }
        if (condition.isArray()) {
            return StreamSupport.stream(condition.spliterator(), false)
                    .allMatch(item -> evaluateCondition(item, combined));
        }
        if (!condition.isObject()) {
            return false;
        }
        final boolean hasAll = condition.has("allOf");
        final boolean hasAny = condition.has("anyOf");
        final boolean hasContains = condition.has("contains");
        final boolean hasNot = condition.has("not");
        if (!(hasAll || hasAny || hasContains || hasNot)) {
            return false;
        }
        final JsonNode allOfValue = condition.get("allOf");
        final JsonNode anyOfValue = condition.get("anyOf");
        final List<JsonNode> allOf = conditionArray(allOfValue);
        final List<JsonNode> anyOf = conditionArray(anyOfValue);
        final List<String> contains = stringArray(condition.get("contains"));
        final boolean andMatches = allOf.isEmpty() || allOf.stream().allMatch(item -> evaluateCondition(item, combined));
        final boolean orMatches = !hasAny || !anyOf.isEmpty() && anyOf.stream().anyMatch(item -> evaluateCondition(item, combined));
        final boolean containsMatches = contains.stream().allMatch(combined::contains);
        final JsonNode notCondition = condition.get("not");
        final boolean notMatches = !hasNot || !evaluateCondition(notCondition, combined);
        return andMatches && orMatches && containsMatches && notMatches;
    }

    private List<JsonNode> conditionArray(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            return List.of(node);
        }
        final List<JsonNode> items = new ArrayList<>();
        node.forEach(items::add);
        return items;
    }

    private List<String> stringArray(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isTextual()) {
            return List.of(node.asText());
        }
        if (!node.isArray()) {
            return List.of();
        }
        final List<String> items = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual()) {
                items.add(item.asText());
            }
        });
        return items;
    }
}
