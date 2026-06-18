package com.finfreedom.agent.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finfreedom.agent.integration.McpToolNotFoundException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads mcp-servers.json, spawns each configured MCP server as an npx subprocess,
 * performs the MCP initialize handshake, and builds a tool-name → client index.
 * <p>
 * All tool invocations are pure stdio JSON-RPC 2.0 — no Spring AI, no Anthropic API calls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpServerManager {

    /** Matches ${VAR_NAME:default_value} placeholders in mcp-servers.json. */
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?\\}");
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    private final ObjectMapper objectMapper;

    private final List<McpJsonRpcClient> clients = new ArrayList<>();
    /** Maps tool name → the MCP client that exposes it. */
    private final ConcurrentHashMap<String, McpJsonRpcClient> toolIndex = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("mcp-servers.json")) {
            if (is == null) {
                log.warn("mcp-servers.json not found on classpath — MCP integration disabled");
                return;
            }
            JsonNode root = objectMapper.readTree(is);
            root.path("mcpServers").fields().forEachRemaining(entry -> {
                try {
                    startServer(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    log.error("Failed to start MCP server '{}': {}", entry.getKey(), e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            log.error("MCP initialization failed — integration layer disabled: {}", e.getMessage(), e);
        }
    }

    private void startServer(String name, JsonNode config) throws Exception {
        List<String> cmd = buildCommand(config);
        Map<String, String> env = buildEnv(config.path("env"));

        McpJsonRpcClient client = new McpJsonRpcClient(name, objectMapper);
        client.start(cmd, env);
        clients.add(client);

        handshake(client);
        discoverTools(client);
    }

    /** MCP initialize + initialized notification. */
    private void handshake(McpJsonRpcClient client) throws Exception {
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "fin-freedom");
        clientInfo.put("version", "1.0.0");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        params.set("capabilities", objectMapper.createObjectNode());
        params.set("clientInfo", clientInfo);

        client.call("initialize", params);
        client.notify("notifications/initialized", null);
        log.info("MCP handshake complete for '{}'", client.getServerName());
    }

    /** Lists tools from the server and registers them in the index. */
    private void discoverTools(McpJsonRpcClient client) throws Exception {
        JsonNode result = client.call("tools/list", null);
        int count = 0;
        for (JsonNode tool : result.path("tools")) {
            String toolName = tool.path("name").asText();
            toolIndex.put(toolName, client);
            count++;
        }
        log.info("MCP server '{}' registered {} tool(s)", client.getServerName(), count);
    }

    /**
     * Invoke an MCP tool by name with JSON-encoded arguments.
     *
     * @param toolName  the MCP tool name (e.g., "jira_create_issue")
     * @param jsonArgs  JSON string of the tool arguments object
     * @return the text content from the MCP tool response
     */
    public String invokeTool(String toolName, String jsonArgs) {
        McpJsonRpcClient client = toolIndex.get(toolName);
        if (client == null) {
            throw new McpToolNotFoundException(toolName);
        }
        try {
            JsonNode arguments = objectMapper.readTree(jsonArgs);
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", arguments);

            JsonNode result = client.call("tools/call", params);
            return extractText(result);
        } catch (McpToolNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("MCP tool invocation failed [" + toolName + "]: " + e.getMessage(), e);
        }
    }

    /** Extracts the first text content block from a tools/call result. */
    private String extractText(JsonNode result) {
        JsonNode content = result.path("content");
        if (content.isArray() && !content.isEmpty()) {
            JsonNode first = content.get(0);
            if ("text".equals(first.path("type").asText())) {
                return first.path("text").asText();
            }
            return first.toString();
        }
        return result.toString();
    }

    public List<String> listAllToolNames() {
        return new ArrayList<>(toolIndex.keySet());
    }

    public boolean isToolAvailable(String toolName) {
        return toolIndex.containsKey(toolName);
    }

    // ── command / env helpers ────────────────────────────────────────────────

    private List<String> buildCommand(JsonNode config) {
        String rawCmd = resolveEnv(config.path("command").asText("npx"));
        // On Windows, npx ships as npx.cmd
        String cmd = IS_WINDOWS && "npx".equals(rawCmd) ? "npx.cmd" : rawCmd;

        List<String> parts = new ArrayList<>();
        parts.add(cmd);
        config.path("args").forEach(arg -> parts.add(resolveEnv(arg.asText())));
        return parts;
    }

    private Map<String, String> buildEnv(JsonNode envNode) {
        Map<String, String> env = new HashMap<>();
        if (envNode.isObject()) {
            envNode.fields().forEachRemaining(e ->
                env.put(e.getKey(), resolveEnv(e.getValue().asText())));
        }
        return env;
    }

    /** Resolves ${VAR_NAME:default} placeholders using System environment variables. */
    private String resolveEnv(String value) {
        if (value == null || !value.contains("${")) return value;
        Matcher m = ENV_PLACEHOLDER.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String varName = m.group(1);
            String defaultVal = m.group(2) != null ? m.group(2) : "";
            String envVal = System.getenv(varName);
            m.appendReplacement(sb, Matcher.quoteReplacement(envVal != null ? envVal : defaultVal));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @PreDestroy
    public void shutdown() {
        clients.forEach(McpJsonRpcClient::close);
        log.info("All MCP servers stopped");
    }
}
