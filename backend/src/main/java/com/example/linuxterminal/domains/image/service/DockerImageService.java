package com.example.linuxterminal.domains.image.service;

import com.example.linuxterminal.global.docker.DockerImageRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class DockerImageService {

    private final DockerImageRepository dockerImageRepository;

    public DockerImageService(DockerImageRepository dockerImageRepository) {
        this.dockerImageRepository = dockerImageRepository;
    }

    public List<ImageInfo> listImages() throws IOException {
        return dockerImageRepository.listImages().stream()
                .map(image -> new ImageInfo(
                        image.id(), image.tags(), image.primaryTag(), image.sizeBytes(),
                        Instant.ofEpochSecond(image.createdEpochSeconds())))
                .toList();
    }

    public ImageInfo pullImage(String imageName) throws IOException {
        String normalized = normalizeImageName(imageName);
        dockerImageRepository.pullImage(normalized);
        return listImages().stream()
                .filter(image -> image.tags().contains(normalized))
                .findFirst()
                .orElseThrow(() -> new IOException("Pulled image was not found locally: " + normalized));
    }

    private String normalizeImageName(String imageName) throws IOException {
        if (imageName == null || !imageName.trim().matches("[a-zA-Z0-9][a-zA-Z0-9._/:@-]{0,254}")) {
            throw new IOException("Docker image name is invalid.");
        }
        String normalized = imageName.trim();
        int lastSlash = normalized.lastIndexOf('/');
        int lastColon = normalized.lastIndexOf(':');
        return normalized.contains("@") || lastColon > lastSlash ? normalized : normalized + ":latest";
    }

    public record ImageInfo(String id, List<String> tags, String primaryTag, long sizeBytes, Instant createdAt) {
    }
}
