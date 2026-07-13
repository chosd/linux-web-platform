package com.example.linuxterminal.global.config;

import com.example.linuxterminal.global.config.TerminalProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.servlet.mvc.Controller;

@Configuration
public class SpaWebMvcConfig implements WebMvcConfigurer {

    private static final String INDEX_FORWARD = "forward:/index.html";
    private static final Controller SPA_FORWARD_CONTROLLER =
            (request, response) -> new ModelAndView(INDEX_FORWARD);

    private final TerminalProperties terminalProperties;

    public SpaWebMvcConfig(TerminalProperties terminalProperties) {
        this.terminalProperties = terminalProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(terminalProperties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-User-Id")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Bean
    public HandlerMapping spaFallbackHandlerMapping() {
        return new AbstractHandlerMapping() {
            {
                setOrder(Ordered.LOWEST_PRECEDENCE - 10);
            }

            @Override
            protected Object getHandlerInternal(HttpServletRequest request) {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                if (isBackendRoute(path) || isStaticResource(path)) {
                    return null;
                }
                return SPA_FORWARD_CONTROLLER;
            }
        };
    }

    private static boolean isBackendRoute(String path) {
        return path.equals("/api")
                || path.startsWith("/api/")
                || path.equals("/ws")
                || path.startsWith("/ws/");
    }

    private static boolean isStaticResource(String path) {
        int lastSlashIndex = path.lastIndexOf('/');
        String fileName = path.substring(lastSlashIndex + 1);
        return fileName.contains(".");
    }
}
