package com.example.linuxterminal.domains.dashboard.controller;

import com.example.linuxterminal.domains.dashboard.dto.HostResourceStatsSample;
import com.example.linuxterminal.domains.dashboard.service.HostResourceStatsService;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final HostResourceStatsService hostResourceStatsService;

    public DashboardController(HostResourceStatsService hostResourceStatsService) {
        this.hostResourceStatsService = hostResourceStatsService;
    }

    @GetMapping("/resources")
    public HostResourceStatsSample getResources() throws IOException {
        return hostResourceStatsService.readStats();
    }
}
