package com.pfe.gestionetudiant.api;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class MobileFileResponseBuilder {

    private MobileFileResponseBuilder() {
    }

    public static ResponseEntity<Resource> asDownload(Resource resource, String filePath) {
        String contentType = "application/octet-stream";
        String fileName = "document";

        try {
            if (StringUtils.hasText(filePath)) {
                Path path = Paths.get(filePath);
                fileName = path.getFileName().toString();
                String detected = Files.probeContentType(path);
                if (detected != null) {
                    contentType = detected;
                }
            }
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }
}
