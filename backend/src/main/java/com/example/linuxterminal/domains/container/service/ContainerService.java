package com.example.linuxterminal.domains.container.service;

import com.example.linuxterminal.domains.container.dto.ResourceLimits;
import com.example.linuxterminal.domains.container.dto.VolumeMount;
import com.example.linuxterminal.domains.network.dto.ContainerNetworkOptions;
import com.example.linuxterminal.domains.network.dto.PortBinding;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface ContainerService {

    ContainerInfo startOrGetContainer(String userId) throws IOException;

    List<ContainerInfo> listContainers(String userId) throws IOException;

    ContainerInfo createContainer(String userId, String displayName) throws IOException;

    ContainerInfo createContainer(
            String userId,
            String displayName,
            ResourceLimits requestedResourceLimits,
            String rootPassword
    ) throws IOException;

    ContainerInfo createContainer(
            String userId,
            String displayName,
            ResourceLimits requestedResourceLimits,
            String rootPassword,
            List<PortBinding> portBindings
    ) throws IOException;

    ContainerInfo createContainer(
            String userId,
            String displayName,
            ResourceLimits requestedResourceLimits,
            String rootPassword,
            List<PortBinding> portBindings,
            List<VolumeMount> volumeMounts,
            ContainerNetworkOptions networkOptions
    ) throws IOException;

    ContainerInfo startContainer(String userId, String containerName) throws IOException;

    ContainerInfo stopContainerByName(String userId, String containerName) throws IOException;

    ContainerInfo restartContainerByName(String userId, String containerName) throws IOException;

    ContainerInfo updateContainer(String userId, String containerName, String displayName) throws IOException;

    ContainerInfo updateContainer(
            String userId,
            String containerName,
            String displayName,
            ResourceLimits requestedResourceLimits
    ) throws IOException;

    void deleteContainer(String userId, String containerName) throws IOException;

    void markConnected(String containerName);

    void markDisconnected(String containerName);

    void markActivity(String containerName);

    List<ContainerInfo> stopIdleContainers(Duration idleTimeout);

    ContainerInfo ensureContainerRunning(String userId, String containerName) throws IOException;

    void verifyContainerOwnership(String userId, String containerName) throws IOException;

    record ContainerInfo(
            String userId,
            String containerName,
            String displayName,
            String status,
            Instant createdAt,
            Double cpuCores,
            Integer memoryMb,
            String action
    ) {
    }
}
