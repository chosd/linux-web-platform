package com.example.linuxterminal.global.docker;

import com.example.linuxterminal.domains.container.dto.ResourceLimits;
import com.example.linuxterminal.domains.network.dto.ContainerNetworkOptions;
import com.example.linuxterminal.domains.network.dto.PortBinding;
import com.example.linuxterminal.global.config.TerminalProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DockerCommandFactory {

    private final TerminalProperties properties;

    public DockerCommandFactory(TerminalProperties properties) {
        this.properties = properties;
    }

    public List<String> runDetachedCommand(String containerName) {
        return runDetachedCommand(containerName, new ResourceLimits(
                Double.valueOf(properties.getDocker().getCpus()),
                parseMemoryMb(properties.getDocker().getMemory())));
    }

    public List<String> runDetachedCommand(String containerName, ResourceLimits resourceLimits) {
        return runDetachedCommand(containerName, resourceLimits, List.of());
    }

    public List<String> runDetachedCommand(
            String containerName,
            ResourceLimits resourceLimits,
            List<PortBinding> portBindings
    ) {
        return runDetachedCommand(containerName, resourceLimits, portBindings, null);
    }

    public List<String> runDetachedCommand(
            String containerName,
            ResourceLimits resourceLimits,
            List<PortBinding> portBindings,
            ContainerNetworkOptions networkOptions
    ) {
        TerminalProperties.Docker docker = properties.getDocker();
        List<String> command = new ArrayList<>();
        command.add(docker.getExecutable());
        command.add("run");
        command.add("-d");
        command.add("--name");
        command.add(containerName);
        for (PortBinding portBinding : safePortBindings(portBindings)) {
            command.add("-p");
            command.add("%d:%d/%s".formatted(
                    portBinding.hostPort(),
                    portBinding.containerPort(),
                    portBinding.protocol().name().toLowerCase()));
        }
        command.add("--cpus=" + resourceLimits.cpuCores());
        command.add("--memory=" + resourceLimits.memoryMb() + "m");
        command.add("--pids-limit=" + docker.getPidsLimit());
        command.add("--network=" + effectiveNetworkName(docker, networkOptions));
        if (networkOptions != null && networkOptions.hasNetwork() && hasText(networkOptions.networkAlias())) {
            /*
             * Docker's embedded DNS works on user-defined bridge networks.
             * The container name is already resolvable inside that network, and this alias adds a stable service name.
             *
             * Example:
             * - Create DB container with containerName "project1_db_container" and networkAlias "db".
             * - Create Python app container on the same network.
             * - The Python app can connect to "db:3306" without knowing the DB container IP.
             *
             * Docker-java equivalent:
             * CreateContainerCmd.withName(containerName)
             *     .withHostConfig(HostConfig.newHostConfig().withNetworkMode(networkName))
             *     .withNetworkingConfig(new NetworkingConfig()
             *         .withEndpointsConfig(Map.of(networkName,
             *             new EndpointConfig().withAliases("db"))));
             */
            command.add("--network-alias");
            command.add(networkOptions.networkAlias().trim());
        }
        command.add("--user");
        command.add(docker.getUser());
        command.add("--workdir");
        command.add(docker.getWorkdir());
        command.add(properties.getImage());
        command.add("tail");
        command.add("-f");
        command.add("/dev/null");
        return command;
    }

    public List<String> containerPortCommand(String containerName) {
        return List.of(properties.getDocker().getExecutable(), "port", containerName);
    }

    public List<String> inspectContainerNetworksCommand(String containerName) {
        return List.of(
                properties.getDocker().getExecutable(),
                "inspect",
                "-f",
                "{{json .NetworkSettings.Networks}}",
                containerName);
    }

    public List<String> createBridgeNetworkCommand(String networkName) {
        return List.of(properties.getDocker().getExecutable(), "network", "create", "--driver", "bridge", networkName);
    }

    public List<String> connectNetworkCommand(String networkName, String containerName) {
        return List.of(properties.getDocker().getExecutable(), "network", "connect", networkName, containerName);
    }

    public List<String> disconnectNetworkCommand(String networkName, String containerName) {
        return List.of(properties.getDocker().getExecutable(), "network", "disconnect", networkName, containerName);
    }

    public List<String> inspectNetworkCommand(String networkName) {
        return List.of(properties.getDocker().getExecutable(), "network", "inspect", networkName);
    }

    public List<String> updateResourceLimitsCommand(String containerName, ResourceLimits resourceLimits) {
        return List.of(
                properties.getDocker().getExecutable(),
                "update",
                "--cpus=" + resourceLimits.cpuCores(),
                "--memory=" + resourceLimits.memoryMb() + "m",
                containerName);
    }

    public List<String> setRootPasswordCommand(String containerName) {
        return List.of(
                properties.getDocker().getExecutable(),
                "exec",
                "-i",
                "-u",
                "root",
                containerName,
                "chpasswd");
    }

    public List<String> statsJsonCommand(String containerName) {
        return List.of(
                properties.getDocker().getExecutable(),
                "stats",
                "--no-stream",
                "--format",
                "{{json .}}",
                containerName);
    }

    public List<String> execCommand(String containerName) {
        List<String> command = new ArrayList<>();
        command.add(properties.getDocker().getExecutable());
        command.add("exec");
        command.add("-i");
        command.add(containerName);
        command.addAll(properties.getDocker().getCommand());
        return command;
    }

    public List<String> listFilesCommand(String containerName, String containerPath) {
        return List.of(
                properties.getDocker().getExecutable(),
                "exec",
                containerName,
                "find",
                containerPath,
                "-mindepth",
                "1",
                "-maxdepth",
                "1",
                "-printf",
                "%f\\t%y\\t%s\\t%T@\\n");
    }

    public List<String> testRegularFileCommand(String containerName, String containerPath) {
        return List.of(
                properties.getDocker().getExecutable(),
                "exec",
                containerName,
                "test",
                "-f",
                containerPath);
    }

    public List<String> readFileCommand(String containerName, String containerPath) {
        return List.of(
                properties.getDocker().getExecutable(),
                "exec",
                containerName,
                "cat",
                containerPath);
    }

    public List<String> copyFileToContainerCommand(String sourcePath, String containerName, String containerPath) {
        return List.of(
                properties.getDocker().getExecutable(),
                "cp",
                sourcePath,
                containerName + ":" + containerPath);
    }

    public List<String> inspectStatusCommand(String containerName) {
        return List.of(
                properties.getDocker().getExecutable(),
                "inspect",
                "-f",
                "{{.State.Status}}",
                containerName);
    }

    public List<String> startCommand(String containerName) {
        return List.of(properties.getDocker().getExecutable(), "start", containerName);
    }

    public List<String> stopCommand(String containerName) {
        return List.of(properties.getDocker().getExecutable(), "stop", containerName);
    }

    public List<String> restartCommand(String containerName) {
        return List.of(properties.getDocker().getExecutable(), "restart", containerName);
    }

    public List<String> removeCommand(String containerName) {
        return List.of(properties.getDocker().getExecutable(), "rm", "-f", containerName);
    }

    private Integer parseMemoryMb(String memory) {
        if (memory == null || memory.isBlank()) {
            return 256;
        }
        String normalized = memory.trim().toLowerCase();
        if (normalized.endsWith("g")) {
            return (int) (Double.parseDouble(normalized.substring(0, normalized.length() - 1)) * 1024);
        }
        if (normalized.endsWith("m")) {
            return (int) Double.parseDouble(normalized.substring(0, normalized.length() - 1));
        }
        return (int) Double.parseDouble(normalized);
    }

    private List<PortBinding> safePortBindings(List<PortBinding> portBindings) {
        return portBindings == null ? Collections.emptyList() : portBindings;
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

}
