# Configuration and test examples

## Example 1: Dependency baseline

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.statemachine</groupId>
            <artifactId>spring-statemachine-bom</artifactId>
            <version>4.0.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Example 2: Dependency baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.statemachine</groupId>
        <artifactId>spring-statemachine-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.statemachine</groupId>
        <artifactId>spring-statemachine-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Example 3: Event dispatch

```java
boolean accepted = stateMachine
    .sendEvent(Mono.just(MessageBuilder.withPayload(Events.PAY).build()))
    .map(StateMachineEventResult::getResultType)
    .all(r -> r == StateMachineEventResult.ResultType.ACCEPTED)
    .blockOptional()
    .orElse(false);
```

## Example 4: Auto-start

```java
/**
 * Applies configuration-model options such as auto-startup and listeners.
 */
@Override
public void configure(StateMachineConfigurationConfigurer<States, Events> config) throws Exception {
    config.withConfiguration().autoStartup(true);
}
```
