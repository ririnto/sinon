---
name: "spring-cloud-data-flow"
description: "Design, deploy, and operate Spring Cloud Data Flow streams and tasks with app registration, stream DSL, task launch, schedules, platform accounts, and pipeline operations. Use this skill when designing, deploying, and operating Spring Cloud Data Flow streams or tasks, including app registration, stream DSL, task launch, schedules, platform accounts, and pipeline operations."
metadata:
  title: "Spring Cloud Data Flow"
  official_project_url: "https://spring.io/projects/spring-cloud-dataflow"
  reference_doc_urls:
    - "https://dataflow.spring.io/docs/"
    - "https://dataflow.spring.io/docs/installation/local/"
  version: "2.11.5"
---

Use this skill when designing, deploying, and operating Spring Cloud Data Flow streams or tasks, including app registration, stream DSL, task launch, schedules, platform accounts, and pipeline operations.

## Boundaries

Use `spring-cloud-data-flow` for SCDF server and shell workflows, stream and task topology design, app registration, deployment properties, and runtime pipeline operations.

- Use `spring-integration` for in-process integration flows inside one application.
- Use batch-job or launchable-task guidance for the internals of a task app itself. SCDF is the orchestration and platform layer around those apps.
- Skipper is the stream-package runtime inside SCDF; use it for stream update and rollback across versions rather than for ordinary task launches.
- Tooling surfaces are the SCDF shell, the SCDF UI, and the SCDF REST API. Pick one consistent surface per operational workflow so deploy, inspect, and rollback steps stay reproducible.

## Common path

The ordinary Spring Cloud Data Flow job is:

1. Decide whether the workload is a long-running stream or a launchable task.
2. Register the source, processor, sink, or task applications with explicit coordinates.
3. Create the stream or task definition and add only the deployment properties actually needed.
4. Deploy or launch it and inspect status, logs, and metrics.
5. For streams, verify the Skipper package state before updating or rolling back a deployed definition.
6. Keep the topology, app versions, and platform account assumptions explicit.

## Dependency baseline

SCDF is primarily an external orchestration platform, not a business-app dependency. Custom apps normally depend on Spring Cloud Stream or Spring Cloud Task rather than an SCDF library.

The current stable SCDF server line is `2.11.5`. Keep examples on the stable GA line unless the task explicitly targets a newer milestone or snapshot.

```text
SCDF server and shell are operated outside the business application.
Custom stream or task apps use their own Spring Boot + Spring Cloud dependencies.
```

## First safe commands

### Register applications

```text
dataflow:>app register --name http --type source --uri maven://org.springframework.cloud.stream.app:http-source-rabbit:3.2.1
dataflow:>app register --name log --type sink --uri maven://org.springframework.cloud.stream.app:log-sink-rabbit:3.2.1
```

### Create and deploy a stream

```text
dataflow:>stream create --name http-log --definition "http | log"
dataflow:>stream deploy --name http-log
```

### Create and launch a task

```text
dataflow:>task create --name import-customers --definition "import-task"
dataflow:>task launch --name import-customers
```

## Coding procedure

1. Pick stream versus task first because deployment, lifecycle, and observability differ.
2. Keep application registration coordinates explicit and versioned.
3. Put platform-specific settings in deployment properties rather than baking them into app code.
4. Keep stream DSL definitions small and composable before introducing large topologies.
5. Treat task parameters and schedule definitions as operational contracts.
6. Verify deployment or launch status after every topology change.

## Implementation examples

### Stream definition

```text
dataflow:>app register --name http --type source --uri maven://org.springframework.cloud.stream.app:http-source-rabbit:3.2.1
dataflow:>app register --name log --type sink --uri maven://org.springframework.cloud.stream.app:log-sink-rabbit:3.2.1
dataflow:>stream create --name http-log --definition "http | log"
dataflow:>stream deploy --name http-log
```

### Stream inspection

```text
dataflow:>stream list
dataflow:>runtime apps
```

### Task app shape

```java
@SpringBootApplication
@EnableTask
public class ImportTaskApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImportTaskApplication.class, args);
    }

    @Bean
    CommandLineRunner importCustomers(CustomerImporter importer) {
        return args -> importer.run();
    }
}
```

This task-app shape assumes a Spring Cloud Task application and therefore keeps `@EnableTask` explicit instead of presenting the app as a plain Boot command-line process.

### Task orchestration shape

```text
dataflow:>task create --name import-customers --definition "import-task"
dataflow:>task launch --name import-customers --arguments "--input=file:/data/customers.csv"
dataflow:>task execution list
```

## Output and configuration shapes

Return these artifacts for the ordinary path:

1. One pinned app-registration command or curated import file
2. One stream or task definition
3. One platform-specific deployment-properties set only when needed
4. One runtime verification command sequence after deploy or launch

### Stream DSL shape

```text
http | log
```

### Task launch argument shape

```text
--input=file:/data/customers.csv
```

### Runtime inspection shape

```text
runtime apps
task execution list
```

## Testing checklist

- Verify app registration points to the intended coordinates and versions.
- Verify stream or task definitions match the intended topology.
- Verify deployments or launches succeed with the chosen platform account.
- Verify one representative runtime inspection command returns the expected deployed or executed state.
- Verify task arguments and deployment properties stay aligned with the custom app contract.

## Production checklist

- Keep registered app coordinates versioned and reviewable.
- Keep stream DSL and task definitions small enough to operate safely.
- Separate platform deployment properties from business-app defaults.
- Verify schedules, platform accounts, and task arguments before promoting to production.
- Treat SCDF shell commands and topology definitions as part of the operational compatibility surface.

## References

- Open [references/platform-setup.md](references/platform-setup.md) when the blocker is installing SCDF or choosing the local, Kubernetes, or Cloud Foundry runtime target before any app registration.
- Open [references/app-registration-metadata.md](references/app-registration-metadata.md) when the blocker is versioned coordinates, bulk import, metadata, or curating registered app catalogs.
- Open [references/platform-accounts.md](references/platform-accounts.md) when the blocker is choosing or configuring the target platform account for a deploy or launch.
- Open [references/schedules.md](references/schedules.md) when the blocker is recurring SCDF-managed task execution.
- Open [references/composed-tasks.md](references/composed-tasks.md) when one task is not enough and SCDF must orchestrate multiple task apps.
- Open [references/runtime-operations.md](references/runtime-operations.md) when the blocker is deeper runtime inspection, stream update or rollback, undeploy, destroy, or post-change operational verification.
- Open [references/troubleshooting.md](references/troubleshooting.md) when registration, deployment, launch, logging, or platform behavior is failing and the diagnosis order is unclear.
