# Spring Vault repositories and query keywords

Open this reference when domain entities must persist in Vault using Spring Data repository abstractions, or when query derivation needs regex patterns, set-based lookups, or Vault 2.0 compatibility.

## Repository setup

Vault repositories apply Spring Data's repository concept on top of Vault.
They use the key/value secrets engine to persist and query domain objects.
The actual secrets engine version (KV v1 or v2) is discovered at runtime.

```java
@Secret
class Credentials {
    @Id String id;
    String password;
    String socialSecurityNumber;
}

interface CredentialsRepository extends CrudRepository<Credentials, String> {
}
```

```java
@Configuration
@EnableVaultRepositories
class ApplicationConfig {
    @Bean
    VaultTemplate vaultTemplate() {
        return new VaultTemplate(vaultEndpoint);
    }
}
```

Dependencies:

```xml
<dependency>
    <groupId>org.springframework.vault</groupId>
    <artifactId>spring-vault-core</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-keyvalue</artifactId>
</dependency>
```

## Query method keywords (4.1 additions)

Query methods derive simple queries from the method name.
Vault has no query engine, so query execution lists children under a context path and applies filtering to the `@Id` property.
All query predicates constrain the identifier only.

### Regex patterns (4.1, #1000)

```java
interface CredentialsRepository extends CrudRepository<Credentials, String> {
    List<Credentials> findByIdRegex(String pattern);
}
```

```java
List<Credentials> results = repo.findByIdRegex("prod-.*-db");
```

### Set-based IN and NOT_IN (4.1, #999)

```java
interface CredentialsRepository extends CrudRepository<Credentials, String> {
    List<Credentials> findByIdIn(Collection<String> ids);
    List<Credentials> findByIdNotIn(Collection<String> ids);
}
```

```java
List<Credentials> results = repo.findByIdIn(List.of("prod-db-1", "prod-db-2"));
```

### Full keyword reference

| Keyword | Sample |
| --- | --- |
| `After`, `GreaterThan` | `findByIdGreaterThan(String id)` |
| `GreaterThanEqual` | `findByIdGreaterThanEqual(String id)` |
| `Before`, `LessThan` | `findByIdLessThan(String id)` |
| `LessThanEqual` | `findByIdLessThanEqual(String id)` |
| `Between` | `findByIdBetween(String from, String to)` |
| `In` | `findByIdIn(Collection ids)` |
| `NotIn` | `findByIdNotIn(Collection ids)` |
| `Like`, `StartingWith`, `EndingWith` | `findByIdLike(String id)` |
| `NotLike`, `IsNotLike` | `findByIdNotLike(String id)` |
| `Containing` | `findByFirstnameContaining(String id)` |
| `NotContaining` | `findByFirstnameNotContaining(String name)` |
| `Regex` | `findByIdRegex(String id)` |
| `Not` | `findByIdNot(String id)` |
| `And` | `findByLastnameAndFirstname` |
| `Or` | `findByLastnameOrFirstname` |
| `Is`, `Equals` | `findByFirstname` |
| `Top`, `First` | `findFirst10ByFirstname` |

## Optimistic locking with KV v2

```java
@Secret
class VersionedCredentials {
    @Id String id;
    @Version int version;
    String password;
}
```

```java
VersionedCredentials credentials = repo.findById("sample-credentials").get();
credentials.setPassword("new-value");
repo.save(credentials);
```

Concurrent updates to the same secret throw `OptimisticLockingFailureException`.

## Vault 2.0.0 compatibility (4.1, #994)

Spring Vault 4.1 is compatible with HashiCorp Vault 2.0.0.
The KV version auto-detection in `opsForKeyValue(String path)` queries `sys/internal/ui/mounts/...` using the Vault 2.0 API surface.
Ensure policies grant access to the mount detection endpoint.

## Decision points

| Situation | Use |
| --- | --- |
| Simple KV read or write | `VaultTemplate` directly |
| Domain objects with CRUD and query derivation | Vault repositories |
| Regex filtering on secret IDs | `findByIdRegex` query method |
| Batch retrieval by known IDs | `findByIdIn` query method |
| Version-aware concurrent updates | `@Version` annotation with KV v2 |

## Gotchas

- Do not use query predicates on non-`@Id` properties.
  - Vault query methods filter only by identifier.
- Do not assume `In`/`NotIn` perform server-side filtering.
  - Vault lists all IDs and filters in memory.
- Do not use Vault repositories with KV v1 when optimistic locking requires version metadata.
