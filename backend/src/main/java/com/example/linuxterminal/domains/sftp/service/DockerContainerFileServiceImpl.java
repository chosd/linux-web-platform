package com.example.linuxterminal.domains.sftp.service;

import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.domains.sftp.dto.ContainerFileEntry;
import com.example.linuxterminal.domains.sftp.service.ContainerFileService;
import com.example.linuxterminal.global.docker.DockerCommandFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;

public class DockerContainerFileServiceImpl implements ContainerFileService {

    private final DockerCommandFactory dockerCommandFactory;
    private final ContainerService containerService;

    public DockerContainerFileServiceImpl(
            DockerCommandFactory dockerCommandFactory,
            ContainerService containerService
    ) {
        this.dockerCommandFactory = dockerCommandFactory;
        this.containerService = containerService;
    }

    public List<ContainerFileEntry> listFiles(String userId, String containerName, String rawPath) throws IOException {
        containerService.verifyContainerOwnership(userId, containerName);
        String containerPath = normalizeContainerPath(rawPath);
        CommandResult result = run(dockerCommandFactory.listFilesCommand(containerName, containerPath));
        if (result.exitCode() != 0) {
            throw new IOException("Failed to list container files. path=%s stderr=%s"
                    .formatted(containerPath, result.stderr()));
        }
        return result.stdout().lines()
                .filter(line -> !line.isBlank())
                .map(this::parseFileEntry)
                .sorted(Comparator
                        .comparing(ContainerFileEntry::type)
                        .thenComparing(ContainerFileEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public DownloadFile openDownload(String userId, String containerName, String rawPath) throws IOException {
        containerService.verifyContainerOwnership(userId, containerName);
        String containerPath = normalizeContainerPath(rawPath);
        CommandResult testResult = run(dockerCommandFactory.testRegularFileCommand(containerName, containerPath));
        if (testResult.exitCode() != 0) {
            throw new IOException("Container file not found or not a regular file. path=" + containerPath);
        }
        Process process = new ProcessBuilder(dockerCommandFactory.readFileCommand(containerName, containerPath)).start();
        return new DownloadFile(fileNameOf(containerPath), new InputStreamResource(process.getInputStream()));
    }

    public void uploadFile(
            String userId,
            String containerName,
            String rawTargetDirectory,
            MultipartFile multipartFile
    ) throws IOException {
        containerService.verifyContainerOwnership(userId, containerName);
        String targetDirectory = normalizeContainerPath(rawTargetDirectory);
        String fileName = sanitizeFileName(multipartFile.getOriginalFilename());
        String targetPath = joinContainerPath(targetDirectory, fileName);
        Path tempFile = Files.createTempFile("container-upload-", "-" + fileName);
        try {
            multipartFile.transferTo(tempFile);
            CommandResult result = run(dockerCommandFactory.copyFileToContainerCommand(
                    tempFile.toString(),
                    containerName,
                    targetPath));
            if (result.exitCode() != 0) {
                throw new IOException("Failed to upload file to container. targetPath=%s stderr=%s"
                        .formatted(targetPath, result.stderr()));
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private CommandResult run(List<String> command) throws IOException {
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

    private ContainerFileEntry parseFileEntry(String line) {
        String[] parts = line.split("\\t", 4);
        String name = parts.length > 0 ? parts[0] : "";
        String rawType = parts.length > 1 ? parts[1] : "f";
        long size = parts.length > 2 ? Long.parseLong(parts[2]) : 0L;
        double epochSeconds = parts.length > 3 ? Double.parseDouble(parts[3]) : 0.0;
        return new ContainerFileEntry(
                name,
                "d".equals(rawType) ? ContainerFileEntry.FileType.DIRECTORY : ContainerFileEntry.FileType.FILE,
                size,
                Instant.ofEpochMilli((long) (epochSeconds * 1000)));
    }

    private String normalizeContainerPath(String rawPath) throws IOException {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        if (rawPath.contains("\0")) {
            throw new IOException("Invalid path.");
        }
        String normalized = rawPath.startsWith("/") ? rawPath : "/" + rawPath;
        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new IOException("Path traversal is not allowed.");
            }
            segments.add(segment);
        }
        return segments.isEmpty() ? "/" : "/" + String.join("/", segments);
    }

    private String sanitizeFileName(String rawFileName) throws IOException {
        if (rawFileName == null || rawFileName.isBlank()) {
            throw new IOException("Upload file name is required.");
        }
        String fileName = Path.of(rawFileName).getFileName().toString();
        if (fileName.isBlank()
                || ".".equals(fileName)
                || "..".equals(fileName)
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.contains("\0")) {
            throw new IOException("Invalid upload file name.");
        }
        return fileName;
    }

    private String joinContainerPath(String directoryPath, String fileName) {
        if ("/".equals(directoryPath)) {
            return "/" + fileName;
        }
        return directoryPath + "/" + fileName;
    }

    private String fileNameOf(String containerPath) {
        int lastSlashIndex = containerPath.lastIndexOf('/');
        if (lastSlashIndex < 0 || lastSlashIndex == containerPath.length() - 1) {
            return "download";
        }
        return containerPath.substring(lastSlashIndex + 1);
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
