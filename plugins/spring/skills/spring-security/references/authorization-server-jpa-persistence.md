# Spring Authorization Server JPA persistence

Open this reference when the task requires `RegisteredClientRepository`, `OAuth2AuthorizationService`, or `OAuth2AuthorizationConsentService` backed by a relational database through JPA.

## When to open this reference

Open this reference when:

- Clients, authorizations, or consent must persist across restarts and cluster nodes
- The application already operates a relational database as the primary store
- The team prefers SQL-visible data for auditing or operational queries
- Issuer resolution for multitenant scenarios requires tenant-scoped repositories

## Official posture

Spring Authorization Server ships built-in JDBC implementations for the core services and an official how-to guide for implementing equivalent core services with JPA. Treat the JPA path here as an application-owned implementation pattern based on that guide, not as a built-in framework module.

### RegisteredClientRepository JPA pattern

Use an application-owned JPA-backed `RegisteredClientRepository` when registered clients must survive application restarts and load from a relational store.

```java
@Configuration
@EnableJpaRepositories(basePackages = "com.example.authorization.repository")
class JpaPersistenceConfig {
}
```

The `RegisteredClient` entity maps to the `oauth2_registered_client` table. Client id, secret, grant types, scopes, redirect URIs, and client settings are persisted as a single aggregate.

### OAuth2AuthorizationService JPA pattern

Use an application-owned JPA-backed `OAuth2AuthorizationService` when authorization state must survive restarts. Model it after the official JPA how-to guide.

Authorization rows are keyed by the `id` column containing the authorization id value.

### OAuth2AuthorizationConsentService JPA pattern

Use an application-owned JPA-backed `OAuth2AuthorizationConsentService` when consent decisions must persist. This stores whether a user has consented to a client accessing their data with particular scopes.

The consent entity maps to the `oauth2_authorization_consent` table with a composite key of client id and principal name.

## Schema expectations

JPA implementations use these tables:

- `oauth2_registered_client` stores the client registration
- `oauth2_authorization` stores authorization state and issued tokens
- `oauth2_authorization_consent` stores consent decisions

Schema is managed by the application through JPA schema generation or explicit migrations. Validate the schema with normal application startup and repository tests rather than assuming a built-in JPA initializer.

## Decision points

| Situation | Choice |
| --- | --- |
| client data must survive restarts and share across nodes | custom JPA-backed `RegisteredClientRepository` |
| authorization state must survive restarts | custom JPA-backed `OAuth2AuthorizationService` |
| consent decisions must survive restarts | custom JPA-backed `OAuth2AuthorizationConsentService` |
| already operating a relational database | JPA over Redis |
| team prefers SQL-visible authorization data | JPA over Redis |
| need lazy loading or entity graphs for client queries | JPA |

## JPA implementation branches

### Mapping and serialization branch

When custom token claims or metadata must survive serialization, customize the JPA entity mapping and JSON conversion in the application-owned repositories and service implementations from the official guide.

### Mixing JPA and Redis

Do not mix separate JPA-backed and Redis-backed authorization services in one application. Choose one persistence mechanism for authorizations.

### Custom client entity

When the standard `RegisteredClient` domain object does not cover the domain, create a separate application-owned JPA entity and map it to and from `RegisteredClient` in the repository implementation.

```java
@Entity
@Table(name = "client")
class ClientEntity {
    @Id
    private String id;

    @Column(name = "tenant_id")
    private String tenantId;
}
```

Custom entities require a custom `RegisteredClientRepository` implementation and explicit mapping logic.

## Production considerations

- Use connection pooling appropriate for the database platform
- Index the `client_id` column for lookup performance
- Consider batch insert for dynamic client registration flows
- Plan for schema migration as the authorization server evolves
- Validate that JPA query performance meets token issuance latency targets

## Official documentation

See:

- [Core model components](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/core-model-components.html)
- [Configuration model](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/configuration-model.html)
