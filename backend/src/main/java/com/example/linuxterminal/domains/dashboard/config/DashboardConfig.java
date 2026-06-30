package com.example.linuxterminal.domains.dashboard.config;

import com.example.linuxterminal.domains.dashboard.service.HostResourceStatsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DashboardConfig {

    @Bean
    HostResourceStatsService hostResourceStatsService() {
        return new HostResourceStatsService();
    }
}
