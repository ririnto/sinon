---
name: spring-cloud-data-flow
description: >-
  Operate Spring Cloud Data Flow stream and task estates with app registration, stream DSL, task launch, schedules, platform accounts, runtime operations, and troubleshooting.
  Use when maintaining existing SCDF servers, Skipper packages, registered apps, composed tasks, platform accounts, or scheduled task launches.
---

# Spring Cloud Data Flow

Use `spring-cloud-data-flow` for SCDF server and shell operations around existing stream or task estates.
Use `spring-cloud` for application-side Spring Cloud dependencies, Spring Cloud Stream, Spring Cloud Task, service discovery, load-balanced clients, and circuit-breaker wiring.

The current public SCDF server artifact line is 2.11.5.
The 2.11.x server line is the last open-source release.
Later patches (for example 2.11.7) ship only in the Spring Enterprise repository to Tanzu Spring customers.
Prefer current Spring Cloud Stream or Spring Cloud Task guidance for new application code.

> [!WARNING]
>
> Spring Cloud Data Flow ended open-source development after the 2.11.x line.
> Spring Cloud Deployer (2.9.x) and Spring Statemachine (4.0.x) ended open-source development in the same announcement.
> Future releases are commercial-only and available only to Tanzu Spring customers.
> Keep guidance on the final open-source lines.

## Common path

1. Verify the SCDF server, Skipper server, and target platform are reachable from the same operational context.
2. Register stream or task applications with explicit coordinates, metadata, and version names.
3. Create stream or task definitions only after platform accounts and runtime properties are known.
4. Launch, schedule, update, or roll back definitions through SCDF runtime operations.
5. Verify deployed application state with SCDF views and the target platform logs or metrics.

## Ordinary path

Register the apps a topology needs, then create and deploy or launch the definition.
The stream and task cycles share one command shape; only the definition DSL and the deploy or launch verb change.

```text
dataflow:>app register --name http --type source --uri maven://org.springframework.cloud.stream.app:http-source-rabbit:3.2.1
dataflow:>app register --name log --type sink --uri maven://org.springframework.cloud.stream.app:log-sink-rabbit:3.2.1
dataflow:>stream create --name http-log --definition "http | log"
dataflow:>stream deploy --name http-log
dataflow:>task create --name print-time --definition "timestamp"
dataflow:>task launch --name print-time
dataflow:>stream list
dataflow:>task execution list
```

Register task apps with the same `app register` command using `--type task`, or import a curated starter catalog with `app import`.

## Surface map

| Surface | Open when |
| --- | --- |
| Platform setup | [references/data-flow-platform-setup.md](references/data-flow-platform-setup.md) |
| Platform accounts | [references/data-flow-platform-accounts.md](references/data-flow-platform-accounts.md) |
| App registration and metadata | [references/data-flow-app-registration-metadata.md](references/data-flow-app-registration-metadata.md) |
| Runtime operations | [references/data-flow-runtime-operations.md](references/data-flow-runtime-operations.md) |
| Composed tasks | [references/data-flow-composed-tasks.md](references/data-flow-composed-tasks.md) |
| Schedules | [references/data-flow-schedules.md](references/data-flow-schedules.md) |
| Troubleshooting | [references/data-flow-troubleshooting.md](references/data-flow-troubleshooting.md) |

## First safe commands

```sh
dataflow version
dataflow app list
dataflow stream list
dataflow task list
```

## Output contract

Return:

1. The SCDF operation or configuration shape
2. The affected stream, task, app registration, schedule, or platform account
3. Verification commands and observed runtime state
4. Remaining platform, version, or deployment risks
