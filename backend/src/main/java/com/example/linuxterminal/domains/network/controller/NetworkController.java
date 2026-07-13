package com.example.linuxterminal.domains.network.controller;

import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.domains.network.dto.ContainerNetworkDashboardResponse;
import com.example.linuxterminal.domains.network.dto.ContainerNetworkRequest;
import com.example.linuxterminal.domains.network.dto.CreateNetworkRequest;
import com.example.linuxterminal.domains.network.dto.NetworkResponse;
import com.example.linuxterminal.domains.network.dto.PortMappingResponse;
import com.example.linuxterminal.domains.network.service.DockerNetworkService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class NetworkController {

    private final DockerNetworkService dockerNetworkService;
    private final ContainerService containerService;

    public NetworkController(DockerNetworkService dockerNetworkService, ContainerService containerService) {
        this.dockerNetworkService = dockerNetworkService;
        this.containerService = containerService;
    }

    @GetMapping("/containers/{containerName}/ports")
    public ResponseEntity<List<PortMappingResponse>> listPorts(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String containerName
    ) throws IOException {
        containerService.verifyContainerOwnership(resolveUserId(userId), containerName);
        return ResponseEntity.ok(dockerNetworkService.listPortMappings(containerName));
    }

    @GetMapping("/containers/{containerName}/network")
    public ResponseEntity<ContainerNetworkDashboardResponse> dashboard(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String containerName
    ) throws IOException {
        containerService.verifyContainerOwnership(resolveUserId(userId), containerName);
        return ResponseEntity.ok(dockerNetworkService.dashboard(containerName));
    }

    @PostMapping("/networks")
    public ResponseEntity<NetworkResponse> createNetwork(@Valid @RequestBody CreateNetworkRequest request)
            throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(dockerNetworkService.createNetwork(request.name()));
    }

    @GetMapping("/networks")
    public ResponseEntity<List<NetworkResponse>> listNetworks() throws IOException {
        return ResponseEntity.ok(dockerNetworkService.listNetworks());
    }

    @PostMapping("/containers/{containerName}/networks")
    public ResponseEntity<NetworkResponse> connectNetwork(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String containerName,
            @Valid @RequestBody ContainerNetworkRequest request
    ) throws IOException {
        containerService.verifyContainerOwnership(resolveUserId(userId), containerName);
        return ResponseEntity.ok(dockerNetworkService.connectContainerToNetwork(containerName, request.networkName()));
    }

    @DeleteMapping("/containers/{containerName}/networks")
    public ResponseEntity<NetworkResponse> disconnectNetwork(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String containerName,
            @Valid @RequestBody ContainerNetworkRequest request
    ) throws IOException {
        containerService.verifyContainerOwnership(resolveUserId(userId), containerName);
        return ResponseEntity.ok(dockerNetworkService.disconnectContainerFromNetwork(containerName, request.networkName()));
    }

    private String resolveUserId(String userId) {
        return userId == null || userId.isBlank() ? "anonymous" : userId;
    }
}
