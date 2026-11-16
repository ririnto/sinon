# HTTP configuration

## First safe configuration

## First safe commands

```sh
./mvnw test -Dtest=BookGraphQlControllerTests
```

```sh
./gradlew test --tests BookGraphQlControllerTests
```

## Schema resource shape

```text
src/main/resources/graphql/schema.graphqls
```

## HTTP endpoint shape

```yaml
spring:
    graphql:
        http:
            path: /graphql
```

## GraphiQL and schema printer

GraphiQL is disabled by default.
Enable it for development with `spring.graphql.graphiql.enabled: true`.
It is auto-enabled with `spring-boot-devtools`.
The endpoint defaults to `/graphiql` and is configurable with `spring.graphql.graphiql.path`.

Expose the schema as text at `/graphql/schema` with `spring.graphql.schema.printer.enabled: true`.

## CORS configuration

Use `spring.graphql.cors.*` properties to configure Cross-Origin Resource Sharing for browser-based clients.

```yaml
spring:
    graphql:
        cors:
            allowed-origins: https://example.org
            allowed-methods: GET,POST
            max-age: 1800s
```

## Schema introspection

Introspection is enabled by default (required by GraphiQL and other tools).
Disable with `spring.graphql.schema.introspection.enabled: false`.

Start with a single schema resource set and one HTTP endpoint.
Add WebSocket, SSE, or RSocket transports only when the API really needs them.
