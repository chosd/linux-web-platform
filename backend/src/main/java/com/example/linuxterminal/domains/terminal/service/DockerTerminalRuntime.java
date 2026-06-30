package com.example.linuxterminal.domains.terminal.service;

import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.domains.terminal.service.TerminalProcessWatcher;
import com.example.linuxterminal.domains.terminal.service.TerminalStreamForwarder;
import com.example.linuxterminal.domains.terminal.service.TerminalProcessExitHandler;
import com.example.linuxterminal.domains.terminal.service.TerminalRuntime;
import com.example.linuxterminal.domains.terminal.domain.TerminalSession;
import com.example.linuxterminal.global.docker.DockerCommandFactory;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
public class DockerTerminalRuntime implements TerminalRuntime {

    private final DockerCommandFactory dockerCommandFactory;
    private final ContainerService containerService;
    private final TerminalStreamForwarder terminalStreamForwarder;
    private final TerminalProcessWatcher terminalProcessWatcher;

    public DockerTerminalRuntime(
            DockerCommandFactory dockerCommandFactory,
            ContainerService containerService,
            TerminalStreamForwarder terminalStreamForwarder,
            TerminalProcessWatcher terminalProcessWatcher
    ) {
        this.dockerCommandFactory = dockerCommandFactory;
        this.containerService = containerService;
        this.terminalStreamForwarder = terminalStreamForwarder;
        this.terminalProcessWatcher = terminalProcessWatcher;
    }

    @Override
    public TerminalSession start(WebSocketSession webSocketSession) throws IOException {
        String userId = resolveUserId(webSocketSession);
        String requestedContainerName = resolveQueryParameter(webSocketSession, "containerName");
        ContainerService.ContainerInfo containerInfo =
                requestedContainerName == null
                        ? containerService.startOrGetContainer(userId)
                        : containerService.ensureContainerRunning(userId, requestedContainerName);
        containerService.markConnected(containerInfo.containerName());
        List<String> command = dockerCommandFactory.execCommand(containerInfo.containerName());
        log.info("[COMMAND][{}]", command);
        Process process = new ProcessBuilder(command).start();
        return new TerminalSession(
                webSocketSession.getId(),
                containerInfo.userId(),
                webSocketSession,
                containerInfo.containerName(),
                process);
    }

    @Override
    public void remove(TerminalSession terminalSession) {
        if (!terminalSession.markClosed()) {
            return;
        }

        terminalSession.closeInput();
        Process process = terminalSession.getProcess();
        if (process.isAlive()) {
            process.destroy();
        }
        containerService.markDisconnected(terminalSession.getContainerName());
    }

    @Override
    public void streamToClient(TerminalSession terminalSession) {
        terminalStreamForwarder.forward(terminalSession, terminalSession.getProcess().getInputStream(), "stdout");
        terminalStreamForwarder.forward(terminalSession, terminalSession.getProcess().getErrorStream(), "stderr");
    }

    @Override
    public void waitForExit(TerminalSession terminalSession, TerminalProcessExitHandler onExit) {
        terminalProcessWatcher.waitForExit(terminalSession, onExit);
    }

    private String resolveUserId(WebSocketSession webSocketSession) {
        String userId = resolveQueryParameter(webSocketSession, "userId");
        return userId == null ? "anonymous" : userId;
    }

    private String resolveQueryParameter(WebSocketSession webSocketSession, String name) {
        String rawQuery = webSocketSession.getUri() == null ? null : webSocketSession.getUri().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        for (String parameter : rawQuery.split("&")) {
            String[] parts = parameter.split("=", 2);
            if (parts.length == 2 && Objects.equals(parts[0], name)) {
                String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}
