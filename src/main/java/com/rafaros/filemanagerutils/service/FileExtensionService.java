package com.rafaros.filemanagerutils.service;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Iterator;
import java.util.List;

public class FileExtensionService {

    private MessageService messageService = new MessageService();

    /* =========================================================
       EXTENSION / CONVERSION (SÉCURISÉE)
       ========================================================= */

    public boolean changeSingleFileExtension(File file, String newExtension) {
        if (file == null || !file.exists() || newExtension == null || newExtension.isEmpty()) {
            return false;
        }

        newExtension = newExtension.replaceFirst("^\\.", "").toLowerCase();
        String srcExt = getFileExtension(file).toLowerCase();

        File outputFile = new File(
                file.getParentFile(),
                getFileNameWithoutExtension(file) + "." + newExtension
        );

        /* ---------- PNG → JPG ---------- */
        if (srcExt.equals(".png") && newExtension.equals("jpg")) {
            return convertToJpg(file, outputFile);
        }

        /* ---------- PNG → WEBP ---------- */
        if (srcExt.equals(".png") && newExtension.equals("webp")) {
            return convertWithImageMagick(file, outputFile);
        }

        /* ---------- HEIC / JPG / JPEG ---------- */
        boolean isHeic = isActuallyHeic(file)
                || srcExt.equals(".heic")
                || srcExt.equals(".jpg")
                || srcExt.equals(".jpeg");

        if (isHeic) {
            if (convertWithImageMagick(file, outputFile)) {
                return true;
            }
            return convertWithHeifDec(file, outputFile);
        }

        /* ---------- AUTRES → RENOMMAGE SIMPLE ---------- */
        try {
            Files.move(
                    file.toPath(),
                    outputFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
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
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {}
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
            Process p = pb.start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /* =========================================================
       PNG → JPG (ImageIO, fond blanc)
       ========================================================= */

    private boolean convertToJpg(File inputFile, File outputFile) {
        try {
            BufferedImage src = ImageIO.read(inputFile);
            if (src == null) return false;

            BufferedImage jpg = new BufferedImage(
                    src.getWidth(),
                    src.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );

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
       HEIC DÉTECTION (INCHANGÉ)
       ========================================================= */

    public boolean isActuallyHeic(File file) {
        if (file == null || !file.exists()) return false;

        String ext = getFileExtension(file).toLowerCase();
        if (ext.equals(".heic")) return true;

        if (ext.equals(".jpg") || ext.equals(".jpeg")) {
            try {
                String mime = Files.probeContentType(file.toPath());
                return "image/heic".equals(mime);
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    /* =========================================================
       UI HANDLER (INTOUCHÉ)
       ========================================================= */

    public void handleProceed(List<File> selectedFiles,
                              TextField extensionField,
                              ProgressBar progressBar) {

        if (selectedFiles == null || selectedFiles.isEmpty()) {
            messageService.showMessage(Alert.AlertType.WARNING,
                    "No files selected",
                    "Please select files or a folder first.");
            return;
        }

        String newExtension = extensionField.getText();
        if (newExtension == null || newExtension.trim().isEmpty()) {
            messageService.showMessage(Alert.AlertType.WARNING,
                    "Invalid extension",
                    "Please enter a valid extension.");
            return;
        }

        final String normalizedExtension =
                newExtension.trim().replaceFirst("^\\.", "").toLowerCase();

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
       RENOMMAGE CONTENU (100 % INTACT 🔒)
       ========================================================= */

    public void handleRenameContent(File selectedDirectory,
                                    CheckBox gatherInContainerCheckbox,
                                    TextField containerNameField,
                                    File containerDirectory) {

        if (selectedDirectory == null) {
            messageService.showMessage("No directory selected", "Error");
            return;
        }

        if (gatherInContainerCheckbox.isSelected()) {
            String containerName = containerNameField.getText().trim();
            if (containerName.isEmpty()) {
                messageService.showMessage("Container name is empty", "Error");
                return;
            }
            containerDirectory = new File(selectedDirectory, containerName);
            if (containerDirectory.exists()) {
                messageService.showMessage("Container directory already exists", "Error");
                return;
            }
        }

        int confirm = messageService.showConfirmDialog(
                "Are you sure you want to rename the content of this directory?",
                "Confirmation"
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        containerDirectory.mkdirs();
        renameFilesRecursively(selectedDirectory,
                gatherInContainerCheckbox,
                containerDirectory);
    }

    private void renameFilesRecursively(File directory,
                                        CheckBox gatherInContainerCheckbox,
                                        File containerDirectory) {

        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                renameFilesRecursively(file, gatherInContainerCheckbox, containerDirectory);
            } else {
                try {
                    String newFileName = directory.getName() + getFileExtension(file);
                    Path target = gatherInContainerCheckbox.isSelected()
                            ? containerDirectory.toPath().resolve(newFileName)
                            : file.toPath().resolveSibling(newFileName);
                    Files.move(file.toPath(), target);
                } catch (IOException ignored) {}
            }
        }
    }

    /* =========================================================
       UTILS (INCHANGÉS)
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