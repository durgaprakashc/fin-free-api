package com.finfreedom.agent.integration;

import com.finfreedom.agent.integration.mcp.McpServerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Thin facade over {@link McpServerManager} that exposes MCP tool operations
 * to the rest of the integration layer.
 * <p>
 * No Spring AI dependency — MCP communication is pure stdio JSON-RPC 2.0.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolService {

    private final McpServerManager mcpServerManager;

    public List<String> listToolNames() {
        return mcpServerManager.listAllToolNames();
    }

    public List<String> getAllToolNames() {
        return listToolNames();
    }

    public Optional<String> findTool(String toolName) {
        return mcpServerManager.isToolAvailable(toolName) ? Optional.of(toolName) : Optional.empty();
    }

    public boolean isToolAvailable(String toolName) {
        return mcpServerManager.isToolAvailable(toolName);
    }

    public String invokeTool(String toolName, String jsonArgs) {
        log.info("Invoking MCP tool: {}", toolName);
        return mcpServerManager.invokeTool(toolName, jsonArgs);
    }
}
