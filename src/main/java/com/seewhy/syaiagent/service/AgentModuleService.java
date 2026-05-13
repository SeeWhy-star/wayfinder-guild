package com.seewhy.syaiagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.model.rpg.AgentModule;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class AgentModuleService {

    private static final String MODULES_RESOURCE = "rpg/modules.json";

    private final List<AgentModule> modules;

    public AgentModuleService(ObjectMapper objectMapper) {
        this.modules = readModules(objectMapper);
    }

    public List<AgentModule> getModules() {
        return modules;
    }

    private List<AgentModule> readModules(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(MODULES_RESOURCE);
        try {
            return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load RPG modules resource: " + MODULES_RESOURCE, ex);
        }
    }
}
