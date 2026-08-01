# Spring Framework AOP cross-cutting

Open this reference when the ordinary container-plus-transactions path is not enough and the task specifically needs Spring AOP.

Use AOP when cross-cutting behavior must wrap many beans consistently.

## Enable proxy-based AOP

Use `@EnableAspectJAutoProxy` when the application needs proxy-based AOP with `@Aspect` classes.

```java
@Configuration
@EnableAspectJAutoProxy
class AppConfig {
}
```

Spring Framework 7.0 defaults all proxy processors to CGLIB, matching Spring Boot behavior.
When `@EnableAspectJAutoProxy` is used alongside other proxy-based features (`@Async`, `@Transactional`), all share the same CGLIB default.
Use `@Proxyable(ProxyType.INTERFACES)` on individual beans to opt out.

## Basic aspect shape

```java
@Aspect
@Component
class ProfilingAspect {
    @Around("execution(* com.example..service.*.*(..))")
    Object profile(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }
}
```

## Common pointcut designators

- `execution(..)` for method execution matching
- `within(..)` for package or type scoping
- `this(..)` and `target(..)` for proxy or target type matching
- `args(..)` for argument-type matching
- `@annotation(..)` for annotation-driven advice
- `bean(..)` for Spring bean-name matching

### Shared pointcut class

```java
class CommonPointcuts {
    @Pointcut("within(com.example.service..*)")
    void inServiceLayer() {
    }

    @Pointcut("execution(* com.example.dao.*.*(..))")
    void dataAccessOperation() {
    }
}
```

## Advice type selection

```java
@Before("CommonPointcuts.dataAccessOperation()")
void beforeCall() {
}

@AfterReturning(pointcut = "CommonPointcuts.dataAccessOperation()", returning = "result")
void afterReturn(Object result) {
}

@AfterThrowing(pointcut = "CommonPointcuts.dataAccessOperation()", throwing = "ex")
void afterThrow(Exception ex) {
}
```

Use `@Around` only when the advice must control execution or timing.
Prefer narrower advice types when that is enough.

## Decision points

| Situation | Guidance |
| --- | --- |
| Cross-cutting concern on many beans | AOP is a good fit |
| One clear call site | use a normal method call or decorator |
| Need `@Async` and `@Scheduled` on the same method | keep scheduling and async execution separate unless proxy and executor behavior are fully explicit |
| Need field or constructor join points | Spring AOP is not enough; full AspectJ may be required |
