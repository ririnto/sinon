# Spring Web Services XPath endpoints

Open this reference when payload parsing is too dynamic or partial for straightforward marshalling.

```java
@XPathParam("/hr:HolidayRequest/hr:employee/text()") String employee
```

Default to marshalling and typed payloads first.
