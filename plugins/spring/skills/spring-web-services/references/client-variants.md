# Spring Web Services client variants

Open this reference when the SOAP client must use specialized transports or alternate message factories.

## Variant selector

- Keep the ordinary path in `SKILL.md` when `WebServiceTemplateBuilder` plus the default message factory is enough.
- Use a custom message factory when the integration needs SOAP 1.2, MTOM, or lower-level SAAJ tuning.
- Use a transport-specific sender when the integration contract requires custom HTTP timeouts, authentication, or a non-default transport stack.

## Dependency hint

When this branch adds a transport sender beyond the starter-managed ordinary path, add the transport dependency explicitly in the build file and keep it next to the SOAP client configuration it serves.

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

## HTTP sender shape

```java
@Bean
HttpComponentsMessageSender messageSender() {
    HttpComponentsMessageSender sender = new HttpComponentsMessageSender();
    sender.setConnectionTimeout(5_000);
    sender.setReadTimeout(5_000);
    return sender;
}
```

```xml
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
</dependency>
```

```java
@Bean
WebServiceTemplate webServiceTemplate(Jaxb2Marshaller marshaller, SaajSoapMessageFactory messageFactory, HttpComponentsMessageSender messageSender) {
    WebServiceTemplate template = new WebServiceTemplate(marshaller);
    template.setMessageFactory(messageFactory);
    template.setMessageSender(messageSender);
    return template;
}
```

## Message factory decision points

- Stay with SAAJ when the client only needs standard SOAP envelopes and headers.
- Change the message factory when SOAP version, attachment handling, or vendor interoperability requires it.
- Keep the marshaller, message factory, and sender together in one configuration class so transport behavior is observable.

## Gotchas

- Do not mix multiple message factories in one client path unless the provider contract forces it.
- Do not hide timeout or authentication behavior in unrelated infrastructure code.
- Do not switch transports without re-running exact XML request and fault assertions.
- Do not assume the starter alone brings in every transport-specific sender dependency.
