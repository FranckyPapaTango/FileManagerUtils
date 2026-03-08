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
import java.util.ArrayList;
import java.util.List;

public class FileExtensionService {

    static {
        ImageIO.setUseCache(false);
    }


    private final MessageService messageService = new MessageService();
    private final CorruptedRepairService corruptedRepairService = new CorruptedRepairService();

    /* =========================================================
       EXTENSION / CONVERSION SÉCURISÉE
       ========================================================= */
    public boolean changeSingleFileExtension(File file, String newExtension, File corruptedDir) {

        if (file == null || !file.exists() || newExtension == null || newExtension.isEmpty()) {
            return false;
        }

        newExtension = newExtension.replaceFirst("^\\.", "").toLowerCase();

        String srcExt = getFileExtension(file).toLowerCase();
        String baseName = getFileNameWithoutExtension(file);

        File parentDir = file.getParentFile();

        boolean sameExtension = srcExt.equals("." + newExtension);

        File outputFile = sameExtension
                ? new File(parentDir, baseName + "_converted." + newExtension)
                : new File(parentDir, baseName + "." + newExtension);

        try {

        /* =========================================
           PNG → JPG
           ========================================= */

            if (srcExt.equals(".png") && newExtension.equals("jpg")) {

                boolean converted = false;

                try {
                    convertToJpg(file);
                    converted = true;
                } catch (IOException ignored) {}

                if (!converted) {

                    File tempFile = new File(parentDir, baseName + "_repaired.png");

                    if (repairImageWithMagick(file, tempFile)) {
                        try {
                            convertToJpg(tempFile);
                            converted = true;
                        } catch (IOException ignored) {}
                    }

                    tempFile.delete();
                }

                if (converted) {
                    Files.deleteIfExists(file.toPath());
                    return true;
                }

                throw new IOException("PNG → JPG conversion failed after repair attempts");
            }

        /* =========================================
           HEIC / JPEG / JPG conversion
           ========================================= */

            boolean isHeic = isActuallyHeic(file)
                    || srcExt.equals(".heic")
                    || srcExt.equals(".jpg")
                    || srcExt.equals(".jpeg");

            if (isHeic) {

                boolean success =
                        convertWithImageMagick(file, outputFile)
                                || convertWithHeifDec(file, outputFile);

                if (success) {

                    if (sameExtension) {
                        Files.deleteIfExists(file.toPath());
                        Files.move(outputFile.toPath(), file.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        Files.deleteIfExists(file.toPath());
                    }

                    return true;
                }

                throw new IOException("HEIC/IM conversion failed");
            }

        /* =========================================
           AUTRES : simple renommage
           ========================================= */

            if (!file.toPath().equals(outputFile.toPath())) {
                Files.move(file.toPath(), outputFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            return true;

        } catch (Exception e) {

            System.err.println("Conversion failed: "
                    + file.getName() + " (" + e.getMessage() + ")");

            try {

                Path target = corruptedDir.toPath().resolve(file.getName());

                int count = 1;
                String name = baseName;
                String ext = getFileExtension(file);

                while (Files.exists(target)) {
                    target = corruptedDir.toPath().resolve(name + "_" + count + ext);
                    count++;
                }

                Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

                System.err.println("Moved to corrupted/: " + target.getFileName());

            } catch (IOException moveEx) {

                System.err.println("Failed to move corrupted file: "
                        + file.getName() + " (" + moveEx.getMessage() + ")");
            }

            try {
                Files.deleteIfExists(outputFile.toPath());
            } catch (IOException ignored) {}

            return false;
        }
    }

    /**
     * Tente de réparer un PNG corrompu avec ImageMagick en ignorant les CRC.
     */
    private boolean repairImageWithMagick(File inputFile, File outputFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "magick",
                    inputFile.getAbsolutePath(),
                    "-define", "png:ignore-crc=TRUE",
                    "-background", "white",
                    "-alpha", "remove",
                    outputFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor();
            return process.exitValue() == 0 && outputFile.exists();
        } catch (Exception e) {
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
                    "-type", "TrueColor",
                    "-alpha", "remove",
                    "-sampling-factor", "4:2:0",
                    "-strip",
                    "-interlace", "Plane",
                    "-quality", "80",
                    outputFile.getAbsolutePath()
            );

            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                String line;

                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exit = process.waitFor();

            return exit == 0 && outputFile.exists();

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

            int exit = process.waitFor();

            return exit == 0 && outputFile.exists();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void convertToJpg(File inputFile) throws IOException {

        File outputFile = new File(
                inputFile.getParentFile(),
                getFileNameWithoutExtension(inputFile) + ".jpg"
        );

        BufferedImage inputImage;
        try {
            inputImage = ImageIO.read(inputFile);
            if (inputImage == null) {
                throw new IOException("ImageIO returned null");
            }
        } catch (Exception e) {
            throw new IOException("ImageIO failed", e);
        }

        // 🔥 Gestion transparence PNG
        BufferedImage convertedImage = new BufferedImage(
                inputImage.getWidth(),
                inputImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g2d = convertedImage.createGraphics();
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, convertedImage.getWidth(), convertedImage.getHeight());
        g2d.drawImage(inputImage, 0, 0, null);
        g2d.dispose();

        if (!ImageIO.write(convertedImage, "jpg", outputFile)) {
            throw new IOException("ImageIO write failed");
        }
    }

    /**
     * Tentative de réparation via ImageMagick
     */
    private boolean repairImage(File imageFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder("magick", "convert",
                    imageFile.getAbsolutePath(), imageFile.getAbsolutePath());
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
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
    public void handleProceed(
            List<File> selectedFiles,
            TextField extensionField,
            ProgressBar progressBar
    ) {
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            messageService.showMessage(
                    Alert.AlertType.WARNING,
                    "No files selected",
                    "Please select files or a folder first."
            );
            return;
        }

        String newExtension = extensionField.getText();
        if (newExtension == null || newExtension.trim().isEmpty()) {
            messageService.showMessage(
                    Alert.AlertType.WARNING,
                    "Invalid extension",
                    "Please enter a valid extension."
            );
            return;
        }

        final String normalizedExtension =
                newExtension.trim().replaceFirst("^\\.", "").toLowerCase();

        progressBar.setProgress(0);
        progressBar.setVisible(true);

        Task<Void> task = new Task<>() {

            @Override
            protected Void call() {

                File parentDir = selectedFiles.get(0).getParentFile();
                File corruptedDir = new File(parentDir, "corrupted");
                if (!corruptedDir.exists()) corruptedDir.mkdirs();

            /* =========================
               1️⃣ PHASE CONVERSION
               ========================= */
                int totalConvert = selectedFiles.size();
                int processed = 0;

                updateMessage("Converting files…");
                updateProgress(0, totalConvert);

                for (File file : selectedFiles) {

                    if (isCancelled()) return null;

                    changeSingleFileExtension(
                            file,
                            normalizedExtension,
                            corruptedDir
                    );

                    processed++;
                    updateProgress(processed, totalConvert);
                    updateMessage(
                            "Converted " + processed + " / " + totalConvert
                    );
                }

            /* =========================
               2️⃣ PHASE RÉPARATION
               ========================= */
                File[] corruptedFiles = corruptedDir.listFiles();
                if (corruptedFiles != null && corruptedFiles.length > 0) {

                    int totalRepair = corruptedFiles.length;
                    int repaired = 0;

                    updateMessage("Repairing corrupted images…");
                    updateProgress(0, totalRepair);

                    for (File corrupted : corruptedFiles) {

                        if (isCancelled()) return null;

                        corruptedRepairService
                                .repairFileWithFallbacks(corrupted, null);

                        repaired++;
                        updateProgress(repaired, totalRepair);
                        updateMessage(
                                "Repaired " + repaired + " / " + totalRepair
                        );
                    }
                }

                updateMessage("All operations completed ✔");
                updateProgress(1, 1);
                return null;
            }
        };

    /* =========================
       UI BINDINGS
       ========================= */
        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            progressBar.setVisible(false);
            messageService.showMessage(
                    Alert.AlertType.INFORMATION,
                    "Completed",
                    "Conversion and repair finished successfully."
            );
        });

        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            messageService.showMessage(
                    Alert.AlertType.ERROR,
                    "Error",
                    "An error occurred during processing."
            );
        });

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

        // renameFilesRecursively(selectedDirectory, gatherInContainer, containerDirectory);
        File[] parentFolders = selectedDirectory.listFiles(File::isDirectory);

        if (parentFolders == null || parentFolders.length == 0) {
            messageService.showMessage(Alert.AlertType.WARNING,
                    "No folders",
                    "Selected directory contains no subfolders.");
            return containerDirectory;
        }

        for (File parent : parentFolders) {
            renameFilesRecursively(parent, gatherInContainer, containerDirectory);
        }
        return containerDirectory;
    }

    public void renameFilesRecursively(File directory, boolean gatherInContainer, File containerDirectory) {
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

    public Path getAvailablePath(Path folder, String filename) {
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