---
title: Skill Description Routing Reference
description: >-
  Reference for calibrating skill descriptions, trigger phrases, and should-trigger versus should-not-trigger examples.
---

Use this reference when the blocker is not skill structure itself, but making the `description` route cleanly and predictably as the first trigger in the agentskills.io loading hierarchy. This file is sufficient on its own for that calibration.

## Routing Goal

The `description` is not a slogan. It is the routing key that tells the agent when to load the skill before `SKILL.md` or any reference file is read.

Prefer this shape:

```yaml
description: >-
  Use this skill when the user asks to write a new skill, review an existing skill, tighten a skill description, or decide what belongs in SKILL.md versus references.
```

Avoid this shape:

```yaml
description: >-
  Skill authoring internals and repository conventions.
```

The correct description names user-visible tasks and outcomes. The incorrect description names internals without saying when the skill should activate.

## Trigger Calibration Rules

- Prefer verb-led task language such as write, review, refactor, tighten, split, or validate.
- Include 3-6 representative triggers, not an exhaustive catalog.
- Name adjacent-domain exclusions only when they are likely confusions.
- Prefer natural phrases a user would say over taxonomy labels a maintainer would invent.
- Keep the description precise enough that unrelated tasks do not activate it by accident.
- If host or platform deltas do not change the ordinary user job, keep one shared skill description instead of splitting routing by host name alone.

## Should-Trigger Examples

- "Write a new repository skill for database migrations."
- "Review this SKILL.md and tighten its scope boundaries."
- "Help me split this skill between common path and references."
- "The description is vague. Rewrite it so routing works better."
- "Make this skill consistent with the rest of the repo."

## Should-Not-Trigger Examples

- "Explain how PostgreSQL indexes work."
- "Fix this failing migration test."
- "Build a CLI command for syncing assets."
- "Review the application architecture."
- "Write a generic system prompt for a support bot."

## Description Review Checklist

- Does the description say when to use the skill, not just what topic it covers?
- Would a user naturally phrase the request in similar terms?
- Are the included triggers part of one coherent unit of work?
- Does the wording avoid broad nouns that could match unrelated tasks?
- Do the exclusions clarify boundaries without depending on cross-skill routing by name?

## Common Failure Patterns

| Failure | Why it breaks routing | Correction |
| --- | --- | --- |
| topic-only wording | the router sees a subject but not an activation condition | rewrite in task language with “Use this skill when...” |
| trigger list that mixes unrelated jobs | matching becomes noisy | split the skill or narrow the description |
| internal repo jargon only | users and routers do not share the same terms | rewrite in plain task language first, then add one repo-specific cue if needed |
| overly broad words such as "improve" or "help" | too many requests become plausible matches | anchor on the repeatable job and outcome |
| separate host-specific descriptions for the same ordinary task | routing splits too early and duplicates the shared path | keep one shared description and branch into focused references only when the exact host delta is the blocker |
