---
metadata:
  reference:
    Spring Security BOM:
      - version: 7.1.1
        url:
          - https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-bom/maven-metadata.xml
          - https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-bom/7.1.1/spring-security-bom-7.1.1.pom
      - version: 7.2.0-M2
        url:
          - https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-bom/7.2.0-M2/spring-security-bom-7.2.0-M2.pom
          - https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-bom/maven-metadata.xml
    Spring Security Guide:
      url: https://docs.spring.io/spring-security/reference/index.html
name: spring-security
description: >-
  Use Spring Security for access rules, authentication, bearer tokens, filter chains, and browser sessions.
---

# Spring Security

## Boundaries

Use `spring-security` for application-side authentication, authorization, filter-chain design, method security, CORS and CSRF policy, session behavior, logout behavior, security headers, exception handling, and security-focused tests.

- Keep business rules outside security configuration.
  - Security should express access policy and integration boundaries, not business workflows.
- Keep reactive security outside the ordinary servlet path.
  - Open the reactive reference only when the application is reactive end to end.

This skill validates and enforces tokens issued by another system.
Token issuance, signing-key ownership, client registration, provider metadata, and consent are a distinct authorization-server job.

## Branch selector

- Open [reactive webflux security](references/reactive-webflux-security.md) only when the application is reactive end to end and uses `SecurityWebFilterChain`.
- Open [delegated login and oauth2 client](references/delegated-login-and-oauth2-client.md) when the application delegates login to an external identity provider or needs outbound OAuth2 client token management.
- Open [ldap authentication](references/ldap-authentication.md) when directory-backed authentication is part of the job.
- Open [saml2 login](references/saml2-login.md) only when enterprise identity-provider requirements specifically depend on SAML2.
- Open [multiple security filter chains](references/multiple-security-filter-chains.md) when different URL spaces need separate servlet chains or a custom filter must be placed relative to Spring Security's built-in filters.
- Open [jwt claim mapping](references/jwt-claim-mapping.md) for non-webapp authority extraction, when default `scope` to authority conversion is not enough, nested or multi-location claim extraction is needed, or issuer, audience, algorithm, or principal-claim rules must be customized.
- Open [exception handling](references/exception-handling.md) when 401 and 403 responses need custom JSON bodies, custom entry points, or custom access-denied handling.
- Open [session management and logout](references/session-management-and-logout.md) when the job depends on concurrent-session control, custom logout success handling, or stateful session persistence rules beyond the ordinary path.
- Open [headers](references/headers.md) when defaults are not enough and the application needs custom CSP, HSTS, frame, or permissions-policy behavior.
- Open [servlet opaque token resource server](references/servlet-opaque-token-resource-server.md) only when the servlet resource server must validate bearer tokens via introspection instead of local JWT verification.

## Dependency and servlet configuration

Spring Security BOM 7.1.1 is a documented stable baseline; 7.2.0-M2 is a milestone.
For a new standalone BOM, check Maven Central for the latest stable version compatible with the project's Boot and Framework lines.
Keep existing pins unless authorized to change them.
A Boot-managed Security 6.x application must remain on compatible APIs or move to a compatible platform line before using Security 7 APIs.
Keep BOM-managed modules versionless; add resource-server and JOSE modules only when the corresponding token validation is needed.

Use `requestMatchers` for ordinary rules and `PathPatternRequestMatcher` when an explicit matcher object is needed.
In a single filter chain, do not use `securityMatcher` without a fallback chain: unmatched requests would be unprotected.
Use stateless sessions for bearer-token APIs and an appropriate stateful policy for browser login.
For SPAs using session cookies, use SPA CSRF support rather than disabling CSRF; separate pure bearer endpoints if they must ignore CSRF.
When authentication is performed manually, save the `SecurityContext` through the chosen repository.
Use explicit CORS origins, methods, and headers for browser cross-origin clients.
Use a strong password encoder, maintain the encoder identifier with stored hashes, and follow an explicit migration policy.

For dependency declarations, servlet configuration, session-policy details, SPA CSRF, password encoding, or non-web resource-server setup, read [servlet setup and configuration](references/servlet-setup-and-configuration.md).

## Coding procedure

1. Keep authorization rules explicit and ordered so public and protected paths are obvious.
2. Keep CSRF policy explicit instead of disabling it reflexively.
   - For stateless APIs, ignore only the paths that do not rely on browser-session cookies.
3. Keep JWT authority mapping, claim interpretation, and principal extraction explicit.
4. Use method security only where request-level rules are not enough.
   - Add `@PostAuthorize`, `@PreFilter`, or `@PostFilter` only when access depends on returned objects or filtered collections.
5. Keep logout behavior explicit for browser flows, and keep 401 versus 403 handling explicit for API flows.
6. Rely on Spring Security's default security headers unless the application has a concrete need for custom CSP, frame, or HSTS behavior.
7. Test both successful access and denial paths for representative endpoints.

## Examples

For method security, collection filtering, security tests, or servlet output and configuration shapes, read [servlet examples and tests](references/servlet-examples-and-tests.md).
Use `@WithUserDetails` only when the test slice supplies the matching `UserDetailsService` entry.
A 401 anonymous-request expectation applies to API, basic, or bearer-token chains that do not redirect to login.

## Testing checklist

Test the changed security boundary with both permitted and rejected requests.
Select additional checks for affected client flows, token rules, and regression risks.

- Verify public endpoints remain accessible without authentication.
- Verify protected endpoints deny unauthenticated requests and reject insufficient authority with the expected 401 or 403 status.
- Verify method security enforces the intended authority, returned-object, or filtered-collection rules.
- Verify CORS, CSRF, and session policy match the real client interaction model.
- Verify logout behavior only where browser-session flows require it.
- Verify password encoding or JWT claim mapping follows the expected strategy.

## Production checklist

- Keep authorization rules explicit and reviewable.
- Never store raw passwords or weak password hashes.
- Do not disable CORS, CSRF, or session protections without a concrete client-driven reason.
- Keep JWT issuer, audience, and authority mapping aligned with the deployed identity provider.
- Keep default security headers unless there is a concrete compatibility reason to customize them.
- Make 401 versus 403 behavior explicit when API clients depend on a stable error contract.
- Treat security tests as part of the application's compatibility and safety surface.
- Do not expose broad `/actuator` paths unless explicitly intended.
  - Permitting `/actuator/health` alone does not imply actuator endpoints are publicly accessible.
  - Secure any additional actuator endpoints explicitly.
