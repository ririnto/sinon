# SOAP output shapes

## Default message factory

`SaajSoapMessageFactory` is the default for SOAP 1.1.
Configure `AxiomSoapMessageFactory` explicitly when the Axiom alternative is required, including for SOAP 1.2.
Use SAAJ-specific settings when needed.
See [references/client-variants.md](client-variants.md) for the message factory decision path.

## Endpoint URI shape

```text
/ws
```

## Payload root mapping shape

```java
@PayloadRoot(namespace = "http://example.com/hr", localPart = "HolidayRequest")
```

## SOAP response contract shape

```xml
<HolidayResponse xmlns="http://example.com/hr">
  <status>OK</status>
</HolidayResponse>
```
