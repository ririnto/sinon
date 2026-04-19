# Spring Cloud Data Flow troubleshooting

Open this reference when registration, deployment, launch, logging, or platform behavior is failing and the diagnosis order is unclear.

## Diagnosis order

Check failures in this order:

1. SCDF server connectivity
2. registered app coordinates and versions
3. definition correctness
4. platform account and deployment properties
5. runtime status, logs, and metrics

## Registration blocker

If an app will not register, verify the coordinate, app type, and artifact reachability before changing the topology.

## Deployment blocker

If a stream will not deploy or a task will not launch, verify platform account selection and deployment-property keys before changing the application artifact.

## Runtime blocker

If SCDF shows a deployment but the app is unhealthy, inspect both SCDF runtime state and the target platform logs.

## Decision points

| Situation | First check |
| --- | --- |
| App cannot be registered | verify coordinates, type, and repository reachability |
| Stream deploy fails | verify platform account and deployer property names |
| Task launch fails | verify task arguments, platform account, and app registration |
| Runtime state looks inconsistent | compare SCDF status with platform logs and metrics |
