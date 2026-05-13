package com.seewhy.syaiagent.controller;

import com.seewhy.syaiagent.model.rpg.AgentModule;
import com.seewhy.syaiagent.eval.TravelEvalCase;
import com.seewhy.syaiagent.model.rpg.RpgEvalRule;
import com.seewhy.syaiagent.model.rpg.RpgEvalCurrentPlanScoreRequest;
import com.seewhy.syaiagent.model.rpg.RpgEvalRunRequest;
import com.seewhy.syaiagent.model.rpg.RpgEvalRunResponse;
import com.seewhy.syaiagent.model.rpg.RpgEvalSampleResult;
import com.seewhy.syaiagent.model.rpg.RpgEvalScoreRequest;
import com.seewhy.syaiagent.model.rpg.RpgNpc;
import com.seewhy.syaiagent.model.rpg.RpgProfile;
import com.seewhy.syaiagent.model.rpg.RpgProject;
import com.seewhy.syaiagent.model.rpg.RpgSkill;
import com.seewhy.syaiagent.model.rpg.RpgSkillMatch;
import com.seewhy.syaiagent.model.rpg.RpgSkillMatchRequest;
import com.seewhy.syaiagent.model.rpg.RpgWorld;
import com.seewhy.syaiagent.service.AgentModuleService;
import com.seewhy.syaiagent.service.RpgEvalService;
import com.seewhy.syaiagent.service.RpgProfileService;
import com.seewhy.syaiagent.service.RpgProjectService;
import com.seewhy.syaiagent.service.RpgSkillService;
import com.seewhy.syaiagent.service.RpgWorldService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/rpg")
public class RpgController {

    private final RpgWorldService rpgWorldService;
    private final RpgProjectService rpgProjectService;
    private final RpgSkillService rpgSkillService;
    private final AgentModuleService agentModuleService;
    private final RpgProfileService rpgProfileService;
    private final RpgEvalService rpgEvalService;

    public RpgController(RpgWorldService rpgWorldService,
                         RpgProjectService rpgProjectService,
                         RpgSkillService rpgSkillService,
                         AgentModuleService agentModuleService,
                         RpgProfileService rpgProfileService,
                         RpgEvalService rpgEvalService) {
        this.rpgWorldService = rpgWorldService;
        this.rpgProjectService = rpgProjectService;
        this.rpgSkillService = rpgSkillService;
        this.agentModuleService = agentModuleService;
        this.rpgProfileService = rpgProfileService;
        this.rpgEvalService = rpgEvalService;
    }

    @GetMapping("/world")
    public RpgWorld getWorld() {
        return rpgWorldService.getWorld();
    }

    @GetMapping("/npcs")
    public List<RpgNpc> getNpcs() {
        return rpgWorldService.getNpcs();
    }

    @GetMapping("/npcs/{id}")
    public ResponseEntity<RpgNpc> getNpc(@PathVariable String id) {
        return rpgWorldService.findNpcById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/projects")
    public List<RpgProject> getProjects() {
        return rpgProjectService.getProjects();
    }

    @GetMapping("/skills")
    public List<RpgSkill> getSkills() {
        return rpgSkillService.getSkills();
    }

    @PostMapping("/skills/match")
    public List<RpgSkillMatch> matchSkills(@RequestBody RpgSkillMatchRequest request) {
        return rpgSkillService.matchSkills(request == null ? null : request.message());
    }

    @GetMapping("/modules")
    public List<AgentModule> getModules() {
        return agentModuleService.getModules();
    }

    @GetMapping("/profile")
    public RpgProfile getProfile() {
        return rpgProfileService.getProfile();
    }

    @GetMapping("/evals/cases")
    public List<TravelEvalCase> getEvalCases() {
        return rpgEvalService.getCases();
    }

    @GetMapping("/evals/rules")
    public List<RpgEvalRule> getEvalRules() {
        return rpgEvalService.getRules();
    }

    @GetMapping("/evals/sample-result")
    public List<RpgEvalSampleResult> getEvalSampleResult() {
        return rpgEvalService.getSampleResults();
    }

    @PostMapping("/evals/run/{caseId}")
    public RpgEvalRunResponse runEval(@PathVariable String caseId,
                                      @RequestBody(required = false) RpgEvalRunRequest request) {
        try {
            return rpgEvalService.runEval(caseId, request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @PostMapping("/evals/score")
    public RpgEvalRunResponse scoreEval(@RequestBody RpgEvalScoreRequest request) {
        try {
            return rpgEvalService.scorePlan(request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @PostMapping("/evals/score-current-plan")
    public RpgEvalRunResponse scoreCurrentPlan(@RequestBody RpgEvalCurrentPlanScoreRequest request) {
        try {
            return rpgEvalService.scoreCurrentPlan(request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
