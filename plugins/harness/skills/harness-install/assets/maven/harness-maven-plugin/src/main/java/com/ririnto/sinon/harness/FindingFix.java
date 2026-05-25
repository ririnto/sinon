package com.ririnto.sinon.harness;

import java.util.List;

/**
 * Describes how a rule violation would be fixed.
 *
 * @param description one-line human-readable description of the fix
 * @param safety      safety classification of the fix
 * @param edits       concrete edits; empty when the rule cannot provide exact text spans
 */
public record FindingFix(
        String description,
        FixSafety safety,
        List<FindingEdit> edits) {}
