package com.finfreedom.agent.memory;

import com.finfreedom.agent.core.AgentRole;
import com.finfreedom.agent.core.PipelineStep;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryConversationMemoryStore implements ConversationMemory {

    private final Map<String, List<ConversationEntry>> store = new ConcurrentHashMap<>();

    @Override
    public void store(String conversationId, AgentRole role, String content) {
        store.computeIfAbsent(conversationId, k -> new ArrayList<>())
             .add(new ConversationEntry(role, content, Instant.now()));
    }

    @Override
    public List<ConversationEntry> getHistory(String conversationId) {
        return List.copyOf(store.getOrDefault(conversationId, List.of()));
    }

    @Override
    public Optional<String> getLatestByRole(String conversationId, AgentRole role) {
        List<ConversationEntry> history = store.getOrDefault(conversationId, List.of());
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).role() == role) {
                return Optional.of(history.get(i).content());
            }
        }
        return Optional.empty();
    }

    @Override
    public String getContextForStep(String conversationId, PipelineStep step) {
        StringBuilder context = new StringBuilder("=== Previous Pipeline Context ===\n\n");
        boolean hasContext = false;

        for (AgentRole role : relevantRolesForStep(step)) {
            Optional<String> content = getLatestByRole(conversationId, role);
            if (content.isPresent()) {
                context.append("[").append(role.getDisplayName()).append(" Output]:\n")
                       .append(content.get()).append("\n\n");
                hasContext = true;
            }
        }

        if (!hasContext) {
            return "";
        }

        context.append("=== End Context ===\n");
        return context.toString();
    }

    @Override
    public void clear(String conversationId) {
        store.remove(conversationId);
    }

    private List<AgentRole> relevantRolesForStep(PipelineStep step) {
        return switch (step) {
            case REQUIREMENT -> List.of();
            case STORIES -> List.of(AgentRole.PM);
            case DESIGN -> List.of(AgentRole.PM);
            case CODE -> List.of(AgentRole.ARCHITECT);
            case TEST -> List.of(AgentRole.DEVELOPER);
            case REVIEW -> List.of(AgentRole.DEVELOPER, AgentRole.QA);
            case FIX -> List.of(AgentRole.REVIEWER, AgentRole.DEVELOPER);
        };
    }
}
