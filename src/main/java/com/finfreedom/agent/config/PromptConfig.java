package com.finfreedom.agent.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class PromptConfig {

    private final ResourceLoader resourceLoader;
    private final Map<String, String> prompts = new HashMap<>();

    public PromptConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void loadPrompts() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
            Resource[] resources = resolver.getResources("classpath:prompts/*.st");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null) {
                    String key = filename.replace(".st", "");
                    String content = resource.getContentAsString(StandardCharsets.UTF_8);
                    prompts.put(key, content);
                    log.info("Loaded prompt: {}", key);
                }
            }
        } catch (IOException e) {
            log.warn("Could not scan prompts directory: {}", e.getMessage());
        }
    }

    public String getPrompt(String key) {
        return prompts.getOrDefault(key,
                "You are an AI assistant specialized in software development. Help with the given task.");
    }
}
