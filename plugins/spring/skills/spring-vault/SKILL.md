---
metadata:
  reference:
    Spring Vault:
      version: 4.1.0
      url:
        - https://docs.spring.io/spring-vault/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/vault/spring-vault-core/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/vault/spring-vault-core/4.1.0/spring-vault-core-4.1.0.pom
    Spring CredHub:
      version: 4.1.0
      url:
        - https://spring.io/projects/spring-credhub
        - https://repo.maven.apache.org/maven2/org/springframework/credhub/spring-credhub-starter/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/credhub/spring-credhub-starter/4.1.0/spring-credhub-starter-4.1.0.pom
    Spring Cloud Vault:
      version: 5.0.2
      url:
        - https://docs.spring.io/spring-cloud-vault/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/cloud/spring-cloud-vault-config/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/cloud/spring-cloud-vault-config/5.0.2/spring-cloud-vault-config-5.0.2.pom
name: spring-vault
description: >-
  Use Spring Vault or CredHub for authentication, secret access, property loading, transit, and rotation.
---

# Spring Vault and CredHub

The direct Spring Vault example targets the 4.1.x API line.
The `4.1.0` Vault and CredHub versions below illustrate current stable API baselines.
Check each artifact on Maven Central when the application needs a release newer than this documented baseline.
Keep existing BOM or version-catalog pins unless the task authorizes changing them.

## Boundaries

Use `spring-vault` for application-side Vault or CredHub access, authentication setup, KV engine access, transit operations, credential management, and Spring configuration import from Vault or CredHub.

- Keep this skill focused on application integration.
  - Vault cluster operations, seal management, and policy administration are platform concerns.
- Keep one auth mode per runtime profile so startup and token-renewal behavior stay predictable.
- Keep Kubernetes auth, reactive access, and versioned KV behavior out of the ordinary path unless the task explicitly needs them.
- Keep Vault repositories, PKI certificate issuance, and Spring Security crypto integration outside the ordinary path unless those surfaces are the actual blocker.
- Keep CredHub advanced credential families, interpolation, and reactive CredHub access in references unless the task explicitly needs them.

## Vendor decision

| Condition | Use |
| --- | --- |
| HashiCorp Vault is the secret store | Vault paths in this skill |
| Cloud Foundry CredHub is the secret store | CredHub paths in this skill |

## Surface map

Read the reference before working on its matching surface:

- [Vault configuration](references/vault-configuration.md): token, AppRole, and property loading.
- [Vault examples](references/vault-examples.md): KV, transit, property import, low-level HTTP, or rotation.
- [Kubernetes authentication](references/kubernetes-authentication.md): pod identity and service-account tokens.
- [Reactive Vault](references/reactive-vault-access.md): reactive access paths.
- [KV versioning and CAS](references/kv-versioning-and-cas.md): versions and multiple writers.
- [Credential rotation](references/credential-rotation.md): secret renewal or certificate issuance and rotation.
- [Repositories](references/repositories.md): Vault persistence and query derivation.
- [CredHub integration](references/credhub-integration.md): CredHub credential access.
- [CredHub authentication](references/credhub-auth-and-credential-variants.md): mutual TLS or OAuth2.
- [Reactive CredHub](references/credhub-reactive-access.md): reactive access paths.
- [CredHub advanced patterns](references/credhub-advanced-credential-patterns.md): interpolation, certificates, permissions, or non-default credential types.

## Task scope

Use the deployment's supported authentication mode, usually token for local work and AppRole for production.
Distinguish direct client access from startup configuration loading.
Preserve secret paths and authentication policy unless changing them is authorized.
Use the sections and references for the affected authentication, secret engine, property import, or credential lifecycle.

## Dependency baseline

Before selecting or changing Vault dependencies, read [Vault dependencies](references/vault-dependencies.md).
Use `spring-vault-core` for direct access; use BOM-managed Spring Cloud Vault only for property loading.
Core alone does not bind `spring.cloud.vault.*` or resolve `vault://`.

## Implementation guidance

- Keep the secret path contract explicit, such as `secret/data/app/{env}/database` for KV v2.
- Keep auth configuration outside business services and inject `VaultTemplate` into a narrow boundary service.
- Distinguish KV v1 and KV v2 paths explicitly.
  Prefer `opsForKeyValue(...)` for ordinary KV access over raw `data/` path handling.
- `opsForKeyValue(String path)` auto-detects KV v1 vs v2 at runtime (4.1).
  Explicit `opsForKeyValue(String, KeyValueBackend)` overrides when the mount version is known and detection overhead is unacceptable.
- Use `VaultClient` for low-level Vault HTTP access instead of `RestOperations` or `RestTemplate`.
- Read typed secret fields into application-specific records or value objects as early as possible.
- Use `getRequiredData()` when the read path must fail fast instead of silently tolerating a missing payload.
- Use transit only when the application needs Vault-managed encryption or signing without exposing raw key material.
- Treat missing secrets, permission denials, and version conflicts as controlled failures, not recoverable happy-path events.

## CredHub integration

Before using CredHub, read [CredHub integration](references/credhub-integration.md) for dependencies, authentication, credential access, verification, and production safeguards.
Prefer mutual TLS; use OAuth2 only when certificates are unavailable, and keep one mode per profile.
Do not log credentials, keys, tokens, or certificate payloads; missing credentials must fail without silent defaults.
For custom CA trust, include Apache HttpClient 5; supply a custom `CredHubOperations` bean for client-certificate mutual TLS.

## Output contract

Report the changed Vault or CredHub contract, authentication and failure behavior, and verification results.
Identify unverified integration behavior or blockers without exposing secrets.
Follow the task's required response format.

For Vault or CredHub response and failure shapes, read [output examples](references/vault-and-credhub-output.md).

## Vault verification

Use existing tests for the changed Vault access or configuration boundary.
Choose integration coverage when authentication, mount behavior, or startup property loading needs the real client boundary.
Select the checks that apply:

- Verify the service reads or writes the expected Vault path for the active environment.
- Verify KV v2 reads unwrap the expected payload and fail fast when required data is absent.
- Verify missing secrets and permission denials fail without leaking secret values.
- For authentication or property-import changes, verify the affected integration boundary.

## Vault production guardrails

- Never log Vault tokens, AppRole secret ids, decrypted secret values, or transit plaintext.
- Align token renewal or AppRole credential rotation with the application's lifecycle expectations.
- Keep secret path conventions stable so deployments, policies, and applications agree on location.
- Bound Vault client timeouts and surface Vault availability through health or startup failure signals.
- Use the narrowest policy needed for the application's read, write, or transit operations.
