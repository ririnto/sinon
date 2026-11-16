# Configuration and test examples

## Example 1: Servlet MVC baseline

```java
@RestController
@RequestMapping("/orders")
class OrderController {
    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/{id}")
    Order get(@PathVariable Long id) {
        return orders.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Order create(@RequestBody @Valid CreateOrderRequest request) {
        return orders.create(request);
    }
}
```

## Example 2: Servlet MVC baseline

```java
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleNotFound(OrderNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        return new ErrorResponse(ex.getBindingResult().getFieldError().getDefaultMessage());
    }
}
```

## Example 3: Reactive WebFlux baseline

```java
@RestController
@RequestMapping("/items")
class ItemController {
    private final ItemService items;

    ItemController(ItemService items) {
        this.items = items;
    }

    @GetMapping("/{id}")
    Mono<Item> get(@PathVariable Long id) {
        return items.findById(id);
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<Item> stream() {
        return items.streamAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Mono<Item> create(@RequestBody @Valid Mono<CreateItemRequest> request) {
        return request.flatMap(items::create);
    }
}
```

## Example 4: API versioning and HTTP interfaces

```java
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "order", types = {OrderClient.class, OrderAdminClient.class})
class HttpServicesConfiguration {
    @Bean
    RestClientHttpServiceGroupConfigurer groupConfigurer() {
        return groups -> groups.forEachClient((group, builder) -> builder.defaultHeader("User-Agent", "My-App"));
    }
}
```

## Example 5: WebClient baseline

```java
@Bean
WebClient orderWebClient() {
    return WebClient.builder().baseUrl("https://api.example.com").defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
}

Mono<Order> loadOrder(WebClient client, long orderId) {
    return client.get()
        .uri("/orders/{id}", orderId)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, response -> response.bodyToMono(String.class).defaultIfEmpty("").map(ApiException::new))
        .onStatus(HttpStatusCode::is5xxServerError, response -> response.bodyToMono(String.class).defaultIfEmpty("").map(UpstreamServiceException::new))
        .bodyToMono(Order.class);
}
```

## Example 6: Focused web tests

```java
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {AppConfig.class, WebConfig.class})
class OrderControllerTests {
    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void getOrderReturnsOk() throws Exception {
        mockMvc.perform(get("/orders/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }
}
```

## Example 7: Focused web tests

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AppConfig.class, ReactiveWebConfig.class})
class ItemControllerTests {
    @Autowired
    ApplicationContext context;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @Test
    void getItemReturnsOk() {
        client.get().uri("/items/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody(Item.class)
            .value(item -> assertNotNull(item.id()));
    }
}
```
