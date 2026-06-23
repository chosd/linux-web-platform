package com.example.linuxterminal.terminal.docker;

import com.example.linuxterminal.terminal.config.TerminalProperties;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ContainerCleanUpScheduler {

    private final ContainerManagementService containerManagementService;
    private final TerminalProperties terminalProperties;

    public ContainerCleanUpScheduler(
            ContainerManagementService containerManagementService,
            TerminalProperties terminalProperties
    ) {
        this.containerManagementService = containerManagementService;
        this.terminalProperties = terminalProperties;
    }

    @Scheduled(fixedDelayString = "${terminal.cleanup-interval:60000}")
    public void stopIdleContainers() {
        List<ContainerManagementService.ContainerInfo> stoppedContainers =
                containerManagementService.stopIdleContainers(terminalProperties.getIdleTimeout());
        if (!stoppedContainers.isEmpty()) {
            log.info("Idle containers stopped. count={} containers={}",
                    stoppedContainers.size(), stoppedContainers);
        }
    }
}
