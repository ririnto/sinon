# Quality Checklist

Use this checklist before finalizing a generated `SPEC.md`. Treat the checklist as a requirements-quality gate, not as an implementation test plan.

## Content Quality

- [ ] The spec has a clear title, status, creation date, and owner or responsible role.
- [ ] The summary explains what is being specified and why it matters in one to three sentences.
- [ ] The problem is stated before any proposed solution or design details.
- [ ] Goals describe outcomes, not implementation tasks.
- [ ] Non-Goals are present, concrete, and useful for preventing scope creep.
- [ ] In-scope and out-of-scope boundaries are explicit.
- [ ] Validated assumptions come from user-provided context, authorized source material, or explicit approval.
- [ ] Optional sections appear only when relevant.
- [ ] No `N/A`, `TBD`, filler, or unresolved template placeholder remains.

## Requirement Quality

- [ ] Every functional requirement has a stable ID such as `FR-001`.
- [ ] Every non-functional requirement has a category ID such as `NFR-SEC-001`.
- [ ] Requirements use RFC 2119 language deliberately.
- [ ] Requirements describe observable behavior or a real constraint.
- [ ] Requirements avoid vague words unless quantified or explained.
- [ ] Requirements avoid implementation details unless the implementation is mandated.
- [ ] Each requirement expresses one behavior or constraint.
- [ ] No requirement silently embeds an unresolved assumption.

## Scenario and Acceptance Quality

- [ ] Every functional requirement has at least one Given/When/Then scenario or a stated reason it cannot be scenario-tested.
- [ ] Acceptance criteria are testable and observable.
- [ ] Negative cases are included for invalid, missing, unauthorized, duplicate, or malformed inputs when relevant.
- [ ] Boundary cases are included for limits, thresholds, sizes, timeouts, and state transitions when relevant.
- [ ] Scenarios do not depend on hidden context outside the spec.

## Non-Functional Quality

- [ ] Performance, security, privacy, reliability, availability, observability, usability, and compliance needs were considered.
- [ ] Included NFRs have metrics, thresholds, or verification methods.
- [ ] Policy constraints are separated from quality thresholds.
- [ ] Security, privacy, safety, or compliance blockers are not treated as optional quality goals.

## Success and Traceability

- [ ] Success criteria are measurable or have an explicit verification method.
- [ ] Success criteria include user, business, operational, or compliance outcomes when relevant.
- [ ] Every acceptance criterion traces to a requirement.
- [ ] Every requirement traces to a scenario, success criterion, source, or open question.
- [ ] Source or evidence is captured for requirements when stakeholder input, external constraints, or existing behavior matters.
- [ ] Verification method is explicit for each requirement or acceptance criterion when testability is not obvious.
- [ ] Open questions are answerable and have an owner or decision point when needed.

## AI-Agent Readiness

- [ ] The spec is self-contained enough for a future agent to use without guessing.
- [ ] The spec avoids mixing WHAT/WHY with HOW unless design is explicitly in scope.
- [ ] The spec gives enough boundaries to prevent feature inflation.
- [ ] The spec does not instruct an agent to mine repository-local docs unless authorized by the user.
- [ ] The spec is short enough to read in one pass, or large details are moved to referenced sub-specs or appendices.

## Failure Response

If a checklist item fails, revise the spec before finalizing it. If revision requires human judgment, record a focused open question instead of guessing.
