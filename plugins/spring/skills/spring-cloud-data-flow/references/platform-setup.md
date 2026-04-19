# Spring Cloud Data Flow platform setup

Open this reference when the blocker is installing SCDF or choosing the local, Kubernetes, or Cloud Foundry runtime target before any app registration.

## Platform-target blocker

Choose the runtime target first because installation shape, platform accounts, and deployment properties depend on it.

- local: fastest for single-node evaluation and development
- Kubernetes: default choice when streams and tasks must run on a cluster
- Cloud Foundry: use when the platform already standardizes on CF deployment

## Setup rule

Do not register or deploy apps until the SCDF server, Skipper or task platform, and target runtime are reachable from the same operational context.

## Verification checklist

- Verify the SCDF server is reachable.
- Verify the shell or UI can talk to the server.
- Verify the target platform account exists before the first deploy or launch.
- Verify one trivial app can be registered before curating larger catalogs.

## Decision points

| Situation | First check |
| --- | --- |
| Unsure where workloads will run | choose the runtime target first |
| SCDF shell connects but deploys fail immediately | verify platform target and account setup |
| Local evaluation is the goal | start with the local platform before cluster targets |
