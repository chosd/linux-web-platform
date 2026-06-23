package com.example.linuxterminal.terminal.docker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ContainerManagementService {

    private final DockerCommandFactory dockerCommandFactory;
    private final ContainerNameGenerator containerNameGenerator;
    private final ContainerMetadataRepository containerMetadataRepository;
    private final ConcurrentHashMap<String, Instant> lastActivityByContainerName = new ConcurrentHashMap<>();
    private final Set<String> connectedContainerNames = ConcurrentHashMap.newKeySet();

    public ContainerManagementService(
            DockerCommandFactory dockerCommandFactory,
            ContainerNameGenerator containerNameGenerator,
            ContainerMetadataRepository containerMetadataRepository
    ) {
        this.dockerCommandFactory = dockerCommandFactory;
        this.containerNameGenerator = containerNameGenerator;
        this.containerMetadataRepository = containerMetadataRepository;
    }

    public ContainerInfo startOrGetContainer(String userId) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        List<ContainerRecord> containers = containerMetadataRepository.findByUserId(normalizedUserId);
        if (containers.isEmpty()) {
            return createContainer(normalizedUserId, "Default Terminal");
        }
        return startContainer(normalizedUserId, containers.getFirst().containerName());
    }

    public List<ContainerInfo> listContainers(String userId) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        List<ContainerInfo> containers = new ArrayList<>();
        for (ContainerRecord container : containerMetadataRepository.findByUserId(normalizedUserId)) {
            containers.add(toContainerInfo(container, statusOf(container.containerName()), "listed"));
        }
        return containers;
    }

    public ContainerInfo createContainer(String userId, String displayName) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        String containerName = UUID.randomUUID().toString().replace("-", "") + "_container";
        String normalizedDisplayName = normalizeDisplayName(displayName);
        ContainerRecord containerRecord = new ContainerRecord(
                normalizedUserId,
                containerName,
                normalizedDisplayName,
                Instant.now());

        run(dockerCommandFactory.runDetachedCommand(containerName));
        containerMetadataRepository.save(containerRecord);
        markActivity(containerName);
        return toContainerInfo(containerRecord, "RUNNING", "created");
    }

    public ContainerInfo startContainer(String userId, String containerName) throws IOException {
        ContainerRecord containerRecord = findOwnedContainer(userId, containerName);
        String status = inspectStatus(containerRecord.containerName());
        if (status == null) {
            run(dockerCommandFactory.runDetachedCommand(containerRecord.containerName()));
            markActivity(containerRecord.containerName());
            return toContainerInfo(containerRecord, "RUNNING", "created");
        }

        if (!"running".equals(status)) {
            run(dockerCommandFactory.startCommand(containerRecord.containerName()));
            markActivity(containerRecord.containerName());
            return toContainerInfo(containerRecord, "RUNNING", "started");
        }

        markActivity(containerRecord.containerName());
        return toContainerInfo(containerRecord, "RUNNING", "reused");
    }

    public ContainerInfo stopContainer(String userId) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        String containerName = containerNameGenerator.forUser(normalizedUserId);
        return stopContainerByName(normalizedUserId, containerName);
    }

    public ContainerInfo stopContainerByName(String userId, String containerName) throws IOException {
        ContainerRecord containerRecord = findOwnedContainer(userId, containerName);
        String status = inspectStatus(containerRecord.containerName());
        if (status == null) {
            return toContainerInfo(containerRecord, "NOT_FOUND", "none");
        }
        if (!"running".equals(status)) {
            return toContainerInfo(containerRecord, statusOf(containerRecord.containerName()), "already_stopped");
        }
        run(dockerCommandFactory.stopCommand(containerRecord.containerName()));
        markActivity(containerRecord.containerName());
        return toContainerInfo(containerRecord, "EXITED", "stopped");
    }

    public ContainerInfo restartContainer(String userId) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        String containerName = containerNameGenerator.forUser(normalizedUserId);
        return restartContainerByName(normalizedUserId, containerName);
    }

    public ContainerInfo restartContainerByName(String userId, String containerName) throws IOException {
        ContainerRecord containerRecord = findOwnedContainer(userId, containerName);
        String status = inspectStatus(containerRecord.containerName());
        if (status == null) {
            return startContainer(containerRecord.userId(), containerRecord.containerName());
        }
        run(dockerCommandFactory.restartCommand(containerRecord.containerName()));
        markActivity(containerRecord.containerName());
        return toContainerInfo(containerRecord, "RUNNING", "restarted");
    }

    public void markConnected(String containerName) {
        connectedContainerNames.add(containerName);
        markActivity(containerName);
    }

    public void markDisconnected(String containerName) {
        connectedContainerNames.remove(containerName);
        markActivity(containerName);
    }

    public void markActivity(String containerName) {
        if (containerName != null && !containerName.isBlank()) {
            lastActivityByContainerName.put(containerName, Instant.now());
        }
    }

    public List<ContainerInfo> stopIdleContainers(Duration idleTimeout) {
        Instant threshold = Instant.now().minus(idleTimeout);
        List<ContainerInfo> stoppedContainers = new ArrayList<>();
        lastActivityByContainerName.forEach((containerName, lastActivityAt) -> {
            if (connectedContainerNames.contains(containerName) || lastActivityAt.isAfter(threshold)) {
                return;
            }
            try {
                String status = inspectStatus(containerName);
                if (!"running".equals(status)) {
                    lastActivityByContainerName.remove(containerName, lastActivityAt);
                    return;
                }
                run(dockerCommandFactory.stopCommand(containerName));
                ContainerInfo info = new ContainerInfo(
                        null,
                        containerName,
                        null,
                        "EXITED",
                        null,
                        "stopped");
                if ("stopped".equals(info.action()) || "already_stopped".equals(info.action())) {
                    stoppedContainers.add(info);
                    lastActivityByContainerName.remove(containerName, lastActivityAt);
                }
            } catch (IOException exception) {
                log.warn("Failed to stop idle container. containerName={}", containerName, exception);
            }
        });
        return stoppedContainers;
    }

    public ContainerInfo ensureContainerRunning(String userId, String containerName) throws IOException {
        return startContainer(userId, containerName);
    }

    private String inspectStatus(String containerName) throws IOException {
        CommandResult result = runAllowingFailure(dockerCommandFactory.inspectStatusCommand(containerName));
        if (result.exitCode() != 0) {
            return null;
        }
        String status = result.stdout().trim();
        return status.isBlank() ? null : status;
    }

    private void run(List<String> command) throws IOException {
        CommandResult result = runAllowingFailure(command);
        if (result.exitCode() != 0) {
            throw new IOException("Docker command failed. command=%s exitCode=%d stderr=%s"
                    .formatted(command, result.exitCode(), result.stderr()));
        }
    }

    private CommandResult runAllowingFailure(List<String> command) throws IOException {
        try {
            Process process = new ProcessBuilder(command).start();
            byte[] stdout = process.getInputStream().readAllBytes();
            byte[] stderr = process.getErrorStream().readAllBytes();
            int exitCode = process.waitFor();
            return new CommandResult(
                    exitCode,
                    new String(stdout, StandardCharsets.UTF_8),
                    new String(stderr, StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running Docker command: " + command, exception);
        }
    }

    private String normalizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "anonymous";
        }
        return userId.trim();
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "Linux Terminal";
        }
        return displayName.trim();
    }

    private ContainerRecord findOwnedContainer(String userId, String containerName) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        return containerMetadataRepository.findByUserIdAndContainerName(normalizedUserId, containerName)
                .orElseThrow(() -> new IOException("Container not found. userId=%s containerName=%s"
                        .formatted(normalizedUserId, containerName)));
    }

    private String statusOf(String containerName) throws IOException {
        String status = inspectStatus(containerName);
        if (status == null) {
            return "NOT_FOUND";
        }
        return switch (status) {
            case "running" -> "RUNNING";
            case "exited" -> "EXITED";
            default -> status.toUpperCase();
        };
    }

    private ContainerInfo toContainerInfo(ContainerRecord containerRecord, String status, String action) {
        return new ContainerInfo(
                containerRecord.userId(),
                containerRecord.containerName(),
                containerRecord.displayName(),
                status,
                containerRecord.createdAt(),
                action);
    }

    public record ContainerInfo(
            String userId,
            String containerName,
            String displayName,
            String status,
            Instant createdAt,
            String action
    ) {
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
