package com.example.linuxterminal.terminal.docker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public class ContainerStatsService {

    private static final long STREAM_INTERVAL_MILLIS = 2000L;

    private final DockerCommandFactory dockerCommandFactory;
    private final ContainerManagementService containerManagementService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public ContainerStatsService(
            DockerCommandFactory dockerCommandFactory,
            ContainerManagementService containerManagementService,
            ObjectMapper objectMapper
    ) {
        this.dockerCommandFactory = dockerCommandFactory;
        this.containerManagementService = containerManagementService;
        this.objectMapper = objectMapper;
    }

    public SseEmitter streamStats(String userId, String containerName) throws IOException {
        containerManagementService.verifyContainerOwnership(userId, containerName);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean active = new AtomicBoolean(true);
        emitter.onCompletion(() -> active.set(false));
        emitter.onTimeout(() -> active.set(false));
        emitter.onError(error -> active.set(false));

        executorService.submit(() -> {
            while (active.get()) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("container-stats")
                            .data(readStats(containerName)));
                    Thread.sleep(STREAM_INTERVAL_MILLIS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    active.set(false);
                } catch (Exception exception) {
                    active.set(false);
                    if (isClientDisconnect(exception)) {
                        log.debug("Container stats stream closed by client. containerName={}", containerName);
                        return;
                    }
                    log.warn("Failed to stream container stats. containerName={}", containerName, exception);
                    emitter.completeWithError(exception);
                }
            }
            if (active.get()) {
                emitter.complete();
            }
        });

        return emitter;
    }

    public ContainerStatsSample readStats(String containerName) throws IOException {
        CommandResult result = run(dockerCommandFactory.statsJsonCommand(containerName));
        if (result.exitCode() != 0) {
            throw new IOException("Docker stats command failed. exitCode=%d stderr=%s"
                    .formatted(result.exitCode(), result.stderr()));
        }
        JsonNode jsonNode = objectMapper.readTree(result.stdout());
        String[] memoryParts = text(jsonNode, "MemUsage").split("\\s+/\\s+", 2);
        String[] networkParts = text(jsonNode, "NetIO").split("\\s+/\\s+", 2);
        String[] blockParts = text(jsonNode, "BlockIO").split("\\s+/\\s+", 2);

        return new ContainerStatsSample(
                Instant.now(),
                parsePercent(text(jsonNode, "CPUPerc")),
                parseSizeMb(memoryParts, 0),
                parseSizeMb(memoryParts, 1),
                parseSizeMb(networkParts, 0),
                parseSizeMb(networkParts, 1),
                parseSizeMb(blockParts, 0),
                parseSizeMb(blockParts, 1));
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private CommandResult run(List<String> command) throws IOException {
        try {
            Process process = new ProcessBuilder(command).start();
            byte[] stdout = process.getInputStream().readAllBytes();
            byte[] stderr = process.getErrorStream().readAllBytes();
            int exitCode = process.waitFor();
            return new CommandResult(
                    exitCode,
                    new String(stdout, StandardCharsets.UTF_8).trim(),
                    new String(stderr, StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running Docker command: " + command, exception);
        }
    }

    private String text(JsonNode jsonNode, String fieldName) {
        JsonNode value = jsonNode.get(fieldName);
        return value == null ? "" : value.asText();
    }

    private double parsePercent(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(value.replace("%", "").trim());
    }

    private double parseSizeMb(String[] values, int index) {
        if (index >= values.length) {
            return 0.0;
        }
        return parseSizeMb(values[index]);
    }

    private double parseSizeMb(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return 0.0;
        }
        String value = rawValue.trim().replace(" ", "");
        String lowerValue = value.toLowerCase(Locale.ROOT);
        double number = Double.parseDouble(lowerValue.replaceAll("[^0-9.]", ""));
        if (lowerValue.endsWith("kib") || lowerValue.endsWith("kb")) {
            return number / 1024.0;
        }
        if (lowerValue.endsWith("gib") || lowerValue.endsWith("gb")) {
            return number * 1024.0;
        }
        if (lowerValue.endsWith("b") && !lowerValue.endsWith("mb") && !lowerValue.endsWith("mib")) {
            return number / 1024.0 / 1024.0;
        }
        return number;
    }

    private boolean isClientDisconnect(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("broken pipe")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
