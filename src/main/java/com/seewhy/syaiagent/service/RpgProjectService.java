package com.seewhy.syaiagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.model.rpg.RpgProject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class RpgProjectService {

    private static final String PROJECTS_RESOURCE = "rpg/projects.json";

    private final List<RpgProject> projects;

    public RpgProjectService(ObjectMapper objectMapper) {
        this.projects = readProjects(objectMapper);
    }

    public List<RpgProject> getProjects() {
        return projects;
    }

    private List<RpgProject> readProjects(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(PROJECTS_RESOURCE);
        try {
            return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load RPG projects resource: " + PROJECTS_RESOURCE, ex);
        }
    }
}
