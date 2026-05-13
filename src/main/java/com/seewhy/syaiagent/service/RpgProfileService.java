package com.seewhy.syaiagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.model.rpg.RpgProfile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class RpgProfileService {

    private static final String PROFILE_RESOURCE = "rpg/profile.json";

    private final RpgProfile profile;

    public RpgProfileService(ObjectMapper objectMapper) {
        this.profile = readProfile(objectMapper);
    }

    public RpgProfile getProfile() {
        return profile;
    }

    private RpgProfile readProfile(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(PROFILE_RESOURCE);
        try {
            return objectMapper.readValue(resource.getInputStream(), RpgProfile.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load RPG profile resource: " + PROFILE_RESOURCE, ex);
        }
    }
}
