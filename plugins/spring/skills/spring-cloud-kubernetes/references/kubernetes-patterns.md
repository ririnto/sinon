---
title: Spring Cloud Kubernetes Patterns Reference
description: >-
  Reference for Spring Cloud Kubernetes version boundaries, reload strategy, and operational alignment.
---

Use this reference when the app is already known to run in Kubernetes and the remaining question is how Spring should integrate with that environment.

## Version Boundary Note

For newer Spring Cloud Kubernetes baselines, prefer Spring Boot cloud-platform detection and current starter behavior over older `spring.cloud.kubernetes.enabled` assumptions. That property was removed in recent releases; relying on it creates upgrade risk. Verify which baseline your project targets before writing new guidance.

## Reload Strategy Nuance

State whether config changes should be observed only on restart or should trigger a runtime refresh. This is an operational decision, not a technical default.

- **restart-driven config** is simpler and safer when the application does not need live reload. Every restart picks up the new ConfigMap values automatically.
- **runtime refresh** (via `@RefreshScope` or the refresh endpoint) adds operational complexity: you must reason about when refresh is safe for in-flight requests and whether all beans behave correctly under mid-run reinitialization. Do not assume runtime reload is free.

Choose restart-driven by default and reach for runtime refresh only when the operational cost has been explicitly reasoned through.

## Probe and Shutdown Alignment

Keep Spring Boot health groups and Kubernetes rollout behavior aligned:

- readiness should reflect dependency availability that matters before traffic arrives; if readiness returns `DOWN` only after all beans are initialized, Kubernetes will not route traffic to unhealthy instances
- liveness should stay narrow enough to avoid unnecessary restarts; including slow or eventually-consistent dependencies in liveness can cause Kubernetes to kill pods that are recovering
- graceful shutdown expectations should match probe and deployment timing; if the terminationGracePeriodSeconds is shorter than the time needed for in-flight requests to complete, some requests will be dropped

## ConfigMap and Secrets Property Source Examples

ConfigMap as a property source — bootstrap-style configuration:

```yaml
spring:
  cloud:
    kubernetes:
      config:
        enabled: true
        sources:
          - name: app-config
            namespace: production
```

Secrets property source — each key in the Secret becomes a property:

```yaml
spring:
  cloud:
    kubernetes:
      secrets:
        sources:
          - name: db-credentials
            namespace: production
```

Secrets hold single-value entries. Under property binding, a Secret key maps directly to a property value. Do not treat Secrets as a flat key-value map with nested structure; the behavior differs from ConfigMaps.

## Reload Strategy Examples

Restart-driven config — simpler; every pod restart picks up new ConfigMap values:

```yaml
spring:
  cloud:
    kubernetes:
      config:
        enabled: true
        sources:
          - name: payments-config
```

`@RefreshScope` runtime reload — requires the `spring-cloud-starter-kubernetes-client-config` and the refresh endpoint:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: refresh
```

```java
@RefreshScope
@Configuration
class PaymentsConfig {
    @Value("${payment.batch-size:10}")
    int batchSize;
}
```

After updating the ConfigMap, call `POST /actuator/refresh` to reload beans annotated with `@RefreshScope`. Verify that in-flight requests and mid-run reinitialization are safe before deploying this to production.

## RBAC Examples

Role — grants read permissions on specific resource types:

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

RoleBinding — binds the Role to a ServiceAccount in a namespace:

```yaml
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: spring-reader-binding
subjects:
  - kind: ServiceAccount
    name: payments-app
    namespace: production
roleRef:
  kind: Role
  name: spring-reader
  apiGroup: rbac.authorization.k8s.io
```

ClusterRole + ClusterRoleBinding — when the app needs to read across namespaces:

```yaml
kind: ClusterRole
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: spring-cluster-reader
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
---
kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: spring-cluster-reader-binding
subjects:
  - kind: ServiceAccount
    name: payments-app
    namespace: production
roleRef:
  kind: ClusterRole
  name: spring-cluster-reader
  apiGroup: rbac.authorization.k8s.io
```

## Namespace Checklist

Before deploying, confirm:

- namespace scope is declared explicitly and the app's service account can read the required resources in that namespace
- RBAC rules grant `get`, `list`, and `watch` on the specific resource types the app needs (ConfigMaps, Services, Endpoints); do not grant broader permissions than required
- Secrets are accessed through the Secrets property source, not treated as ordinary ConfigMap values; Secrets values are single-value by design and behave differently under property binding

## Common Pitfalls

- implicit namespace assumptions; when namespace is not declared, the app defaults to the namespace of the pod, which may not match the intended config or discovery scope
- missing RBAC for config or discovery calls; a missing role binding causes runtime lookup failures that are hard to distinguish from application bugs
- treating Secrets and ConfigMaps as the same operational surface; Secrets require different handling and should not be casually substituted for encrypted ConfigMap values
