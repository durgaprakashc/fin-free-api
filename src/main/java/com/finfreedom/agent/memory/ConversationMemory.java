package com.finfreedom.agent.memory;

import com.finfreedom.agent.core.AgentRole;
import com.finfreedom.agent.core.PipelineStep;

import java.util.List;
import java.util.Optional;

public interface ConversationMemory {

    void store(String conversationId, AgentRole role, String content);

    List<ConversationEntry> getHistory(String conversationId);

    Optional<String> getLatestByRole(String conversationId, AgentRole role);

    String getContextForStep(String conversationId, PipelineStep step);

    void clear(String conversationId);
}
