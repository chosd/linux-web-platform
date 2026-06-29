package com.example.linuxterminal.terminal.docker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ContainerMetadataRepository {

    private static final TypeReference<List<ContainerRecord>> CONTAINER_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path storageDir = Path.of("data", "containers");

    public ContainerMetadataRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized List<ContainerRecord> findByUserId(String userId) throws IOException {
        return readUserContainers(userId);
    }

    public synchronized Optional<ContainerRecord> findByUserIdAndContainerName(
            String userId,
            String containerName
    ) throws IOException {
        return readUserContainers(userId).stream()
                .filter(container -> container.containerName().equals(containerName))
                .findFirst();
    }

    public synchronized void save(ContainerRecord containerRecord) throws IOException {
        List<ContainerRecord> containers = new ArrayList<>(readUserContainers(containerRecord.userId()));
        containers.add(containerRecord);
        writeUserContainers(containerRecord.userId(), containers);
    }

    public synchronized ContainerRecord updateDisplayName(
            String userId,
            String containerName,
            String displayName
    ) throws IOException {
        List<ContainerRecord> containers = new ArrayList<>(readUserContainers(userId));
        for (int index = 0; index < containers.size(); index++) {
            ContainerRecord container = containers.get(index);
            if (container.containerName().equals(containerName)) {
                ContainerRecord updated = new ContainerRecord(
                        container.userId(),
                        container.containerName(),
                        displayName,
                        container.createdAt());
                containers.set(index, updated);
                writeUserContainers(userId, containers);
                return updated;
            }
        }
        throw new IOException("Container not found. userId=%s containerName=%s".formatted(userId, containerName));
    }

    public synchronized boolean deleteByUserIdAndContainerName(String userId, String containerName) throws IOException {
        List<ContainerRecord> containers = new ArrayList<>(readUserContainers(userId));
        boolean removed = containers.removeIf(container -> container.containerName().equals(containerName));
        if (removed) {
            writeUserContainers(userId, containers);
        }
        return removed;
    }

    private List<ContainerRecord> readUserContainers(String userId) throws IOException {
        Path userFile = userFile(userId);
        if (!Files.isRegularFile(userFile)) {
            return List.of();
        }
        return objectMapper.readValue(userFile.toFile(), CONTAINER_LIST_TYPE);
    }

    private Path userFile(String userId) {
        String fileName = userId.toLowerCase().replaceAll("[^a-z0-9_.-]", "-");
        if (fileName.isBlank()) {
            fileName = "anonymous";
        }
        return storageDir.resolve(fileName + ".json");
    }

    private void writeUserContainers(String userId, List<ContainerRecord> containers) throws IOException {
        Files.createDirectories(storageDir);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(userFile(userId).toFile(), containers);
    }
}
