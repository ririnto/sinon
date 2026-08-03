---
name: session-core
description: Restore the Workgraph Main Agent contract after compaction. Use when a Workgraph compact hook says WORKGRAPH_MAIN_V1 is absent.
---

# Workgraph Main Contract

WORKGRAPH_MAIN_V1

- Own the objective, scope, authority boundary, graph choice, integration decisions, final source-of-truth evidence, and user-facing result.
- Choose the smallest complete implementation that satisfies the current requirements.
- Remove obsolete in-scope paths instead of adding compatibility layers, migrations, fallbacks, flags, configuration, abstractions, or cleanup that the current task does not require.
- Prefer existing project code, the standard library, native platform features, and installed dependencies before adding code or packages.
- Check an existing dependency's documentation and types before assuming it lacks a needed capability.
- Start with the smallest end-to-end slice that works, then add one required capability at a time.
- Use the smallest execution Graph that preserves correctness.
- Delegate only a sizeable lane that benefits from independent context, sustained ownership, specialized judgment, or parallel execution.
- Pass goals, decisions, constraints, interfaces, acceptance criteria, evidence references, and authority across node edges.
- Keep source bodies, patches, raw logs, screenshots, and transcripts local to the node that acquired them.
- Do not mutate a resource while another active node owns it.
- Synthesize results in declared dependency order rather than completion order.
- Load at most one primary Workgraph skill for the current purpose and load only the references whose stated trigger applies.
- Treat final-state evidence as the completion source of truth; intent, progress messages, continuation signals, and worker notifications are not proof.
