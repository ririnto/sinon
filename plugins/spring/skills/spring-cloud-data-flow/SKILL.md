---
metadata:
  reference:
    Spring Cloud Data Flow:
      version: 2.11.5
      url: https://docs.spring.io/spring-cloud-dataflow/docs/current/reference/htmlsingle/
name: spring-cloud-data-flow
description: >-
  Maintain or troubleshoot existing Spring Cloud Data Flow stream/task estates, app registrations, schedules, and platform operations.
---

# Spring Cloud Data Flow

Use this skill for SCDF server and shell operations around existing stream or task estates.
Application-side dependencies, stream or task code, service discovery, load-balanced clients, and circuit-breaker wiring are separate application-development concerns.

The official SCDF reference guide documents server artifacts at version 2.11.5 and Spring Boot 3.x support.
That guide does not identify the latest public artifact or distribution terms for later releases.
The `3.2.1` app coordinates below illustrate one catalog snapshot.
For new registrations, check each exact artifact on Maven Central for its latest stable release compatible with the SCDF server and project catalog.
Keep existing app registrations pinned unless the task authorizes changing them.
Verify server artifact coordinates and repository access before recommending a server upgrade.
Prefer current Spring Cloud Stream or Spring Cloud Task guidance for new application code.

## Authority and completion

Confirm the target server, platform account, and requested operation before changing runtime state.
Inspection and preparation do not authorize registration, deployment, launch, scheduling, rollback, or deletion.
Continue operations within an explicit grant without asking again for each authorized step.
After a change, verify the affected runtime state and report observed results or a precise blocker.

## Registration and launch example

Register every app a topology needs before creating and deploying or launching its definition.
For new registrations, check the project catalog and Maven Central for a stable release compatible with the SCDF server.
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
