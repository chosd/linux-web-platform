package com.example.linuxterminal.domains.container.service;

import com.example.linuxterminal.domains.container.repository.FileContainerMetadataRepository;
import com.example.linuxterminal.domains.container.dto.ResourceLimits;
import com.example.linuxterminal.domains.container.dto.VolumeMount;
import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.domains.container.service.ContainerService.ContainerInfo;
import com.example.linuxterminal.domains.container.domain.ContainerRecord;
import com.example.linuxterminal.domains.network.dto.ContainerNetworkOptions;
import com.example.linuxterminal.domains.network.dto.PortBinding;
import com.example.linuxterminal.domains.network.service.DockerNetworkService;
import com.example.linuxterminal.global.config.TerminalProperties;
import com.example.linuxterminal.global.docker.DockerCommandFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DockerContainerServiceImpl implements ContainerService {

    private final DockerCommandFactory dockerCommandFactory;
    private final ContainerNameGenerator containerNameGenerator;
    private final FileContainerMetadataRepository containerMetadataRepository;
    private final DockerNetworkService dockerNetworkService;
    private final TerminalProperties terminalProperties;
    private final ConcurrentHashMap<String, Instant> lastActivityByContainerName = new ConcurrentHashMap<>();
    private final Set<String> connectedContainerNames = ConcurrentHashMap.newKeySet();

    public DockerContainerServiceImpl(
            DockerCommandFactory dockerCommandFactory,
            ContainerNameGenerator containerNameGenerator,
            FileContainerMetadataRepository containerMetadataRepository,
            DockerNetworkService dockerNetworkService,
            TerminalProperties terminalProperties
    ) {
        this.dockerCommandFactory = dockerCommandFactory;
        this.containerNameGenerator = containerNameGenerator;
        this.containerMetadataRepository = containerMetadataRepository;
        this.dockerNetworkService = dockerNetworkService;
        this.terminalProperties = terminalProperties;
    }

    public ContainerInfo startOrGetContainer(String userId) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        List<ContainerRecord> containers = containerMetadataRepository.findByUserId(normalizedUserId);
        if (containers.isEmpty()) {
            return createContainer(normalizedUserId, "Default Terminal", null, null);
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
        return createContainer(userId, displayName, null, null);
    }

    public ContainerInfo createContainer(
            String userId,
            String displayName,
            ResourceLimits requestedResourceLimits,
            String rootPassword
    ) throws IOException {
        return createContainer(userId, displayName, requestedResourceLimits, rootPassword, List.of());
    }

    public ContainerInfo createContainer(
            String userId,
            String displayName,
            ResourceLimits requestedResourceLimits,
            String rootPassword,
            List<PortBinding> portBindings
    ) throws IOException {
        return createContainer(userId, displayName, requestedResourceLimits, rootPassword, portBindings, List.of(), null);
    }

    public ContainerInfo createContainer(
            String userId,
            String displayName,
            ResourceLimits requestedResourceLimits,
            String rootPassword,
            List<PortBinding> portBindings,
            List<VolumeMount> volumeMounts,
            ContainerNetworkOptions networkOptions
    ) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        String containerName = UUID.randomUUID().toString().replace("-", "") + "_container";
        String normalizedDisplayName = normalizeDisplayName(displayName);
        ResourceLimits resourceLimits = normalizeResourceLimits(requestedResourceLimits);
        List<PortBinding> normalizedPortBindings = normalizePortBindings(portBindings);
        List<VolumeMount> normalizedVolumeMounts = normalizeVolumeMounts(volumeMounts);
        ContainerNetworkOptions normalizedNetworkOptions = normalizeNetworkOptions(networkOptions, normalizedDisplayName);
        validateNetworkForPortBindings(normalizedPortBindings, normalizedNetworkOptions);
        dockerNetworkService.validatePortBindings(normalizedPortBindings);
        ContainerRecord containerRecord = new ContainerRecord(
                normalizedUserId,
                containerName,
                normalizedDisplayName,
                Instant.now(),
                resourceLimits.cpuCores(),
                resourceLimits.memoryMb());

        run(dockerCommandFactory.runDetachedCommand(
                containerName,
                resourceLimits,
                normalizedPortBindings,
                normalizedVolumeMounts,
                normalizedNetworkOptions));
        applyRootPasswordIfPresent(containerName, rootPassword);
        containerMetadataRepository.save(containerRecord);
        markActivity(containerName);
        return toContainerInfo(containerRecord, "RUNNING", "created");
    }

    public ContainerInfo startContainer(String userId, String containerName) throws IOException {
        ContainerRecord containerRecord = findOwnedContainer(userId, containerName);
        String status = inspectStatus(containerRecord.containerName());
        if (status == null) {
            run(dockerCommandFactory.runDetachedCommand(containerRecord.containerName(), limitsOf(containerRecord)));
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

    public ContainerInfo updateContainer(String userId, String containerName, String displayName) throws IOException {
        return updateContainer(userId, containerName, displayName, null);
    }

    public ContainerInfo updateContainer(
            String userId,
            String containerName,
            String displayName,
            ResourceLimits requestedResourceLimits
    ) throws IOException {
        ContainerRecord containerRecord = findOwnedContainer(userId, containerName);
        ResourceLimits resourceLimits = normalizeResourceLimits(requestedResourceLimits == null
                ? limitsOf(containerRecord)
                : requestedResourceLimits);
        String status = inspectStatus(containerRecord.containerName());
        if (status != null) {
            run(dockerCommandFactory.updateResourceLimitsCommand(containerRecord.containerName(), resourceLimits));
        }
        ContainerRecord updatedContainerRecord = containerMetadataRepository.updateContainer(
                containerRecord.userId(),
                containerRecord.containerName(),
                normalizeDisplayName(displayName),
                resourceLimits);
        markActivity(updatedContainerRecord.containerName());
        return toContainerInfo(updatedContainerRecord, statusOf(updatedContainerRecord.containerName()), "updated");
    }

    public void deleteContainer(String userId, String containerName) throws IOException {
        ContainerRecord containerRecord = findOwnedContainer(userId, containerName);
        String status = inspectStatus(containerRecord.containerName());
        if (status != null) {
            run(dockerCommandFactory.removeCommand(containerRecord.containerName()));
        }
        containerMetadataRepository.deleteByUserIdAndContainerName(
                containerRecord.userId(),
                containerRecord.containerName());
        connectedContainerNames.remove(containerRecord.containerName());
        lastActivityByContainerName.remove(containerRecord.containerName());
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
                        null,
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

    public void verifyContainerOwnership(String userId, String containerName) throws IOException {
        findOwnedContainer(userId, containerName);
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

    private void runWithInput(List<String> command, String input) throws IOException {
        try {
            Process process = new ProcessBuilder(command).start();
            process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            process.getOutputStream().close();
            byte[] stderr = process.getErrorStream().readAllBytes();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Docker command failed. command=%s exitCode=%d stderr=%s"
                        .formatted(command, exitCode, new String(stderr, StandardCharsets.UTF_8)));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running Docker command: " + command, exception);
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

    private ResourceLimits normalizeResourceLimits(ResourceLimits resourceLimits) throws IOException {
        double cpuCores = resourceLimits == null || resourceLimits.cpuCores() == null ? 0.5 : resourceLimits.cpuCores();
        int memoryMb = resourceLimits == null || resourceLimits.memoryMb() == null ? 256 : resourceLimits.memoryMb();
        if (cpuCores < 0.1 || cpuCores > 4.0) {
            throw new IOException("CPU limit must be between 0.1 and 4.0 cores.");
        }
        if (memoryMb < 128 || memoryMb > 4096) {
            throw new IOException("Memory limit must be between 128MB and 4096MB.");
        }
        return new ResourceLimits(cpuCores, memoryMb);
    }

    private ResourceLimits limitsOf(ContainerRecord containerRecord) {
        return new ResourceLimits(
                containerRecord.cpuCores() == null ? 0.5 : containerRecord.cpuCores(),
                containerRecord.memoryMb() == null ? 256 : containerRecord.memoryMb());
    }

    private List<PortBinding> normalizePortBindings(List<PortBinding> portBindings) {
        return portBindings == null ? List.of() : portBindings;
    }

    private void validateNetworkForPortBindings(
            List<PortBinding> portBindings,
            ContainerNetworkOptions networkOptions
    ) throws IOException {
        if (portBindings == null || portBindings.isEmpty()) {
            return;
        }
        String effectiveNetworkName = networkOptions != null && networkOptions.hasNetwork()
                ? networkOptions.networkName().trim()
                : terminalProperties.getDocker().getNetwork();
        if ("none".equals(effectiveNetworkName.toLowerCase(Locale.ROOT))) {
            throw new IOException("Port bindings require bridge or user-defined Docker network. Current network is none.");
        }
    }

    private List<VolumeMount> normalizeVolumeMounts(List<VolumeMount> volumeMounts) throws IOException {
        if (volumeMounts == null || volumeMounts.isEmpty()) {
            return List.of();
        }

        Path allowedBase = createAndResolveAllowedVolumeBase();
        List<VolumeMount> normalizedVolumeMounts = new ArrayList<>();
        Set<String> containerPaths = ConcurrentHashMap.newKeySet();
        for (VolumeMount volumeMount : volumeMounts) {
            if (volumeMount == null) {
                continue;
            }
            Path hostPath = createAndResolveHostVolumePath(volumeMount.hostPath(), allowedBase);
            String containerPath = normalizeContainerVolumePath(volumeMount.containerPath());
            if (!containerPaths.add(containerPath)) {
                throw new IOException("Container volume path is duplicated: " + containerPath);
            }
            normalizedVolumeMounts.add(new VolumeMount(hostPath.toString(), containerPath));
        }
        return List.copyOf(normalizedVolumeMounts);
    }

    private Path createAndResolveAllowedVolumeBase() throws IOException {
        String configuredBase = terminalProperties.getDocker().getAllowedVolumeHostPathBase();
        if (configuredBase == null || configuredBase.isBlank()) {
            throw new IOException("Allowed volume host path base is not configured.");
        }
        Path allowedBase = Path.of(configuredBase.trim()).toAbsolutePath().normalize();
        Files.createDirectories(allowedBase);
        return allowedBase.toRealPath();
    }

    private Path createAndResolveHostVolumePath(String rawHostPath, Path allowedBase) throws IOException {
        if (rawHostPath == null || rawHostPath.isBlank()) {
            throw new IOException("Volume host path is required.");
        }
        if (rawHostPath.contains(":")) {
            throw new IOException("Volume host path must not contain ':'.");
        }
        Path hostPath = Path.of(rawHostPath.trim()).toAbsolutePath().normalize();
        if (!hostPath.startsWith(allowedBase) || hostPath.equals(allowedBase)) {
            throw new IOException("Volume host path must be under allowed base path: " + allowedBase);
        }
        Files.createDirectories(hostPath);
        Path realHostPath = hostPath.toRealPath();
        if (!realHostPath.startsWith(allowedBase) || realHostPath.equals(allowedBase)) {
            throw new IOException("Volume host path must be under allowed base path: " + allowedBase);
        }
        return realHostPath;
    }

    private String normalizeContainerVolumePath(String rawContainerPath) throws IOException {
        if (rawContainerPath == null || rawContainerPath.isBlank()) {
            throw new IOException("Volume container path is required.");
        }
        if (rawContainerPath.contains(":")) {
            throw new IOException("Volume container path must not contain ':'.");
        }
        Path containerPath = Path.of(rawContainerPath.trim()).normalize();
        if (!containerPath.isAbsolute() || containerPath.getNameCount() == 0) {
            throw new IOException("Volume container path must be an absolute directory below root.");
        }
        return containerPath.toString();
    }

    private ContainerNetworkOptions normalizeNetworkOptions(
            ContainerNetworkOptions networkOptions,
            String normalizedDisplayName
    ) throws IOException {
        if (networkOptions == null || !networkOptions.hasNetwork()) {
            return null;
        }
        String networkName = networkOptions.networkName().trim();
        if (!networkName.matches("[a-zA-Z0-9_.-]{1,63}")) {
            throw new IOException("Network name may contain only letters, numbers, dot, underscore, and hyphen.");
        }
        String networkAlias = networkOptions.networkAlias();
        if (networkAlias == null || networkAlias.isBlank()) {
            networkAlias = normalizedDisplayName;
        }
        String normalizedAlias = normalizeNetworkAlias(networkAlias);
        return new ContainerNetworkOptions(networkName, normalizedAlias);
    }

    private String normalizeNetworkAlias(String value) throws IOException {
        String normalized = value.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9_.-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            throw new IOException("Network alias must contain at least one usable character.");
        }
        if (normalized.length() > 63) {
            normalized = normalized.substring(0, 63);
        }
        if (!normalized.matches("[a-z0-9][a-z0-9_.-]*")) {
            throw new IOException("Network alias must start with a letter or number.");
        }
        return normalized;
    }

    private void applyRootPasswordIfPresent(String containerName, String rootPassword) throws IOException {
        if (rootPassword == null || rootPassword.isBlank()) {
            return;
        }
        if (rootPassword.length() < 8) {
            throw new IOException("Root password must be at least 8 characters.");
        }
        runWithInput(dockerCommandFactory.setRootPasswordCommand(containerName), "root:" + rootPassword + "\n");
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
                containerRecord.cpuCores(),
                containerRecord.memoryMb(),
                action);
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
