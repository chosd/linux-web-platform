package com.example.linuxterminal.terminal.docker;

import com.example.linuxterminal.terminal.core.TerminalProcessExitHandler;
import com.example.linuxterminal.terminal.core.TerminalRuntime;
import com.example.linuxterminal.terminal.core.TerminalSession;
import com.example.linuxterminal.terminal.io.TerminalProcessWatcher;
import com.example.linuxterminal.terminal.io.TerminalStreamForwarder;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Service
public class DockerTerminalService implements TerminalRuntime {

    private final DockerCommandFactory dockerCommandFactory;
    private final ContainerManagementService containerManagementService;
    private final TerminalStreamForwarder terminalStreamForwarder;
    private final TerminalProcessWatcher terminalProcessWatcher;

    public DockerTerminalService(
            DockerCommandFactory dockerCommandFactory,
            ContainerManagementService containerManagementService,
            TerminalStreamForwarder terminalStreamForwarder,
            TerminalProcessWatcher terminalProcessWatcher
    ) {
        this.dockerCommandFactory = dockerCommandFactory;
        this.containerManagementService = containerManagementService;
        this.terminalStreamForwarder = terminalStreamForwarder;
        this.terminalProcessWatcher = terminalProcessWatcher;
    }

    @Override
    public TerminalSession start(WebSocketSession webSocketSession) throws IOException {
        String userId = resolveUserId(webSocketSession);
        String requestedContainerName = resolveQueryParameter(webSocketSession, "containerName");
        ContainerManagementService.ContainerInfo containerInfo =
                requestedContainerName == null
                        ? containerManagementService.startOrGetContainer(userId)
                        : containerManagementService.ensureContainerRunning(userId, requestedContainerName);
        containerManagementService.markConnected(containerInfo.containerName());
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
        containerManagementService.markDisconnected(terminalSession.getContainerName());
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
