package com.example.linuxterminal.domains.terminal.service;

import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.domains.terminal.domain.TerminalSession;
import com.example.linuxterminal.domains.terminal.repository.TerminalSessionRepository;
import com.example.linuxterminal.global.config.TerminalProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DefaultTerminalSessionService implements TerminalSessionService {

    private final TerminalSessionRepository terminalSessionRepository;
    private final TerminalRuntime terminalRuntime;
    private final TerminalProperties properties;
    private final ContainerService containerService;
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "terminal-idle-cleanup");
                thread.setDaemon(true);
                return thread;
            });

    public DefaultTerminalSessionService(
            TerminalSessionRepository terminalSessionRepository,
            TerminalRuntime terminalRuntime,
            TerminalProperties properties,
            ContainerService containerService
    ) {
        this.terminalSessionRepository = terminalSessionRepository;
        this.terminalRuntime = terminalRuntime;
        this.properties = properties;
        this.containerService = containerService;
    }

    @PostConstruct
    void startCleanupScheduler() {
        long cleanupIntervalMillis = properties.getCleanupInterval().toMillis();
        cleanupExecutor.scheduleWithFixedDelay(
                this::cleanupIdleSessions,
                cleanupIntervalMillis,
                cleanupIntervalMillis,
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdownCleanupScheduler() {
        cleanupExecutor.shutdownNow();
        terminalSessionRepository.forEach((sessionId, terminalSession) -> close(
                sessionId,
                "application shutdown",
                CloseStatus.SERVICE_RESTARTED.withReason("Server is shutting down.")));
    }

    @Override
    public void create(WebSocketSession webSocketSession) throws IOException {
        TerminalSession terminalSession;
        try {
            terminalSession = terminalRuntime.start(webSocketSession);
        } catch (IOException exception) {
            log.warn("Failed to start terminal session. sessionId={}", webSocketSession.getId(), exception);
            closeWebSocket(webSocketSession,
                    CloseStatus.SERVER_ERROR.withReason("Terminal server could not start Docker."));
            throw exception;
        }
        terminalSessionRepository.save(terminalSession);
        terminalRuntime.streamToClient(terminalSession);
        terminalRuntime.waitForExit(terminalSession,
                exitCode -> close(
                        webSocketSession.getId(),
                        "container process exited: " + exitCode,
                        closeStatusForExitCode(exitCode)));
        log.info("Terminal session started. sessionId={} containerName={}",
                webSocketSession.getId(), terminalSession.getContainerName());
    }

    @Override
    public void write(String webSocketSessionId, String payload) throws IOException {
        TerminalSession terminalSession = terminalSessionRepository.findById(webSocketSessionId).orElse(null);
        if (terminalSession == null || terminalSession.isClosed()) {
            return;
        }
        containerService.markActivity(terminalSession.getContainerName());
        terminalSession.write(payload);
    }

    @Override
    public void close(String webSocketSessionId, String reason) {
        close(webSocketSessionId, reason, false);
    }

    @Override
    public void close(String webSocketSessionId, String reason, boolean closeWebSocket) {
        close(webSocketSessionId, reason,
                closeWebSocket ? CloseStatus.SERVER_ERROR.withReason(reason) : null);
    }

    @Override
    public void close(String webSocketSessionId, String reason, CloseStatus closeStatus) {
        TerminalSession terminalSession = terminalSessionRepository.remove(webSocketSessionId).orElse(null);
        if (terminalSession == null) {
            return;
        }
        log.info("Terminal session closing. sessionId={} reason={}", webSocketSessionId, reason);
        terminalRuntime.remove(terminalSession);
        if (closeStatus != null) {
            closeWebSocket(terminalSession.getWebSocketSession(), closeStatus);
        }
    }

    @Override
    public void cleanupIdleSessions() {
        Instant now = Instant.now();
        terminalSessionRepository.forEach((sessionId, terminalSession) -> {
            if (terminalSession.isIdle(properties.getIdleTimeout(), now)) {
                close(sessionId, "idle timeout",
                        CloseStatus.SESSION_NOT_RELIABLE.withReason("Idle timeout. Terminal closed."));
            }
        });
    }

    private void closeWebSocket(WebSocketSession webSocketSession, CloseStatus closeStatus) {
        if (!webSocketSession.isOpen()) {
            return;
        }
        try {
            webSocketSession.close(closeStatus);
        } catch (IOException exception) {
            log.warn("Failed to close terminal websocket. sessionId={}", webSocketSession.getId(), exception);
        }
    }

    private CloseStatus closeStatusForExitCode(int exitCode) {
        if (exitCode == 0) {
            return CloseStatus.NORMAL.withReason("container process exited");
        }
        if (exitCode == 125) {
            return CloseStatus.SERVER_ERROR.withReason("Docker could not start the terminal container.");
        }
        return CloseStatus.SERVER_ERROR.withReason("container process exited: " + exitCode);
    }
}
