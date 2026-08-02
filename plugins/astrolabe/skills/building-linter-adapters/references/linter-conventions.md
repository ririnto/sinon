# Linter Conventions

Open this reference when native-tool selection, detailed diagnostic records, machine-readable output, or fixture-axis implementation needs more detail than the owning skill carries.

## Native Tool Matrix

Use ktlint with native custom rules for Kotlin, Spotless or the native formatter for Maven, Ruff for Python, and oxlint with oxfmt for Bun and TypeScript.

Use `bunx` for oxlint plugin loading when the repository provides that path.

## Diagnostic Records

Expose `file`, `line`, `column`, `rule`, `severity`, `message`, `expectedFix`, and `fixSafety` fields when those values apply.

Directory findings state why line and column do not apply, while file findings carry the location, rule, severity, concise message, expected fix behavior, and safety label fields.

Machine-readable output starts with a parseable record and ends with checked-file and violation totals.

Preserve the native linter exit code while parsing valid diagnostics independently.

## Supported Option Families

Expose only fields that the adapter supports.

- Scope: `sourceRootsPerStack`, `includePathsPerStack`, `excludePathsPerStack`, `extensionsPerStack`, and `visibilityPerStack`.
- Fix: `autoFormat`, `fixSafetyDefault`, `fixDescriptionTemplate`, `previewLines.before`, and `previewLines.after`.
- Output: `snippetContextLines`, `groupBy`, `showHelp`, `showBefore`, `showAfter`, and `failOn`.
- Suppression: `inlineSuppressionTokens`, `suppressionScope`, and `requireSuppressionReason`.
- Messages: `messages.rule`, `messages.fix`, and `messages.help`.
- Localization: `locale` and `colorize`.

A rule manifest documents the supported fields, types, allowed values, and defaults from the actual adapter contract.

Do not copy generic defaults into a manifest or README when the adapter does not support that option.

## Manifest and Fixture Contract

Fixtures use the shipped selector shape and cover each production installation axis, including presence and absence boundaries.

Exercise absent input as a real case rather than treating it as an escape path.
