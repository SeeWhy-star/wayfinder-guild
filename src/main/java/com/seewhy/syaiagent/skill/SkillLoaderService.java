package com.seewhy.syaiagent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class SkillLoaderService {

    private static final String SKILL_PATTERN = "classpath*:skills/*/SKILL.md";

    private final ResourcePatternResolver resourcePatternResolver;

    public SkillLoaderService(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    public List<Skill> loadAllSkills() {
        try {
            Resource[] resources = resourcePatternResolver.getResources(SKILL_PATTERN);
            List<Skill> skills = new ArrayList<>();
            for (Resource resource : resources) {
                skills.add(parseSkill(resource));
            }
            return skills.stream()
                    .sorted(Comparator.comparingInt(Skill::priority).reversed().thenComparing(Skill::id))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load travel skills", ex);
        }
    }

    public List<Skill> selectSkills(String userInput) {
        String normalizedInput = normalize(userInput);
        return loadAllSkills().stream()
                .filter(skill -> matches(skill, normalizedInput))
                .limit(5)
                .toList();
    }

    private Skill parseSkill(Resource resource) throws IOException {
        String markdown = resource.getContentAsString(StandardCharsets.UTF_8);
        if (!markdown.startsWith("---")) {
            throw new IllegalStateException("Skill file missing front matter: " + resource.getDescription());
        }

        int metadataEnd = markdown.indexOf("\n---", 3);
        if (metadataEnd < 0) {
            throw new IllegalStateException("Skill file front matter is not closed: " + resource.getDescription());
        }

        String metadataBlock = markdown.substring(3, metadataEnd).strip();
        String content = markdown.substring(metadataEnd + 4).strip();
        Map<String, String> metadata = parseMetadata(metadataBlock);

        String id = required(metadata, "id", resource);
        return new Skill(
                id,
                metadata.getOrDefault("name", id),
                metadata.getOrDefault("description", ""),
                splitCsv(metadata.get("tags")),
                splitCsv(metadata.get("triggers")),
                parsePriority(metadata.get("priority")),
                content
        );
    }

    private Map<String, String> parseMetadata(String metadataBlock) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String line : metadataBlock.split("\\R")) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            metadata.put(key, value);
        }
        return metadata;
    }

    private String required(Map<String, String> metadata, String key, Resource resource) {
        String value = metadata.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Skill file missing required metadata '" + key + "': " + resource.getDescription());
        }
        return value;
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private int parsePriority(String value) {
        if (value == null || value.isBlank()) {
            return 50;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("Invalid skill priority '{}', using default 50", value);
            return 50;
        }
    }

    private boolean matches(Skill skill, String normalizedInput) {
        return containsAny(normalizedInput, skill.triggers())
                || normalizedInput.contains(normalize(skill.id()));
    }

    private boolean containsAny(String normalizedInput, List<String> candidates) {
        return candidates.stream()
                .map(this::normalize)
                .anyMatch(candidate -> !candidate.isBlank() && normalizedInput.contains(candidate));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).strip();
    }
}
