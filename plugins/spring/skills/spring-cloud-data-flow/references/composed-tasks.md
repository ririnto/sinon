# Spring Cloud Data Flow composed tasks

Open this reference when one task is not enough and the blocker is SCDF orchestration across multiple task applications.

Do not build a composed task when one batch or task app can already own the whole workflow safely.

## Composition boundary

Use composed tasks only when the workflow is naturally made of separate launchable task apps with explicit sequencing or transitions.

## Basic composition shape

```text
dataflow:>task create --name import-pipeline --definition "load-data && validate-data && publish-data"
dataflow:>task launch --name import-pipeline
```

Keep the definition small enough that each subtask remains independently operable and testable.

## Inspection blocker

After launch, verify both the parent composed-task execution and the child task executions.

```text
dataflow:>task execution list
```

Check that failure or success states propagate the way operators expect.

## Limits versus one task app

Prefer one task app when:

- the workflow shares one transactional boundary,
- the steps are tightly coupled in one codebase,
- or operators do not need per-subtask visibility.

Use composed tasks when operational sequencing and per-task isolation are the main need.

## Decision points

| Situation | First check |
| --- | --- |
| Workflow is small and tightly coupled | keep one task app |
| Operators need step-by-step task visibility | consider a composed task |
| Failure handling is unclear | verify parent and child execution semantics before rollout |
