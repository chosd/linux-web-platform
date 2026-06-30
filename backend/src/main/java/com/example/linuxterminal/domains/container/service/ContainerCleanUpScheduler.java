package com.example.linuxterminal.domains.container.service;

import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.global.config.TerminalProperties;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class ContainerCleanUpScheduler {

    private final ContainerService containerService;
    private final TerminalProperties terminalProperties;

    public ContainerCleanUpScheduler(
            ContainerService containerService,
            TerminalProperties terminalProperties
    ) {
        this.containerService = containerService;
        this.terminalProperties = terminalProperties;
    }

    @Scheduled(fixedDelayString = "${terminal.cleanup-interval:60000}")
    public void stopIdleContainers() {
        List<ContainerService.ContainerInfo> stoppedContainers =
                containerService.stopIdleContainers(terminalProperties.getIdleTimeout());
        if (!stoppedContainers.isEmpty()) {
            log.info("Idle containers stopped. count={} containers={}",
                    stoppedContainers.size(), stoppedContainers);
        }
    }
}
