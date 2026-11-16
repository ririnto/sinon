# Configuration and test examples

## Example 1: Optional adapter and test modules

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.integration</groupId>
        <artifactId>spring-integration-file</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.integration</groupId>
        <artifactId>spring-integration-http</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.integration</groupId>
        <artifactId>spring-integration-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Example 2: New modules in 7.1

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.integration</groupId>
        <artifactId>spring-integration-cloudevents</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.integration</groupId>
        <artifactId>spring-integration-grpc</artifactId>
    </dependency>
</dependencies>
```

## Example 3: HTTP outbound: RestClient

```java
@Bean
IntegrationFlow httpOutboundFlow(RestClient restClient) {
    return IntegrationFlow.from("http.out.input")
        .handle(Http.outboundGateway("/api/orders")
            .restClient(restClient)
            .expectedResponseType(String.class))
        .get();
}
```

## Example 4: Redis: DSL, GETDEL, native CAS/CAD

```java
@Bean
IntegrationFlow redisFlow() {
    return IntegrationFlow.from("redis.in")
        .handle(Redis.outboundEndpoint(redisTemplate())
            .command(Commands.SET).topic("si.test.topic"))
        .get();
}
```

## Example 5: Basic flow shape

```java
@Bean
IntegrationFlow orderFlow(OrderValidator validator, OrderService service) {
    return IntegrationFlow.from("orders.input")
        .transform(OrderCommand.class, command -> command.normalize())
        .filter(OrderCommand.class, validator::isValid)
        .handle(service, "process")
        .get();
}
```

## Example 6: Error channel shape

```java
@Bean
IntegrationFlow integrationErrors() {
    return IntegrationFlow.from("errorChannel")
        .handle(message -> logger.warn("integration failure", message.getPayload()))
        .get();
}
```

## Core baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-integration</artifactId>
    </dependency>
</dependencies>
```
