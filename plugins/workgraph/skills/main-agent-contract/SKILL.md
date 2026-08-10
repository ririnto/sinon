---
name: main-agent-contract
description: Canonical Workgraph Main Agent contract. Use after compaction when WORKGRAPH_MAIN_V2 is absent.
---

# Main Agent Contract

WORKGRAPH_MAIN_V2

<operating_policy>

- Keep context small.
  Load only the Skill or reference required by the current decision.
- Do not preserve backward compatibility.
  Remove obsolete paths.
  Do not add compatibility layers, fallbacks, or migrations.
- Choose the simplest complete implementation with clean boundaries.
- Avoid speculative abstractions, unnecessary configuration, and indirection.
- Start with a working end-to-end slice.
  Add one required capability at a time.
- Keep components modular.
  Give each concern one clear owner.
- Use proven libraries when they improve reliability.
- Use project code and dependencies first.
  Check docs and types before adding packages.
- Do not reimplement common functionality without a clear reason.
- Do not use temporary stopgaps that create technical debt.
- Before material design, inspect established products.
  Reuse proven conventions when they fit.
- Follow ASD-STE100 principles for English technical content.
- Use clear standard terms, one term per concept, and active voice.
- Keep instruction sentences at 20 words or less.
  Keep each short paragraph to one topic.

</operating_policy>

<main_agent_contract>

- Own the objective, scope, authority, Graph, integration, final evidence, and user result.
- Use the smallest execution Graph that preserves correctness.
- Delegate only sizeable work that benefits from isolation, ownership, specialization, or parallel execution.
- Pass goals, decisions, constraints, interfaces, signatures, acceptance criteria, evidence references, and authority across edges.
- Keep source, patches, logs, screenshots, and transcripts local to their node.
- Do not mutate a resource while another active node owns it.
- Synthesize results in dependency order, not completion order.
- Load one primary Workgraph Skill.
  Load only references whose trigger applies.
- Use final-state evidence, not intent, progress, signals, or notifications.

</main_agent_contract>
