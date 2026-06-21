package com.example.linuxterminal.terminal;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
public class TerminalSessionManager {

    private static final Logger log = LoggerFactory.getLogger(TerminalSessionManager.class);

    private final Map<String, TerminalSession> sessions = new ConcurrentHashMap<>();
    private final DockerTerminalService dockerTerminalService;
    private final TerminalProperties properties;
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "terminal-idle-cleanup");
                thread.setDaemon(true);
                return thread;
            });

    public TerminalSessionManager(DockerTerminalService dockerTerminalService, TerminalProperties properties) {
        this.dockerTerminalService = dockerTerminalService;
        this.properties = properties;
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
        sessions.keySet().forEach(sessionId -> close(
                sessionId,
                "application shutdown",
                CloseStatus.SERVICE_RESTARTED.withReason("Server is shutting down.")));
    }

    public void create(WebSocketSession webSocketSession) throws IOException {
        TerminalSession terminalSession;
        try {
            terminalSession = dockerTerminalService.start(webSocketSession);
        } catch (IOException exception) {
            log.warn("Failed to start terminal session. sessionId={}", webSocketSession.getId(), exception);
            closeWebSocket(webSocketSession,
                    CloseStatus.SERVER_ERROR.withReason("Terminal server could not start Docker."));
            throw exception;
        }
        sessions.put(webSocketSession.getId(), terminalSession);
        dockerTerminalService.streamToWebSocket(terminalSession, terminalSession.getProcess().getInputStream(), "stdout");
        dockerTerminalService.streamToWebSocket(terminalSession, terminalSession.getProcess().getErrorStream(), "stderr");
        dockerTerminalService.waitForExit(terminalSession,
                exitCode -> close(
                        webSocketSession.getId(),
                        "container process exited: " + exitCode,
                        closeStatusForExitCode(exitCode)));
        log.info("Terminal session started. sessionId={} containerName={}",
                webSocketSession.getId(), terminalSession.getContainerName());
    }

    public void write(String webSocketSessionId, String payload) throws IOException {
        TerminalSession terminalSession = sessions.get(webSocketSessionId);
        if (terminalSession == null || terminalSession.isClosed()) {
            return;
        }
        terminalSession.write(payload);
    }

    public void close(String webSocketSessionId, String reason) {
        close(webSocketSessionId, reason, false);
    }

    public void close(String webSocketSessionId, String reason, boolean closeWebSocket) {
        close(webSocketSessionId, reason,
                closeWebSocket ? CloseStatus.SERVER_ERROR.withReason(reason) : null);
    }

    public void close(String webSocketSessionId, String reason, CloseStatus closeStatus) {
        TerminalSession terminalSession = sessions.remove(webSocketSessionId);
        if (terminalSession == null) {
            return;
        }
        log.info("Terminal session closing. sessionId={} reason={}", webSocketSessionId, reason);
        dockerTerminalService.remove(terminalSession);
        if (closeStatus != null) {
            closeWebSocket(terminalSession.getWebSocketSession(), closeStatus);
        }
    }

    public void cleanupIdleSessions() {
        Instant now = Instant.now();
        sessions.forEach((sessionId, terminalSession) -> {
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
