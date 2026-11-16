# Configuration and test examples

## Example 1: Dependency baseline

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

## Example 2: Schema and marshaller shape

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

## Example 3: SOAP servlet registration shape

```java
@Bean
ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
    MessageDispatcherServlet servlet = new MessageDispatcherServlet();
    servlet.setApplicationContext(context);
    servlet.setTransformWsdlLocations(true);
    return new ServletRegistrationBean<>(servlet, "/ws/*");
}
```

## Example 4: WSDL publication shape

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

## Example 5: `WebServiceTemplateBuilder` client shape

```java
@Bean
WebServiceTemplate webServiceTemplate(WebServiceTemplateBuilder builder, Jaxb2Marshaller marshaller) {
    return builder.setMarshaller(marshaller).setUnmarshaller(marshaller)
        .setDefaultUri("https://example.com/ws").build();
}
```

## Example 6: Basic SOAP fault mapping shape

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
