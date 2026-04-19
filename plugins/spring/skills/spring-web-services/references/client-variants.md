# Spring Web Services client variants

Open this reference when the SOAP client must use specialized transports or alternate message factories.

```java
@Bean
SaajSoapMessageFactory messageFactory() {
    return new SaajSoapMessageFactory();
}

@Bean
WebServiceTemplate webServiceTemplate(Jaxb2Marshaller marshaller, SaajSoapMessageFactory messageFactory) {
    WebServiceTemplate template = new WebServiceTemplate(marshaller);
    template.setMessageFactory(messageFactory);
    return template;
}
```

Keep one client transport style per module unless multiple external providers force otherwise.
