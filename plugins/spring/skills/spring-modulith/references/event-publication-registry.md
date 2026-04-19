# Spring Modulith event publication registry

Open this reference when module events must be tracked, replayed, or completed reliably after failures.

## Registry boundary

Use the event publication registry when module reactions must survive failures or be inspected operationally.

## Starter shape

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-jpa</artifactId>
</dependency>
```

## Completion mode shape

```yaml
spring:
  modulith:
    events:
      completion-mode: update
```

## Publication lifecycle shape

```java
IncompleteEventPublications publications = registry.findIncompletePublications();
```

```java
FailedEventPublications failures = registry.findFailedPublications();
failures.resubmit();
```

Use the registry lifecycle views when operations must inspect incomplete or failed publications and intentionally resubmit them.

## Starter choices

- Use the JPA starter when the application already persists operational state with JPA.
- Use the JDBC, MongoDB, or Neo4j starter only when that persistence technology already anchors the application runtime.
- Keep the common path on in-memory event publication unless failure recovery or operational visibility is a real requirement.

## Decision points

| Situation | Use |
| --- | --- |
| Event handling must be tracked across failures | publication registry |
| Simple in-memory event collaboration is enough | stay on the common path |
| Operations must inspect stale publications | registry plus operational visibility |
