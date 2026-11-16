# SOAP setup

## Schema and marshaller shape

For schema and marshaller shape, see [example 2](configuration-examples.md#example-2-schema-and-marshaller-shape).

## Boot ordinary-path property shape

```properties
spring.webservices.path=/ws
spring.webservices.wsdl-locations=classpath:/wsdl
```

When the Boot starter is present, `spring.webservices.path` defaults to `/services`.
Set it explicitly when the integration contract expects a different path.

## SOAP servlet registration shape

For SOAP servlet registration shape, see [example 3](configuration-examples.md#example-3-soap-servlet-registration-shape).

Use the Boot property path above as the ordinary path first.
Add explicit servlet registration only when the application is not relying on Spring Boot's auto-configured SOAP servlet path or when the mapping must differ from the default deployment shape.

## WSDL publication shape

For WSDL publication shape, see [example 4](configuration-examples.md#example-4-wsdl-publication-shape).

## `WebServiceTemplateBuilder` client shape

For `WebServiceTemplateBuilder` client shape, see [example 5](configuration-examples.md#example-5-webservicetemplatebuilder-client-shape).

`setDefaultUri` is required so that `marshalSendAndReceive` has a destination.
Override per-call with a URI argument or `WebServiceMessageCallback` when needed.

## Basic SOAP fault mapping shape

Use `SoapFaultMappingExceptionResolver` to map exception types to SOAP fault codes.

For basic SOAP fault mapping shape, see [example 6](configuration-examples.md#example-6-basic-soap-fault-mapping-shape).
