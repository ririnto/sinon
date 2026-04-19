# Spring Web Services WS-Security

Open this reference when the ordinary endpoint-plus-template path in `SKILL.md` is not enough and the task needs signing, encryption, username tokens, or message-level trust.

## WS-Security boundary

Add WS-Security only when the SOAP integration contract explicitly requires signing, encryption, username tokens, or message-level trust.

- Good fit: enterprise SOAP integrations with formal policy requirements.
- Poor fit: internal services that already rely on transport-level TLS and do not require SOAP security headers.

Keep WS-Security configuration close to the SOAP client or server configuration layer.

## Interceptor shape

```java
@Bean
Wss4jSecurityInterceptor securityInterceptor() {
    Wss4jSecurityInterceptor interceptor = new Wss4jSecurityInterceptor();
    interceptor.setValidationActions("UsernameToken");
    interceptor.setValidationCallbackHandler(callbackHandler());
    return interceptor;
}
```

Attach the interceptor where the SOAP messages actually enter or leave:

- server side: add it to the endpoint interceptor chain
- client side: add it to the `WebServiceTemplate` interceptor list

## Gotchas

- Do not add WS-Security just because the transport already uses TLS.
- Do not scatter keystore and interceptor configuration across unrelated modules.
- Do not treat message-level security as part of the ordinary SOAP path when the contract does not require it.
