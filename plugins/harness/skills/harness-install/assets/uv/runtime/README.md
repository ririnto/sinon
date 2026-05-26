# uv Harness Validator

Run `uv run --script docs/harness/uv/harness_check.py`.

## Structural parity

The uv validator mirrors the Gradle harness model through a shared `RuleContext`, a `HarnessCheckRule` protocol, and the enum-style `HarnessCheck` registry in `harness_check.py`. Rules stay in a flat `rules/` package because Python imports and PEP 723 single-file execution do not need Gradle's `rules/fs`, `rules/text`, and `rules/ast` package hierarchy; the registry category and manifest key provide the grouping boundary.
