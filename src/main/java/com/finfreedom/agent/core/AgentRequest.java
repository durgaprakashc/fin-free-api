package com.finfreedom.agent.core;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {

    @NotNull
    private AgentRole role;

    @NotBlank
    private String prompt;

    private Map<String, Object> context;

    private String conversationId;
}
