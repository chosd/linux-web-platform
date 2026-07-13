package com.example.linuxterminal.domains.sftp.service;

import com.example.linuxterminal.domains.container.service.ContainerService;
import com.example.linuxterminal.domains.sftp.dto.ContainerFileEntry;
import com.example.linuxterminal.domains.sftp.service.ContainerFileService;
import com.example.linuxterminal.global.docker.DockerExecRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.stereotype.Service;

@Service
public class DockerContainerFileServiceImpl implements ContainerFileService {

    private final DockerExecRepository dockerExecRepository;
    private final ContainerService containerService;

    public DockerContainerFileServiceImpl(
            DockerExecRepository dockerExecRepository,
            ContainerService containerService
    ) {
        this.dockerExecRepository = dockerExecRepository;
        this.containerService = containerService;
    }

    public List<ContainerFileEntry> listFiles(String userId, String containerName, String rawPath) throws IOException {
        containerService.verifyContainerOwnership(userId, containerName);
        String containerPath = normalizeContainerPath(rawPath);
        DockerExecRepository.ExecResult result = dockerExecRepository.exec(
                containerName,
                "find",
                containerPath,
                "-mindepth",
                "1",
                "-maxdepth",
                "1",
                "-printf",
                "%f\\t%y\\t%s\\t%T@\\n");
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
        DockerExecRepository.ExecResult testResult = dockerExecRepository.exec(containerName, "test", "-f", containerPath);
        if (testResult.exitCode() != 0) {
            throw new IOException("Container file not found or not a regular file. path=" + containerPath);
        }
        return new DownloadFile(
                fileNameOf(containerPath),
                new InputStreamResource(dockerExecRepository.openExecStdoutStream(containerName, "cat", containerPath)));
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
        DockerExecRepository.ExecResult result = dockerExecRepository.exec(
                containerName,
                null,
                multipartFile.getInputStream(),
                "sh",
                "-c",
                "cat > \"$1\"",
                "upload",
                targetPath);
        if (result.exitCode() != 0) {
            throw new IOException("Failed to upload file to container. targetPath=%s stderr=%s"
                    .formatted(targetPath, result.stderr()));
        }
    }

    @Override
    public void createDirectory(String userId, String containerName, String rawParentPath, String directoryName)
            throws IOException {
        containerService.verifyContainerOwnership(userId, containerName);
        String parentPath = normalizeContainerPath(rawParentPath);
        String normalizedName = sanitizeFileName(directoryName);
        runRequired(
                containerName,
                "Failed to create container directory.",
                "mkdir",
                joinContainerPath(parentPath, normalizedName));
    }

    @Override
    public void rename(String userId, String containerName, String rawPath, String newName) throws IOException {
        containerService.verifyContainerOwnership(userId, containerName);
        String sourcePath = normalizeMutablePath(rawPath);
        String targetPath = joinContainerPath(parentPathOf(sourcePath), sanitizeFileName(newName));
        runRequired(
                containerName,
                "Failed to rename container path.",
                "mv",
                sourcePath,
                targetPath);
    }

    @Override
    public void delete(String userId, String containerName, String rawPath) throws IOException {
        containerService.verifyContainerOwnership(userId, containerName);
        String containerPath = normalizeMutablePath(rawPath);
        runRequired(
                containerName,
                "Failed to delete container path.",
                "rm",
                "-rf",
                "--",
                containerPath);
    }

    private void runRequired(String containerName, String message, String... command) throws IOException {
        DockerExecRepository.ExecResult result = dockerExecRepository.exec(containerName, command);
        if (result.exitCode() != 0) {
            throw new IOException(message + " stderr=" + result.stderr());
        }
    }

    private String normalizeMutablePath(String rawPath) throws IOException {
        String path = normalizeContainerPath(rawPath);
        if ("/".equals(path)) {
            throw new IOException("The container root directory cannot be changed.");
        }
        return path;
    }

    private String parentPathOf(String path) {
        int separatorIndex = path.lastIndexOf('/');
        return separatorIndex <= 0 ? "/" : path.substring(0, separatorIndex);
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

}
