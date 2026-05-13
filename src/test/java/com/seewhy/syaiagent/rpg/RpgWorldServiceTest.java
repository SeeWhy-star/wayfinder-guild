package com.seewhy.syaiagent.rpg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.model.rpg.RpgWorld;
import com.seewhy.syaiagent.service.AgentModuleService;
import com.seewhy.syaiagent.service.RpgProfileService;
import com.seewhy.syaiagent.service.RpgProjectService;
import com.seewhy.syaiagent.service.RpgSkillService;
import com.seewhy.syaiagent.service.RpgWorldService;
import com.seewhy.syaiagent.skill.SkillLoaderService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpgWorldServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadWorldReadsConfiguredAreasAndNpcs() {
        RpgWorldService service = new RpgWorldService(objectMapper);
        RpgWorld world = service.getWorld();

        assertEquals("wayfinder-guild", world.id());
        assertEquals("Where AI finds its way.", world.taglineEn());
        assertTrue(world.areas().size() >= 9);
        assertTrue(world.npcs().size() >= 8);
        assertTrue(world.areas().stream().anyMatch(area -> area.id().equals("travel-cabin")));
        assertTrue(service.findNpcById("travel-guide").isPresent());
        assertTrue(service.findNpcById("missing-npc").isEmpty());
    }

    @Test
    void loadPortfolioResourcesReadsProjectsSkillsModulesAndProfile() {
        RpgProjectService projectService = new RpgProjectService(objectMapper);
        RpgSkillService skillService = new RpgSkillService(
                objectMapper,
                new SkillLoaderService(new PathMatchingResourcePatternResolver())
        );
        AgentModuleService moduleService = new AgentModuleService(objectMapper);
        RpgProfileService profileService = new RpgProfileService(objectMapper);

        assertTrue(projectService.getProjects().size() >= 3);
        assertTrue(skillService.getSkills().size() >= 5);
        assertFalse(moduleService.getModules().isEmpty());
        assertEquals("SeeWhy", profileService.getProfile().name());
        assertTrue(moduleService.getModules().stream().anyMatch(module -> module.id().equals("rpg-portfolio-backend")));
    }

    @Test
    void matchSkillsUsesTravelSkillTriggers() {
        RpgSkillService skillService = new RpgSkillService(
                objectMapper,
                new SkillLoaderService(new PathMatchingResourcePatternResolver())
        );

        assertTrue(skillService.matchSkills("我和父母 3 个人，6 月去日本 7 天，预算 2 万，想轻松一点")
                .stream()
                .anyMatch(skill -> skill.id().equals("japan-travel")));
    }

    @Test
    void promptTemplatesAreAvailableAsResources() {
        assertTrue(new ClassPathResource("prompts/rpg/npc-persona.st").exists());
        assertTrue(new ClassPathResource("prompts/rpg/project-blacksmith.st").exists());
        assertTrue(new ClassPathResource("prompts/rpg/skill-shrine.st").exists());
        assertTrue(new ClassPathResource("prompts/rpg/eval-arena.st").exists());
        assertTrue(new ClassPathResource("prompts/rpg/trace-summary.st").exists());
    }
}
