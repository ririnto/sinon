# Session implementation examples

## Spring Security principal lookup and session invalidation

```java
@Service
class SessionAdministrationService {
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    SessionAdministrationService(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    void expireAllSessionsFor(String username) {
        Map<String, ? extends Session> sessions =
            sessionRepository.findByPrincipalName(username);
        for (String sessionId : sessions.keySet()) {
            sessionRepository.deleteById(sessionId);
        }
    }
}
```

## Example integration test shape

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SessionFlowTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void sessionStateIsReusedAcrossRequests() throws Exception {
        MockHttpServletRequestBuilder firstRequest = post("/cart/items").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"SKU-1\"}");
        MvcResult first = mockMvc.perform(firstRequest)
            .andExpect(status().isOk())
            .andReturn();
        Cookie sessionCookie = first.getResponse().getCookie("SESSION");
        MockHttpServletRequestBuilder secondRequestBuilder = post("/cart/items").cookie(sessionCookie).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"SKU-2\"}");
        ResultActions secondRequest = mockMvc.perform(secondRequestBuilder);
        assertAll(() -> secondRequest.andExpect(status().isOk()), () -> secondRequest.andExpect(jsonPath("$.itemCount").value(2)));
    }
}
```
