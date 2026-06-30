package com.example.linuxterminal.domains.network.service;

import com.example.linuxterminal.domains.network.dto.ContainerNetworkDashboardResponse;
import com.example.linuxterminal.domains.network.dto.NetworkResponse;
import com.example.linuxterminal.domains.network.dto.PortBinding;
import com.example.linuxterminal.domains.network.dto.PortMappingResponse;
import java.io.IOException;
import java.util.List;

public interface DockerNetworkService {

    NetworkResponse createNetwork(String networkName) throws IOException;

    NetworkResponse connectContainerToNetwork(String containerId, String networkName) throws IOException;

    NetworkResponse disconnectContainerFromNetwork(String containerId, String networkName) throws IOException;

    void validatePortBindings(List<PortBinding> portBindings) throws IOException;

    List<PortMappingResponse> listPortMappings(String containerName) throws IOException;

    ContainerNetworkDashboardResponse dashboard(String containerName) throws IOException;
}
