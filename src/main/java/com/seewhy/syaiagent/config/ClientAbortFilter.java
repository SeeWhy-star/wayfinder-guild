package com.seewhy.syaiagent.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@WebFilter(urlPatterns = "/*")
@Slf4j
public class ClientAbortFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("中止") || msg.contains("An established connection was aborted")
                    || msg.contains("Broken pipe") || msg.contains("Connection reset by peer"))) {
                log.debug("Client aborted connection (suppressed): {}", msg);
                // swallow and return quietly
                return;
            }
            throw e;
        }
    }
}
