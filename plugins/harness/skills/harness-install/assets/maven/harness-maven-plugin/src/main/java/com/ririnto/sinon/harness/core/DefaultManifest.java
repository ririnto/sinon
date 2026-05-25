package com.ririnto.sinon.harness.core;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import tools.jackson.databind.JsonNode;
import java.util.List;

/**
 * Default manifest facade backed by a Jackson JSON node.
 */
public final class DefaultManifest implements Manifest {
    private final JsonNode raw;

    /**
     * Creates a manifest facade.
     *
     * @param raw raw manifest JSON node
     */
    public DefaultManifest(JsonNode raw) {
        this.raw = raw;
    }

    @Override
    public JsonNode raw() {
        return raw;
    }

    @Override
    public boolean isEnabled(String category) {
        return HarnessCheckHelper.applies(raw, category);
    }

    @Override
    public Severity severityOf(String category) {
        return Severity.valueOf(HarnessCheckHelper.getSeverity(raw, category));
    }

    @Override
    public List<String> stringArray(String category, String key) {
        final JsonNode section = categoryObject(category);
        if (section == null) {
            return List.of();
        }
        final JsonNode parameters = section.get("parameters");
        if (parameters == null) {
            return List.of();
        }
        return HarnessCheckHelper.extractPaths(parameters.get(key));
    }

    @Override
    public String stringValue(String category, String key) {
        final JsonNode section = categoryObject(category);
        if (section == null) {
            return "";
        }
        final JsonNode parameters = section.get("parameters");
        if (parameters == null) {
            return "";
        }
        final JsonNode value = parameters.get(key);
        return value != null && value.isTextual() ? value.asText() : "";
    }

    @Override
    public JsonNode categoryObject(String category) {
        final JsonNode section = raw.get(category);
        return section != null && section.isObject() ? section : null;
    }
}
