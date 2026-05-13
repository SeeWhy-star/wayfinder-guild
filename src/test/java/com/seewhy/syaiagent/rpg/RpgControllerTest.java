package com.seewhy.syaiagent.rpg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seewhy.syaiagent.controller.RpgController;
import com.seewhy.syaiagent.eval.TravelEvalHarness;
import com.seewhy.syaiagent.service.RpgEvalService;
import com.seewhy.syaiagent.service.WayfinderDemoService;
import com.seewhy.syaiagent.service.AgentModuleService;
import com.seewhy.syaiagent.service.RpgProfileService;
import com.seewhy.syaiagent.service.RpgProjectService;
import com.seewhy.syaiagent.service.RpgSkillService;
import com.seewhy.syaiagent.service.RpgWorldService;
import com.seewhy.syaiagent.skill.SkillLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RpgControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        SkillLoaderService skillLoaderService = new SkillLoaderService(new PathMatchingResourcePatternResolver());
        RpgController controller = new RpgController(
                new RpgWorldService(objectMapper),
                new RpgProjectService(objectMapper),
                new RpgSkillService(objectMapper, skillLoaderService),
                new AgentModuleService(objectMapper),
                new RpgProfileService(objectMapper),
                new RpgEvalService(new TravelEvalHarness(objectMapper), new WayfinderDemoService(false))
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getWorldReturnsWayfinderGuildMetadata() throws Exception {
        mockMvc.perform(get("/rpg/world"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("wayfinder-guild"))
                .andExpect(jsonPath("$.name").value("Wayfinder Guild"))
                .andExpect(jsonPath("$.areas", hasSize(greaterThanOrEqualTo(9))))
                .andExpect(jsonPath("$.quickRoutes", hasSize(5)));
    }

    @Test
    void getNpcReturnsConfiguredNpcOrNotFound() throws Exception {
        mockMvc.perform(get("/rpg/npcs/travel-guide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("travel-guide"))
                .andExpect(jsonPath("$.areaId").value("travel-cabin"));

        mockMvc.perform(get("/rpg/npcs/missing-npc"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPortfolioCollectionsReturnConfiguredData() throws Exception {
        mockMvc.perform(get("/rpg/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));

        mockMvc.perform(get("/rpg/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))));

        mockMvc.perform(get("/rpg/modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/rpg/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("SeeWhy"))
                .andExpect(jsonPath("$.title").value("Java & AI Application Engineer"));
    }

    @Test
    void matchSkillsReturnsSelectedTravelSkills() throws Exception {
        mockMvc.perform(post("/rpg/skills/match")
                        .contentType("application/json")
                        .content("{\"message\":\"我和父母 3 个人，6 月去日本 7 天，预算 2 万，想轻松一点\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].matchedReason").exists());
    }

    @Test
    void getEvalMetadataReturnsCasesAndRules() throws Exception {
        mockMvc.perform(get("/rpg/evals/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/rpg/evals/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].id").value("case-alignment"));

        mockMvc.perform(get("/rpg/evals/sample-result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].rule").value("case-alignment"));
    }
}
