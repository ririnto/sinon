---
name: description-design
description: Tune Agent Skill descriptions for trigger precision without adding workflow summaries.
---

# Description Design

Open this file when the skill already has the right scope but `description` text is still too vague, too broad, or hard to trigger correctly.

## Core rule

The description is activation metadata. It should tell the loader when to use the skill, not summarize the whole workflow.

## Default pattern

```text
[Imperative capability]. Use when [trigger conditions, user intents, file types, systems, or goals not already named in the capability].
```

The trigger clause is optional. Include it only when it adds vocabulary distinct from the capability statement, such as artifact names, domain terms, timing cues, or alternate phrasing users commonly use.

## Examples

Example with a trigger clause:

```text
Generate production-ready OpenAPI operation blocks and validation notes. Use when designing REST endpoints, function tool schemas, or contract-driven API docs.
```

Example without a trigger clause:

```text
Extract and normalize tables from CSV, TSV, and spreadsheet exports.
```

Weak example:

```text
Helps with APIs.
```

Problems with the weak example:

- It does not name the artifact or outcome.
- It has no concrete trigger vocabulary.
- It is too broad to exclude nearby jobs.

## Specificity ladder

1. Start with the job in verb form.
2. Add two or three concrete nouns users are likely to mention.
3. Add a `Use when ...` clause only if it contributes new routing vocabulary.
4. Remove host names, team jargon, and extra adjacent jobs.
5. Test against nearby prompts.

## Strong patterns

File-type driven:

```text
Extract and normalize tables from CSV, TSV, and spreadsheet exports. Use when task involves messy tabular data, column mapping, or data cleaning before analysis.
```

Workflow driven:

```text
Prepare a release candidate by updating versions, drafting notes, and validating deploy prerequisites. Use when cutting a release branch or preparing a tagged release.
```

Tooling driven:

```text
Design strict schemas, request payloads, and tool-facing config files for automation workflows. Use when building schema-driven agent tooling or contract-heavy prompts.
```

## Offline trigger test

1. Write three prompts that should activate the skill.
2. Write three adjacent prompts that should route elsewhere.
3. Compare the wording in those prompts with the description.
4. Add missing nouns or triggers.
5. Remove wording that causes false matches.

## Final checks

- Does the description name one coherent job?
- Does it avoid workflow-summary shortcuts?
- Is it valid outside one host product unless the skill is host-specific?
- Is it 1024 characters or less?
- Would another engineer load it for intended prompts and avoid it for nearby prompts?
