package com.seewhy.syaiagent.controller;

import com.seewhy.syaiagent.security.OwnerAccessService;
import com.seewhy.syaiagent.service.WayfinderDemoService;
import com.seewhy.syaiagent.trace.AgentTraceEvent;
import com.seewhy.syaiagent.trace.AgentTraceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/travel/trace")
public class TravelTraceController {

    private final AgentTraceService agentTraceService;
    private final WayfinderDemoService wayfinderDemoService;
    private final OwnerAccessService ownerAccessService;

    public TravelTraceController(AgentTraceService agentTraceService,
                                 WayfinderDemoService wayfinderDemoService,
                                 OwnerAccessService ownerAccessService) {
        this.agentTraceService = agentTraceService;
        this.wayfinderDemoService = wayfinderDemoService;
        this.ownerAccessService = ownerAccessService;
    }

    @GetMapping("/{chatId}")
    public List<AgentTraceEvent> getTraceEvents(@PathVariable String chatId,
                                                @RequestParam(defaultValue = "false") boolean liveMode,
                                                HttpServletRequest request) {
        if (liveMode) {
            if (ownerAccessService.hasOwnerAccess(request)) {
                return agentTraceService.getEvents(chatId);
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owner token required for live trace access.");
        }
        return wayfinderDemoService.demoTrace(chatId);
    }

    @GetMapping(value = "/{chatId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentTraceEvent>> streamTraceEvents(@PathVariable String chatId,
                                                                    HttpServletRequest request) {
        if (!ownerAccessService.hasOwnerAccess(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owner token required for live trace streaming.");
        }
        return agentTraceService.stream(chatId)
                .map(event -> ServerSentEvent.<AgentTraceEvent>builder()
                        .event(event.step().name())
                        .id(event.traceId())
                        .data(event)
                        .build());
    }
}
