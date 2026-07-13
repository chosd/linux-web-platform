package com.example.linuxterminal.domains.image.controller;

import com.example.linuxterminal.domains.image.service.DockerImageService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final DockerImageService dockerImageService;

    public ImageController(DockerImageService dockerImageService) {
        this.dockerImageService = dockerImageService;
    }

    @GetMapping
    public ResponseEntity<List<DockerImageService.ImageInfo>> list() throws IOException {
        return ResponseEntity.ok(dockerImageService.listImages());
    }

    @PostMapping("/pull")
    public ResponseEntity<DockerImageService.ImageInfo> pull(@Valid @RequestBody PullImageRequest request)
            throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(dockerImageService.pullImage(request.imageName()));
    }

    public record PullImageRequest(@NotBlank String imageName) {
    }
}
