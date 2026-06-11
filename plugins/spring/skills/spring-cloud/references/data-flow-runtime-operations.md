# Spring Cloud Data Flow runtime operations

Open this reference when the ordinary register-create-deploy path in [SKILL.md](../SKILL.md) is not enough and the blocker is deeper runtime inspection, update, undeploy, destroy, or post-change operational verification.

## Post-change verification flow

After any topology or deployment change, inspect registered apps, deployed runtime apps, task executions, and logs or metrics.

```text
dataflow:>app list
dataflow:>runtime apps
dataflow:>task execution list
```

## Stream runtime blocker

Use runtime inspection to verify deployed stream apps, instance count, and current state after deploy, update, or undeploy operations.

```text
dataflow:>stream list
dataflow:>runtime apps
dataflow:>stream undeploy --name http-log
```

## Stream update and rollback blocker

Use stream update and rollback when a deployed stream definition or deploy-time property set must change without losing track of the currently packaged version.

```text
dataflow:>stream update --name http-log --properties "deployer.http.count=3"
dataflow:>stream rollback --name http-log
dataflow:>runtime apps
dataflow:>skipper package list
```

Verify the runtime state and the current Skipper package version together before declaring the change complete.

## Task runtime blocker

Use task-execution inspection to verify launch state, exit status, and execution history.

```text
dataflow:>task execution list
dataflow:>task execution display --id 123
```

## Logs and metrics blocker

Use SCDF runtime views together with the target platform logs or metrics to confirm deployment health after every change.

- Verify the app is deployed or launched where SCDF says it is.
- Verify one runtime signal such as logs, metrics, or status before considering the rollout complete.

## Update and destroy blocker

Treat update, undeploy, and destroy actions as explicit operational transitions.

```text
dataflow:>stream undeploy --name http-log
dataflow:>stream destroy --name http-log
dataflow:>task destroy --name import-customers
```

## Decision points

| Situation | First check |
| --- | --- |
| Deploy succeeded but nothing is running | inspect `runtime apps` and platform logs |
| Stream misbehaves after a deploy-time property change | use `stream rollback` and verify the current Skipper package version |
| Task launched but state is unclear | inspect task execution history |
| Topology changed unexpectedly | verify registered app versions and current definitions |
| Cleanup is required | undeploy first, then destroy deliberately |
