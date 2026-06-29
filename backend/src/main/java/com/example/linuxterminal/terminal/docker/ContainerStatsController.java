package com.example.linuxterminal.terminal.docker;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/containers")
public class ContainerStatsController {

    private final ContainerStatsService containerStatsService;

    public ContainerStatsController(ContainerStatsService containerStatsService) {
        this.containerStatsService = containerStatsService;
    }

    @GetMapping(path = "/{containerName}/stats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStats(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "userId", required = false) String queryUserId,
            @PathVariable String containerName
    ) throws IOException {
        return containerStatsService.streamStats(resolveUserId(queryUserId, userId), containerName);
    }

    private String resolveUserId(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "anonymous";
    }
}
