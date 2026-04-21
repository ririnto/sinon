# Spring Web Services XPath endpoints

Open this reference when payload parsing is too dynamic or partial for straightforward marshalling.

## When XPath beats marshalling

- Use XPath endpoints when only a few fields matter and generating JAXB types would add more noise than value.
- Stay with marshalling when the schema is stable and the full request or response object belongs in application code.
- Keep namespace bindings explicit because most XPath mistakes in SOAP endpoints come from namespace drift.

```java
@Namespace(prefix = "hr", uri = "http://example.com/hr")
@PayloadRoot(namespace = "http://example.com/hr", localPart = "HolidayRequest")
@ResponsePayload
public HolidayResponse handle(@XPathParam("/hr:HolidayRequest/hr:employee/text()") String employee, @XPathParam("/hr:HolidayRequest/hr:startDate/text()") String startDate) {
    return service.book(employee, startDate);
}
```

```java
@XPathParam("/hr:HolidayRequest/hr:employee/text()") String employee
```

Default to marshalling and typed payloads first.

## Gotchas

- Do not use XPath endpoints as a shortcut when a stable schema already maps cleanly to generated types.
- Do not omit namespace declarations from the endpoint method; the XPath expression usually fails silently when prefixes are wrong.
- Do not parse large payload fragments repeatedly when one unmarshalled request object would be simpler and faster.
