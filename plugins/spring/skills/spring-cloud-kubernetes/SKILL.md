---
name: spring-cloud-kubernetes
description: >-
  Use this skill when the user asks to "use Spring Cloud Kubernetes", "read config from ConfigMaps or Secrets", "use Kubernetes discovery in Spring", "configure Spring Cloud Kubernetes RBAC or namespaces", or needs guidance on Spring Cloud Kubernetes patterns.
---

# Spring Cloud Kubernetes

## Overview

Use this skill to design Spring applications that integrate with Kubernetes runtime metadata, discovery, ConfigMaps, Secrets, namespace behavior, and RBAC-aware configuration. The common case is deciding whether the application needs config loading, discovery, or both, then making namespace and RBAC assumptions explicit. Treat Secrets as a different operational surface from ordinary config.

## Use This Skill When

- You are loading application settings from ConfigMaps or Secrets.
- You are using Kubernetes service discovery in Spring.
- You need explicit namespace and RBAC-aware runtime behavior.
- Do not use this skill when the task is general Kubernetes operations outside Spring app behavior.

## Common-Case Workflow

1. Identify whether the app needs configuration loading, service discovery, or both.
2. Make namespace assumptions explicit.
3. Keep RBAC and service-account needs visible from the start.
4. Use ConfigMaps and Secrets deliberately instead of treating Kubernetes as a generic key store.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-kubernetes-client-config</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-kubernetes-client-discovery</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one explicit config-plus-discovery shape:

```yaml
spring:
  cloud:
    kubernetes:
      discovery:
        namespaces:
          - payments
      config:
        enabled: true
        sources:
          - name: payments-config
```

---

*Applies when:* the app runs in Kubernetes and needs both runtime config loading and service discovery.

## Ready-to-Adapt Templates

ConfigMap-backed configuration — load app settings from a named Kubernetes ConfigMap:

```yaml
spring:
  cloud:
    kubernetes:
      config:
        enabled: true
        sources:
          - name: payments-config
```

Secret-backed configuration — load sensitive values from a named Kubernetes Secret:

```yaml
spring:
  cloud:
    kubernetes:
      secrets:
        enabled: true
        sources:
          - name: db-credentials
```

Namespace-scoped discovery — limit service discovery to one explicit namespace:

```yaml
spring:
  cloud:
    kubernetes:
      discovery:
        namespaces:
          - payments
```

RBAC reader role — minimum role shape for config and discovery reads:

```yaml
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: spring-reader
rules:
  - apiGroups:
      - ""
    resources:
      - configmaps
      - services
      - endpoints
    verbs:
      - get
      - list
      - watch
```

If Secret property sources are enabled, include `secrets` in the RBAC resources alongside `configmaps`.

## Validate the Result

Validate the common case with these checks:

- it is clear whether the app needs config, discovery, or both
- namespace scope is explicit rather than implicit
- RBAC assumptions are visible and match the resources the app must read
- Secrets are treated differently from ordinary ConfigMap-backed values

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| config versus discovery choice, namespace scope, RBAC heuristics, or reload strategy nuance | `./references/kubernetes-patterns.md` |

## Invariants

- MUST make namespace assumptions explicit.
- MUST keep service-account and RBAC needs visible.
- SHOULD separate config-loading concerns from service-discovery concerns.
- MUST treat Secrets differently from ordinary config values.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| leaving namespace selection implicit | the app may read or discover the wrong resources | declare the namespace scope explicitly |
| forgetting RBAC for discovery or config reads | runtime lookups fail even though Spring config looks correct | make read permissions explicit alongside the app config |
| treating ConfigMaps and Secrets as the same operational surface | sensitive data handling becomes sloppy | separate secret handling from ordinary configuration decisions |

## Scope Boundaries

- Activate this skill for:
  - Spring Cloud Kubernetes configuration and discovery
  - namespace and RBAC-aware runtime behavior
  - ConfigMap and Secret integration
- Do not use this skill as the primary source for:
  - generic Kubernetes operations outside Spring app behavior
  - gateway route policy
