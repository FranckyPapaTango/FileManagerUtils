package com.rafaros.filemanagerutils.service;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.List;

public class FileExtensionService {

    private final MessageService messageService = new MessageService();

    /* =========================================================
       EXTENSION / CONVERSION SÉCURISÉE
       ========================================================= */
    public boolean changeSingleFileExtension(File file, String newExtension) {
        if (file == null || !file.exists() || newExtension == null || newExtension.isEmpty()) return false;

        newExtension = newExtension.replaceFirst("^\\.", "").toLowerCase();
        String srcExt = getFileExtension(file).toLowerCase();
        File outputFile = new File(file.getParentFile(), getFileNameWithoutExtension(file) + "." + newExtension);

        try {
            /* ---------- PNG → JPG ---------- */
            if (srcExt.equals(".png") && newExtension.equals("jpg")) {
                return convertToJpg(file, outputFile);
            }

            /* ---------- PNG → WEBP et HEIC / JPG / JPEG ---------- */
            boolean isHeic = isActuallyHeic(file) || srcExt.equals(".heic") || srcExt.equals(".jpg") || srcExt.equals(".jpeg");
            if (isHeic) {
                if (convertWithImageMagick(file, outputFile)) return true;
                return convertWithHeifDec(file, outputFile);
            }

            /* ---------- AUTRES → RENOMMAGE SIMPLE ---------- */
            if (!file.toPath().equals(outputFile.toPath())) {
                Files.move(file.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /* =========================================================
       CONVERSIONS
       ========================================================= */
    private boolean convertWithImageMagick(File inputFile, File outputFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "magick",
                    inputFile.getAbsolutePath(),
                    "-auto-orient",
                    "-colorspace", "sRGB",
                    "-depth", "8",
                    "-alpha", "remove",
                    "-sampling-factor", "4:2:0",
                    "-strip",
                    "-interlace", "Plane",
                    "-quality", "80",
                    outputFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line); // logging des erreurs / warnings
                }
            }

            return process.waitFor() == 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean convertWithHeifDec(File inputFile, File outputFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "heif-dec.exe",
                    inputFile.getAbsolutePath(),
                    outputFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean convertToJpg(File inputFile, File outputFile) {
        try {
            BufferedImage src = ImageIO.read(inputFile);
            if (src == null) return false;

            BufferedImage jpg = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = jpg.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, src.getWidth(), src.getHeight());
            g.drawImage(src, 0, 0, null);
            g.dispose();

            return ImageIO.write(jpg, "jpg", outputFile);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /* =========================================================
       HEIC DÉTECTION
       ========================================================= */
    public boolean isActuallyHeic(File file) {
        if (file == null || !file.exists()) return false;
        String ext = getFileExtension(file).toLowerCase();
        if (ext.equals(".heic")) return true;
        if (ext.equals(".jpg") || ext.equals(".jpeg")) {
            try {
                String mime = Files.probeContentType(file.toPath());
                return "image/heic".equals(mime);
            } catch (IOException ignored) {}
        }
        return false;
    }

    /* =========================================================
       UI HANDLER
       ========================================================= */
    public void handleProceed(List<File> selectedFiles, TextField extensionField, ProgressBar progressBar) {
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            messageService.showMessage(Alert.AlertType.WARNING, "No files selected", "Please select files or a folder first.");
            return;
        }

        String newExtension = extensionField.getText();
        if (newExtension == null || newExtension.trim().isEmpty()) {
            messageService.showMessage(Alert.AlertType.WARNING, "Invalid extension", "Please enter a valid extension.");
            return;
        }

        final String normalizedExtension = newExtension.trim().replaceFirst("^\\.", "").toLowerCase();
        progressBar.setProgress(0);
        progressBar.setVisible(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                int total = selectedFiles.size();
                int processed = 0;
                for (File file : selectedFiles) {
                    changeSingleFileExtension(file, normalizedExtension);
                    updateProgress(++processed, total);
                }
                return null;
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        new Thread(task, "file-extension-task").start();
    }

    /* =========================================================
       RENOMMAGE CONTENU SÉCURISÉ
       ========================================================= */
    public File handleRenameContent(File selectedDirectory,
                                    boolean gatherInContainer,
                                    String containerName) {

        if (selectedDirectory == null) {
            messageService.showMessage(Alert.AlertType.ERROR, "Error", "No directory selected");
            return null;
        }

        File containerDirectory = null;
        if (gatherInContainer) {
            if (containerName == null || containerName.trim().isEmpty()) {
                messageService.showMessage(Alert.AlertType.ERROR, "Error", "Container name is empty");
                return null;
            }
            containerDirectory = new File(selectedDirectory, containerName.trim());
            if (containerDirectory.exists()) {
                messageService.showMessage(Alert.AlertType.ERROR, "Error", "Container directory already exists");
                return null;
            }
            containerDirectory.mkdirs();
        }

        boolean confirmed = messageService.showConfirmDialog(
                "Are you sure you want to rename the content of this directory?",
                "Confirmation"
        );
        if (!confirmed) return containerDirectory;

        renameFilesRecursively(selectedDirectory, gatherInContainer, containerDirectory);
        return containerDirectory;
    }

    private void renameFilesRecursively(File directory, boolean gatherInContainer, File containerDirectory) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                renameFilesRecursively(file, gatherInContainer, containerDirectory);
            } else {
                try {
                    String baseName = directory.getName();
                    String ext = getFileExtension(file);
                    Path target = gatherInContainer
                            ? getAvailablePath(containerDirectory.toPath(), baseName + ext)
                            : getAvailablePath(file.toPath().getParent(), baseName + ext);
                    Files.move(file.toPath(), target);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private Path getAvailablePath(Path folder, String filename) {
        Path target = folder.resolve(filename);
        int count = 1;
        String name = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : "";
        while (Files.exists(target)) {
            target = folder.resolve(name + "_" + count + ext);
            count++;
        }
        return target;
    }

    /* =========================================================
       UTILS
       ========================================================= */
    public String getFileExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot);
    }

    public String getFileNameWithoutExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }
}