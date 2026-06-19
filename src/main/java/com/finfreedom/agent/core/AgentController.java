package com.finfreedom.agent.core;

import com.finfreedom.agent.memory.ConversationEntry;
import com.finfreedom.agent.memory.ConversationMemory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@Tag(name = "Agent", description = "Prompt registry and pipeline step tracking. Claude Code is the AI — these endpoints serve prompts and record results.")
public class AgentController {

    private final AgentService agentService;
    private final ConversationMemory conversationMemory;

    @GetMapping("/roles")
    @Operation(summary = "List all agent roles with metadata")
    public ResponseEntity<List<AgentService.AgentRoleInfo>> listRoles() {
        return ResponseEntity.ok(agentService.listRoles());
    }

    @GetMapping("/prompts/{role}")
    @Operation(summary = "Get the system prompt for a role — paste this into Claude Code to activate the agent persona")
    public ResponseEntity<AgentResponse> getPrompt(@PathVariable AgentRole role) {
        return ResponseEntity.ok(agentService.getPrompt(role));
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "Get conversation history for a pipeline run")
    public ResponseEntity<List<ConversationEntry>> getConversation(@PathVariable String id) {
        return ResponseEntity.ok(conversationMemory.getHistory(id));
    }

    @PostMapping("/conversations/{id}/record")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a completed pipeline step result from Claude Code")
    public ResponseEntity<Void> recordStep(
            @PathVariable String id,
            @RequestBody @Valid RecordStepRequest request) {
        agentService.recordStep(id, request.getRole(), request.getContent());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear a conversation / start a fresh pipeline run")
    public ResponseEntity<Void> clearConversation(@PathVariable String id) {
        conversationMemory.clear(id);
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class RecordStepRequest {
        @NotNull
        private AgentRole role;
        @NotBlank
        private String content;
    }
}
