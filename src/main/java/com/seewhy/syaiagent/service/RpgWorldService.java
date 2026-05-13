package com.seewhy.syaiagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.model.rpg.RpgNpc;
import com.seewhy.syaiagent.model.rpg.RpgWorld;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class RpgWorldService {

    private static final String WORLD_RESOURCE = "rpg/world.json";

    private final RpgWorld world;

    public RpgWorldService(ObjectMapper objectMapper) {
        this.world = readWorld(objectMapper);
    }

    public RpgWorld getWorld() {
        return world;
    }

    public List<RpgNpc> getNpcs() {
        return world.npcs();
    }

    public Optional<RpgNpc> findNpcById(String id) {
        return world.npcs().stream()
                .filter(npc -> npc.id().equals(id))
                .findFirst();
    }

    private RpgWorld readWorld(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(WORLD_RESOURCE);
        try {
            return objectMapper.readValue(resource.getInputStream(), RpgWorld.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load RPG world resource: " + WORLD_RESOURCE, ex);
        }
    }
}
