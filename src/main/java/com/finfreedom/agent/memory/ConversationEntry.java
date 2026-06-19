package com.finfreedom.agent.memory;

import com.finfreedom.agent.core.AgentRole;

import java.time.Instant;

public record ConversationEntry(AgentRole role, String content, Instant timestamp) {}
