# Spring AI MCP client and server boundaries

Open this reference when the task involves Model Context Protocol connections, MCP-capable tool calling, or decisions about whether Spring AI acts as an MCP client or MCP server.

Keep MCP transport and authorization boundaries explicit. Do not collapse MCP tool calls into ordinary tool-calling patterns.

## When to open this

- Configuring Spring AI as an MCP client connecting to an external MCP server
- Exposing Spring AI functions through an MCP server endpoint
- Choosing the MCP transport protocol (stdio, SSE, or Streamable HTTP)
- Handling MCP-specific authorization or authentication
- Debugging MCP tool resolution or capability negotiation

## MCP client boundary

Spring AI can act as an MCP client that calls tools exposed by an external MCP server. This is distinct from ordinary Spring AI tool calling where tool implementations live inside the application.

### MCP client starter choice

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

Use the client starter when the application needs to discover and invoke MCP tools from another process or service.

### Calling MCP tools through ChatClient

```java
@Service
class McpToolCaller {
    private final ChatClient chat;

    McpToolCaller(ChatClient.Builder builder, ToolCallbackProvider mcpTools) {
        this.chat = builder.defaultToolCallbacks(mcpTools).build();
    }

    String query(String question) {
        return chat.prompt()
            .user(question)
            .call()
            .content();
    }
}
```

The MCP client starter exposes MCP-backed tool callbacks that can be attached to `ChatClient`. Tool availability depends on the capabilities exposed by the remote MCP server.

## MCP server boundary

Spring AI can expose tools, resources, or prompts as an MCP server so external MCP clients can call them. Use this when other MCP clients in the system need access to Spring-managed capabilities.

### MCP server starter choice

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

Then configure the transport explicitly:

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: SSE
        type: SYNC
        annotation-scanner:
          enabled: true
```

### Exposing a Spring tool via MCP annotations

```java
@Component
class WeatherTools {
    @McpTool(name = "weather", description = "Get current weather for a location")
    String weather(String location) {
        return "The weather in " + location + " is sunny.";
    }
}
```

With the MCP server starter and annotation scanning enabled, Spring AI discovers annotated beans and registers MCP capabilities automatically.

## Transport choices

| Transport | Use when | Pitfall |
| --- | --- | --- |
| stdio | Local process, fast startup, containerized tools | Best for local or tightly coupled process boundaries |
| HTTP/SSE | Remote servers, firewalls, standard HTTP infrastructure | Requires HTTP endpoint availability; SSE is unidirectional |
| Streamable HTTP | Newer MCP HTTP deployments with request/response semantics | Requires explicit protocol configuration |

Choose the transport based on deployment topology, not library preference.

## MCP authorization and audit

MCP tool calls represent external system access. Treat them like any other tool boundary:

- Authenticate MCP server connections using the transport-specific mechanisms (API keys, mTLS, OAuth).
- Audit MCP tool invocations separately from ordinary chat logs. MCP calls can have different cost, latency, and authorization profiles.
- Validate that the MCP server capabilities match what the application expects before registering tools.

```java
@Service
class McpAuditService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    void auditMcpCall(String toolName, String arguments) {
        logger.atInfo().log(() -> "MCP tool invoked: tool=" + toolName + " args=" + arguments);
    }
}
```

## MCP versus ordinary tool calling

Do not treat MCP tools and ordinary Spring AI `@Tool` methods as equivalent.

| Aspect | Ordinary `@Tool` | MCP tool |
| --- | --- | --- |
| Implementation | In-application Java method | External process or service |
| Transport | Direct JVM call | stdio, SSE, Streamable HTTP, stateless HTTP |
| Capability negotiation | Static annotation | Dynamic at runtime |
| Tool list | Compile-time known | Resolved from MCP server |
| Error handling | Standard Java exceptions | MCP protocol error messages |
| Audit | Standard logging | MCP-specific audit |

Use ordinary `@Tool` for in-process operations. Use MCP tools when the tool implementation lives outside the Spring AI application.

## Decision points

| Situation | Choice |
| --- | --- |
| Tool lives in the same application | Ordinary `@Tool` annotation |
| Tool lives in an external process | MCP stdio client |
| Tool lives on a remote HTTP service | MCP HTTP/SSE client |
| Application exposes tools to external MCP clients | MCP server starter plus `@McpTool`/related annotations |
| Need to compose multiple MCP servers | Register multiple `McpClient` beans and compose in `ChatClient.Builder` |
| Debugging MCP capability mismatch | Enable debug logging for `McpClient` and inspect the capability list |

## Pitfalls

- Do not register an MCP client as an ordinary tool. Use the MCP client integration path instead of pretending the remote tool is an in-process bean.
- MCP tool resolution happens at registration time. If the MCP server is unavailable when the `McpClient` bean is created, tool registration fails.
- stdio transports block on read/write. Avoid stdio for tools that may be called concurrently.
- MCP servers that expose many tools can cause a large prompt payload. Consider filtering the tool list.
- Authorization between MCP client and server is not handled by the Spring AI library. Configure transport-level security explicitly.
