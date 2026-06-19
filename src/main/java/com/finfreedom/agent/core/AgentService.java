package com.finfreedom.agent.core;

import com.finfreedom.agent.config.PromptConfig;
import com.finfreedom.agent.memory.ConversationMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final PromptConfig promptConfig;
    private final ConversationMemory conversationMemory;

    public AgentResponse getPrompt(AgentRole role) {
        String systemPrompt = promptConfig.getPrompt(role.getPromptKey());
        return AgentResponse.builder()
                .role(role)
                .content(systemPrompt)
                .timestamp(Instant.now())
                .metadata(Map.of("promptKey", role.getPromptKey(), "usage", "paste into Claude Code"))
                .build();
    }

    public void recordStep(String conversationId, AgentRole role, String content) {
        conversationMemory.store(conversationId, role, content);
    }

    public List<AgentRoleInfo> listRoles() {
        return Arrays.stream(AgentRole.values())
                .map(r -> new AgentRoleInfo(r, r.getDisplayName(), r.getPromptKey(), r.getDefaultTemperature()))
                .toList();
    }

    public record AgentRoleInfo(AgentRole role, String displayName, String promptKey, double temperature) {}
}
