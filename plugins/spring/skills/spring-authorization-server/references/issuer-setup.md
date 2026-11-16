# Issuer setup

## Filter chains and issuer

For filter chains and issuer, see [example 2](configuration-examples.md#example-2-filter-chains-and-issuer).

The explicit issuer above is a single-issuer example.
For multiple issuers, do not configure a global `.issuer(...)`.
Enable `multipleIssuersAllowed(true)` and delegate registered clients, authorizations, consent, signing keys, and related components by issuer as described in [references/multitenancy.md](multitenancy.md).
Keep a single issuer stable and equal to the externally visible URL behind proxies and TLS termination.
Add `.oidc(Customizer.withDefaults())` to the authorization-server configurer only when the provider must publish OIDC metadata, UserInfo, or ID-token surfaces.

## Development user and public client

For development user and public client, see [example 3](configuration-examples.md#example-3-development-user-and-public-client).

The `{noop}` password and in-memory repositories are local-development shapes only.
Use a public client with PKCE unless the client runs on a server that can protect a secret.

## Signing key and decoder

For signing key and decoder, see [example 4](configuration-examples.md#example-4-signing-key-and-decoder).

Generate ephemeral keys only for local development.
Load protected, rotation-aware signing material from the deployment's key-management boundary before production.

## Minimal token customization

For minimal token customization, see [example 5](configuration-examples.md#example-5-minimal-token-customization).

Add only claims that a documented resource-server or client contract consumes.
Keep token customization centralized so the issued-token schema remains reviewable.

## Local verification commands

```sh
./mvnw test
```

```sh
./gradlew test
```

After starting the local server, inspect the published contract:

```sh
curl -fsS http://localhost:9000/.well-known/oauth-authorization-server
curl -fsS http://localhost:9000/oauth2/jwks
```
