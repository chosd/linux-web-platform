package com.example.linuxterminal.domains.container.service;

import com.example.linuxterminal.domains.container.dto.ResourceLimits;
import com.example.linuxterminal.domains.container.dto.ResolvedVolumeMount;
import com.example.linuxterminal.domains.container.dto.VolumeMount;
import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.domains.container.service.ContainerService.ContainerInfo;
import com.example.linuxterminal.domains.container.domain.ContainerRecord;
import com.example.linuxterminal.domains.network.dto.ContainerNetworkOptions;
import com.example.linuxterminal.domains.network.dto.PortBinding;
import com.example.linuxterminal.domains.network.service.DockerNetworkService;
import com.example.linuxterminal.global.config.TerminalProperties;
import com.example.linuxterminal.global.docker.DockerContainerRepository;
import com.example.linuxterminal.global.docker.DockerImageRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.github.dockerjava.api.model.Container;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DockerContainerServiceImpl implements ContainerService {

    private final DockerContainerRepository dockerContainerRepository;
    private final DockerImageRepository dockerImageRepository;
    private final ContainerNameGeneratorService containerNameGeneratorService;
    private final DockerNetworkService dockerNetworkService;
    private final TerminalProperties terminalProperties;
    private final ConcurrentHashMap<String, Instant> lastActivityByContainerName = new ConcurrentHashMap<>();
    private final Set<String> connectedContainerNames = ConcurrentHashMap.newKeySet();

    public DockerContainerServiceImpl(
            DockerContainerRepository dockerContainerRepository,
            DockerImageRepository dockerImageRepository,
            ContainerNameGeneratorService containerNameGeneratorService,
            DockerNetworkService dockerNetworkService,
            TerminalProperties terminalProperties
    ) {
        this.dockerContainerRepository = dockerContainerRepository;
        this.dockerImageRepository = dockerImageRepository;
        this.containerNameGeneratorService = containerNameGeneratorService;
        this.dockerNetworkService = dockerNetworkService;
        this.terminalProperties = terminalProperties;
    }

    public ContainerInfo startOrGetContainer(String userId) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        List<ContainerInfo> containers = listContainers(normalizedUserId);
        if (containers.isEmpty()) {
            return createContainer(normalizedUserId, "Default Terminal", null, null);
        }
        ContainerInfo first = containers.getFirst();
        return startContainer(normalizedUserId, first.containerName());
    }

    public List<ContainerInfo> listContainers(String userId) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        List<Container> dockerContainers = dockerContainerRepository.listContainers();
        List<ContainerInfo> result = new ArrayList<>();
        for (Container container : dockerContainers) {
            if (container.getNames() == null || container.getNames().length == 0) {
                continue;
            }
            String rawName = container.getNames()[0];
            String containerName = rawName.startsWith("/") ? rawName.substring(1) : rawName;
            
            boolean isManaged = containerName.endsWith("_container");
            Map<String, String> labels = container.getLabels();
            if (labels != null && labels.containsKey("display-name")) {
                isManaged = true;
            }
            if (!isManaged) {
                continue;
            }

            String labelUserId = labels != null ? labels.get("user-id") : null;
            if (labelUserId == null) {
                labelUserId = "anonymous";
            }
            if (!normalizedUserId.equals(labelUserId)) {
                continue;
            }

            String displayName = labels != null ? labels.get("display-name") : null;
            if (displayName == null) {
                displayName = containerName;
            }

            Double cpuCores = 0.5;
            if (labels != null && labels.containsKey("cpu-cores")) {
                try {
                    cpuCores = Double.parseDouble(labels.get("cpu-cores"));
                } catch (NumberFormatException ignored) {}
            }

            Integer memoryMb = 256;
            if (labels != null && labels.containsKey("memory-mb")) {
                try {
                    memoryMb = Integer.parseInt(labels.get("memory-mb"));
                } catch (NumberFormatException ignored) {}
            }

            String imageName = container.getImage();
            Instant createdAt = Instant.ofEpochSecond(container.getCreated() != null ? container.getCreated() : Instant.now().getEpochSecond());
            String status = container.getState() != null ? container.getState().toUpperCase() : "RUNNING";

            result.add(new ContainerInfo(
                    labelUserId,
                    containerName,
                    displayName,
                    status,
                    createdAt,
                    cpuCores,
                    memoryMb,
                    imageName,
                    "listed"
            ));
        }
        result.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return result;
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
        return createContainer(userId, displayName, requestedResourceLimits, rootPassword, portBindings, List.of(), null, null);
    }

    public ContainerInfo createContainer(
            String userId,
            String displayName,
            ResourceLimits requestedResourceLimits,
            String rootPassword,
            List<PortBinding> portBindings,
            List<VolumeMount> volumeMounts,
            ContainerNetworkOptions networkOptions,
            String imageName
    ) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        String containerName = UUID.randomUUID().toString().replace("-", "") + "_container";
        String normalizedDisplayName = normalizeDisplayName(displayName);
        ResourceLimits resourceLimits = normalizeResourceLimits(requestedResourceLimits);
        List<PortBinding> normalizedPortBindings = normalizePortBindings(portBindings);
        List<ResolvedVolumeMount> normalizedVolumeMounts = normalizeVolumeMounts(volumeMounts);
        ContainerNetworkOptions normalizedNetworkOptions = normalizeNetworkOptions(networkOptions, normalizedDisplayName);
        String normalizedImageName = normalizeImageName(imageName);
        validateNetworkForPortBindings(normalizedPortBindings, normalizedNetworkOptions);
        dockerNetworkService.validatePortBindings(normalizedPortBindings);
        ContainerRecord containerRecord = new ContainerRecord(
                normalizedUserId,
                containerName,
                normalizedDisplayName,
                Instant.now(),
                resourceLimits.cpuCores(),
                resourceLimits.memoryMb(),
                normalizedImageName);

        Map<String, String> labels = new HashMap<>();
        labels.put("user-id", normalizedUserId);
        labels.put("display-name", normalizedDisplayName);
        labels.put("cpu-cores", String.valueOf(resourceLimits.cpuCores()));
        labels.put("memory-mb", String.valueOf(resourceLimits.memoryMb()));

        dockerContainerRepository.createAndStart(
                containerName,
                resourceLimits,
                normalizedPortBindings,
                normalizedVolumeMounts,
                normalizedNetworkOptions,
                normalizedImageName,
                labels);
        try {
            applyRootPasswordIfPresent(containerName, rootPassword);
        } catch (IOException exception) {
            log.warn("Failed to set root password for container '{}'. Container was created successfully, but password might not be set. Error: {}", containerName, exception.getMessage());
        }
        markActivity(containerName);
        return toContainerInfo(containerRecord, "RUNNING", "created");
    }

    public ContainerInfo startContainer(String userId, String containerName) throws IOException {
        ContainerRecord containerRecord = findOwnedContainer(userId, containerName);
        String status = inspectStatus(containerRecord.containerName());
        if (status == null) {
            Map<String, String> labels = new HashMap<>();
            labels.put("user-id", containerRecord.userId());
            labels.put("display-name", containerRecord.displayName());
            labels.put("cpu-cores", String.valueOf(containerRecord.cpuCores() != null ? containerRecord.cpuCores() : 0.5));
            labels.put("memory-mb", String.valueOf(containerRecord.memoryMb() != null ? containerRecord.memoryMb() : 256));

            dockerContainerRepository.createAndStart(
                    containerRecord.containerName(), limitsOf(containerRecord), List.of(), List.of(), null,
                    effectiveImageName(containerRecord), labels);
            markActivity(containerRecord.containerName());
            return toContainerInfo(containerRecord, "RUNNING", "created");
        }

        if (!"running".equals(status)) {
            dockerContainerRepository.start(containerRecord.containerName());
            markActivity(containerRecord.containerName());
            return toContainerInfo(containerRecord, "RUNNING", "started");
        }

        markActivity(containerRecord.containerName());
        return toContainerInfo(containerRecord, "RUNNING", "reused");
    }

    public ContainerInfo stopContainer(String userId) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        String containerName = containerNameGeneratorService.forUser(normalizedUserId);
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
        dockerContainerRepository.stop(containerRecord.containerName());
        markActivity(containerRecord.containerName());
        return toContainerInfo(containerRecord, "EXITED", "stopped");
    }

    public ContainerInfo restartContainer(String userId) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        String containerName = containerNameGeneratorService.forUser(normalizedUserId);
        return restartContainerByName(normalizedUserId, containerName);
    }

    public ContainerInfo restartContainerByName(String userId, String containerName) throws IOException {
        ContainerRecord containerRecord = findOwnedContainer(userId, containerName);
        String status = inspectStatus(containerRecord.containerName());
        if (status == null) {
            return startContainer(containerRecord.userId(), containerRecord.containerName());
        }
        dockerContainerRepository.restart(containerRecord.containerName());
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
            dockerContainerRepository.update(containerRecord.containerName(), resourceLimits);
        }
        ContainerRecord updatedContainerRecord = new ContainerRecord(
                containerRecord.userId(),
                containerRecord.containerName(),
                normalizeDisplayName(displayName),
                containerRecord.createdAt(),
                resourceLimits.cpuCores(),
                resourceLimits.memoryMb(),
                containerRecord.imageName()
        );
        markActivity(updatedContainerRecord.containerName());
        return toContainerInfo(updatedContainerRecord, statusOf(updatedContainerRecord.containerName()), "updated");
    }

    public void deleteContainer(String userId, String containerName) throws IOException {
        ContainerRecord containerRecord = findOwnedContainer(userId, containerName);
        String status = inspectStatus(containerRecord.containerName());
        if (status != null) {
            dockerContainerRepository.remove(containerRecord.containerName());
        }
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
                dockerContainerRepository.stop(containerName);
                ContainerInfo info = new ContainerInfo(
                        null,
                        containerName,
                        null,
                        "EXITED",
                        null,
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
        return dockerContainerRepository.status(containerName);
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

    private String normalizeImageName(String imageName) throws IOException {
        String normalized = imageName == null || imageName.isBlank() ? terminalProperties.getImage() : imageName.trim();
        if (!normalized.matches("[a-zA-Z0-9][a-zA-Z0-9._/:@-]{0,254}")) {
            throw new IOException("Docker image name is invalid.");
        }
        if (!dockerImageRepository.imageExists(normalized)) {
            throw new IOException("Docker image is not available locally: " + normalized);
        }
        return normalized;
    }

    private String effectiveImageName(ContainerRecord containerRecord) {
        return containerRecord.imageName() == null || containerRecord.imageName().isBlank()
                ? terminalProperties.getImage()
                : containerRecord.imageName();
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

    private List<ResolvedVolumeMount> normalizeVolumeMounts(List<VolumeMount> volumeMounts) throws IOException {
        if (volumeMounts == null || volumeMounts.isEmpty()) {
            return List.of();
        }

        List<ResolvedVolumeMount> normalizedVolumeMounts = new ArrayList<>();
        Set<String> containerPaths = ConcurrentHashMap.newKeySet();
        Set<String> hostPaths = ConcurrentHashMap.newKeySet();
        for (VolumeMount volumeMount : volumeMounts) {
            if (volumeMount == null) {
                continue;
            }
            String hostPath = normalizeHostVolumePath(volumeMount.sourcePath());
            if (!hostPaths.add(hostPath)) {
                throw new IOException("Host volume path is duplicated: " + hostPath);
            }
            String containerPath = normalizeContainerVolumePath(volumeMount.containerPath());
            if (!containerPaths.add(containerPath)) {
                throw new IOException("Container volume path is duplicated: " + containerPath);
            }
            normalizedVolumeMounts.add(new ResolvedVolumeMount(hostPath, containerPath, volumeMount.accessMode()));
        }
        return List.copyOf(normalizedVolumeMounts);
    }

    private String normalizeHostVolumePath(String rawHostPath) throws IOException {
        if (rawHostPath == null || rawHostPath.isBlank()) {
            throw new IOException("Host volume path is required.");
        }
        if (rawHostPath.contains("\0")) {
            throw new IOException("Host volume path is invalid.");
        }
        Path hostPath = Path.of(rawHostPath.trim()).normalize();
        if (!hostPath.isAbsolute()) {
            throw new IOException("Host volume path must be absolute.");
        }
        return hostPath.toString();
    }

    private String normalizeContainerVolumePath(String rawContainerPath) throws IOException {
        if (rawContainerPath == null || rawContainerPath.isBlank()) {
            throw new IOException("Volume container path is required.");
        }
        String trimmed = rawContainerPath.trim();
        if (trimmed.contains(":") || trimmed.contains("\0")) {
            throw new IOException("Volume container path is invalid.");
        }
        if (!trimmed.startsWith("/") || trimmed.equals("/")) {
            throw new IOException("Volume container path must be absolute.");
        }
        String normalized = trimmed.replaceAll("/+", "/");
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
        dockerContainerRepository.setRootPassword(containerName, rootPassword);
    }

    private ContainerRecord findOwnedContainer(String userId, String containerName) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        try {
            var inspect = dockerContainerRepository.inspect(containerName);
            Map<String, String> labels = inspect.getConfig().getLabels();
            
            String labelUserId = labels != null ? labels.get("user-id") : null;
            if (labelUserId == null) {
                labelUserId = "anonymous";
            }
            if (!normalizedUserId.equals(labelUserId)) {
                throw new IOException("Container ownership verification failed. containerName=" + containerName);
            }
            
            String displayName = labels != null ? labels.get("display-name") : null;
            if (displayName == null) {
                displayName = containerName;
            }
            
            Double cpuCores = 0.5;
            if (labels != null && labels.containsKey("cpu-cores")) {
                try {
                    cpuCores = Double.parseDouble(labels.get("cpu-cores"));
                } catch (NumberFormatException ignored) {}
            }
            
            Integer memoryMb = 256;
            if (labels != null && labels.containsKey("memory-mb")) {
                try {
                    memoryMb = Integer.parseInt(labels.get("memory-mb"));
                } catch (NumberFormatException ignored) {}
            }
            
            String imageName = inspect.getConfig().getImage();
            String createdAt = inspect.getCreated();
            if (createdAt == null) {
                createdAt = Instant.now().toString();
            }
            
            return new ContainerRecord(
                    labelUserId,
                    containerName,
                    displayName,
                    Instant.parse(createdAt),
                    cpuCores,
                    memoryMb,
                    imageName
            );
        } catch (Exception exception) {
            throw new IOException("Container not found. userId=%s containerName=%s"
                    .formatted(normalizedUserId, containerName), exception);
        }
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
                effectiveImageName(containerRecord),
                action);
    }

}
