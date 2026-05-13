package com.seewhy.syaiagent.security;

import com.seewhy.syaiagent.service.WayfinderDemoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class OwnerAccessInterceptor implements HandlerInterceptor {

    private final OwnerAccessService ownerAccessService;
    private final WayfinderDemoService wayfinderDemoService;

    public OwnerAccessInterceptor(OwnerAccessService ownerAccessService,
                                  WayfinderDemoService wayfinderDemoService) {
        this.ownerAccessService = ownerAccessService;
        this.wayfinderDemoService = wayfinderDemoService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!requiresOwnerAccess(request)) {
            return true;
        }
        if (ownerAccessService.hasOwnerAccess(request)) {
            return true;
        }
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Owner token required for live model, tool, API, MCP, search, or artifact access."
        );
    }

    private boolean requiresOwnerAccess(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }
        }
        String method = request.getMethod();

        if (path.startsWith("/travel/manus/artifacts/")) {
            return true;
        }
        if (is(method, HttpMethod.POST) && path.equals("/travel/manus/demo-tool")) {
            return true;
        }
        if (is(method, HttpMethod.GET) && path.equals("/travel/manus/chat")) {
            return false;
        }

        if (is(method, HttpMethod.POST) && path.equals("/travel/chat")) {
            return true;
        }
        if (is(method, HttpMethod.GET) && path.equals("/travel/chat/stream")) {
            return false;
        }
        if (is(method, HttpMethod.GET) && (
                path.equals("/travel/chat/sync")
                        || path.equals("/travel/chat/sse")
                        || path.equals("/travel/chat/server_sent_event")
                        || path.equals("/travel/chat/sse_emitter"))) {
            return true;
        }
        if (is(method, HttpMethod.POST) && path.equals("/travel/report")) {
            return true;
        }
        if (is(method, HttpMethod.POST) && path.equals("/travel/plan")) {
            return false;
        }
        if (is(method, HttpMethod.POST) && path.equals("/travel/rag")) {
            return true;
        }
        if (is(method, HttpMethod.POST) && path.equals("/travel/rag/explain")) {
            return false;
        }
        if (is(method, HttpMethod.POST) && path.equals("/travel/quick")) {
            return true;
        }
        if (is(method, HttpMethod.GET) && path.equals("/travel/system/info")) {
            return true;
        }
        if (is(method, HttpMethod.POST) && path.startsWith("/rpg/evals/run/")) {
            return !wayfinderDemoService.isEnabled();
        }
        return false;
    }

    private boolean is(String method, HttpMethod expected) {
        return expected.matches(method);
    }
}
