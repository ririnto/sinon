# Description Design

Open this file when the skill already has the right scope but the `description` text is still too vague, too broad, or hard to trigger correctly.

The description is the primary trigger signal in most skill-loading models, so optimizing it is part of authoring, not an afterthought.

## Default pattern

Use this structure:

```text
[Verb phrase describing the capability]. Use when [trigger conditions, user intents, file types, systems, or goals].
```

Example:

```text
Generate production-ready OpenAPI operation blocks and validation notes. Use when designing REST endpoints, function tool schemas, or contract-driven API docs.
```

## What good descriptions contain

- the actual job
- the best trigger conditions
- domain-specific keywords
- concrete nouns
- concrete file or system names
- wording that makes the skill feel decisive rather than generic

## Specificity ladder

Use this sequence when tuning a description:

1. Start with the job in verb form.
2. Add the trigger condition with 'Use when ...'.
3. Add two or three concrete nouns users are likely to mention.
4. Remove host names, team jargon, and extra adjacent jobs.
5. Test again with nearby prompts.

## Weak pattern

```text
Helps with APIs.
```

Problems:

- too short
- unclear trigger
- missing output expectation
- low discoverability

## Strong patterns

### File-type driven

```text
Extract and normalize tables from CSV, TSV, and spreadsheet exports. Use when the task involves messy tabular data, column mapping, or data cleaning before analysis.
```

### Workflow driven

```text
Prepare a release candidate by updating versions, drafting notes, and validating deploy prerequisites. Use when cutting a release branch or preparing a tagged release.
```

### Tooling driven

```text
Design strict schemas, request payloads, and tool-facing config files for automation workflows. Use when building schema-driven agent tooling or contract-heavy prompts.
```

## Offline trigger-testing method

Test the description without any external validator:

1. Write three prompts that should activate the skill.
2. Write three adjacent prompts that should route elsewhere.
3. Compare the wording in those prompts with the actual description.
4. Add missing nouns or triggers.
5. Remove any wording that causes false matches.

Example:

| Prompt type | Prompt | Expected result |
| --- | --- | --- |
| Should match | 'Refactor this skill so the main workflow stays in SKILL.md.' | Match |
| Should match | 'Write a new offline-ready agent skill with additive references only.' | Match |
| Should not match | 'Review the quality of this model response.' | No match |
| Should not match | 'Create a product-specific Claude command.' | No match |

> These examples use the skill-authoring domain for illustration. Substitute your own domain's prompts when testing a different type of skill.

## Common mistakes

- naming a domain without the task
- naming the task without the trigger
- writing marketing copy instead of operational guidance
- exceeding the strict common-denominator length limit
- stuffing platform-specific fields into the main `description`
- widening the description until unrelated prompts begin to match

## Reusable checklist

Ask:

1. Could another engineer choose this skill correctly from the description alone?
2. Does it mention both what and when?
3. Does it include likely search terms from user prompts?
4. Is it still valid if the skill is loaded outside Claude?
5. Does it describe one coherent job rather than several adjacent jobs?
6. Is it 1024 characters or less?
