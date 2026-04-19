# Spring Cloud Data Flow schedules

Open this reference when the ordinary register-create-launch path in [SKILL.md](../SKILL.md) is not enough and the blocker is recurring SCDF-managed task execution.

Keep schedule definitions explicit and reviewable because they become production automation.

## Schedule boundary

Schedules apply to tasks, not to long-running streams.

Use a schedule only when the workload is naturally launchable as a task and must run repeatedly under SCDF control.

## Schedule lifecycle

```text
dataflow:>schedule create --name nightly-import --task-definition-name import-customers --properties "scheduler.cron.expression=0 0 2 * * *"
dataflow:>schedule list
dataflow:>schedule destroy --name nightly-import
```

Treat schedule creation, inspection, and destruction as part of the operational contract.

## Schedule parameter blocker

Keep task arguments and schedule properties aligned:

```text
dataflow:>schedule create --name nightly-import --task-definition-name import-customers --arguments "--input=file:/data/customers.csv" --properties "scheduler.cron.expression=0 0 2 * * *"
```

If the task contract changes, update both the task definition and the schedule payload intentionally.

## Promotion cautions

- Verify timezone and cron semantics before promoting a schedule.
- Verify the selected platform account and namespace before enabling a recurring task.
- Prefer one clear schedule per operational use case instead of overlapping cron expressions.

## Decision points

| Situation | First check |
| --- | --- |
| Workload must run continuously | use a stream, not a schedule |
| Scheduled task runs with wrong parameters | verify stored schedule arguments and properties |
| Schedule exists but no executions appear | verify the platform account and scheduler support |
