# Spring Framework AspectJ and load-time weaving

Open this reference when the task needs AspectJ load-time weaving, field or constructor join points, or weaving that persists across deployment boundaries.

## When to use AspectJ instead of Spring AOP

Spring AOP (proxy-based) handles method execution join points only. Use AspectJ when:

- Join points include field access or constructor calls
- Weaving must persist across classloader or deployment boundaries
- The application uses `@Configurable` for domain object injection
- Performance overhead of proxy creation is unacceptable

## Enable load-time weaving

Add the minimum AspectJ load-time weaving libraries first. In this standalone Spring Framework BOM path, `aspectjweaver` keeps an explicit version because `spring-framework-bom` does not manage `org.aspectj:*` artifacts.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-framework-bom</artifactId>
            <version>7.0.7</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-aop</artifactId>
</dependency>
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjweaver</artifactId>
    <version>1.9.25</version>
</dependency>
</dependencies>
```

Add these optional libraries only when the task requires them:

```xml
<dependencies>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-aspects</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-instrument</artifactId>
</dependency>
</dependencies>
```

Use `spring-aspects` for `@Configurable` support or other Spring-provided AspectJ aspects. Add `spring-instrument` only when the runtime uses Spring's instrumentation-agent path.

If the application instead follows Spring Boot dependency management, omit the explicit `aspectjweaver` version. Do not add a second direct `aspectjweaver` declaration when another Boot-managed dependency already brings it onto the classpath unless the task explicitly requires a manual override.

Configure the Java agent in the runtime. The most common AspectJ LTW path uses the AspectJ weaver agent directly:

```sh
-javaagent:path/to/aspectjweaver.jar
```

Use Spring's instrumentation agent only when the runtime setup requires that path:

```sh
-javaagent:path/to/spring-instrument.jar
```

Enable load-time weaving in the application context. Use the explicit AspectJ weaving mode when the configuration itself is responsible for turning weaving on:

```java
@Configuration
@EnableLoadTimeWeaving(aspectjWeaving = AspectJWeaving.ENABLED)
@EnableSpringConfigured
class AppConfig {
}
```

## META-INF/aop.xml configuration

Place `META-INF/aop.xml` on the classpath:

```xml
<!DOCTYPE aspectj PUBLIC "-//AspectJ//DTD//EN" "http://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>
    <weaver>
        <include within="com.example..*"/>
    </weaver>
    <aspects>
        <aspect name="com.example.common.ProfilingAspect"/>
    </aspects>
</aspectj>
```

The weaver processes only classes in the specified package range. Narrow the scope to avoid unnecessary weaving overhead.

## @Configurable for domain object injection

Inject Spring beans into domain objects not managed by the container:

```java
@Configurable(autowire = Autowire.BY_TYPE)
class Order {
    @Autowired
    private OrderValidator validator;

    public void validate() {
        validator.check(this);
    }
}
```

Enable annotation-driven introduction:

```xml
<context:spring-configured/>
```

Or in Java configuration:

```java
@Configuration
@EnableSpringConfigured
class AppConfig {
}
```

Use `@Configurable` sparingly. It requires LTW and adds startup cost. Most domain objects do not need direct container access.

## Aspect definition for LTW

```java
@Aspect
class MeasurementAspect {
    @Around("execution(* com.example..service.*.*(..))")
    Object measure(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        Object result = pjp.proceed();
        long elapsed = System.nanoTime() - start;
        System.out.println(pjp + " took " + elapsed + " ns");
        return result;
    }
}
```

The aspect runs in the woven class, not through a Spring proxy. Field and constructor join points work normally.

## Decision points

| Situation | Use |
| --- | --- |
| Method execution only | Spring AOP proxy |
| Field access or constructor calls | AspectJ LTW |
| Domain object needs container bean | `@Configurable` + LTW |
| Weaving must survive classloader reload | AspectJ LTW |
