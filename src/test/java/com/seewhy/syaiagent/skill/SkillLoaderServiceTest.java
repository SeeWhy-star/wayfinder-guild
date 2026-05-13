package com.seewhy.syaiagent.skill;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLoaderServiceTest {

    private final SkillLoaderService skillLoaderService =
            new SkillLoaderService(new PathMatchingResourcePatternResolver());

    @Test
    void loadAllSkillsReadsConfiguredMarkdownSkills() {
        List<Skill> skills = skillLoaderService.loadAllSkills();

        assertTrue(skills.size() >= 5);
        assertTrue(skills.stream().anyMatch(skill -> skill.id().equals("family-trip-planning")));
        assertTrue(skills.stream().anyMatch(skill -> skill.id().equals("japan-travel")));
        assertTrue(skills.stream().anyMatch(skill -> skill.id().equals("china-domestic-travel")));
    }

    @Test
    void selectSkillsMatchesExampleTravelRequest() {
        List<String> selectedSkillIds = skillLoaderService
                .selectSkills("我和父母 3 个人，6 月去日本 7 天，预算 2 万，想轻松一点")
                .stream()
                .map(Skill::id)
                .toList();

        assertEquals(List.of(
                "family-trip-planning",
                "japan-travel",
                "relaxed-travel",
                "budget-travel"
        ), selectedSkillIds);
    }

    @Test
    void selectSkillsForShanghaiDomesticTripDoesNotLoadJapanTravel() {
        List<String> selectedSkillIds = skillLoaderService
                .selectSkills("北京出发，去上海，3天，3人，预算20000 CNY。")
                .stream()
                .map(Skill::id)
                .toList();

        assertTrue(selectedSkillIds.contains("china-domestic-travel"));
        assertTrue(selectedSkillIds.contains("budget-travel"));
        assertFalse(selectedSkillIds.contains("japan-travel"));
        assertFalse(selectedSkillIds.contains("family-trip-planning"));
    }

    @Test
    void selectSkillsIgnoresGenericTagsFromOutputInstructions() {
        List<String> selectedSkillIds = skillLoaderService
                .selectSkills("北京出发，去上海，3天，3人，预算20000 CNY。 Return a structured TravelPlan with transportation and itinerary details.")
                .stream()
                .map(Skill::id)
                .toList();

        assertTrue(selectedSkillIds.contains("china-domestic-travel"));
        assertFalse(selectedSkillIds.contains("japan-travel"));
    }

    @Test
    void selectSkillsForJapanTripStillLoadsJapanTravel() {
        List<String> selectedSkillIds = skillLoaderService
                .selectSkills("上海出发去京都5天，预算15000 CNY。")
                .stream()
                .map(Skill::id)
                .toList();

        assertTrue(selectedSkillIds.contains("japan-travel"));
        assertFalse(selectedSkillIds.contains("china-domestic-travel"));
    }
}
