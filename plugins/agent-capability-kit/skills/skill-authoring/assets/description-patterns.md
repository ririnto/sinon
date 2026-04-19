# Description Patterns

## Recommended formula

```text
[Primary capability]. Use when [task, inputs, systems, file types, or user intent].
```

## Specificity calibration

Aim for the narrowest wording that still catches the intended prompts.

- Too broad: matches several adjacent jobs and gives weak routing signals
- Too narrow: depends on one host, vendor, team name, or niche phrasing that users may never write
- Good calibration: names the job, the trigger conditions, and a few likely nouns without locking to one product

## Strong examples

- Review SQL queries for performance, correctness, and unsafe mutations. Use when editing migrations, dashboards, or production reporting queries.
- Draft reproducible incident summaries from logs, alerts, and timelines. Use when writing postmortems, status updates, or executive summaries after an outage.
- Build strict JSON schemas and example payloads for tool-calling workflows. Use when implementing agent-facing APIs, config-driven tooling, or contract-heavy automation.

## Weak examples

- Helps with SQL.
- API helper.
- General review skill.

## Rewriting examples

| Weak | Better |
| --- | --- |
| Helps with docs. | Draft API and product documentation from source files, examples, and changelogs. Use when updating developer docs or release notes. |
| Works on tests. | Add or repair focused automated tests for changed code paths. Use when a patch needs regression coverage or CI failures show missing assertions. |
| Security skill. | Review code and configurations for secrets exposure, unsafe network calls, and privilege risks. Use when changing auth, infrastructure, CI, or external integrations. |

## Offline trigger test

Run this quick test before you keep a description:

1. Read only the `description`, not the title.
2. Write two prompts that should activate the skill.
3. Write two nearby prompts that should not activate it.
4. Revise until the intended prompts fit and the nearby prompts do not.

## Repair moves

- Add concrete nouns when the description feels generic.
- Add the user intent or file type when the trigger feels fuzzy.
- Remove product names when the description feels locked to one host.
- Remove extra capabilities when the description advertises several adjacent jobs.
