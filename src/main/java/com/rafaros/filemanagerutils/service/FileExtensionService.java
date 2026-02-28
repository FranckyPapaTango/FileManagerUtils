package com.rafaros.filemanagerutils.service;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileExtensionService {

    private MessageService messageService = new MessageService();

    /**
     * Change l'extension de tous les fichiers dans la liste.
     * Si le fichier est HEIC ou JPG, utilise ImageMagick pour convertir en JPEG 24bpp.
     * Pour les autres extensions, conserve le comportement existant.
     *
     * @param files        liste de fichiers
     * @param newExtension nouvelle extension sans le point
     * @return true si tout s'est bien passé, false sinon
     */
    public boolean changeFilesExtension(List<File> files, String newExtension) {
        if (files == null || files.isEmpty() || newExtension == null || newExtension.isEmpty()) {
            return false;
        }

        if (newExtension.startsWith(".")) {
            newExtension = newExtension.substring(1);
        }

        boolean allSuccess = true;

        for (File file : files) {
            String ext = getFileExtension(file).toLowerCase();

            // HEIC ou JPG → conversion ImageMagick
            if (ext.equals(".heic") || ext.equals(".jpg") || ext.equals(".jpeg")) {
                String outputFileName = getFileNameWithoutExtension(file) + "." + newExtension;
                File outputFile = new File(file.getParentFile(), outputFileName);

                boolean converted = convertWithImageMagick(file, outputFile);
                if (!converted) allSuccess = false;

            } else {
                // Autres extensions → juste renommer
                Path source = file.toPath();
                Path target = source.resolveSibling(getFileNameWithoutExtension(file) + "." + newExtension);
                try {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                    allSuccess = false;
                }
            }
        }

        return allSuccess;
    }

    /**
     * Utilise ImageMagick pour convertir une image en JPEG 24bpp
     */
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

            // Affiche le log ImageMagick
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Converted successfully: " + outputFile.getAbsolutePath());
                return true;
            } else {
                System.err.println("ImageMagick failed for: " + inputFile.getAbsolutePath());
                return false;
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Retourne le nom du fichier sans l'extension
     */
    public String getFileNameWithoutExtension(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return (dotIndex == -1) ? name : name.substring(0, dotIndex);
    }

    public String getFileNameWithoutExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    /**
     * Retourne l'extension du fichier avec le point
     */
    public String getFileExtension(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return (dotIndex == -1) ? "" : name.substring(dotIndex);
    }

    public String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex);
    }

    public boolean changeSingleFileExtension(File file, String newExtension) {
        if (file == null || !file.exists() || newExtension == null || newExtension.isEmpty()) {
            return false;
        }

        if (newExtension.startsWith(".")) newExtension = newExtension.substring(1);

        boolean isHeic = isActuallyHeic(file)
                || getFileExtension(file).equalsIgnoreCase(".heic")
                || getFileExtension(file).equalsIgnoreCase(".jpg")
                || getFileExtension(file).equalsIgnoreCase(".jpeg");

        File outputFile = new File(file.getParentFile(),
                getFileNameWithoutExtension(file) + "." + newExtension);

        if (isHeic) {
            // Essayer ImageMagick
            if (convertWithImageMagick(file, outputFile)) {
                return true;
            }
            // fallback avec heif-dec.exe
            return convertWithHeifDec(file, outputFile);
        } else {
            // simple renommage
            Path source = file.toPath();
            Path target = source.resolveSibling(getFileNameWithoutExtension(file) + "." + newExtension);
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    /**
     * Conversion fallback via heif-dec.exe
     */
    private boolean convertWithHeifDec(File inputFile, File outputFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "heif-dec.exe",
                    inputFile.getAbsolutePath(),
                    outputFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) System.out.println(line);
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Converted via heif-dec.exe: " + outputFile.getAbsolutePath());
                return true;
            } else {
                System.err.println("heif-dec.exe failed for: " + inputFile.getAbsolutePath());
                return false;
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Vérifie si heif-convert est installé et accessible
     */
    private boolean isHeifConvertAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("heif-convert", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Conversion via heif-convert
     */
    private boolean convertWithHeifConvert(File inputFile, File outputFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "heif-convert",
                    inputFile.getAbsolutePath(),
                    outputFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Affiche le log
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Converted via heif-convert: " + outputFile.getAbsolutePath());
                return true;
            } else {
                System.err.println("heif-convert failed for: " + inputFile.getAbsolutePath());
                return false;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }



    public boolean isActuallyHeic(File file) {
        if (file == null || !file.exists()) return false;
        String ext = getFileExtension(file).toLowerCase();

        // Déjà .heic → vrai HEIC
        if (ext.equals(".heic")) return true;

        // Sinon on vérifie le type MIME pour les jpg/jpeg
        if (ext.equals(".jpg") || ext.equals(".jpeg")) {
            try {
                Path path = file.toPath();
                String mime = Files.probeContentType(path);
                return mime != null && mime.equals("image/heic");
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        return false;
    }

    public void handleProceed(List<File> selectedFiles, TextField extensionField, ProgressBar progressBar) {

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
                StringBuilder failedFiles = new StringBuilder();

                for (File file : selectedFiles) {
                    boolean ok;
                    try {
                        ok = changeSingleFileExtension(file, normalizedExtension);
                    } catch (Exception e) {
                        e.printStackTrace();
                        ok = false;
                    }

                    if (!ok) {
                        failedFiles.append(file.getAbsolutePath()).append("\n");
                    }

                    processed++;
                    updateProgress(processed, total);
                }

                if (failedFiles.length() > 0) {
                    final String failed = failedFiles.toString();
                    Platform.runLater(() -> messageService.showMessage(
                            Alert.AlertType.WARNING,
                            "Some files could not be processed",
                            failed
                    ));
                }

                return null;
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            progressBar.setVisible(false);

            messageService.showMessage(Alert.AlertType.INFORMATION,
                    "File Extension",
                    "Processing completed.");
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressBar.setVisible(false);

            messageService.showMessage(Alert.AlertType.ERROR,
                    "File Extension",
                    "An error occurred during processing.");
        });

        new Thread(task, "file-extension-task").start();
    }


    public void handleRenameContent(File selectedDirectory, CheckBox gatherInContainerCheckbox, TextField containerNameField, File containerDirectory) {
        if (selectedDirectory == null) {
            messageService.showMessage("No directory selected", "Error");
            return;
        }
        if (gatherInContainerCheckbox.isSelected()) {
            String containerName = containerNameField.getText().trim();
            while (true) {
                if (containerName.isEmpty()) {
                    messageService.showMessage("Container name is empty", "Error");
                    return;
                }
                containerDirectory = new File(selectedDirectory, containerName);
                // Vérifie si le répertoire de conteneur existe déjà
                if (containerDirectory.exists()) {
                    messageService.showMessage("Container directory already exists. Please define a different name.", "Error");
                    return;
                }
                break;
            }
        }
        int confirm = messageService.showConfirmDialog("Are you sure you want to rename the content of this directory?", "Confirmation");
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        // Crée le répertoire de conteneur s'il n'existe pas
        if (!containerDirectory.mkdirs()) {
            messageService.showMessage("Failed to create container directory", "Error");
            return;
        }
        boolean success = renameFilesInDirectory(selectedDirectory, gatherInContainerCheckbox, containerDirectory);
        if (success) {
            messageService.showMessage("Success!", "Information");
        } else {
            messageService.showMessage("An error occurred", "Error");
        }
    }

    private boolean renameFilesInDirectory(File directory, CheckBox gatherInContainerCheckbox, File containerDirectory) {
        try {
            renameFilesRecursively(directory, gatherInContainerCheckbox, containerDirectory);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ---------------------------
    // Renommage des fichiers dans un dossier
    // ---------------------------
    private void renameFilesRecursively(File directory, CheckBox gatherInContainerCheckbox, File containerDirectory) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                renameFilesRecursively(file, gatherInContainerCheckbox, containerDirectory);
            } else {
                String newFileName = directory.getName() + getFileExtension(file);

                Path source = file.toPath();
                Path target;

                if (gatherInContainerCheckbox.isSelected() && containerDirectory != null) {
                    target = containerDirectory.toPath().resolve(newFileName);
                } else {
                    target = source.resolveSibling(newFileName);
                }

                // Gestion des collisions de fichiers
                int counter = 1;
                String nameWithoutExt = getFileNameWithoutExtension(newFileName);
                String ext = getFileExtension(newFileName);

                while (Files.exists(target)) {
                    String indexedName = nameWithoutExt + "_" + counter + ext;
                    target = target.getParent().resolve(indexedName);
                    counter++;
                }

                // Déplacer le fichier
                try {
                    Files.move(source, target);
                } catch (IOException e) {
                    System.err.println("Failed to move file: " + source);
                    e.printStackTrace();
                }
            }
        }
    }
}
