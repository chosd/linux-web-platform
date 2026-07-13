package com.example.linuxterminal.domains.network.service;

import com.example.linuxterminal.domains.network.dto.ContainerNetworkDashboardResponse;
import com.example.linuxterminal.domains.network.dto.NetworkResponse;
import com.example.linuxterminal.domains.network.dto.PortBinding;
import com.example.linuxterminal.domains.network.dto.PortMappingResponse;
import com.example.linuxterminal.global.docker.DockerNetworkRepository;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class DockerNetworkServiceImpl implements DockerNetworkService {

    private static final int FIRST_UNPRIVILEGED_PORT = 1024;
    private static final int LAST_PORT = 65535;

    private final DockerNetworkRepository dockerNetworkRepository;

    public DockerNetworkServiceImpl(DockerNetworkRepository dockerNetworkRepository) {
        this.dockerNetworkRepository = dockerNetworkRepository;
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
        return dockerNetworkRepository.listPortMappings(containerName);
    }

    @Override
    public ContainerNetworkDashboardResponse dashboard(String containerName) throws IOException {
        return new ContainerNetworkDashboardResponse(
                containerName,
                dockerNetworkRepository.listContainerNetworks(containerName),
                listPortMappings(containerName));
    }

    @Override
    public NetworkResponse createNetwork(String networkName) throws IOException {
        String normalizedName = normalizeNetworkName(networkName);
        return dockerNetworkRepository.createNetwork(normalizedName);
    }

    @Override
    public List<NetworkResponse> listNetworks() throws IOException {
        return dockerNetworkRepository.listBridgeNetworks();
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
        return dockerNetworkRepository.connectNetwork(normalizedNetworkName, containerName);
    }

    public NetworkResponse disconnectContainer(String containerName, String networkName) throws IOException {
        String normalizedNetworkName = normalizeNetworkName(networkName);
        return dockerNetworkRepository.disconnectNetwork(normalizedNetworkName, containerName);
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

}
