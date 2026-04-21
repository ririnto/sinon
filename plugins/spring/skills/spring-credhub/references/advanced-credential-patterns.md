# Spring CredHub advanced credential patterns

Open this reference when the ordinary password, JSON, or value read-write path in [SKILL.md](../SKILL.md) is not enough and the blocker is advanced credential usage such as interpolation, certificate generation, permissions or info APIs, or less-common credential families.

The current stable Spring CredHub line is 4.0.1, and these APIs stay additive to the ordinary starter-backed service path in [SKILL.md](../SKILL.md).

## Interpolation blocker

Interpolation is useful when an application consumes a compound document whose placeholders are resolved by CredHub before delivery.

```text
((/app/prod/db-password))
```

Use interpolation only when the consuming system already expects that placeholder contract.

## Less-common credential-family blocker

- `ValueCredential`: simple scalar values such as feature flags or single tokens.
- `JsonCredential`: grouped settings that should move together.
- `PasswordCredential`: generated or rotated passwords.
- `CertificateCredential`: certificate material owned by CredHub.
- `UserCredential`: paired username/password credentials managed together.

Choose the narrowest type that matches the real secret shape so reads stay typed and predictable.

```java
UserCredential credential = credHub.credentials()
    .getByName(new SimpleCredentialName("/app/prod/app-user"), UserCredential.class)
    .getValue();
```

Use `UserCredential` only when the platform truly stores the username and password as one managed unit. Otherwise, keep the username in configuration and read only the secret value from CredHub.

## Certificate generation blocker

Use generated certificates only when CredHub is the system of record for certificate issuance and renewal.

```java
credHub.credentials().generateCertificate(
    new SimpleCredentialName("/app/prod/client-cert")
);
```

```java
CertificateCredential certificate = credHub.credentials()
    .getByName(new SimpleCredentialName("/app/prod/client-cert"), CertificateCredential.class)
    .getValue();
```

When applications only consume an existing certificate, prefer typed reads over generation calls in the application path.

## Permissions and info blocker

Use permissions or info/version calls only when the application actually manages CredHub access or needs explicit server-capability checks.

- Keep permissions APIs out of the common path.
- Keep info/version checks near startup diagnostics or operator-facing health checks, not inside ordinary credential reads.
- Keep server-capability checks explicit so version or info calls do not silently become part of every request path.

## Gotchas

- Do not add interpolation, certificate generation, and permissions APIs to the same service boundary unless the application is explicitly acting as a CredHub management client.
- Do not use certificate generation in a request path that only needs to read an already-issued certificate.

## Decision points

| Situation | First check |
| --- | --- |
| A placeholder document must be resolved by CredHub | use interpolation |
| The platform expects CredHub-managed certificate issuance | use certificate generation APIs |
| The app needs unusual credential families | pick the typed credential class explicitly |
| The app is trying to manage access rules | verify permissions APIs are really required |

## Related blockers

- Return to [SKILL.md](../SKILL.md) when the blocker is an ordinary password, JSON, or value read-write path.
- Open [auth-and-credential-variants.md](auth-and-credential-variants.md) when the actual blocker is client authentication mode selection.
- Open [reactive-access.md](reactive-access.md) when the actual blocker is a reactive CredHub client boundary.
