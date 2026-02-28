package com.rafaros.filemanagerutils.service;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class StrateMovingService {

    private final MessageService messageService = new MessageService();

    public void handleMoveFiles(Label strateStatusLabel, Button moveFilesButton, Path destinationRoot, ProgressBar exportProgressBar) {

        if (destinationRoot == null) {
            strateStatusLabel.setText("Status: Please select a destination folder first!");
            return;
        }

        moveFilesButton.setDisable(true);
        exportProgressBar.setProgress(0);
        exportProgressBar.setVisible(true);
        exportProgressBar.setManaged(true);

        new Thread(() -> {
            try {
                Path logPath = Paths.get(System.getProperty("user.home"), "OneDrive", "Desktop", "desktop_2.txt");

                if (!Files.exists(logPath)) {
                    Platform.runLater(() -> {
                        strateStatusLabel.setText("Status: desktop_2.txt not found!");
                        moveFilesButton.setDisable(false);
                        exportProgressBar.setVisible(false);
                    });
                    return;
                }

                List<String> restoredFiles = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(logPath.toFile()), StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        if (restoredFiles.isEmpty() && line.startsWith("\uFEFF")) line = line.substring(1);
                        restoredFiles.add(line);
                    }
                }

                if (restoredFiles.isEmpty()) {
                    Platform.runLater(() -> {
                        strateStatusLabel.setText("Status: No files to move found in desktop_2.txt");
                        moveFilesButton.setDisable(false);
                        exportProgressBar.setVisible(false);
                    });
                    return;
                }

                int totalFiles = restoredFiles.size();
                int movedCount = 0;

                for (int i = 0; i < totalFiles; i++) {
                    String filePathStr = restoredFiles.get(i);
                    if (filePathStr.startsWith("?")) continue;

                    Path filePath;
                    try {
                        filePath = Paths.get(filePathStr);
                    } catch (InvalidPathException e) {
                        System.err.println("Skipping invalid path: " + filePathStr);
                        continue;
                    }

                    if (!Files.exists(filePath)) continue;

                    String fileName = filePath.getFileName().toString();
                    String baseName = getFileNameWithoutExtension(fileName);
                    String ext = getFileExtension(fileName);
                    String cleanFolderName = cleanName(fileName);
                    if (cleanFolderName.isEmpty()) cleanFolderName = "UNKNOWN";

                    Path targetDir = destinationRoot.resolve(cleanFolderName);
                    Files.createDirectories(targetDir);
                    Path targetFile = targetDir.resolve(fileName);

                    int counter = 1;
                    while (Files.exists(targetFile)) {
                        targetFile = targetDir.resolve(baseName + "_" + counter + ext);
                        counter++;
                    }

                    try {
                        Files.move(filePath, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        movedCount++;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    int finalI = i;
                    int finalMovedCount = movedCount;
                    Platform.runLater(() -> {
                        exportProgressBar.setProgress((double) (finalI + 1) / totalFiles);
                        strateStatusLabel.setText("Status: Moving file " + (finalI + 1) + "/" + totalFiles);
                    });
                }

                int finalMovedCount1 = movedCount;
                Platform.runLater(() -> {
                    strateStatusLabel.setText("Status: " + finalMovedCount1 + " file(s) moved successfully!");
                    moveFilesButton.setDisable(false);
                    exportProgressBar.setVisible(false);
                    messageService.showMessage(Alert.AlertType.INFORMATION,
                            "Move Completed",
                            finalMovedCount1 + " file(s) moved successfully.");
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    strateStatusLabel.setText("Status: Error during file moving.");
                    moveFilesButton.setDisable(false);
                    exportProgressBar.setVisible(false);
                    messageService.showMessage(Alert.AlertType.ERROR,
                            "Error",
                            "An error occurred during file moving.");
                });
            }
        }).start();
    }

    private String cleanName(String fileName) {
        String name = getFileNameWithoutExtension(fileName).trim();
        boolean changed;
        do {
            String before = name;
            name = name.replaceAll("\\s*\\(\\d+\\)$", "");
            name = name.replaceAll("_\\d+$", "");
            name = name.replaceAll("(\\p{L})\\d+$", "$1");
            name = name.replaceAll("^\\d+(\\p{L})", "$1");
            name = name.trim();
            changed = !name.equals(before);
        } while (changed);

        name = name.replaceAll(
                "^[^\\p{L}\\p{N}\\+\\-_'() ]+|[^\\p{L}\\p{N}\\+\\-_'() ]+$",
                ""
        );
        name = name.replace('\u00A0', ' ')
                .replace('\u2007', ' ')
                .replace('\u202F', ' ');
        name = name.replaceAll("\\s+", " ").trim();
        if (name.isEmpty()) name = "UNKNOWN";
        return name;
    }

    public void handleExportRecycleBin(Button exportRecycleBinButton, Label strateStatusLabel) {

        exportRecycleBinButton.setDisable(true);

        new Thread(() -> {
            try {

                String script =
                        "$desktopPath = [Environment]::GetFolderPath('Desktop')\n" +
                                "$logFile = Join-Path $desktopPath 'desktop_2.txt'\n" +
                                "$shell = New-Object -ComObject Shell.Application\n" +
                                "$recycleBin = $shell.Namespace(0xA)\n" +
                                "$results = @()\n" +
                                "foreach ($item in @($recycleBin.Items())) {\n" +
                                "    $originalPath = $recycleBin.GetDetailsOf($item, 1)\n" +
                                "    if ($originalPath) {\n" +
                                "        $fullPath = Join-Path $originalPath $item.Name\n" +
                                "        $results += $fullPath\n" +
                                "        $item.InvokeVerb(\"undelete\")\n" +
                                "    }\n" +
                                "}\n" +
                                "$results | Set-Content -Path $logFile -Encoding UTF8\n";

                Path tempScript = Files.createTempFile("export_restore_recyclebin_", ".ps1");
                Files.write(tempScript, script.getBytes(StandardCharsets.UTF_8));

                ProcessBuilder pb = new ProcessBuilder(
                        "powershell.exe",
                        "-STA",
                        "-ExecutionPolicy", "Bypass",
                        "-NoProfile",
                        "-File", tempScript.toAbsolutePath().toString()
                );

                Process process = pb.start();
                int exitCode = process.waitFor();
                Files.deleteIfExists(tempScript);

                Platform.runLater(() -> {
                    exportRecycleBinButton.setDisable(false);
                    if (exitCode == 0) {
                        strateStatusLabel.setText("Status: Recycle Bin restored successfully!");
                        messageService.showMessage(Alert.AlertType.INFORMATION,
                                "Success",
                                "Recycle Bin export completed.\n\n" +
                                        "✔ Files restored to original locations\n" +
                                        "✔ desktop_2.txt generated on Desktop");
                    } else {
                        strateStatusLabel.setText("Status: PowerShell exited with code " + exitCode);
                        messageService.showMessage(Alert.AlertType.ERROR,
                                "Error",
                                "PowerShell exited with code: " + exitCode);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    exportRecycleBinButton.setDisable(false);
                    strateStatusLabel.setText("Status: Error restoring Recycle Bin.");
                    messageService.showMessage(Alert.AlertType.ERROR,
                            "Error",
                            "An error occurred while exporting/restoring the Recycle Bin.");
                });
            }
        }).start();
    }

    public void handleCleanFoldersName(Label strateStatusLabel) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Parent Folder");
        File parentDir = chooser.showDialog(null);
        if (parentDir == null) {
            strateStatusLabel.setText("Status: No folder selected.");
            return;
        }

        File[] subDirs = parentDir.listFiles(File::isDirectory);
        if (subDirs == null || subDirs.length == 0) {
            strateStatusLabel.setText("Status: No subfolders found.");
            return;
        }

        int renamedCount = 0;

        for (File subDir : subDirs) {
            String originalName = subDir.getName();
            String cleanFolderName = originalName
                    .replaceAll("\\d+", "")
                    .replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (cleanFolderName.isEmpty()) cleanFolderName = "UNKNOWN";

            Path targetDir = parentDir.toPath().resolve(cleanFolderName);

            try {
                if (Files.exists(targetDir)) {
                    try (Stream<Path> files = Files.list(subDir.toPath())) {
                        files.forEach(file -> {
                            try {
                                if (!Files.isWritable(file)) return;
                                Path targetFile = targetDir.resolve(file.getFileName());
                                String nameWithoutExt = getFileNameWithoutExtension(file.getFileName().toString());
                                String ext = getFileExtension(file.getFileName().toString());
                                int counter = 1;
                                while (Files.exists(targetFile)) {
                                    targetFile = targetDir.resolve(nameWithoutExt + "_" + counter + ext);
                                    counter++;
                                }
                                Files.move(file, targetFile);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    }

                    File[] remaining = subDir.listFiles();
                    if (remaining == null || remaining.length == 0) {
                        Files.delete(subDir.toPath());
                    }
                    renamedCount++;
                } else if (!originalName.equals(cleanFolderName)) {
                    Files.move(subDir.toPath(), targetDir);
                    renamedCount++;
                }
            } catch (IOException e) {
                System.err.println("Failed to process folder: " + subDir.getAbsolutePath());
                e.printStackTrace();
            }
        }

        strateStatusLabel.setText("Status: " + renamedCount + " folder(s) cleaned/merged.");
    }

    public void handleExportFolderList(TextArea selectedFilesInfo) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Folder to Export List");

        File selectedDir = directoryChooser.showDialog(selectedFilesInfo.getScene().getWindow());
        if (selectedDir == null || !selectedDir.isDirectory()) {
            messageService.showMessage(Alert.AlertType.WARNING, "No folder selected", "Please select a valid folder.");
            return;
        }

        Path outputPath = Paths.get(System.getProperty("user.home"), "OneDrive", "Desktop", "desktop_2.txt");

        try {
            Files.createDirectories(outputPath.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                Files.walk(selectedDir.toPath())
                        .filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                writer.write(path.toAbsolutePath().toString());
                                writer.newLine();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }

            messageService.showMessage(Alert.AlertType.INFORMATION,
                    "Export Completed",
                    "File list exported to: " + outputPath.toAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
            messageService.showMessage(Alert.AlertType.ERROR,
                    "Error",
                    "Failed to export file list: " + e.getMessage());
        }
    }

    public String getFileNameWithoutExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    public String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex);
    }
}