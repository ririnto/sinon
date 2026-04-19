# Device authorization grant

Open this reference when the ordinary browser-based authorization-code flow in [SKILL.md](../SKILL.md) is not enough and the blocker is the OAuth 2.0 Device Authorization Grant.

## Device-flow blocker

**Problem:** the client cannot drive a standard browser redirect flow directly and must ask the user to authorize on a second device.

**Solution:** expose the device authorization and device verification endpoints as one coordinated protocol surface.

```text
device authorization: /oauth2/device_authorization
device verification: /oauth2/device_verification
```

Treat both endpoints as one feature. The client obtains a device code first, then the user completes approval on the verification endpoint.

## Endpoint customization seam

Use the official configurer only when device-flow request handling must change.

```java
http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
    .deviceAuthorizationEndpoint(deviceAuthorization -> {
    })
    .deviceVerificationEndpoint(deviceVerification -> {
    });
```

## Decision points

| Situation | First choice |
| --- | --- |
| TV, CLI, or constrained device cannot receive the browser redirect | device authorization grant |
| Standard browser-capable client can use PKCE | stay on authorization code with PKCE |
| Need custom prompts or verification UX | customize only the device endpoints |

## Pitfalls

- Do not add the device flow when a normal browser-based PKCE flow is sufficient.
- Do not customize only one half of the device flow and ignore the other endpoint.
- Do not forget to test polling, user-code verification, and one rejected device flow path.
