# Pushed Authorization Requests

Open this reference when the ordinary authorization request redirect in [SKILL.md](../SKILL.md) is not enough and the blocker is Pushed Authorization Request (PAR) support.

## PAR blocker

**Problem:** the client must submit authorization parameters directly to the authorization server before redirecting the browser.

**Solution:** enable and treat `/oauth2/par` as part of the provider contract rather than as a UI-level redirect detail.

PAR is useful when request parameters are large, signed, sensitive, or must be validated before the user-agent redirect happens.

## Endpoint customization seam

Use the official endpoint configurer when PAR request validation or handling must change.

```java
http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
    .pushedAuthorizationRequestEndpoint(par -> par
        .authenticationProviders(providers -> {
        })
    );
```

Keep PAR-specific validation on the PAR endpoint seam instead of duplicating the logic in unrelated authorization filters.

## Decision points

| Situation | First choice |
| --- | --- |
| Client protocol requires pre-registered authorization requests | enable PAR support |
| Request parameters are too large or sensitive for ordinary redirects | prefer PAR |
| Plain authorization redirect flow is sufficient | stay on the ordinary path |

## Pitfalls

- Do not treat PAR as a generic replacement for all authorization requests without a client requirement.
- Do not duplicate authorization-request validation in both the PAR and authorization endpoints without a clear reason.
- Do not enable PAR and forget to test the `/oauth2/par` to `/oauth2/authorize` handoff.
