---
name: implement
description: Use when implementing a feature, bugfix, refactor, or design change in a repository, before writing implementation code.
---

# Implement

Carry the requested change to completion with the minimum sufficient edit.
Read the shared rules from `../docs/rules.md` relative to this skill directory.
This skill owns the implementation procedure.
The shared rules document owns the rules.
The `review` skill consumes the same rules.

## Procedure

1. Read the root instruction file the active host actually loads, plus the code, tests, configuration, and type definitions the change touches.
   Do not assume a universal instruction filename.
   Discover it per host.
2. Read `../docs/rules.md`, plus the package-local language document and tool document matching each selected language and build profile under change.
3. Record the plan as a GitHub or GitLab issue: intended outcome, scope, affected files, and the proof that will demonstrate completion.
   Keep execution state in agent context, not in disk plan files.
4. Make the smallest complete change at the root cause.
   Reuse existing helpers, patterns, and installed dependencies before adding anything.
   Delegate bounded read-only exploration to the cheapest capable agent when the host supports it.
5. Run the narrowest existing checks that exercise the changed behavior and record commands with exit codes.
   Do not add tests that mirror the implementation without protecting a real acceptance criterion.
6. Remove replaced code, fallbacks, scratch files, and obsolete paths in the same change.
7. Report outcome, changed paths, evidence with its class, and any named gaps.
   Output fields are recommended choices.
   The task or host requirement wins over any fixed schema.

## Decisions

- Weigh time pressure against correctness: never simplify away input validation at trust boundaries, data-loss prevention, security measures, or accessibility basics.
- When a rule blocks progress, name the file, quote the rule, and continue with user intent taking priority.
- Treat conflicting shipped guidance as a defect to report, not an instruction to follow.

## Update Triggers

Update this skill when the implementation procedure changes.
Update `docs/rules.md` when a rule changes.
