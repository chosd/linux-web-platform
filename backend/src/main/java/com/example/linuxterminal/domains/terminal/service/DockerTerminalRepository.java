package com.example.linuxterminal.domains.terminal.service;

import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.domains.terminal.domain.TerminalSession;
import com.example.linuxterminal.global.config.TerminalProperties;
import com.example.linuxterminal.global.docker.DockerExecRepository;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class DockerTerminalRepository implements TerminalRuntime {

    private static final int PIPE_BUFFER_SIZE = 64 * 1024;

    private final DockerExecRepository dockerExecRepository;
    private final TerminalProperties terminalProperties;
    private final ContainerService containerService;
    private final TerminalStreamForwarderService terminalStreamForwarderService;
    private final TerminalProcessWatcherService terminalProcessWatcherService;

    public DockerTerminalRepository(
            DockerExecRepository dockerExecRepository,
            TerminalProperties terminalProperties,
            ContainerService containerService,
            TerminalStreamForwarderService terminalStreamForwarderService,
            TerminalProcessWatcherService terminalProcessWatcherService
    ) {
        this.dockerExecRepository = dockerExecRepository;
        this.terminalProperties = terminalProperties;
        this.containerService = containerService;
        this.terminalStreamForwarderService = terminalStreamForwarderService;
        this.terminalProcessWatcherService = terminalProcessWatcherService;
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

        PipedInputStream execStdin = new PipedInputStream(PIPE_BUFFER_SIZE);
        PipedOutputStream sessionStdin = new PipedOutputStream(execStdin);
        PipedInputStream stdout = new PipedInputStream(PIPE_BUFFER_SIZE);
        PipedOutputStream stdoutWriter = new PipedOutputStream(stdout);
        PipedInputStream stderr = new PipedInputStream(PIPE_BUFFER_SIZE);
        PipedOutputStream stderrWriter = new PipedOutputStream(stderr);

        String[] command = terminalProperties.getDocker().getCommand().toArray(String[]::new);
        String execId = dockerExecRepository.createExec(
                containerInfo.containerName(),
                null,
                true,
                true,
                true,
                false,
                command);
        AtomicReference<TerminalSession> sessionReference = new AtomicReference<>();
        ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                try {
                    if (frame.getStreamType() == StreamType.STDERR) {
                        stderrWriter.write(frame.getPayload());
                        stderrWriter.flush();
                        return;
                    }
                    stdoutWriter.write(frame.getPayload());
                    stdoutWriter.flush();
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to forward Docker terminal frame.", exception);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                closeOutputPipes(stdoutWriter, stderrWriter);
                TerminalSession terminalSession = sessionReference.get();
                if (terminalSession != null) {
                    terminalSession.markExited(1);
                }
                if (throwable instanceof CancellationException ||
                    (throwable.getCause() instanceof CancellationException)) {
                    log.info("Docker terminal exec stream was cancelled (normal shutdown). containerName={}",
                            containerInfo.containerName());
                } else {
                    log.warn("Docker terminal exec stream failed. containerName={}",
                            containerInfo.containerName(), throwable);
                }
            }

            @Override
            public void onComplete() {
                closeOutputPipes(stdoutWriter, stderrWriter);
                TerminalSession terminalSession = sessionReference.get();
                if (terminalSession != null) {
                    terminalSession.markExited(dockerExecRepository.inspectExecExitCode(execId));
                }
            }
        };
        InputStream auditStream = dockerExecRepository.openExecStdoutStreamAsUser(
                containerInfo.containerName(),
                "root",
                "tail", "-f", "/var/log/command_audit.log"
        );
        Thread.ofVirtual().start(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(auditStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Terminal Command Log] userId: {}, container: {}, command: {}",
                            userId, containerInfo.containerName(), line);
                }
            } catch (IOException exception) {
                log.debug("Audit log tail stream closed for container: {}", containerInfo.containerName(), exception);
            }
        });

        TerminalSession terminalSession = new TerminalSession(
                webSocketSession.getId(),
                containerInfo.userId(),
                webSocketSession,
                containerInfo.containerName(),
                sessionStdin,
                stdout,
                stderr,
                callback,
                auditStream);
        sessionReference.set(terminalSession);
        dockerExecRepository.startExec(execId, execStdin, false, callback);
        log.info("Docker terminal exec started. sessionId={} containerName={}",
                webSocketSession.getId(), containerInfo.containerName());
        return terminalSession;
    }

    @Override
    public void remove(TerminalSession terminalSession) {
        if (!terminalSession.markClosed()) {
            return;
        }

        terminalSession.closeInput();
        terminalSession.closeRuntime();
        terminalSession.closeAuditStream();
        terminalSession.markExited(0);
        containerService.markDisconnected(terminalSession.getContainerName());
    }

    @Override
    public void streamToClient(TerminalSession terminalSession) {
        terminalStreamForwarderService.forward(terminalSession, terminalSession.getStdout(), "stdout");
        terminalStreamForwarderService.forward(terminalSession, terminalSession.getStderr(), "stderr");
    }

    @Override
    public void waitForExit(TerminalSession terminalSession, TerminalProcessExitHandler onExit) {
        terminalProcessWatcherService.waitForExit(terminalSession, onExit);
    }

    private void closeOutputPipes(PipedOutputStream stdoutWriter, PipedOutputStream stderrWriter) {
        closeQuietly(stdoutWriter);
        closeQuietly(stderrWriter);
    }

    private void closeQuietly(PipedOutputStream outputStream) {
        try {
            outputStream.close();
        } catch (IOException ignored) {
            // Closing output pipes is best effort during Docker exec callback completion.
        }
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
