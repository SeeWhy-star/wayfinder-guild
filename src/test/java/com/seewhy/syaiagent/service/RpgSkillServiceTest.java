package com.seewhy.syaiagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.model.rpg.RpgSkillMatch;
import com.seewhy.syaiagent.skill.Skill;
import com.seewhy.syaiagent.skill.SkillLoaderService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RpgSkillServiceTest {

    @Test
    void matchSkillsReturnsActualMatchedTerms() {
        SkillLoaderService loader = mock(SkillLoaderService.class);
        when(loader.selectSkills(anyString())).thenReturn(List.of(
                skill("family-trip-planning", "Family Trip Planning", List.of("parents", "family", "elderly", "children", "kids")),
                skill("japan-travel", "Japan Travel", List.of("japan", "tokyo", "osaka", "kyoto")),
                skill("relaxed-travel", "Relaxed Travel", List.of("relaxed", "slow", "easy")),
                skill("budget-travel", "Budget Travel", List.of("budget", "cost", "cheap"))
        ));
        RpgSkillService service = new RpgSkillService(new ObjectMapper(), loader);

        List<RpgSkillMatch> matches = service.matchSkills(
                "I will travel to Japan with my parents for 7 days, budget 20000 CNY, relaxed pace.");

        assertMatchedTerm(matches, "family-trip-planning", "parents");
        assertMatchedTerm(matches, "japan-travel", "japan");
        assertMatchedTerm(matches, "relaxed-travel", "relaxed");
        assertTrue(matches.stream()
                .filter(match -> match.id().equals("budget-travel"))
                .findFirst()
                .orElseThrow()
                .matchedTerms()
                .stream()
                .anyMatch(term -> term.equalsIgnoreCase("budget") || term.equalsIgnoreCase("20000 CNY")));
    }

    private void assertMatchedTerm(List<RpgSkillMatch> matches, String skillId, String term) {
        assertTrue(matches.stream()
                .filter(match -> match.id().equals(skillId))
                .findFirst()
                .orElseThrow()
                .matchedTerms()
                .stream()
                .anyMatch(item -> item.equalsIgnoreCase(term)));
    }

    private Skill skill(String id, String name, List<String> triggers) {
        return new Skill(id, name, name, List.of(id), triggers, 50, "");
    }
}
