# Spring Cloud Bus refresh

Open this reference when the ordinary config-gateway-client path in `SKILL.md` is not enough and the platform actually needs distributed refresh or event propagation.

## Transport dependency shapes

### AMQP transport

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bus-amqp</artifactId>
</dependency>
```

### Kafka transport

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bus-kafka</artifactId>
</dependency>
```

## Bus actuator endpoints

```properties
management.endpoints.web.exposure.include=busrefresh,busenv
```

### Refresh all instances

```text
POST /actuator/busrefresh
```

### Update one environment value

```text
POST /actuator/busenv
```

Body shape:

```json
{
  "name": "feature.flag",
  "value": "true"
}
```

## Addressing shape

Targeting uses a bus id derived from service id and instance id.

```text
/actuator/busrefresh/orders-service:123
```

## Decision points

| Situation | Use |
| --- | --- |
| Distributed refresh after config change | Bus refresh |
| One-off value propagation across instances | Bus env |
| No cross-instance refresh need | do not add Bus |
