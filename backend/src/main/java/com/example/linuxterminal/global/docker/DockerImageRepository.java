package com.example.linuxterminal.global.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Image;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Repository;

@Repository
public class DockerImageRepository {

    private static final long CALLBACK_TIMEOUT_SECONDS = 30L;

    private final DockerClient dockerClient;

    public DockerImageRepository(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public List<DockerImageInfo> listImages() throws IOException {
        try {
            return dockerClient.listImagesCmd().withShowAll(true).exec().stream()
                    .map(this::toDockerImageInfo)
                    .sorted(Comparator.comparing(DockerImageInfo::primaryTag, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (RuntimeException exception) {
            throw new IOException("Docker API failed to list images.", exception);
        }
    }

    public boolean imageExists(String imageName) throws IOException {
        return listImages().stream().anyMatch(image -> image.tags().contains(imageName));
    }

    public void pullImage(String imageName) throws IOException {
        try {
            dockerClient.pullImageCmd(imageName).exec(new PullImageResultCallback())
                    .awaitCompletion(CALLBACK_TIMEOUT_SECONDS * 10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while pulling Docker image.", exception);
        } catch (RuntimeException exception) {
            throw new IOException("Docker API failed to pull image: " + imageName, exception);
        }
    }

    private DockerImageInfo toDockerImageInfo(Image image) {
        List<String> tags = image.getRepoTags() == null
                ? List.of()
                : List.of(image.getRepoTags()).stream().filter(tag -> !"<none>:<none>".equals(tag)).toList();
        return new DockerImageInfo(
                image.getId(),
                tags,
                tags.isEmpty() ? "<untagged>" : tags.getFirst(),
                image.getSize() == null ? 0L : image.getSize(),
                image.getCreated() == null ? 0L : image.getCreated());
    }

    public record DockerImageInfo(String id, List<String> tags, String primaryTag, long sizeBytes, long createdEpochSeconds) {
    }
}
