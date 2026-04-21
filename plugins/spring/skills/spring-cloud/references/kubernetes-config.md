# Spring Cloud Kubernetes config

Open this reference when the task depends on ConfigMap-backed Spring Cloud Kubernetes config import or Kubernetes-driven config reload.

## Starter shape

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-kubernetes-client-config</artifactId>
</dependency>
```

Keep this branch separate from the ordinary Config Server path in `SKILL.md`. Use it only when Kubernetes itself is the configuration source of truth.

```yaml
spring:
  config:
    import: kubernetes:
```

```yaml
spring:
  cloud:
    kubernetes:
      config:
        sources:
          - name: orders-config
```

## Reload shape

```yaml
spring:
  cloud:
    kubernetes:
      config:
        reload:
          enabled: true
          mode: EVENT
```

Use `EVENT` mode when the cluster can publish change notifications reliably. Keep reload disabled until the source ConfigMaps, refresh-scoped beans, and rebinding expectations are all stable.

## Gotchas

- Do not combine Kubernetes config import and Config Server import casually in one service unless the precedence order is intentional.
- Do not enable reload until the source configuration set and rebinding expectations are stable.
- Do not assume namespace defaults are safe when the target cluster uses team-specific namespaces.

## Validation rule

Verify the effective ConfigMap source names, namespaces, and reload mode against the target cluster contract.
