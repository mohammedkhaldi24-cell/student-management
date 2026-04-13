package com.pfe.gestionetudiant.util;

import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Helpers UI pour afficher les metadonnees de documents dans Thymeleaf.
 */
public final class FileUiUtils {

    private FileUiUtils() {
    }

    public static String fileName(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return "Aucun fichier";
        }
        try {
            return Paths.get(filePath).getFileName().toString();
        } catch (Exception ex) {
            return filePath;
        }
    }

    public static String extension(String filePath) {
        String name = fileName(filePath);
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    public static String iconClass(String filePath) {
        String ext = extension(filePath);
        return switch (ext) {
            case "pdf" -> "bi-file-earmark-pdf-fill text-danger";
            case "doc", "docx" -> "bi-file-earmark-word-fill text-primary";
            case "xls", "xlsx", "csv" -> "bi-file-earmark-excel-fill text-success";
            case "ppt", "pptx" -> "bi-file-earmark-ppt-fill text-warning";
            case "png", "jpg", "jpeg", "gif", "webp", "bmp" -> "bi-file-earmark-image-fill text-info";
            case "zip", "rar", "7z", "tar", "gz" -> "bi-file-earmark-zip-fill text-secondary";
            case "txt", "md", "log" -> "bi-file-earmark-text-fill text-muted";
            default -> "bi-file-earmark-fill text-muted";
        };
    }

    public static String readableSize(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return "-";
        }
        try {
            Path path = Paths.get(filePath).normalize();
            if (!Files.exists(path)) {
                return "introuvable";
            }
            long size = Files.size(path);
            if (size < 1024) {
                return size + " B";
            }
            double kb = size / 1024.0;
            if (kb < 1024) {
                return String.format(Locale.ROOT, "%.1f KB", kb);
            }
            double mb = kb / 1024.0;
            if (mb < 1024) {
                return String.format(Locale.ROOT, "%.1f MB", mb);
            }
            double gb = mb / 1024.0;
            return String.format(Locale.ROOT, "%.2f GB", gb);
        } catch (Exception ex) {
            return "-";
        }
    }
}
