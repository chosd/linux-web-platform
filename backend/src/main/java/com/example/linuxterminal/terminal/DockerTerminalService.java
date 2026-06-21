package com.example.linuxterminal.terminal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

@Service
public class DockerTerminalService {

    private static final Logger log = LoggerFactory.getLogger(DockerTerminalService.class);
    private static final int CONTAINER_SUFFIX_BYTES = 6;

    private final TerminalProperties properties;
    private final DockerCommandFactory dockerCommandFactory;
    private final TerminalWebSocketSender webSocketSender;
    private final SecureRandom secureRandom = new SecureRandom();

    public DockerTerminalService(
            TerminalProperties properties,
            DockerCommandFactory dockerCommandFactory,
            TerminalWebSocketSender webSocketSender
    ) {
        this.properties = properties;
        this.dockerCommandFactory = dockerCommandFactory;
        this.webSocketSender = webSocketSender;
    }

    public TerminalSession start(WebSocketSession webSocketSession) throws IOException {
        String containerName = createContainerName(webSocketSession.getId());
        List<String> command = dockerCommandFactory.runCommand(containerName);
        Process process = new ProcessBuilder(command).start();
        return new TerminalSession(webSocketSession.getId(), webSocketSession, containerName, process);
    }

    public void remove(TerminalSession terminalSession) {
        if (!terminalSession.markClosed()) {
            return;
        }

        terminalSession.closeInput();
        Process process = terminalSession.getProcess();
        if (process.isAlive()) {
            process.destroy();
        }

        List<String> command = dockerCommandFactory.removeCommand(terminalSession.getContainerName());
        try {
            Process rmProcess = new ProcessBuilder(command).start();
            int exitCode = rmProcess.waitFor();
            if (exitCode != 0) {
                log.warn("Docker container cleanup finished with non-zero exitCode. containerName={} exitCode={}",
                        terminalSession.getContainerName(), exitCode);
            }
        } catch (IOException exception) {
            log.warn("Failed to start docker cleanup process. containerName={}",
                    terminalSession.getContainerName(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while cleaning docker container. containerName={}",
                    terminalSession.getContainerName(), exception);
        }
    }

    public Thread streamToWebSocket(TerminalSession terminalSession, InputStream inputStream, String streamName) {
        Thread thread = Thread.ofVirtual()
                .name("terminal-" + streamName + "-" + terminalSession.getWebSocketSessionId())
                .unstarted(() -> forwardStream(terminalSession, inputStream));
        thread.start();
        return thread;
    }

    public Thread waitForExit(TerminalSession terminalSession, TerminalProcessExitHandler onExit) {
        Thread thread = Thread.ofVirtual()
                .name("terminal-process-" + terminalSession.getWebSocketSessionId())
                .unstarted(() -> {
                    try {
                        int exitCode = terminalSession.getProcess().waitFor();
                        if (!terminalSession.isClosed()) {
                            log.info("Terminal process exited. sessionId={} exitCode={}",
                                    terminalSession.getWebSocketSessionId(), exitCode);
                            onExit.onExit(exitCode);
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
        thread.start();
        return thread;
    }

    private void forwardStream(TerminalSession terminalSession, InputStream inputStream) {
        char[] buffer = new char[2048];
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, read);
                webSocketSender.sendText(terminalSession.getWebSocketSession(), chunk);
            }
        } catch (IOException exception) {
            if (!terminalSession.isClosed()) {
                log.warn("Failed to forward terminal stream. sessionId={}",
                        terminalSession.getWebSocketSessionId(), exception);
            }
        }
    }

    String createContainerName(String webSocketSessionId) {
        byte[] randomBytes = new byte[CONTAINER_SUFFIX_BYTES];
        secureRandom.nextBytes(randomBytes);
        String randomSuffix = HexFormat.of().formatHex(randomBytes);
        String sanitized = webSocketSessionId == null ? "session" : webSocketSessionId
                .toLowerCase()
                .replaceAll("[^a-z0-9_.-]", "-")
                .replaceAll("^[^a-z0-9]+", "")
                .replaceAll("[^a-z0-9]+$", "");
        if (sanitized.isBlank()) {
            sanitized = "session";
        }
        int maxSessionIdLength = properties.getDocker().getContainerNameSessionIdLength();
        if (sanitized.length() > maxSessionIdLength) {
            sanitized = sanitized.substring(0, maxSessionIdLength);
        }
        return properties.getDocker().getContainerNamePrefix() + "-" + sanitized + "-" + randomSuffix;
    }
}
