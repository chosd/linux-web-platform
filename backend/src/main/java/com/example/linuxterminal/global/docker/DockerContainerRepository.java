package com.example.linuxterminal.global.docker;

import com.example.linuxterminal.domains.container.dto.ResourceLimits;
import com.example.linuxterminal.domains.container.dto.ResolvedVolumeMount;
import com.example.linuxterminal.domains.container.dto.VolumeMount;
import com.example.linuxterminal.domains.network.dto.ContainerNetworkOptions;
import com.example.linuxterminal.domains.network.dto.PortBinding;
import com.example.linuxterminal.global.config.TerminalProperties;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Repository;

@Repository
public class DockerContainerRepository {

    private final DockerClient dockerClient;
    private final TerminalProperties terminalProperties;
    private final DockerExecRepository dockerExecRepository;

    public DockerContainerRepository(
            DockerClient dockerClient,
            TerminalProperties terminalProperties,
            DockerExecRepository dockerExecRepository
    ) {
        this.dockerClient = dockerClient;
        this.terminalProperties = terminalProperties;
        this.dockerExecRepository = dockerExecRepository;
    }

    public void createAndStart(
            String containerName,
            ResourceLimits resourceLimits,
            List<PortBinding> portBindings,
            List<ResolvedVolumeMount> volumeMounts,
            ContainerNetworkOptions networkOptions,
            String imageName,
            Map<String, String> labels
    ) throws IOException {
        try {
            TerminalProperties.Docker docker = terminalProperties.getDocker();
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withNanoCPUs(toNanoCpus(resourceLimits))
                    .withMemory(toMemoryBytes(resourceLimits))
                    .withPidsLimit((long) docker.getPidsLimit())
                    .withNetworkMode(effectiveNetworkName(docker, networkOptions));
            List<ExposedPort> exposedPorts = configurePorts(hostConfig, portBindings);
            configureVolumes(hostConfig, volumeMounts);

            var command = dockerClient.createContainerCmd(imageName)
                    .withName(containerName)
                    .withHostConfig(hostConfig)
                    .withUser(docker.getUser())
                    .withWorkingDir(docker.getWorkdir())
                    .withLabels(labels)
                    .withCmd("tail", "-f", "/dev/null");
            if (!exposedPorts.isEmpty()) {
                command.withExposedPorts(exposedPorts);
            }
            if (networkOptions != null && networkOptions.hasNetwork() && hasText(networkOptions.networkAlias())) {
                String networkName = networkOptions.networkName().trim();
                if (!"bridge".equalsIgnoreCase(networkName)) {
                    command.withAliases(networkOptions.networkAlias().trim());
                }
            }
            dockerClient.startContainerCmd(command.exec().getId()).exec();
        } catch (RuntimeException exception) {
            throw new IOException("Docker API failed to create container: " + containerName, exception);
        }
    }

    public String status(String containerName) throws IOException {
        try {
            return dockerClient.inspectContainerCmd(containerName).exec().getState().getStatus();
        } catch (NotFoundException exception) {
            return null;
        } catch (RuntimeException exception) {
            throw new IOException("Docker API failed to inspect container: " + containerName, exception);
        }
    }

    public void start(String containerName) throws IOException {
        run(() -> dockerClient.startContainerCmd(containerName).exec(), "start", containerName);
    }

    public void stop(String containerName) throws IOException {
        run(() -> dockerClient.stopContainerCmd(containerName).exec(), "stop", containerName);
    }

    public void restart(String containerName) throws IOException {
        run(() -> dockerClient.restartContainerCmd(containerName).exec(), "restart", containerName);
    }

    public void remove(String containerName) throws IOException {
        run(() -> dockerClient.removeContainerCmd(containerName).withForce(true).exec(), "remove", containerName);
    }

    public void update(String containerName, ResourceLimits limits) throws IOException {
        run(() -> dockerClient.updateContainerCmd(containerName)
                .withNanoCPUs(toNanoCpus(limits))
                .withMemory(toMemoryBytes(limits))
                .exec(), "update", containerName);
    }

    public void setRootPassword(String containerName, String password) throws IOException {
        if (password == null || password.isBlank()) {
            return;
        }
        String escapedPassword = password.replace("'", "'\\''");
        String shellCommand = String.format("echo 'root:%s' | chpasswd", escapedPassword);
        DockerExecRepository.ExecResult result = dockerExecRepository.exec(
                containerName,
                "root",
                (InputStream) null,
                "sh", "-c", shellCommand);
        if (result.exitCode() != 0) {
            throw new IOException("Failed to set root password. stderr=" + result.stderr());
        }
    }

    public List<Container> listContainers() throws IOException {
        try {
            return dockerClient.listContainersCmd().withShowAll(true).exec();
        } catch (RuntimeException exception) {
            throw new IOException("Docker API failed to list containers.", exception);
        }
    }

    public InspectContainerResponse inspect(String containerName) throws IOException {
        try {
            return dockerClient.inspectContainerCmd(containerName).exec();
        } catch (RuntimeException exception) {
            throw new IOException("Docker API failed to inspect container: " + containerName, exception);
        }
    }

    private long toNanoCpus(ResourceLimits resourceLimits) {
        return (long) (resourceLimits.cpuCores() * 1_000_000_000L);
    }

    private long toMemoryBytes(ResourceLimits resourceLimits) {
        return resourceLimits.memoryMb() * 1024L * 1024L;
    }

    private List<ExposedPort> configurePorts(HostConfig hostConfig, List<PortBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        Ports ports = new Ports();
        List<ExposedPort> exposedPorts = new ArrayList<>();
        for (PortBinding binding : bindings) {
            ExposedPort exposedPort = binding.protocol() == PortBinding.Protocol.UDP
                    ? ExposedPort.udp(binding.containerPort())
                    : ExposedPort.tcp(binding.containerPort());
            ports.bind(exposedPort, Ports.Binding.bindPort(binding.hostPort()));
            exposedPorts.add(exposedPort);
        }
        hostConfig.withPortBindings(ports);
        return exposedPorts;
    }

    private void configureVolumes(HostConfig hostConfig, List<ResolvedVolumeMount> mounts) {
        if (mounts == null || mounts.isEmpty()) {
            return;
        }
        List<Bind> binds = new ArrayList<>();
        for (ResolvedVolumeMount mount : mounts) {
            AccessMode accessMode = mount.accessMode() == VolumeMount.AccessMode.READ_ONLY ? AccessMode.ro : AccessMode.rw;
            binds.add(new Bind(mount.hostPath(), new Volume(mount.containerPath()), accessMode));
        }
        hostConfig.withBinds(binds);
    }

    private String effectiveNetworkName(TerminalProperties.Docker docker, ContainerNetworkOptions networkOptions) {
        if (networkOptions != null && networkOptions.hasNetwork()) {
            return networkOptions.networkName().trim();
        }
        return docker.getNetwork();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void run(Runnable action, String actionName, String containerName) throws IOException {
        try {
            action.run();
        } catch (RuntimeException exception) {
            throw new IOException("Docker API operation failed. action=%s containerName=%s"
                    .formatted(actionName, containerName), exception);
        }
    }
}
