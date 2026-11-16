# Vault and CredHub output shapes

## Output shapes

### KV read shape

```json
{
  "username": "app",
  "password": "secret-value"
}
```

### Transit encrypt shape

```text
vault:v1:8sd7f6...
```

### Missing secret failure shape

```text
Vault secret missing at secret/app/prod/database
```

### CredHub credential read shape

```java
credHub.credentials().getByName(new SimpleCredentialName("/app/prod/db-password"), PasswordCredential.class)
```

### CredHub credential write shape

```java
credHub.credentials().write(ValueCredentialRequest.builder().name(new SimpleCredentialName("/app/%s/feature-flag".formatted(env))).value(new ValueCredential(value)).build());
```

### CredHub missing credential failure shape

```text
CredHub credential unreadable: /app/prod/db-password
```
