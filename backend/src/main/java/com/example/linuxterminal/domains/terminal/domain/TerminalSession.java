package com.example.linuxterminal.domains.terminal.domain;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
public class TerminalSession {

    private final String webSocketSessionId;
    private final String userId;
    private final WebSocketSession webSocketSession;
    private final String containerName;
    private final OutputStream stdin;
    private final InputStream stdout;
    private final InputStream stderr;
    private final Closeable runtimeHandle;
    private final Closeable auditStream;
    private final CountDownLatch exitLatch = new CountDownLatch(1);
    private final AtomicInteger exitCode = new AtomicInteger(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean inputClosed = new AtomicBoolean(false);
    private volatile Instant lastAccessedAt = Instant.now();

    public TerminalSession(
            String webSocketSessionId,
            String userId,
            WebSocketSession webSocketSession,
            String containerName,
            OutputStream stdin,
            InputStream stdout,
            InputStream stderr,
            Closeable runtimeHandle,
            Closeable auditStream
    ) {
        this.webSocketSessionId = webSocketSessionId;
        this.userId = userId;
        this.webSocketSession = webSocketSession;
        this.containerName = containerName;
        this.stdin = stdin;
        this.stdout = stdout;
        this.stderr = stderr;
        this.runtimeHandle = runtimeHandle;
        this.auditStream = auditStream;
    }

    public String getWebSocketSessionId() {
        return webSocketSessionId;
    }

    public String getUserId() {
        return userId;
    }

    public WebSocketSession getWebSocketSession() {
        return webSocketSession;
    }

    public String getContainerName() {
        return containerName;
    }

    public InputStream getStdout() {
        return stdout;
    }

    public InputStream getStderr() {
        return stderr;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public boolean markClosed() {
        return closed.compareAndSet(false, true);
    }

    public void markExited(int code) {
        exitCode.set(code);
        exitLatch.countDown();
    }

    public int awaitExit() throws InterruptedException {
        exitLatch.await();
        return exitCode.get();
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
            // Closing input is best effort because the Docker exec stream performs final cleanup.
        }
    }

    public void closeRuntime() {
        try {
            runtimeHandle.close();
        } catch (IOException ignored) {
            // Closing the Docker exec callback is best effort during session cleanup.
        }
    }

    public void closeAuditStream() {
        if (auditStream != null) {
            try {
                auditStream.close();
            } catch (IOException ignored) {
                // Closing audit stream is best effort.
            }
        }
    }
}
