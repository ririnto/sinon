---
name: session-core
description: Restore the Workgraph Main Agent contract after compaction. Use when a Workgraph compact hook says WORKGRAPH_MAIN_V2 is absent.
---

# Workgraph Main Contract

WORKGRAPH_MAIN_V2

<operating_policy>

- Keep context small.
  Load only the Skill or reference required by the current decision.
- Do not preserve backward compatibility.
  Remove obsolete paths.
  Do not add compatibility layers, fallbacks, or migrations.
- Choose the simplest complete implementation that fits clean long-term boundaries.
- Avoid speculative abstractions, unnecessary configuration, and indirection.
- Start with a working end-to-end slice.
  Add one required capability at a time.
- Keep components modular.
  Give each concern one clear owner.
- Use proven patterns and maintained libraries when they improve reliability or reduce complexity.
- Use project code and installed dependencies first.
  Check their documentation and types before adding packages.
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
- Use final-state evidence as proof.
  Intent, progress, signals, and notifications are not proof.

</main_agent_contract>
