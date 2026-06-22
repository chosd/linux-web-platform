package com.example.linuxterminal.terminal.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.socket.WebSocketSession;

public class TerminalSession {

    private final String webSocketSessionId;
    private final WebSocketSession webSocketSession;
    private final String containerName;
    private final Process process;
    private final OutputStream stdin;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean inputClosed = new AtomicBoolean(false);
    private volatile Instant lastAccessedAt = Instant.now();

    public TerminalSession(
            String webSocketSessionId,
            WebSocketSession webSocketSession,
            String containerName,
            Process process
    ) {
        this.webSocketSessionId = webSocketSessionId;
        this.webSocketSession = webSocketSession;
        this.containerName = containerName;
        this.process = process;
        this.stdin = process.getOutputStream();
    }

    public String getWebSocketSessionId() {
        return webSocketSessionId;
    }

    public WebSocketSession getWebSocketSession() {
        return webSocketSession;
    }

    public String getContainerName() {
        return containerName;
    }

    public Process getProcess() {
        return process;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public boolean markClosed() {
        return closed.compareAndSet(false, true);
    }

    public void touch() {
        lastAccessedAt = Instant.now();
    }

    public boolean isIdle(Duration idleTimeout, Instant now) {
        return lastAccessedAt.plus(idleTimeout).isBefore(now);
    }

    public void write(String payload) throws IOException {
        touch();
        if (inputClosed.get()) {
            return;
        }
        stdin.write(payload.getBytes(StandardCharsets.UTF_8));
        stdin.flush();
    }

    public void closeInput() {
        if (!inputClosed.compareAndSet(false, true)) {
            return;
        }
        try {
            stdin.close();
        } catch (IOException ignored) {
            // Closing input is best effort because docker rm -f performs the final cleanup.
        }
    }
}

