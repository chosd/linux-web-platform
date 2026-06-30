package com.example.linuxterminal.domains.network.service;

import com.example.linuxterminal.domains.network.dto.ContainerNetworkDashboardResponse;
import com.example.linuxterminal.domains.network.dto.ContainerNetworkResponse;
import com.example.linuxterminal.domains.network.dto.NetworkResponse;
import com.example.linuxterminal.domains.network.dto.PortBinding;
import com.example.linuxterminal.domains.network.dto.PortMappingResponse;
import com.example.linuxterminal.global.docker.DockerCommandFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DockerNetworkServiceImpl implements DockerNetworkService {

    private static final int FIRST_UNPRIVILEGED_PORT = 1024;
    private static final int LAST_PORT = 65535;

    private final DockerCommandFactory dockerCommandFactory;
    private final ObjectMapper objectMapper;

    public DockerNetworkServiceImpl(DockerCommandFactory dockerCommandFactory, ObjectMapper objectMapper) {
        this.dockerCommandFactory = dockerCommandFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void validatePortBindings(List<PortBinding> portBindings) throws IOException {
        if (portBindings == null || portBindings.isEmpty()) {
            return;
        }

        Set<String> requestedHostPorts = new HashSet<>();
        Set<String> requestedContainerPorts = new HashSet<>();
        for (PortBinding portBinding : portBindings) {
            PortBinding normalized = normalize(portBinding);
            String hostKey = normalized.protocol() + ":" + normalized.hostPort();
            String containerKey = normalized.protocol() + ":" + normalized.containerPort();
            if (!requestedHostPorts.add(hostKey)) {
                throw new IOException("Duplicate host port binding: " + normalized.hostPort());
            }
            if (!requestedContainerPorts.add(containerKey)) {
                throw new IOException("Duplicate container port binding: " + normalized.containerPort());
            }
            validatePortRange(normalized.hostPort(), "Host port");
            validatePortRange(normalized.containerPort(), "Container port");
            ensureUnprivilegedHostPort(normalized.hostPort());
            ensureHostPortAvailable(normalized);
        }
    }

    @Override
    public List<PortMappingResponse> listPortMappings(String containerName) throws IOException {
        CommandResult result = runAllowingFailure(dockerCommandFactory.containerPortCommand(containerName));
        if (result.exitCode() != 0) {
            throw new IOException("Failed to inspect container ports. stderr=" + result.stderr());
        }
        return result.stdout().lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(this::parsePortLine)
                .toList();
    }

    @Override
    public ContainerNetworkDashboardResponse dashboard(String containerName) throws IOException {
        return new ContainerNetworkDashboardResponse(
                containerName,
                listContainerNetworks(containerName),
                listPortMappings(containerName));
    }

    @Override
    public NetworkResponse createNetwork(String networkName) throws IOException {
        String normalizedName = normalizeNetworkName(networkName);
        CommandResult result = runAllowingFailure(dockerCommandFactory.createBridgeNetworkCommand(normalizedName));
        if (result.exitCode() != 0) {
            throw new IOException("Failed to create Docker network. stderr=" + result.stderr());
        }
        return inspectNetwork(normalizedName);
    }

    @Override
    public NetworkResponse connectContainerToNetwork(String containerId, String networkName) throws IOException {
        return connectContainer(containerId, networkName);
    }

    @Override
    public NetworkResponse disconnectContainerFromNetwork(String containerId, String networkName) throws IOException {
        return disconnectContainer(containerId, networkName);
    }

    public NetworkResponse connectContainer(String containerName, String networkName) throws IOException {
        String normalizedNetworkName = normalizeNetworkName(networkName);
        CommandResult result = runAllowingFailure(
                dockerCommandFactory.connectNetworkCommand(normalizedNetworkName, containerName));
        if (result.exitCode() != 0 && !result.stderr().toLowerCase(Locale.ROOT).contains("already exists")) {
            throw new IOException("Failed to connect Docker network. stderr=" + result.stderr());
        }
        return inspectNetwork(normalizedNetworkName);
    }

    public NetworkResponse disconnectContainer(String containerName, String networkName) throws IOException {
        String normalizedNetworkName = normalizeNetworkName(networkName);
        CommandResult result = runAllowingFailure(
                dockerCommandFactory.disconnectNetworkCommand(normalizedNetworkName, containerName));
        if (result.exitCode() != 0) {
            throw new IOException("Failed to disconnect Docker network. stderr=" + result.stderr());
        }
        return inspectNetwork(normalizedNetworkName);
    }

    private List<ContainerNetworkResponse> listContainerNetworks(String containerName) throws IOException {
        CommandResult result = runAllowingFailure(dockerCommandFactory.inspectContainerNetworksCommand(containerName));
        if (result.exitCode() != 0) {
            throw new IOException("Failed to inspect container networks. stderr=" + result.stderr());
        }
        if (result.stdout().isBlank()) {
            return List.of();
        }

        JsonNode root = objectMapper.readTree(result.stdout());
        List<ContainerNetworkResponse> networks = new ArrayList<>();
        root.fields().forEachRemaining(entry -> {
            JsonNode network = entry.getValue();
            networks.add(new ContainerNetworkResponse(
                    entry.getKey(),
                    text(network, "NetworkID"),
                    text(network, "IPAddress"),
                    text(network, "Gateway"),
                    text(network, "MacAddress")));
        });
        return networks;
    }

    private NetworkResponse inspectNetwork(String networkName) throws IOException {
        CommandResult result = runAllowingFailure(dockerCommandFactory.inspectNetworkCommand(networkName));
        if (result.exitCode() != 0) {
            throw new IOException("Failed to inspect Docker network. stderr=" + result.stderr());
        }
        JsonNode root = objectMapper.readTree(result.stdout());
        JsonNode network = root.isArray() && !root.isEmpty() ? root.get(0) : root;
        return new NetworkResponse(
                text(network, "Name"),
                text(network, "Id"),
                text(network, "Driver"),
                text(network, "Scope"));
    }

    private PortMappingResponse parsePortLine(String line) {
        String[] sides = line.split("\\s+->\\s+", 2);
        String[] containerParts = sides[0].split("/", 2);
        Integer containerPort = parseInteger(containerParts[0]);
        PortBinding.Protocol protocol = containerParts.length > 1 && "udp".equalsIgnoreCase(containerParts[1])
                ? PortBinding.Protocol.UDP
                : PortBinding.Protocol.TCP;
        String hostIp = "";
        Integer hostPort = null;
        if (sides.length > 1) {
            int separatorIndex = sides[1].lastIndexOf(':');
            if (separatorIndex >= 0) {
                hostIp = sides[1].substring(0, separatorIndex).replace("[", "").replace("]", "");
                hostPort = parseInteger(sides[1].substring(separatorIndex + 1));
            }
        }
        String url = hostPort == null ? null : "http://localhost:" + hostPort;
        return new PortMappingResponse(hostPort, containerPort, protocol, hostIp, url);
    }

    private PortBinding normalize(PortBinding portBinding) throws IOException {
        if (portBinding == null) {
            throw new IOException("Port binding is required.");
        }
        return new PortBinding(portBinding.hostPort(), portBinding.containerPort(), portBinding.protocol());
    }

    private void validatePortRange(Integer port, String label) throws IOException {
        if (port == null || port < 1 || port > LAST_PORT) {
            throw new IOException(label + " must be between 1 and 65535.");
        }
    }

    private void ensureUnprivilegedHostPort(int hostPort) throws IOException {
        if (hostPort < FIRST_UNPRIVILEGED_PORT) {
            throw new IOException("Host port must be 1024 or higher. Requested: " + hostPort);
        }
    }

    private void ensureHostPortAvailable(PortBinding portBinding) throws IOException {
        if (portBinding.protocol() == PortBinding.Protocol.UDP) {
            ensureUdpPortAvailable(portBinding.hostPort());
            return;
        }
        ensureTcpPortAvailable(portBinding.hostPort());
    }

    private void ensureTcpPortAvailable(int hostPort) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(hostPort, 1, InetAddress.getByName("0.0.0.0"))) {
            serverSocket.setReuseAddress(false);
        } catch (IOException exception) {
            throw new IOException("Host TCP port is already in use: " + hostPort, exception);
        }
    }

    private void ensureUdpPortAvailable(int hostPort) throws IOException {
        try (DatagramSocket datagramSocket = new DatagramSocket(hostPort, InetAddress.getByName("0.0.0.0"))) {
            datagramSocket.setReuseAddress(false);
        } catch (IOException exception) {
            throw new IOException("Host UDP port is already in use: " + hostPort, exception);
        }
    }

    private String normalizeNetworkName(String networkName) throws IOException {
        if (networkName == null || networkName.isBlank()) {
            throw new IOException("Network name is required.");
        }
        String normalized = networkName.trim();
        if (!normalized.matches("[a-zA-Z0-9_.-]{1,63}")) {
            throw new IOException("Network name may contain only letters, numbers, dot, underscore, and hyphen.");
        }
        return normalized;
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

    private String text(JsonNode jsonNode, String fieldName) {
        JsonNode value = jsonNode.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
