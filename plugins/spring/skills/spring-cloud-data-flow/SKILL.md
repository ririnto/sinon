---
name: spring-cloud-data-flow
description: >-
  Maintain or troubleshoot existing Spring Cloud Data Flow stream/task estates, app registrations, schedules, and platform operations.
---

# Spring Cloud Data Flow

Use this skill for SCDF server and shell operations around existing stream or task estates.
Application-side dependencies, stream or task code, service discovery, load-balanced clients, and circuit-breaker wiring are separate application-development concerns.

The current public SCDF server artifact is 2.11.5.
The 2.11.x server line is the last open-source release.
Later patches (for example 2.11.7) ship only in the Spring Enterprise repository to Tanzu Spring customers.
Prefer current Spring Cloud Stream or Spring Cloud Task guidance for new application code.

> [!WARNING]
>
> Spring Cloud Data Flow ended open-source development after the 2.11.x line.
> Spring Cloud Deployer (2.9.x) and Spring Statemachine (4.0.x) ended open-source development in the same announcement.
> Future releases are commercial-only and available only to Tanzu Spring customers.
> Keep guidance on the final open-source lines.

## Authority and completion

Confirm the target server, platform account, and requested operation before changing runtime state.
Inspection and preparation do not authorize registration, deployment, launch, scheduling, rollback, or deletion.
Continue operations within an explicit grant without asking again for each authorized step.
After a change, verify the affected runtime state and report observed results or a precise blocker.

## Registration and launch example

Register every app a topology needs before creating and deploying or launching its definition.
Metadata is optional and is only needed when operators must inspect app options or deployment properties.
The stream and task cycles share one command shape.
Only the definition DSL and deploy or launch verb change.

```text
dataflow:>app register --name http --type source --uri maven://org.springframework.cloud.stream.app:http-source-rabbit:3.2.1
dataflow:>app register --name log --type sink --uri maven://org.springframework.cloud.stream.app:log-sink-rabbit:3.2.1
dataflow:>app register --name timestamp --type task --uri maven://io.spring:timestamp-task:3.2.1
dataflow:>stream create --name http-log --definition "http | log"
dataflow:>stream deploy --name http-log
dataflow:>task create --name print-time --definition "timestamp"
dataflow:>task launch --name print-time
dataflow:>stream list
dataflow:>task execution list
```

Import a curated starter catalog with `app import` only when the deployment owns and verifies that catalog.

## Surface map

| Surface | Open when |
| --- | --- |
| Platform setup | [references/platform-setup.md](references/platform-setup.md) |
| Platform accounts | [references/platform-accounts.md](references/platform-accounts.md) |
| App registration and metadata | [references/app-registration-metadata.md](references/app-registration-metadata.md) |
| Runtime operations | [references/runtime-operations.md](references/runtime-operations.md) |
| Composed tasks | [references/composed-tasks.md](references/composed-tasks.md) |
| Schedules | [references/schedules.md](references/schedules.md) |
| Troubleshooting | [references/troubleshooting.md](references/troubleshooting.md) |

## First safe commands

```text
dataflow:>version
dataflow:>app list
dataflow:>stream list
dataflow:>task list
```
