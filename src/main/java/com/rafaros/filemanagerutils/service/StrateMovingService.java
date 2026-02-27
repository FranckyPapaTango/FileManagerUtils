package com.rafaros.filemanagerutils.service;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.DirectoryChooser;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StrateMovingService {

    private final FileExtensionService fileExtensionService = new FileExtensionService();


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
                    String baseName = fileExtensionService.getFileNameWithoutExtension(fileName);
                    String ext = fileExtensionService.getFileExtension(fileName);
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

                    // ⚡ Mettre à jour la ProgressBar et le Label
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
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    strateStatusLabel.setText("Status: Error during file moving.");
                    moveFilesButton.setDisable(false);
                    exportProgressBar.setVisible(false);
                });
            }
        }).start();
    }


    /**
     * Nettoie le nom du fichier pour générer un nom de dossier valide.
     */
    private String cleanName(String fileName) {
        String name = fileExtensionService.getFileNameWithoutExtension(fileName).trim();
        boolean changed;
        do {
            String before = name;
            // 🔹 Supprime (123) en fin
            name = name.replaceAll("\\s*\\(\\d+\\)$", "");
            // 🔹 Supprime _1, _12, _001 en fin
            name = name.replaceAll("_\\d+$", "");
            // 🔹 Supprime chiffres collés en FIN de mot
            name = name.replaceAll("(\\p{L})\\d+$", "$1");
            // 🔹 Supprime chiffres collés en DÉBUT de mot
            name = name.replaceAll("^\\d+(\\p{L})", "$1");
            name = name.trim();
            changed = !name.equals(before);
        } while (changed);
        // 🔹 Nettoyage doux des bords seulement
        name = name.replaceAll(
                "^[^\\p{L}\\p{N}\\+\\-_'() ]+|[^\\p{L}\\p{N}\\+\\-_'() ]+$",
                ""
        );
        // 🔒 PARANO MODE : normalisation des espaces Unicode chelous
        name = name.replace('\u00A0', ' ')   // espace insécable
                .replace('\u2007', ' ')   // espace figure
                .replace('\u202F', ' ');  // espace fine insécable
        // 🔹 Espaces propres (compression à 1)
        name = name.replaceAll("\\s+", " ").trim();
        if (name.isEmpty()) name = "UNKNOWN";
        return name;
    }




    public void handleExportRecycleBin(Button exportRecycleBinButton, Label strateStatusLabel) {

        exportRecycleBinButton.setDisable(true);

        new Thread(() -> {
            try {

                // 🔥 Script PowerShell pour restaurer la corbeille et lister les fichiers restaurés
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
                                "        # RESTAURATION RÉELLE\n" +
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
                        showMessage(
                                "Recycle Bin export completed.\n\n" +
                                        "✔ Files restored to original locations\n" +
                                        "✔ desktop_2.txt generated on Desktop",
                                "Success"
                        );
                    } else {
                        strateStatusLabel.setText("Status: PowerShell exited with code " + exitCode);
                        showMessage(
                                "PowerShell exited with code: " + exitCode,
                                "Error"
                        );
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    exportRecycleBinButton.setDisable(false);
                    strateStatusLabel.setText("Status: Error restoring Recycle Bin.");
                    showMessage(
                            "An error occurred while exporting/restoring the Recycle Bin.",
                            "Error"
                    );
                });
            }
        }).start();
    }

    private void showMessage(String message, String title) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE));
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

            // Nettoyage du nom du dossier
//            String cleanFolderName = originalName
//                    .replaceAll("\\d+", "")                           // supprime les chiffres
//                    .replaceAll("^[\\-_'+]+|[\\-_'+]+$", "")          // supprime _, -, ', + au début ou fin
//                    .replaceAll("[()\\[\\]{}]", "")                  // supprime (), [], {}
//                    .replaceAll("[^a-zA-Z0-9\\s\\-_'+]", "")         // garde lettres, chiffres, espaces, _, -, ', +
//                    .replaceAll("\\s+", " ")                          // condense les espaces multiples
//                    .trim();
            String cleanFolderName = originalName
                    // supprime chiffres SEULEMENT s’ils sont seuls ou parasites
                    .replaceAll("\\d+", "")
                    // supprime caractères NON lettres/chiffres aux BORDS seulement
                    .replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "")
                    // espaces propres
                    .replaceAll("\\s+", " ")
                    .trim();



            if (cleanFolderName.isEmpty()) cleanFolderName = "UNKNOWN";

            Path targetDir = parentDir.toPath().resolve(cleanFolderName);

            try {
                if (Files.exists(targetDir)) {
                    // Fusionner le contenu du dossier actuel dans le dossier existant
                    try (Stream<Path> files = Files.list(subDir.toPath())) {
                        files.forEach(file -> {
                            try {

                                if (!Files.isWritable(file)) {
                                    System.err.println("Locked or inaccessible file skipped: " + file);
                                    return; // ✅ return "void" = OK
                                }

                                Path targetFile = targetDir.resolve(file.getFileName());
                                String nameWithoutExt = fileExtensionService.getFileNameWithoutExtension(file.getFileName().toString());
                                String ext = fileExtensionService.getFileExtension(file.getFileName().toString());
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

                    // Supprimer le dossier seulement s'il est vide
                    File[] remaining = subDir.listFiles();
                    if (remaining == null || remaining.length == 0) {
                        Files.delete(subDir.toPath());
                    }

                    renamedCount++;
                } else if (!originalName.equals(cleanFolderName)) {
                    // Renommer le dossier si nécessaire
                    Files.move(subDir.toPath(), targetDir);
                    renamedCount++;
                }
                // sinon le dossier est déjà propre, on ne fait rien
            } catch (IOException e) {
                System.err.println("Failed to process folder: " + subDir.getAbsolutePath());
                e.printStackTrace();
            }
        }

        strateStatusLabel.setText("Status: " + renamedCount + " folder(s) cleaned/merged.");
    }

}