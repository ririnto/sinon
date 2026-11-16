# Web Services implementation examples

## SOAP endpoint

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

`@PayloadRoot` is the primary routing annotation.
Use `@SoapAction` on the same method when the integration contract routes by SOAP Action header instead of payload root.

Spring WS supports both SOAP 1.1 and SOAP 1.2.
`SaajSoapMessageFactory` is the default for SOAP 1.1.
Configure `AxiomSoapMessageFactory` explicitly when the Axiom alternative is required.
See [references/client-variants.md](client-variants.md) for message factory details.

## SOAP server configuration

```java
@Configuration
@EnableWs
class WsConfig {
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

Use `WsConfigurer` only when optional endpoint interceptors such as WS-Security are required.
See [references/ws-security.md](ws-security.md) for that branch's dependency and test expectations.
Do not extend `WsConfigurerAdapter` -- it was removed in Spring WS 5.0.

## `WebServiceTemplate` client call

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

## Server-side contract test with `MockWebServiceClient`

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

## Client-side test with `MockWebServiceServer`

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
