package com.example.linuxterminal.terminal.docker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/containers")
public class ContainerController {

    private final ContainerManagementService containerManagementService;

    public ContainerController(ContainerManagementService containerManagementService) {
        this.containerManagementService = containerManagementService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<ContainerManagementService.ContainerInfo>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) throws IOException {
        return ResponseEntity.ok(containerManagementService.listContainers(resolveUserId(userId)));
    }

    @PostMapping("/create")
    public ResponseEntity<ContainerManagementService.ContainerInfo> create(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody CreateContainerRequest request
    ) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(containerManagementService.createContainer(resolveUserId(userId), request.displayName()));
    }

    @PostMapping("/start")
    public ResponseEntity<ContainerManagementService.ContainerInfo> start(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody ContainerRequest request
    ) throws IOException {
        return ResponseEntity.ok(containerManagementService.startContainer(
                resolveUserId(request.userId(), userId),
                request.containerName()));
    }

    @PostMapping("/stop")
    public ResponseEntity<ContainerManagementService.ContainerInfo> stop(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody ContainerRequest request
    ) throws IOException {
        return ResponseEntity.ok(containerManagementService.stopContainerByName(
                resolveUserId(request.userId(), userId),
                request.containerName()));
    }

    @PostMapping("/restart")
    public ResponseEntity<ContainerManagementService.ContainerInfo> restart(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody ContainerRequest request
    ) throws IOException {
        return ResponseEntity.ok(containerManagementService.restartContainerByName(
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

    public record CreateContainerRequest(@NotBlank String displayName) {
    }

    public record ContainerRequest(String userId, @NotBlank String containerName) {
    }
}
