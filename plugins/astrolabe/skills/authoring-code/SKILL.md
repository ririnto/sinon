---
name: authoring-code
description: Use when editing TypeScript, JavaScript, Python, Kotlin, Markdown, shell, YAML, or package code that needs language-scoped source conventions.
---

# Authoring Code

## Match Rules to the Language

Identify the file language, runtime, formatter, linter, and validation scope before editing.

Apply a rule only where the language and its native tool can express it.

Do not force one language's indentation, import, collection, or path convention onto another language.

Use concrete types, portable syntax, and native formatters instead of cross-language imitation.

## Preserve Program Meaning

Prefer immutable construction and keep function-like bodies free of blank physical lines.

Preserve side-effect order, type meaning, lazy evaluation, exception clarity, chaining, branch clarity, and domain meaning when simplifying code.

Use structural JSDoc in `.js`, `.mjs`, and `.cjs` files rather than vague object types.

Use the UTF-8 marker required by the file's shebang and runtime convention.

Use native language collection builders and path imports when the language reference requires them.

## Enforce the Package You Author

Apply these rules to the package or plugin being authored, including its source, manifests, hooks, skills, references, and documentation.

Check the package's own examples and supporting assets before reporting completion.

## Run the Owning Checks

Run the narrow formatter or linter that owns each changed rule and report the commands that exercised it.

Identify any rule that remains prose-only because the native tool cannot express it.

## Return Evidence

Return changed files, signatures, style decisions, formatter or linter evidence, and blockers.

Open [references/language-rules.md](references/language-rules.md) for language-specific markers, builders, imports, and naming rules.
