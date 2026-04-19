# Spring Cloud Data Flow platform accounts

Open this reference when the ordinary register-create-deploy path in [SKILL.md](../SKILL.md) is not enough and the blocker is choosing or configuring the target platform account for a deploy or launch.

Keep account selection explicit so operators know where a stream or task will run.

## Account-selection blocker

Choose the platform account before deployment or launch, not after the topology already exists.

- Streams and tasks can target different platform accounts.
- Account choice affects resource quotas, namespaces, credentials, and deployment properties.
- Keep the chosen account visible in the shell command or deployment definition review.

## Deployment-property handoff

Pass platform-specific settings as deployment properties instead of baking them into the application artifact.

```text
dataflow:>stream deploy --name http-log --properties "deployer.http.memory=1024m,deployer.http.count=2"
```

```text
dataflow:>task launch --name import-customers --properties "deployer.import-task.kubernetes.namespace=batch-jobs"
```

## Verification after account selection

After choosing an account:

1. Deploy or launch the workload.
2. Inspect runtime state from SCDF.
3. Verify the target platform actually received the deployment in the expected namespace, space, or cluster context.

## Decision points

| Situation | First check |
| --- | --- |
| Deploy lands on the wrong platform | verify the selected platform account |
| Launch succeeds in SCDF but app is missing on the platform | verify account credentials and target namespace or space |
| Per-app deploy properties seem ignored | verify property key prefixes match the registered app names |
