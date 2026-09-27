package dev.network.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(CorrelationFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = request.getHeader("X-Correlation-Id");
        if (id == null || !id.matches("[0-9a-f-]{36}")) id = UUID.randomUUID().toString();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlation_id", id)) {
            response.setHeader("X-Correlation-Id", id);
            chain.doFilter(request, response);
            LOG.info("request completed method={} status={}", request.getMethod(), response.getStatus());
        }
    }
}
