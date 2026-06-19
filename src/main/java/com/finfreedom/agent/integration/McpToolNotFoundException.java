package com.finfreedom.agent.integration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class McpToolNotFoundException extends RuntimeException {

    public McpToolNotFoundException(String toolName) {
        super("MCP tool not found: " + toolName
                + ". Ensure the corresponding MCP server is running and connected.");
    }
}
