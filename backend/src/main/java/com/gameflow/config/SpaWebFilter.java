package com.gameflow.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * SPA fallback filter for the embedded React frontend.
 * <p>
 * When the Vite-built frontend is bundled into {@code static/} on the
 * classpath, this filter forwards any non-API, non-WS, non-asset request
 * to {@code /index.html} so React Router's {@code BrowserRouter} works
 * with HTML5 history.
 * <p>
 * The filter is a no-op during development (when no {@code static/index.html}
 * exists on the classpath) so it does not interfere with the Vite dev proxy.
 */
@Component
public class SpaWebFilter implements WebFilter {

    private final boolean spaEnabled;

    public SpaWebFilter() {
        this.spaEnabled = new ClassPathResource("static/index.html").exists();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!spaEnabled) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();

        // Let API endpoints, WebSocket, and static assets pass through.
        if (path.startsWith("/api/")
                || path.startsWith("/ws/")
                || path.contains(".")   // e.g. .js, .css, .png, .svg, .ico
        ) {
            return chain.filter(exchange);
        }

        // Forward everything else to index.html (SPA fallback).
        return chain.filter(
                exchange.mutate()
                        .request(exchange.getRequest().mutate().uri(URI.create("/index.html")).build())
                        .build()
        );
    }
}
