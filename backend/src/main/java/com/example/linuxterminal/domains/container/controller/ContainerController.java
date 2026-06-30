package com.example.linuxterminal.domains.container.controller;

import com.example.linuxterminal.domains.container.dto.ResourceLimits;
import com.example.linuxterminal.domains.container.dto.VolumeMount;
import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.domains.network.dto.ContainerNetworkOptions;
import com.example.linuxterminal.domains.network.dto.PortBinding;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/containers")
public class ContainerController {

    private final ContainerService containerService;

    public ContainerController(ContainerService containerService) {
        this.containerService = containerService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<ContainerService.ContainerInfo>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) throws IOException {
        return ResponseEntity.ok(containerService.listContainers(resolveUserId(userId)));
    }

    @GetMapping
    public ResponseEntity<List<ContainerService.ContainerInfo>> findAll(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) throws IOException {
        return ResponseEntity.ok(containerService.listContainers(resolveUserId(userId)));
    }

    @PostMapping("/create")
    public ResponseEntity<ContainerService.ContainerInfo> create(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody CreateContainerRequest request
    ) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(containerService.createContainer(
                        resolveUserId(userId),
                        request.displayName(),
                        request.toResourceLimits(),
                        request.rootPassword(),
                        request.toPortBindings(),
                        request.toVolumeMounts(),
                        request.toNetworkOptions()));
    }

    @PostMapping
    public ResponseEntity<ContainerService.ContainerInfo> createRest(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody CreateContainerRequest request
    ) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(containerService.createContainer(
                        resolveUserId(userId),
                        request.displayName(),
                        request.toResourceLimits(),
                        request.rootPassword(),
                        request.toPortBindings(),
                        request.toVolumeMounts(),
                        request.toNetworkOptions()));
    }

    @PatchMapping("/{containerName}")
    public ResponseEntity<ContainerService.ContainerInfo> update(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String containerName,
            @Valid @RequestBody UpdateContainerRequest request
    ) throws IOException {
        return ResponseEntity.ok(containerService.updateContainer(
                resolveUserId(userId),
                containerName,
                request.displayName(),
                request.toResourceLimits()));
    }

    @DeleteMapping("/{containerName}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String containerName
    ) throws IOException {
        containerService.deleteContainer(resolveUserId(userId), containerName);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/start")
    public ResponseEntity<ContainerService.ContainerInfo> start(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody ContainerRequest request
    ) throws IOException {
        return ResponseEntity.ok(containerService.startContainer(
                resolveUserId(request.userId(), userId),
                request.containerName()));
    }

    @PostMapping("/stop")
    public ResponseEntity<ContainerService.ContainerInfo> stop(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody ContainerRequest request
    ) throws IOException {
        return ResponseEntity.ok(containerService.stopContainerByName(
                resolveUserId(request.userId(), userId),
                request.containerName()));
    }

    @PostMapping("/restart")
    public ResponseEntity<ContainerService.ContainerInfo> restart(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody ContainerRequest request
    ) throws IOException {
        return ResponseEntity.ok(containerService.restartContainerByName(
                resolveUserId(request.userId(), userId),
                request.containerName()));
    }

    private String resolveUserId(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "anonymous";
    }

    public record CreateContainerRequest(
            @NotBlank String displayName,
            String rootPassword,
            @Valid ResourceLimitsRequest resourceLimits,
            @Valid List<PortBinding> portBindings,
            @Valid List<VolumeMount> volumeMounts,
            String networkName,
            String networkAlias
    ) {
        ResourceLimits toResourceLimits() {
            return resourceLimits == null ? null : resourceLimits.toResourceLimits();
        }

        List<PortBinding> toPortBindings() {
            return portBindings == null ? List.of() : portBindings;
        }

        List<VolumeMount> toVolumeMounts() {
            return volumeMounts == null ? List.of() : volumeMounts;
        }

        ContainerNetworkOptions toNetworkOptions() {
            if (networkName == null || networkName.isBlank()) {
                return null;
            }
            return new ContainerNetworkOptions(networkName, networkAlias);
        }
    }

    public record UpdateContainerRequest(
            @NotBlank String displayName,
            @Valid ResourceLimitsRequest resourceLimits
    ) {
        ResourceLimits toResourceLimits() {
            return resourceLimits == null ? null : resourceLimits.toResourceLimits();
        }
    }

    public record ContainerRequest(String userId, @NotBlank String containerName) {
    }

    public record ResourceLimitsRequest(
            @DecimalMin("0.1") @DecimalMax("4.0") Double cpuCores,
            @Min(128) @Max(4096) Integer memoryMb
    ) {
        ResourceLimits toResourceLimits() {
            return new ResourceLimits(cpuCores, memoryMb);
        }
    }
}
