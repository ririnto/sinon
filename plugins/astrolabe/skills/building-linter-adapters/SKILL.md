---
name: building-linter-adapters
description: Use when designing or changing native-linter adapters, diagnostics, fix behavior, manifest options, fixtures, or installation checks.
---

# Building Linter Adapters

## Use Native Authority

Use the ecosystem linter and formatter as the authority for enforceable language rules.

Keep package checks for structure, manifests, hooks, links, and behavior that the native tool cannot express.

Open [references/linter-conventions.md](references/linter-conventions.md) for the native-tool matrix and runtime loading requirements.

## Define the Adapter Contract

Identify the native tool, input contract, output format, and supported installation variants before implementation.

Expose only controls the rule actually supports, including scope, fix, output, suppression, message, and localization controls when they apply.

Give every exposed option a schema type, allowed values, and a default from the adapter's actual contract, and document the same contract in the rule README.

Reject unsupported options instead of silently ignoring them.

Implement a fixer only when the native API supports a correct fix, and label fix behavior as `safe`, `unsafe`, or `manual`.

## Emit Actionable Diagnostics

Record the applicable location, rule, severity, concise message, expected fix behavior, and safety label for every finding.

Show before and after text when a fix exists.

Preserve native exit semantics while parsing valid diagnostics independently.

Open [references/linter-conventions.md](references/linter-conventions.md) for detailed diagnostic record fields, machine-readable output, and manifest or fixture contract.

## Cover Installation Axes

Mirror the shipped manifest selectors in every fixture.

Exercise presence and absence boundaries across each production installation axis, including roots, includes, excludes, extensions, visibility, and input availability when those axes apply.

Treat absent input as a tested case, not as an escape path.

## Verify the Adapter

Run valid and invalid cases, inspect diagnostics, and preserve the native exit code.

State which rules the native tool owns and which checks remain package validation.

Return adapter signatures, manifest and fixture paths, diagnostic examples, exit-code evidence, production-axis fixture coverage, and unresolved blockers.
