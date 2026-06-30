package com.example.linuxterminal.domains.sftp.adapter.in.web;

import com.example.linuxterminal.domains.sftp.application.dto.ContainerFileEntry;
import com.example.linuxterminal.domains.sftp.application.port.in.ContainerFileService;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/containers")
public class ContainerFileController {

    private final ContainerFileService containerFileService;

    public ContainerFileController(ContainerFileService containerFileService) {
        this.containerFileService = containerFileService;
    }

    @GetMapping("/{containerName}/files")
    public ResponseEntity<List<ContainerFileEntry>> listFiles(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String containerName,
            @RequestParam(value = "userId", required = false) String queryUserId,
            @RequestParam(value = "path", defaultValue = "/") String path
    ) throws IOException {
        return ResponseEntity.ok(containerFileService.listFiles(resolveUserId(queryUserId, userId), containerName, path));
    }

    @GetMapping("/{containerName}/files/download")
    public ResponseEntity<Resource> downloadFile(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String containerName,
            @RequestParam(value = "userId", required = false) String queryUserId,
            @RequestParam("path") String path
    ) throws IOException {
        ContainerFileService.DownloadFile downloadFile =
                containerFileService.openDownload(resolveUserId(queryUserId, userId), containerName, path);
        InputStreamResource resource = downloadFile.resource();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(downloadFile.fileName()))
                .body(resource);
    }

    @PostMapping(path = "/{containerName}/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadFile(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String containerName,
            @RequestParam(value = "userId", required = false) String queryUserId,
            @RequestParam(value = "path", defaultValue = "/") String path,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        containerFileService.uploadFile(resolveUserId(queryUserId, userId), containerName, path, file);
        return ResponseEntity.noContent().build();
    }

    private String resolveUserId(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "anonymous";
    }

    private String contentDisposition(String fileName) {
        return ContentDisposition.attachment()
                .filename(fileName)
                .build()
                .toString();
    }
}
