# Spring CredHub advanced credential patterns

Open this reference when the ordinary password, JSON, or value read-write path in [SKILL.md](../SKILL.md) is not enough and the blocker is advanced credential usage such as interpolation, certificate generation, permissions or info APIs, or less-common credential families.

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

## Certificate generation blocker

Use generated certificates only when CredHub is the system of record for certificate issuance and renewal.

```java
credHub.credentials().generateCertificate(
    new SimpleCredentialName("/app/prod/client-cert")
);
```

When applications only consume an existing certificate, prefer typed reads over generation calls in the application path.

## Permissions and info blocker

Use permissions or info/version calls only when the application actually manages CredHub access or needs explicit server-capability checks.

- Keep permissions APIs out of the common path.
- Keep info/version checks near startup diagnostics or operator-facing health checks, not inside ordinary credential reads.
- Keep server-capability checks explicit so version or info calls do not silently become part of every request path.

## Decision points

| Situation | First check |
| --- | --- |
| A placeholder document must be resolved by CredHub | use interpolation |
| The platform expects CredHub-managed certificate issuance | use certificate generation APIs |
| The app needs unusual credential families | pick the typed credential class explicitly |
| The app is trying to manage access rules | verify permissions APIs are really required |
