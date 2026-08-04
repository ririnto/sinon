---
name: instruction-authoring
description: Write lean prompts, hooks, skills, agent instructions, and authorized durable memory. Use when creating or revising behavior contracts for Claude Code or another agent runtime.
---

# Instruction Authoring

## Define the Contract

State the user-visible outcome, scope, authority, acceptance criteria, and stopping condition before procedure.

Include product context only when it changes interpretation or tradeoffs.

Use action verbs for authorized mutation and inspection verbs for assessment-only work.

Describe the destination and constraints while leaving routine path selection to the model.

Reserve absolute language for true invariants, required fields, safety boundaries, and prohibited side effects.

## Write the Delta

- Remove behavior already supplied by the active system prompt, tool definition, client runtime, or a canonical project instruction.
- Assign each surviving rule to one owner and remove synonyms, repeated reminders, obsolete scaffolding, and generic re-check language.
- Resolve contradictions rather than adding another exception.
- Write the desired behavior and add a prohibition only for a concrete observed failure mode.
- Prefer an interface, schema, decision rule, or stopping condition over examples that constrain valid paths.
- Add examples only when representative evaluation shows a remaining format or edge-case gap.
- Request conclusions, concise rationale, evidence, and uncertainty rather than private chain-of-thought.

## Apply Progressive Timing

- Put only common cross-task invariants and important boundaries in session context.
- Put a specialized workflow in one purpose-specific skill.
- Put detail in an owned reference only when the skill names the exact condition for loading it.
- Do not make one workflow require traversal across several skills.
- Do not point a skill at another skill.
- Do not create a chain from one reference to another reference.

Read [Agent Skills format](references/agent-skills-format.md) when creating or changing skill frontmatter, directory layout, resources, or discovery behavior.

## Durable Memory

Create, update, or delete durable user memory only after an explicit user request for a memory change.

Store only a fact or rule that remains useful across future tasks and changes a decision.

Update the canonical entry instead of appending a near-duplicate.

Exclude one-off status, temporary paths, current-session requirements, stale results, duplicated policy, and facts available from the current source on demand.

## Review

Check that each instruction has one owner, each optional detail has one retrieval trigger, and every remaining token materially changes behavior.
