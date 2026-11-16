---
metadata:
  reference:
    Spring Authorization Server:
      version: 1.5.8
      url:
        - https://docs.spring.io/spring-authorization-server/reference/
        - https://docs.spring.io/spring-authorization-server/reference/guides/how-to-pkce.html
        - https://github.com/spring-projects/spring-authorization-server/tree/1.5.8
        - https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-oauth2-authorization-server/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-oauth2-authorization-server/1.5.8/spring-security-oauth2-authorization-server-1.5.8.pom
    Spring Security Authorization Server:
      version: 7.1.1
      url:
        - https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/configuration-model.html
        - https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/core-model-components.html
        - https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/protocol-endpoints.html
        - https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/getting-started.html
        - https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-oauth2-authorization-server/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-oauth2-authorization-server/7.1.1/spring-security-oauth2-authorization-server-7.1.1.pom
name: spring-authorization-server
description: >-
  Use Spring OAuth2/OIDC for token issuance, client registration, consent, and provider endpoints.
---

# Spring Authorization Server

## Boundaries and task scope

Use this skill for the token issuer: authentication, consent, token issuance, provider metadata, and signing keys.
Bearer validation and outbound OAuth2 token management belong to relying parties.
An issuer using `oauth2Login()` in its login chain for external authentication remains in scope.
Standalone Spring Authorization Server 1.5.8 is final; active development continues in Spring Security 7, aligned with its version and BOM.
Examples use the Boot 4.1 managed path.

Identify the issuer, client type, and protocol contract; use authorization code with PKCE for public interactive clients.
Add protocol surfaces only when the client contract requires them.
Local examples are not prerequisites for modifying an existing provider.

## Dependency baseline

Before recommending a new version, check each selected Maven artifact on Maven Central for its latest stable release compatible with the project's Boot and Framework lines.
Keep project BOM or version-catalog management and existing pins unless the task authorizes changing them.

For the baseline, see [example 1](references/configuration-examples.md#example-1-dependency-baseline).

Use Spring Boot dependency management for this path.
Do not add a standalone Spring Authorization Server 1.5.x version beside Spring Security 7 modules.

For local server tests and published-contract inspection, use [local verification commands](references/issuer-setup.md#local-verification-commands).

## Required components

Use ordered authorization-server and resource-owner login filter chains, `RegisteredClientRepository`, `JWKSource<SecurityContext>`, and `JwtDecoder`.
Configure an explicit `AuthorizationServerSettings` issuer for single-issuer deployments or issuer-scoped settings and components for multiple issuers.
Clustered production requires a shared external or custom `JWKSource<SecurityContext>` and durable client, authorization, and consent stores.

## Issuer setup

When configuring filter chains, issuer, local users, public clients, signing keys, decoders, or token claims, read [issuer setup](references/issuer-setup.md) and [configuration examples](references/configuration-examples.md).
Keep a single issuer stable and externally visible; for multiple issuers omit global `.issuer(...)`, enable `multipleIssuersAllowed(true)`, and isolate components by issuer.
Enable OIDC only for required OIDC surfaces.
In-memory repositories, `{noop}` passwords, and ephemeral signing keys are local-only; protect and rotate deployed keys.
Use PKCE for public clients unless the client can protect a secret on a server.
Centralize token customization and add only claims documented clients or resource servers consume.

## Protocol surface

Baseline endpoints are `/oauth2/authorize`, `/oauth2/token`, `/.well-known/oauth-authorization-server`, and `/oauth2/jwks`.
Enable OIDC metadata, UserInfo, and ID tokens only when required.
Logout, device authorization, PAR, dynamic registration, introspection, and revocation are also conditional; publish them only when required by a client contract and their access policy is tested.

## Validation and production

Verify affected protocol and deployment contracts on success and rejection paths: metadata issuer and endpoints, registered and unregistered redirects, valid and invalid PKCE, the required valid verifier for public clients, approved scopes and claims, and unknown clients or invalid scopes.
Before clustered production, verify keys, clients, authorization state, and consent survive restart.
Keep the issuer stable behind proxies and TLS; protect keys and plan overlapping rotation.
Persist clients, authorizations, and consent when continuity matters.
Provision redirect URIs, scopes, grants, and client authentication explicitly.
Treat endpoint behavior, claims, consent, and discovery metadata as client compatibility surfaces.

## References

Read references when their conditions apply:

- [Client authentication](references/client-authentication-variants.md): confidential clients or non-default token-endpoint authentication.
- [Proof of possession](references/proof-of-possession-variants.md): DPoP or MTLS-bound tokens.
- [Token generation](references/token-generation-and-customization.md): token formats, generators, or claims beyond the minimal JWT hook.
- [OIDC provider endpoints](references/oidc-provider-endpoints.md): OIDC discovery, UserInfo, logout, or ID-token behavior.
- [PAR](references/pushed-authorization-requests.md): clients require pushed authorization requests.
- [Device authorization](references/device-authorization-grant.md): browserless clients require device authorization.
- [Introspection and revocation](references/introspection-and-revocation.md): token liveness inspection or revocation.
- [Dynamic registration](references/dynamic-client-registration.md): external self-service client registration.
- [Federated identity](references/federated-identity-and-social-login.md): external user authentication provider.
- [Extension grants](references/extension-grants.md): extension grants or token exchange.
- [JPA](references/jpa-persistence.md) or [Redis](references/redis-persistence.md): clients, authorization state, or consent must survive restart.
- [Multitenancy](references/multitenancy.md): tenant-specific issuer, clients, or signing keys.
- [Endpoint customization](references/endpoint-customization.md): protocol request or response handling changes.
- [Deployment and operations](references/deployment-and-operations.md): proxying, production hardening, or key operations.
- [Authorization Server upgrade](references/upgrade-spring-authorization-server.md) or [Security 6 to 7 migration](references/migrate-spring-security-6-to-7.md): version migration.
- [Authorization endpoint tests](references/testing-authorization-endpoint.md) or [refresh path tests](references/testing-refresh-path.md): deeper authorization-code or refresh-token verification.
- [Token metadata and JWK tests](references/testing-token-metadata-and-jwk-endpoints.md), [OIDC tests](references/testing-oidc-endpoints.md), or [introspection, revocation, and consent tests](references/testing-introspection-revocation-and-consent.md): deeper verification of the corresponding protocol requests and responses.
