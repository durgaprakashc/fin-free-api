package com.finfreedom.agent.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a single MCP server subprocess and communicates with it via JSON-RPC 2.0 over stdio.
 * No Spring AI involved — just direct process I/O.
 */
@Slf4j
public class McpJsonRpcClient implements AutoCloseable {

    private static final long TIMEOUT_SECS = 30;

    private final String serverName;
    private final ObjectMapper objectMapper;

    private Process process;
    private PrintWriter stdin;
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private volatile boolean running;

    public McpJsonRpcClient(String serverName, ObjectMapper objectMapper) {
        this.serverName = serverName;
        this.objectMapper = objectMapper;
    }

    public void start(List<String> command, Map<String, String> env) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(env);
        process = pb.start();
        stdin = new PrintWriter(new OutputStreamWriter(process.getOutputStream()), true);
        running = true;

        daemon("mcp-stderr-" + serverName, () -> drain(process.getErrorStream()));
        daemon("mcp-stdout-" + serverName, () -> readLoop(process.getInputStream()));

        log.info("Started MCP server '{}' (pid {})", serverName, process.pid());
    }

    private void drain(InputStream stream) {
        try (var reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[{}] stderr: {}", serverName, line);
            }
        } catch (IOException e) {
            if (running) log.warn("[{}] stderr closed: {}", serverName, e.getMessage());
        }
    }

    private void readLoop(InputStream stream) {
        try (var reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                dispatch(line);
            }
        } catch (IOException e) {
            if (running) log.error("[{}] stdout read error: {}", serverName, e.getMessage());
        } finally {
            // unblock any callers waiting for a response
            pending.values().forEach(f ->
                f.completeExceptionally(new IOException("MCP server '" + serverName + "' disconnected")));
        }
    }

    private void dispatch(String line) {
        if (line.isBlank()) return;
        try {
            JsonNode msg = objectMapper.readTree(line);
            JsonNode idNode = msg.get("id");
            if (idNode != null && !idNode.isNull()) {
                int id = idNode.asInt();
                CompletableFuture<JsonNode> future = pending.remove(id);
                if (future != null) {
                    if (msg.has("error")) {
                        future.completeExceptionally(
                            new IOException("RPC error [" + serverName + "]: " + msg.get("error")));
                    } else {
                        future.complete(msg.get("result"));
                    }
                }
            }
            // Notifications (no id field) are intentionally ignored
        } catch (Exception e) {
            log.debug("[{}] unparseable line: {}", serverName, line);
        }
    }

    /** Send a request and block until the response arrives (or timeout). */
    public JsonNode call(String method, JsonNode params) throws Exception {
        int id = idCounter.getAndIncrement();
        ObjectNode req = objectMapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", method);
        if (params != null) req.set("params", params);

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        stdin.println(objectMapper.writeValueAsString(req));

        try {
            return future.get(TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.remove(id);
            throw new IOException("Timeout waiting for '" + method + "' from MCP server '" + serverName + "'");
        }
    }

    /** Send a JSON-RPC notification (fire-and-forget, no id). */
    public void notify(String method, JsonNode params) throws Exception {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("jsonrpc", "2.0");
        n.put("method", method);
        if (params != null) n.set("params", params);
        stdin.println(objectMapper.writeValueAsString(n));
    }

    public String getServerName() {
        return serverName;
    }

    @Override
    public void close() {
        running = false;
        if (process != null && process.isAlive()) {
            process.destroy();
            log.info("Stopped MCP server '{}'", serverName);
        }
    }

    private void daemon(String name, Runnable task) {
        Thread t = new Thread(task, name);
        t.setDaemon(true);
        t.start();
    }
}
