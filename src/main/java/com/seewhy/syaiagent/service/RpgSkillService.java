package com.seewhy.syaiagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.model.rpg.RpgSkillMatch;
import com.seewhy.syaiagent.model.rpg.RpgSkill;
import com.seewhy.syaiagent.skill.Skill;
import com.seewhy.syaiagent.skill.SkillLoaderService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RpgSkillService {

    private static final String SKILLS_RESOURCE = "rpg/skills.json";
    private static final Pattern MONEY_PATTERN = Pattern.compile("\\b\\d{3,7}\\s*(?:CNY|RMB|USD|JPY)?\\b", Pattern.CASE_INSENSITIVE);

    private final List<RpgSkill> skills;
    private final SkillLoaderService skillLoaderService;

    public RpgSkillService(ObjectMapper objectMapper, SkillLoaderService skillLoaderService) {
        this.skills = readSkills(objectMapper);
        this.skillLoaderService = skillLoaderService;
    }

    public List<RpgSkill> getSkills() {
        return skills;
    }

    public List<RpgSkillMatch> matchSkills(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        Map<String, RpgSkill> rpgSkillByCategory = new LinkedHashMap<>();
        for (RpgSkill skill : skills) {
            rpgSkillByCategory.put(normalize(skill.category()), skill);
        }
        return skillLoaderService.selectSkills(message).stream()
                .map(skill -> toMatch(skill, rpgSkillByCategory, message))
                .toList();
    }

    private RpgSkillMatch toMatch(Skill skill, Map<String, RpgSkill> rpgSkillByCategory, String message) {
        RpgSkill mapped = mapToRpgSkill(skill, rpgSkillByCategory);
        String category = mapped == null ? inferCategory(skill) : mapped.category();
        String level = mapped == null ? "intermediate" : mapped.level();
        String rpgName = mapped == null ? toTitle(skill.id()) + " Star" : mapped.name();
        String description = mapped == null || mapped.description() == null || mapped.description().isBlank()
                ? skill.description()
                : mapped.description();
        List<String> matchedTerms = matchedTerms(skill, message);
        return new RpgSkillMatch(
                skill.id(),
                skill.name(),
                rpgName,
                level,
                category,
                description,
                matchedTerms,
                skill.triggers(),
                matchedTerms.isEmpty()
                        ? "Matched by travel skill metadata."
                        : "Matched terms: " + String.join(", ", matchedTerms)
        );
    }

    private List<String> matchedTerms(Skill skill, String message) {
        String normalizedInput = normalize(message);
        List<String> terms = new ArrayList<>();
        for (String trigger : skill.triggers()) {
            String normalizedTrigger = normalize(trigger);
            if (!normalizedTrigger.isBlank() && normalizedInput.contains(normalizedTrigger)) {
                terms.add(trigger);
            }
        }
        if ("budget-travel".equals(skill.id())) {
            Matcher matcher = MONEY_PATTERN.matcher(message == null ? "" : message);
            while (matcher.find()) {
                String amount = matcher.group().trim();
                if (!amount.isBlank() && terms.stream().noneMatch(item -> normalize(item).equals(normalize(amount)))) {
                    terms.add(amount);
                }
            }
        }
        return terms.stream().distinct().toList();
    }

    private RpgSkill mapToRpgSkill(Skill skill, Map<String, RpgSkill> rpgSkillByCategory) {
        String category = normalize(inferCategory(skill));
        return rpgSkillByCategory.get(category);
    }

    private String inferCategory(Skill skill) {
        String text = normalize(skill.id() + " " + String.join(" ", skill.tags()));
        if (text.contains("budget")) {
            return "Quality";
        }
        if (text.contains("family") || text.contains("relaxed") || text.contains("food") || text.contains("japan")) {
            return "Agent";
        }
        return "Travel";
    }

    private String toTitle(String value) {
        String normalized = value == null ? "Skill" : value.replace('-', ' ');
        StringBuilder builder = new StringBuilder();
        for (String part : normalized.split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(' ');
        }
        return builder.toString().strip();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).strip();
    }

    private List<RpgSkill> readSkills(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(SKILLS_RESOURCE);
        try {
            return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load RPG skills resource: " + SKILLS_RESOURCE, ex);
        }
    }
}
