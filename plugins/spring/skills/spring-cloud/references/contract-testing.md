# Spring Cloud Contract testing

Open this reference when the ordinary config-gateway-client path in `SKILL.md` is not enough and the task is specifically about consumer-producer verification or generated stubs.

This is a testing and compatibility surface, not ordinary remote-call wiring.

## Contract DSL shapes

### Groovy DSL

```groovy
import org.springframework.cloud.contract.spec.Contract

Contract.make {
    request {
        method 'GET'
        url '/orders/1'
    }
    response {
        status OK()
        body([id: 1, status: 'CREATED'])
    }
}
```

### YAML DSL

```yaml
request:
  method: GET
  url: /orders/1
response:
  status: 200
  body:
    id: 1
    status: CREATED
```

## Maven plugin shape

```xml
<plugin>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-contract-maven-plugin</artifactId>
    <extensions>true</extensions>
    <configuration>
        <baseClassForTests>com.example.contract.BaseContractTest</baseClassForTests>
        <contractsDirectory>src/test/resources/contracts</contractsDirectory>
    </configuration>
</plugin>
```

## Generated test modes

- MockMvc for servlet HTTP endpoints
- WebTestClient for reactive endpoints
- JAX-RS only when the stack actually uses it

## Stub Runner shape

```java
@AutoConfigureStubRunner(
    ids = "com.example:orders-service:+:stubs:8081",
    stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
class ConsumerTests {
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Provider verifies HTTP or messaging contract | generated provider tests |
| Consumer wants executable stubs | Stub Runner |
| Reactive provider endpoint | WebTestClient mode |
