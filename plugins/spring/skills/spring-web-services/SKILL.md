---
metadata:
  reference:
    Spring Web Services:
      version: 5.0.2
      url:
        - https://docs.spring.io/spring-ws/docs/current/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/ws/spring-ws-bom/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/ws/spring-ws-bom/5.0.2/spring-ws-bom-5.0.2.pom
    Spring Boot 4.1:
      version: 4.1.1
      url:
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom
name: spring-web-services
description: >-
  Use Spring Web Services for SOAP endpoints and clients, XSD/WSDL contracts, XML, faults, and WS-Security.
---

# Spring Web Services

## Boundaries

Use `spring-web-services` for SOAP transport, XML contract publication, endpoint mapping, SOAP client calls, and SOAP-specific testing.

- Ordinary HTTP JSON APIs and GraphQL APIs are outside this skill's scope unless the task is SOAP or XML-contract driven.
- Keep business logic outside endpoint handlers.
  - SOAP endpoints should translate XML payloads to application services and back.
- Keep WS-Security, XPath-centric payload parsing, and specialized client transports out of the ordinary path unless those are the actual blocker.

## Baseline

The examples target Spring Web Services 5.0.x.
Spring Web Services 5.0 requires JDK 17 or later, Jakarta EE 11 (Servlet 6.1, Jakarta XML Bind 4.0, Jakarta Activation 2.1), Spring Framework 7.0, Spring Security 7.0, Apache WSS4J 4.0, and JUnit 6.0.

- Spring WS 5.0.x aligns with Spring Boot 4.0.x and 4.1.x.
  - The Boot starter manages the Spring WS version.
- `XwsSecurityInterceptor` was removed in Spring WS 4.0 and is not available.
  - Use `Wss4jSecurityInterceptor` for all WS-Security configuration.
- Do not extend `WsConfigurerAdapter`; Spring WS 5.0 removed it.
  Implement `WsConfigurer` directly only when optional endpoint interceptors, such as WS-Security, are required.

## Task scope

Preserve published XML schemas, namespaces, payload roots, and fault contracts unless changing them is authorized.
For a new SOAP integration, define the XSD and WSDL contract and keep JAXB-bound types aligned with it.
Implement endpoint or client behavior for the requested application use case rather than building both sides by default.
Use the sections and references for the affected contract, transport, marshalling, or security concern.

In Spring Boot, prefer starter-managed wiring.
Add manual `MessageDispatcherServlet` registration only when the deployment needs custom servlet registration.

## Surface map

- Stay in `SKILL.md` for the ordinary endpoint-plus-template path: contract-first XSD and WSDL publication, servlet registration, `@Endpoint` handlers, JAXB marshalling, `WebServiceTemplate`, basic SOAP fault mapping, and contract tests.
- Open [references/ws-security.md](references/ws-security.md) when the integration contract requires signing, encryption, username tokens, or message-level trust.
- Open [references/xpath-endpoints.md](references/xpath-endpoints.md) when payload parsing is too dynamic for ordinary marshalling.
- Open [references/client-variants.md](references/client-variants.md) when the client must use specialized transports or alternate message factories.

## Dependency baseline

Before recommending a new version, check each selected Maven artifact on Maven Central for its latest stable release compatible with the project's Boot and Framework lines.
Keep project BOM or version-catalog management and existing pins unless the task authorizes changing them.

Use the Boot starter for ordinary SOAP server or client work and add test support for SOAP contract verification.

For Boot 4.0.x and 4.1.x, use `spring-boot-starter-webservices`.
The starter manages the Spring WS version automatically.

For dependency baseline, see [example 1](references/configuration-examples.md#example-1-dependency-baseline).

## SOAP setup

For schema and marshaller, servlet registration, WSDL publication, client setup, or fault mapping, read [SOAP setup](references/soap-setup.md) and [configuration examples](references/configuration-examples.md).
The Boot starter defaults `spring.webservices.path` to `/services`; set it when the contract differs.
Prefer Boot's auto-configured servlet path unless explicit registration is needed.
Set a default URI for `marshalSendAndReceive`, or override it per call.
Use `SoapFaultMappingExceptionResolver` to map exceptions to SOAP faults.

## Implementation guidance

- Start from the XML schema and keep namespace names stable once clients exist.
- Keep each `@PayloadRoot` handler small and map XML objects into application commands or queries immediately.
- Use a marshaller consistently so request and response XML stay schema-aligned.
- Translate domain failures into SOAP faults deliberately instead of leaking generic exceptions.
- Keep client configuration, endpoint URIs, and WS-Security settings in one place.
- Test with real XML payloads instead of Java object assertions alone.

## Edge cases

- Use `@Action` annotation on endpoint methods and `ActionEndpointMapping` or `AnnotationActionEndpointMapping` when the integration contract routes by WS-Addressing Action header.
  - Spring WS supports WS-Addressing 1.0 and the August 2004 draft.
- Use `AddressingEndpointInterceptor` for WS-Addressing validation, duplicate message detection, and out-of-band response delivery.
  - Configure `preInterceptors` and `postInterceptors` on `AbstractAddressingEndpointMapping` subclasses.

## Implementation examples

For SOAP endpoints, server configuration, `WebServiceTemplate` clients, or `MockWebServiceClient` and `MockWebServiceServer` tests, read [implementation examples](references/implementation-examples.md).

For message factory selection, endpoint URIs, payload-root mappings, or SOAP response contract shapes, read [SOAP output shapes](references/soap-output-shapes.md).

## Verification

Use existing SOAP tests for the changed contract and regression risks.
Select server or client coverage for the affected side of the integration, using real XML payloads.
Select the checks that apply:

- Verify the published WSDL and XSD match the actual endpoint namespace and payload roots.
- Verify server-side tests use real XML payloads that satisfy the schema.
- Verify client tests assert the exact SOAP request and response payloads sent through `WebServiceTemplate`.
- For endpoint changes, assert the response XML or SOAP fault shape without bypassing the SOAP stack.
- Verify expected domain failures map to the intended SOAP fault shape.
- Verify WS-Security headers are present only when the integration contract requires them.

## Production checklist

- Keep namespaces, element names, and SOAP action expectations stable after clients are published.
- Bound client timeouts and connection settings for all remote SOAP calls.
- Keep WSDL publication and reverse-proxy base paths aligned so generated locations stay valid.
- Avoid mixing multiple XML marshalling conventions in one module unless the contract truly requires it.
- Treat SOAP contract tests as part of the compatibility surface for releases.

## Output contract

Report the changed SOAP contract, relevant endpoint or client configuration, and verification results.
Identify unverified interoperability or security behavior and any blocker.
Follow the task's required response format.
