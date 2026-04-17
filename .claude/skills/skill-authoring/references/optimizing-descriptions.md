# Optimizing skill descriptions

> How to improve a skill's `description` so it triggers reliably on relevant prompts. Load this document when a skill is misfiring or when tuning triggers.
>
> This document omits the full eval-driven iteration loop (train/validation split, scripted trigger-rate measurement) from the original source. The principles for writing and diagnosing descriptions are preserved in full.

A skill only helps if it gets activated. The `description` field in `SKILL.md` frontmatter is the primary mechanism agents use to decide whether to load a skill for a given task. An under-specified description means the skill will not trigger when it should; an over-broad description means it triggers when it should not.

## How skill triggering works

Agents use progressive disclosure to manage context. At startup, they load only the `name` and `description` of each available skill — just enough to decide when a skill might be relevant. When a user's task matches a description, the agent reads the full `SKILL.md` into context and follows its instructions.

This means the description carries the entire burden of triggering. If the description does not convey when the skill is useful, the agent will not know to reach for it.

One important nuance: agents typically only consult skills for tasks that require knowledge or capabilities beyond what they can handle alone. A simple, one-step request like 'read this PDF' may not trigger a PDF skill even if the description matches perfectly, because the agent can handle it with basic tools. Tasks that involve specialized knowledge — an unfamiliar API, a domain-specific workflow, or an uncommon format — are where a well-written description makes the difference.

## Writing effective descriptions

Before testing, it helps to know what a good description looks like. A few principles:

- Use imperative phrasing. Frame the description as an instruction to the agent: 'Use this skill when...' rather than 'This skill does...'. The agent is deciding whether to act, so tell it when to act.
- Focus on user intent, not implementation. Describe what the user is trying to achieve, not the skill's internal mechanics. The agent matches against what the user asked for.
- Err on the side of being pushy. Explicitly list contexts where the skill applies, including cases where the user does not name the domain directly: 'even if they do not explicitly mention "CSV" or "analysis".'
- Keep it concise. A few sentences to a short paragraph is usually right — long enough to cover the skill's scope, short enough that it does not bloat the agent's context across many skills. The specification enforces a hard limit of 1024 characters.

Before and after:

```yaml
description: Process CSV files.
```

```yaml
description: Analyze CSV and tabular data files — compute summary statistics, add derived columns, generate charts, and clean messy data. Use this skill when the user has a CSV, TSV, or Excel file and wants to explore, transform, or visualize the data, even if they do not explicitly mention "CSV" or "analysis."
```

The improved description is more specific about what the skill does (summary stats, derived columns, charts, cleaning) and broader about when it applies (CSV, TSV, Excel; even without explicit keywords).

## Designing trigger test queries

To assess whether a description triggers reliably, gather realistic user prompts labeled with whether they should or should not trigger the skill.

```json
[
  { "query": "I've got a spreadsheet in ~/data/q4_results.xlsx with revenue in col C and expenses in col D — can you add a profit margin column and highlight anything under 10%?", "should_trigger": true },
  { "query": "whats the quickest way to convert this json file to yaml", "should_trigger": false }
]
```

Aim for roughly 20 queries: 8-10 that should trigger and 8-10 that should not.

### Should-trigger queries

These test whether the description captures the skill's scope. Vary them along several axes:

- Phrasing: some formal, some casual, some with typos or abbreviations.
- Explicitness: some name the skill's domain directly ('analyze this CSV'), others describe the need without naming it ('my boss wants a chart from this data file').
- Detail: mix terse prompts with context-heavy ones — a short 'analyze my sales CSV and make a chart' alongside a longer message with file paths, column names, and backstory.
- Complexity: vary the number of steps and decision points. Include single-step tasks alongside multi-step workflows to test whether the agent can discern the skill is relevant when the task it addresses is buried in a larger chain.

The most useful should-trigger queries are ones where the skill would help but the connection is not obvious from the query alone. These are the cases where description wording makes the difference — if the query already asks for exactly what the skill does, any reasonable description would trigger.

### Should-not-trigger queries

The most valuable negative test cases are near-misses — queries that share keywords or concepts with your skill but actually need something different. These test whether the description is precise, not just broad.

For a CSV analysis skill, weak negative examples would be:

- `Write a fibonacci function` — obviously irrelevant, tests nothing.
- `What's the weather today?` — no keyword overlap, too easy.

Strong negative examples:

- `I need to update the formulas in my Excel budget spreadsheet` — shares 'spreadsheet' and 'data' concepts, but needs Excel editing, not CSV analysis.
- `can you write a python script that reads a csv and uploads each row to our postgres database` — involves CSV, but the task is database ETL, not analysis.

### Tips for realism

Real user prompts contain context that generic test queries lack. Include:

- File paths (`~/Downloads/report_final_v2.xlsx`).
- Personal context (`my manager asked me to...`).
- Specific details (column names, company names, data values).
- Casual language, abbreviations, and occasional typos.

## Manual triggering check

A lightweight alternative to a scripted eval: paste each test query into the target agent with the skill installed and observe whether the agent invokes it. Because model behavior is nondeterministic, repeat each query a few times (3 runs is a reasonable starting point) and track how often the skill activates.

A should-trigger query passes if it activates the skill on a majority of runs; a should-not-trigger query passes if it does not. Fix the description when either type fails consistently.

## Revising a description

Focus on generalizing. Do not chase individual failing queries word-for-word.

- If should-trigger queries are failing, the description may be too narrow. Broaden the scope or add context about when the skill is useful. Add paraphrases and domain-adjacent terms (the user may not name the domain explicitly).
- If should-not-trigger queries are false-triggering, the description may be too broad. Add specificity about what the skill does NOT do, or clarify the boundary between this skill and adjacent capabilities. A negative-boundary sentence ('Do NOT use for X — use Y instead') often resolves confusion.
- Avoid adding specific keywords from failed queries — that is overfitting. Instead, find the general category or concept those queries represent and address that.
- If you are stuck after several iterations, try a structurally different approach to the description rather than incremental tweaks. A different framing or sentence structure may break through where refinement cannot.
- Check that the description stays under the 1024-character limit — descriptions tend to grow during optimization.

## Applying the result

Once the description triggers reliably:

1. Update the `description` field in the skill's `SKILL.md` frontmatter.
2. Verify the description is under the 1024-character limit.
3. Run a few fresh sanity-check prompts manually — ones that were not part of the tuning process. If those still behave correctly, you have a reasonable signal that the description generalizes.
