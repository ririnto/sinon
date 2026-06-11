---
name: spring-web-services
description: >-
  Build contract-first SOAP services and clients in Spring with XSD or WSDL contracts, `@Endpoint` handlers, XML marshalling, `WebServiceTemplate`, and WS-Security integration. Use when generating JAXB types from schema, configuring `MessageDispatcherServlet`, handling SOAP faults, or applying WS-Security policy to SOAP client or server endpoints.
---

# Spring Web Services

## Boundaries

Use `spring-web-services` for SOAP transport, XML contract publication, endpoint mapping, SOAP client calls, and SOAP-specific testing.

- Ordinary HTTP JSON APIs and GraphQL APIs are outside this skill's scope unless the task is SOAP or XML-contract driven.
- Keep business logic outside endpoint handlers. SOAP endpoints should translate XML payloads to application services and back.
- Keep WS-Security, XPath-centric payload parsing, and specialized client transports out of the ordinary path unless those are the actual blocker.

## Baseline

Spring Web Services 5.0 requires JDK 17+ (compatible through JDK 27), Jakarta EE 11 (Servlet 6.1, Jakarta XML Bind 4.0, Jakarta Activation 2.1), Spring Framework 7.0, Spring Security 7.0, Apache WSS4J 4.0, and JUnit 6.0.

- Spring WS 5.0.x aligns with Spring Boot 4.0.x and 4.1.x. The Boot starter manages the Spring WS version.
- `XwsSecurityInterceptor` was removed in Spring WS 4.0 and is not available. Use `Wss4jSecurityInterceptor` for all WS-Security configuration.
- `WsConfigurerAdapter` was removed in Spring WS 5.0. Implement `WsConfigurer` directly (it provides default methods).

## Common path

The ordinary Spring Web Services job is:

1. Define the XSD and WSDL contract first.
2. Generate or hand-maintain the JAXB-bound types and keep them aligned with the schema.
3. Expose the SOAP endpoint path and add manual `MessageDispatcherServlet` registration only when the deployment needs custom servlet mapping.
4. Implement an `@Endpoint` handler that maps one payload root to one application use case.
5. Add basic SOAP fault mapping so domain failures become stable SOAP responses.
6. Add a server or client test that proves the XML payload and SOAP response match the contract.

In Spring Boot, prefer the starter-managed ordinary path first: keep the starter, marshaller, schema, endpoint, and test wiring explicit, but do not reintroduce manual servlet setup unless the deployment actually needs custom servlet registration.

### Branch selector

- Stay in `SKILL.md` for the ordinary endpoint-plus-template path: contract-first XSD and WSDL publication, servlet registration, `@Endpoint` handlers, JAXB marshalling, `WebServiceTemplate`, basic SOAP fault mapping, and contract tests.
- Open [references/ws-security.md](references/ws-security.md) when the integration contract requires signing, encryption, username tokens, or message-level trust.
- Open [references/xpath-endpoints.md](references/xpath-endpoints.md) when payload parsing is too dynamic for ordinary marshalling.
- Open [references/client-variants.md](references/client-variants.md) when the client must use specialized transports or alternate message factories.

## Dependency baseline

Use the Boot starter for ordinary SOAP server or client work and add test support for SOAP contract verification.

For Boot 4.0.x and 4.1.x, use `spring-boot-starter-webservices`. The starter manages the Spring WS version automatically.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webservices</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ws</groupId>
        <artifactId>spring-ws-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## First safe configuration

### Schema and marshaller shape

```java
@Bean
XsdSchema holidaysSchema() {
    return new SimpleXsdSchema(new ClassPathResource("xsd/holidays.xsd"));
}

@Bean
Jaxb2Marshaller marshaller() {
    Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
    marshaller.setContextPath("com.example.hr.schema");
    return marshaller;
}
```

### Boot ordinary-path property shape

```properties
spring.webservices.path=/ws
spring.webservices.wsdl-locations=classpath:/wsdl
```

When the Boot starter is present, `spring.webservices.path` defaults to `/services`. Set it explicitly when the integration contract expects a different path.

### SOAP servlet registration shape

```java
@Bean
ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
    MessageDispatcherServlet servlet = new MessageDispatcherServlet();
    servlet.setApplicationContext(context);
    servlet.setTransformWsdlLocations(true);
    return new ServletRegistrationBean<>(servlet, "/ws/*");
}
```

Use the Boot property path above as the ordinary path first. Add explicit servlet registration only when the application is not relying on Spring Boot's auto-configured SOAP servlet path or when the mapping must differ from the default deployment shape.

### WSDL publication shape

```java
@Bean(name = "holidays")
DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema holidaysSchema) {
    DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
    definition.setPortTypeName("HolidaysPort");
    definition.setLocationUri("/ws");
    definition.setTargetNamespace("http://example.com/hr");
    definition.setSchema(holidaysSchema);
    return definition;
}
```

### `WebServiceTemplateBuilder` client shape

```java
@Bean
WebServiceTemplate webServiceTemplate(WebServiceTemplateBuilder builder, Jaxb2Marshaller marshaller) {
    return builder.setMarshaller(marshaller).setUnmarshaller(marshaller)
        .setDefaultUri("https://example.com/ws").build();
}
```

`setDefaultUri` is required so that `marshalSendAndReceive` has a destination. Override per-call with a URI argument or `WebServiceMessageCallback` when needed.

### Basic SOAP fault mapping shape

Use `SoapFaultMappingExceptionResolver` to map exception types to SOAP fault codes.

```java
@Bean
SoapFaultMappingExceptionResolver soapFaultMappingExceptionResolver() {
    SoapFaultMappingExceptionResolver resolver = new SoapFaultMappingExceptionResolver();
    Properties exceptionMappings = new Properties();
    exceptionMappings.setProperty(BookingNotAllowedException.class.getName(), SoapFaultDefinition.SERVER + ",Booking failed");
    SoapFaultDefinition definition = new SoapFaultDefinition();
    definition.setFaultCode(SoapFaultDefinition.SERVER);
    resolver.setExceptionMappings(exceptionMappings);
    resolver.setDefaultFault(definition);
    resolver.setOrder(1);
    return resolver;
}
```

## Coding procedure

1. Start from the XML schema and keep namespace names stable once clients exist.
2. Keep each `@PayloadRoot` handler small and map XML objects into application commands or queries immediately.
3. Use a marshaller consistently so request and response XML stay schema-aligned.
4. Translate domain failures into SOAP faults deliberately instead of leaking generic exceptions.
5. Keep client configuration, endpoint URIs, and WS-Security settings in one place.
6. Test with real XML payloads, not only Java object assertions.

## Edge cases

- Open [references/ws-security.md](references/ws-security.md) when the contract requires SOAP-level authentication, signing, or encryption.
- Open [references/xpath-endpoints.md](references/xpath-endpoints.md) when payload parsing is too dynamic for JAXB.
- Open [references/client-variants.md](references/client-variants.md) when the client must use a non-ordinary transport or alternate message factory.
- Use `@Action` annotation on endpoint methods and `ActionEndpointMapping` or `AnnotationActionEndpointMapping` when the integration contract routes by WS-Addressing Action header. Spring WS supports WS-Addressing 1.0 and the August 2004 draft.
- Use `AddressingEndpointInterceptor` for WS-Addressing validation, duplicate message detection, and out-of-band response delivery. Configure `preInterceptors` and `postInterceptors` on `AbstractAddressingEndpointMapping` subclasses.

## Implementation examples

### SOAP endpoint

```java
@Endpoint
class HolidayEndpoint {
    private static final String NAMESPACE = "http://example.com/hr";
    private final HolidayService service;

    HolidayEndpoint(HolidayService service) {
        this.service = service;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "HolidayRequest")
    @ResponsePayload
    HolidayResponse handle(@RequestPayload HolidayRequest request) {
        return service.book(request);
    }
}
```

`@PayloadRoot` is the primary routing annotation. Use `@SoapAction` on the same method when the integration contract routes by SOAP Action header instead of payload root.

Spring WS supports both SOAP 1.1 and SOAP 1.2. The default `AxiomSoapMessageFactory` produces SOAP 1.1 messages and can be configured for SOAP 1.2. Use SAAJ when SAAJ-specific behavior is required. See [references/client-variants.md](references/client-variants.md) for message factory details.

### SOAP server configuration

```java
@Configuration
@EnableWs
class WsConfig implements WsConfigurer {
    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        interceptors.add(securityInterceptor());
    }

    @Bean
    Wss4jSecurityInterceptor securityInterceptor() {
        Wss4jSecurityInterceptor interceptor = new Wss4jSecurityInterceptor();
        interceptor.setValidationActions("UsernameToken");
        interceptor.setValidationCallbackHandler(callbackHandler());
        return interceptor;
    }

    @Bean
    ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(context);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean(name = "holidays")
    DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema holidaysSchema) {
        DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("HolidaysPort");
        definition.setLocationUri("/ws");
        definition.setTargetNamespace("http://example.com/hr");
        definition.setSchema(holidaysSchema);
        return definition;
    }
}
```

Use `implements WsConfigurer` and override `addInterceptors` for interceptor registration. Do not extend `WsConfigurerAdapter` -- it was removed in Spring WS 5.0.

### `WebServiceTemplate` client call

```java
@Service
class HolidayClient {
    private final WebServiceTemplate webServiceTemplate;

    HolidayClient(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    HolidayResponse book(HolidayRequest request) {
        return (HolidayResponse) webServiceTemplate.marshalSendAndReceive(request);
    }
}
```

Use `WebServiceMessageCallback` for per-call SOAP Action headers, authentication, or URI overrides.

### Server-side contract test with `MockWebServiceClient`

```java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.ws.test.server.MockWebServiceClient;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.payload;
import org.springframework.xml.transform.StringSource;

@SpringBootTest
class HolidayEndpointTests {
    @Autowired
    ApplicationContext applicationContext;

    @Test
    void respondsWithContractPayload() throws Exception {
        MockWebServiceClient client = MockWebServiceClient.createClient(applicationContext);
        client.sendRequest(withPayload(new StringSource("<HolidayRequest xmlns='http://example.com/hr'/>")))
            .andExpect(noFault())
            .andExpect(payload(new StringSource("<HolidayResponse xmlns='http://example.com/hr'><status>OK</status></HolidayResponse>")));
    }
}
```

### Client-side test with `MockWebServiceServer`

```java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import static org.springframework.ws.test.client.RequestMatchers.payload;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;
import org.springframework.xml.transform.StringSource;

@SpringBootTest
class HolidayClientTests {
    @Autowired
    HolidayClient client;

    @Autowired
    WebServiceTemplate template;

    @Test
    void sendsRequest() {
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect(payload(new StringSource("<HolidayRequest xmlns='http://example.com/hr'/>")))
            .andRespond(withPayload(new StringSource("<HolidayResponse xmlns='http://example.com/hr'><status>OK</status></HolidayResponse>")));
        client.book(new HolidayRequest());
        server.verify();
    }
}
```

## Output and configuration shapes

### Default message factory

Spring WS defaults to `AxiomSoapMessageFactory` producing SOAP 1.1 messages. Configure the selected message factory for SOAP 1.2; use SAAJ when SAAJ features are required. See [references/client-variants.md](references/client-variants.md) for the message factory decision path.

### Endpoint URI shape

```text
/ws
```

### Payload root mapping shape

```java
@PayloadRoot(namespace = "http://example.com/hr", localPart = "HolidayRequest")
```

### SOAP response contract shape

```xml
<HolidayResponse xmlns="http://example.com/hr">
  <status>OK</status>
</HolidayResponse>
```

## Testing checklist

- Verify the published WSDL and XSD match the actual endpoint namespace and payload roots.
- Verify server-side tests use real XML payloads that satisfy the schema.
- Verify client tests assert the exact SOAP request and response payloads sent through `WebServiceTemplate`.
- Verify at least one server-side test asserts the endpoint response XML or SOAP fault shape without bypassing the SOAP stack.
- Verify expected domain failures map to the intended SOAP fault shape.
- Verify WS-Security headers are present only when the integration contract requires them.

## Production checklist

- Keep namespaces, element names, and SOAP action expectations stable after clients are published.
- Bound client timeouts and connection settings for all remote SOAP calls.
- Keep WSDL publication and reverse-proxy base paths aligned so generated locations stay valid.
- Avoid mixing multiple XML marshalling conventions in one module unless the contract truly requires it.
- Treat SOAP contract tests as part of the compatibility surface for releases.

## References

- Open [references/ws-security.md](references/ws-security.md) when the ordinary endpoint-plus-template path is not enough and the task needs SOAP-level signing, encryption, or username tokens.
- Open [references/xpath-endpoints.md](references/xpath-endpoints.md) when the ordinary marshalling path is not enough and the task needs XPath parsing.
- Open [references/client-variants.md](references/client-variants.md) when the ordinary client path is not enough and the task needs special client transports or alternate message factories.
