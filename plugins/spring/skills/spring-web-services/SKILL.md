---
name: "spring-web-services"
description: "Build contract-first SOAP services and clients in Spring with XSD or WSDL contracts, `@Endpoint` handlers, XML marshalling, `WebServiceTemplate`, and WS-Security integration. Use this skill when building contract-first SOAP services or clients in Spring with XSD or WSDL contracts, `@Endpoint` handlers, XML marshalling, `WebServiceTemplate`, SOAP faults, and WS-Security-aware integration."
metadata:
  title: "Spring Web Services"
  official_project_url: "https://spring.io/projects/spring-ws"
  reference_doc_urls:
    - "https://docs.spring.io/spring-ws/docs/current/reference/html/"
  compatibility_note: "When the reference page and project listing disagree, align examples to the Spring Web Services version already selected in the build."
  version: "5.0.1"
---

Use this skill when building contract-first SOAP services or clients in Spring with XSD or WSDL contracts, `@Endpoint` handlers, XML marshalling, `WebServiceTemplate`, SOAP faults, and WS-Security-aware integration.

## Boundaries

Use `spring-web-services` for SOAP transport, XML contract publication, endpoint mapping, SOAP client calls, and SOAP-specific testing.

- Ordinary HTTP JSON APIs and GraphQL APIs are outside this skill's scope unless the task is SOAP or XML-contract driven.
- Keep business logic outside endpoint handlers. SOAP endpoints should translate XML payloads to application services and back.
- Keep WS-Security, XPath-centric payload parsing, and specialized client transports out of the ordinary path unless those are the actual blocker.

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

For the current Boot 4.x line, use `spring-boot-starter-webservices`. Older Boot lines may still use `spring-boot-starter-web-services`.

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
    return builder.setMarshaller(marshaller).setUnmarshaller(marshaller).build();
}
```

### Basic SOAP fault mapping shape

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

### SOAP server configuration

```java
@Configuration
@EnableWs
class WsConfig extends WsConfigurerAdapter {
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

### Server-side contract test with `MockWebServiceClient`

```java
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
