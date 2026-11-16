# Servlet examples and tests

## Implementation examples

### Method security

```java
@Service
class InvoiceService {
    @PreAuthorize("hasAuthority('SCOPE_invoices:write')")
    void approve(long invoiceId) {
    }

    @PostAuthorize("returnObject.owner().equals(authentication.name)")
    InvoiceView load(long invoiceId) {
        return new InvoiceView(invoiceId, "alice");
    }
}
```

### Collection filtering branch

```java
@PreFilter(filterTarget = "invoiceIds", value = "filterObject > 0")
void reprocess(List<Long> invoiceIds) {
}
```

### Security test

```java
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTests {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void requiresScope() throws Exception {
        MockHttpServletRequestBuilder request = get("/admin").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_admin")));
        mvc.perform(request).andExpect(status().isOk());
    }
}
```

### @WithMockUser test support

```java
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTests {
    @Autowired
    MockMvc mvc;

    @Test
    @WithMockUser(username = "alice@example.com", authorities = "ROLE_ADMIN")
    void adminAccessForAuthenticatedUser() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(status().isOk());
    }

    @Test
    void rejectsAnonymousRequest() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(status().is3xxRedirection());
    }
}
```

Use `@WithUserDetails` only when the test slice also provides the matching `UserDetailsService` entry for that username.

### Denied-access test shape for API security

```java
@Test
void rejectsAnonymousRequest() throws Exception {
    mvc.perform(get("/admin"))
        .andExpect(status().isUnauthorized());
}
```

Use this 401 expectation only for API/basic/bearer-token style security chains that do not redirect to a login page.

### Forbidden-access test shape

```java
@Test
void rejectsInsufficientAuthority() throws Exception {
    MockHttpServletRequestBuilder request = get("/admin").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_read")));
    mvc.perform(request).andExpect(status().isForbidden());
}
```

## Output and configuration shapes

### Protected scope shape

```text
SCOPE_invoices:write
```

### Public endpoint rule shape

```java
.requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
```

### JWT-enabled resource-server shape

```java
.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
```

### Stateless session shape

```java
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

### Basic logout shape

```java
.logout(logout -> logout.logoutSuccessUrl("/login?logout"))
```
