# Spring Vault KV versioning and CAS

Open this reference when multiple writers can update the same secret, the task needs a specific KV v2 version, or the write path depends on check-and-set behavior.

Keep the application path contract explicit about whether it targets KV v1 or KV v2.

## KV version boundary

- KV v1 uses unversioned path semantics.
- KV v2 supports versions, soft delete, undelete, and check-and-set behavior.

## Versioned access shape

```java
VersionedKeyValueOperations operations = vault.opsForVersionedKeyValue("secret");
Versioned<Map<String, Object>> version = operations.get("app/prod/database", Versioned.Version.from(3));
```

## CAS write shape

```java
VersionedKeyValueOperations operations = vault.opsForVersionedKeyValue("secret");
operations.put("app/prod/database", Map.of("username", "app"), Versioned.Version.from(7));
```

## Testing rule

Add tests for version conflicts, expected soft-delete behavior, and any startup failure that should remain fail-fast when configuration import depends on a versioned path.

## Gotchas

- Do not treat KV v2 like KV v1 when version or CAS behavior matters.
- Do not leave the mount or path ambiguous when some environments use KV v1 and others use KV v2.
- Do not ignore version conflicts in multi-writer flows.
