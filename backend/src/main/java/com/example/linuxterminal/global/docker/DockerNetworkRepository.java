package com.example.linuxterminal.global.docker;

import com.example.linuxterminal.domains.network.dto.ContainerNetworkResponse;
import com.example.linuxterminal.domains.network.dto.NetworkResponse;
import com.example.linuxterminal.domains.network.dto.PortBinding;
import com.example.linuxterminal.domains.network.dto.PortMappingResponse;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Ports;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Repository;

@Repository
public class DockerNetworkRepository {

    private final DockerClient dockerClient;

    public DockerNetworkRepository(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public NetworkResponse createNetwork(String networkName) throws IOException {
        try {
            dockerClient.createNetworkCmd()
                    .withName(networkName)
                    .withDriver("bridge")
                    .withCheckDuplicate(true)
                    .exec();
            return inspectNetwork(networkName);
        } catch (RuntimeException exception) {
            throw new IOException("Failed to create Docker network.", exception);
        }
    }

    public List<NetworkResponse> listBridgeNetworks() throws IOException {
        try {
            return dockerClient.listNetworksCmd()
                    .withFilter("driver", List.of("bridge"))
                    .exec()
                    .stream()
                    .map(this::toNetworkResponse)
                    .sorted(Comparator.comparing(NetworkResponse::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (RuntimeException exception) {
            throw new IOException("Failed to list Docker networks.", exception);
        }
    }

    public NetworkResponse connectNetwork(String networkName, String containerName) throws IOException {
        try {
            dockerClient.connectToNetworkCmd()
                    .withNetworkId(networkName)
                    .withContainerId(containerName)
                    .withContainerNetwork(new ContainerNetwork())
                    .exec();
            return inspectNetwork(networkName);
        } catch (RuntimeException exception) {
            if (!containsMessage(exception, "already exists")) {
                throw new IOException("Failed to connect Docker network.", exception);
            }
            return inspectNetwork(networkName);
        }
    }

    public NetworkResponse disconnectNetwork(String networkName, String containerName) throws IOException {
        try {
            dockerClient.disconnectFromNetworkCmd()
                    .withNetworkId(networkName)
                    .withContainerId(containerName)
                    .exec();
            return inspectNetwork(networkName);
        } catch (RuntimeException exception) {
            throw new IOException("Failed to disconnect Docker network.", exception);
        }
    }

    public NetworkResponse inspectNetwork(String networkName) throws IOException {
        try {
            return toNetworkResponse(dockerClient.inspectNetworkCmd().withNetworkId(networkName).exec());
        } catch (RuntimeException exception) {
            throw new IOException("Failed to inspect Docker network.", exception);
        }
    }

    public NetworkSettings inspectNetworkSettings(String containerName) throws IOException {
        try {
            return dockerClient.inspectContainerCmd(containerName).exec().getNetworkSettings();
        } catch (RuntimeException exception) {
            throw new IOException("Failed to inspect container network settings. containerName=" + containerName, exception);
        }
    }

    public List<PortMappingResponse> listPortMappings(String containerName) throws IOException {
        NetworkSettings networkSettings = inspectNetworkSettings(containerName);
        Ports ports = networkSettings == null ? null : networkSettings.getPorts();
        if (ports == null || ports.getBindings() == null) {
            return List.of();
        }
        List<PortMappingResponse> mappings = new ArrayList<>();
        ports.getBindings().forEach((exposedPort, bindings) -> {
            if (bindings == null) {
                return;
            }
            for (Ports.Binding binding : bindings) {
                Integer hostPort = parseInteger(binding.getHostPortSpec());
                PortBinding.Protocol protocol = "udp".equalsIgnoreCase(exposedPort.getScheme())
                        ? PortBinding.Protocol.UDP
                        : PortBinding.Protocol.TCP;
                mappings.add(new PortMappingResponse(
                        hostPort,
                        exposedPort.getPort(),
                        protocol,
                        binding.getHostIp(),
                        hostPort == null ? null : "http://localhost:" + hostPort));
            }
        });
        return mappings.stream()
                .sorted(Comparator
                        .comparing(PortMappingResponse::containerPort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(PortMappingResponse::hostPort, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    public List<ContainerNetworkResponse> listContainerNetworks(String containerName) throws IOException {
        NetworkSettings networkSettings = inspectNetworkSettings(containerName);
        Map<String, ContainerNetwork> networks = networkSettings == null ? null : networkSettings.getNetworks();
        if (networks == null || networks.isEmpty()) {
            return List.of();
        }
        return networks.entrySet().stream()
                .map(entry -> new ContainerNetworkResponse(
                        entry.getKey(),
                        entry.getValue().getNetworkID(),
                        entry.getValue().getIpAddress(),
                        entry.getValue().getGateway(),
                        entry.getValue().getMacAddress()))
                .sorted(Comparator.comparing(ContainerNetworkResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }

    private NetworkResponse toNetworkResponse(Network network) {
        return new NetworkResponse(network.getName(), network.getId(), network.getDriver(), network.getScope());
    }

    private boolean containsMessage(Throwable throwable, String expected) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
