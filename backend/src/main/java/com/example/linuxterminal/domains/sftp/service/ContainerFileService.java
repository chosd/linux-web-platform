package com.example.linuxterminal.domains.sftp.service;

import com.example.linuxterminal.domains.sftp.dto.ContainerFileEntry;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;

public interface ContainerFileService {

    List<ContainerFileEntry> listFiles(String userId, String containerName, String rawPath) throws IOException;

    DownloadFile openDownload(String userId, String containerName, String rawPath) throws IOException;

    void uploadFile(String userId, String containerName, String rawTargetDirectory, MultipartFile multipartFile)
            throws IOException;

    record DownloadFile(String fileName, InputStreamResource resource) {
    }
}
