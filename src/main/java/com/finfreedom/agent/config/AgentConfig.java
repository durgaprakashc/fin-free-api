package com.finfreedom.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent configuration — no ChatClient beans needed.
 * Claude Code (covered by Claude Pro) is the AI orchestrator.
 * Prompts are loaded by PromptConfig and served via AgentController.
 * MCP tool integrations (Jira/GitHub/Confluence) are managed by McpServerManager
 * via direct stdio JSON-RPC — no Spring AI dependency required.
 */
@Configuration
public class AgentConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
