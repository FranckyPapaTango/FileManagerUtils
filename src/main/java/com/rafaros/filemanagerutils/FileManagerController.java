package com.rafaros.filemanagerutils;

import com.rafaros.filemanagerutils.service.FileExtensionService;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javafx.util.Duration;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImageReadException;

public class FileManagerController {

    // ===========================
    // FXML Variables
    // ===========================
    @FXML
    private TextField extensionField;
    @FXML
    private TextField selectedDirectoryField;
    @FXML
    private TextField selectedImagesField;
    @FXML
    private CheckBox gatherInContainerCheckbox;
    @FXML
    private TextField containerNameField;
    @FXML
    private Label locationLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private TextField distributionFolderField;
    @FXML
    private TextField subdivisionCountField;
    @FXML
    private Label totalFilesLabel;
    @FXML
    private Label filesPerFolderLabel;
    @FXML
    private Label remainingFilesLabel;
    @FXML
    private Label distributionStatusLabel;
    @FXML
    private Button startDistributionButton;
    @FXML
    private TextField restrictionField;
    @FXML
    private TextField destinationField;
    @FXML
    private Label strateStatusLabel;
    @FXML
    private Button moveFilesButton;
    @FXML
    private ProgressBar distributionProgressBar;
    @FXML
    private Button cleanFoldersButton;
    @FXML
    private Button exportRecycleBinButton;
    @FXML
    private VBox presentationCard;
    @FXML
    private Tab presentationTab;

    // ===========================
    // Variables internes
    // ===========================
    private File selectedDirectory2;       // pour handleGenerateFilesList
    private List<File> selectedFiles;
    private File selectedDirectory;
    private File containerDirectory;
    private File distributionRootFolder;
    private Path destinationRoot;

    private final FileExtensionService fileExtensionService = new FileExtensionService();

    // ===========================
    // Sélection de fichiers
    // ===========================
    @FXML
    private void handleSelectFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files");
        selectedFiles = fileChooser.showOpenMultipleDialog(new Stage());
    }

    @FXML
    private void handleSelectImages() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Images");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.png", "*.bpp")
        );
        selectedFiles = fileChooser.showOpenMultipleDialog(new Stage());
        if (selectedFiles != null) {
            StringBuilder fileNames = new StringBuilder();
            for (File file : selectedFiles) {
                fileNames.append(file.getName()).append("; ");
            }
            selectedImagesField.setText(fileNames.toString());
        }
    }

    @FXML
    private void handleProceed() {
        if (extensionField == null) {
            showFxAlert(Alert.AlertType.ERROR, "Configuration error", "extensionField not injected",
                    "Check fx:id=\"extensionField\" in FXML.");
            return;
        }
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            showFxAlert(Alert.AlertType.WARNING, "No files selected", null, "Please select files first.");
            return;
        }
        String newExtension = extensionField.getText().trim();
        if (newExtension.isEmpty()) {
            showFxAlert(Alert.AlertType.WARNING, "Invalid extension", null, "Extension field is empty.");
            return;
        }
        boolean success = fileExtensionService.changeFilesExtension(selectedFiles, newExtension);
        showFxAlert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                "Extension change",
                null,
                success ? "Extensions updated successfully." : "An error occurred.");
    }

    private void showFxAlert(Alert.AlertType alertType, String title, String headerText, String contentText) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title != null ? title : "Alert");
            alert.setHeaderText(headerText); // peut être null
            alert.setContentText(contentText != null ? contentText : "");
            alert.showAndWait();
        });
    }

    // ===========================
    // Sélection de répertoires
    // ===========================
    @FXML
    private void handleSelectDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Directory");
        selectedDirectory = directoryChooser.showDialog(new Stage());
        if (selectedDirectory != null) {
            selectedDirectoryField.setText(selectedDirectory.getName());
        }
    }

    @FXML
    private void handleChooseLocation() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Folder");
        File directory = directoryChooser.showDialog(null);
        if (directory != null) {
            selectedDirectory2 = directory;
            locationLabel.setText("Selected: " + directory.getAbsolutePath());
        } else {
            locationLabel.setText("No folder selected");
        }
    }

    // ===========================
    // Gestion des images
    // ===========================
    @FXML
    private void handleRepairImages() {
        if (selectedFiles != null) {
            for (File file : selectedFiles) {
                if (!isImageValid(file)) {
                    System.out.println("Attempting to repair corrupted file: " + file.getName());
                    if (repairImage(file)) {
                        System.out.println("File repaired: " + file.getName());
                    } else {
                        System.out.println("Failed to repair file: " + file.getName());
                        file.delete();
                    }
                } else {
                    System.out.println("File is valid: " + file.getName());
                }
            }
        }
    }

    @FXML
    private void handleGatherInContainer() {
        containerNameField.setVisible(gatherInContainerCheckbox.isSelected());
    }

    private boolean isImageValid(File imageFile) {
        try {
            if (ImageIO.read(imageFile) == null) return false;
            try {
                if (Imaging.getImageInfo(imageFile) == null) return false;
            } catch (ImageReadException e) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ===========================
    // Génération des fichiers listés
    // ===========================
    @FXML
    private void handleGenerateFilesList() {
        if (selectedDirectory2 == null) {
            statusLabel.setText("Status: Please select a folder first!");
            return;
        }

        String restriction = restrictionField.getText() != null ? restrictionField.getText().trim() : "";
        boolean hasRestriction = !restriction.isEmpty();
        Path outputDir = Paths.get(selectedDirectory2.getAbsolutePath(), "fileList");

        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            List<String> filePaths = Files.walk(selectedDirectory2.toPath())
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .filter(path -> path.matches(".*\\.(jpg|JPG|jpeg|JPEG|png|PNG|bmp|BMP|webp|WEBP|gif|GIF)$"))
                    .filter(path -> {
                        if (hasRestriction) {
                            Path parentDir = Paths.get(path).getParent();
                            return parentDir != null && parentDir.getFileName().toString().startsWith(restriction);
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            if (filePaths.isEmpty()) {
                statusLabel.setText("Status: No matching files found.");
                return;
            }

            Collections.shuffle(filePaths, new Random(System.currentTimeMillis()));
            int MAX_FILES_PER_TXT = 12_000;
            int totalFiles = (int) Math.ceil((double) filePaths.size() / MAX_FILES_PER_TXT);

            for (int i = 0; i < totalFiles; i++) {
                int start = i * MAX_FILES_PER_TXT;
                int end = Math.min(start + MAX_FILES_PER_TXT, filePaths.size());
                List<String> subList = filePaths.subList(start, end);

                String fileName = String.format("filesList_%d.txt", i + 1);
                Path outputFile = outputDir.resolve(fileName);

                try (OutputStream outputStream = Files.newOutputStream(outputFile);
                     Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    // Écriture BOM UTF-8
                    outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                    for (String filePath : subList) {
                        writer.write(filePath + System.lineSeparator());
                    }
                }
            }

            statusLabel.setText(String.format(
                    "Status: %d randomized file(s) list generated successfully in UTF-8 with BOM!", totalFiles
            ));

        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Status: An error occurred while generating the files list.");
        }
    }

    // ===========================
    // Renommage des fichiers
    // ===========================
    @FXML
    private void handleRenameContent() {
        if (selectedDirectory == null) {
            showMessage("No directory selected", "Error");
            return;
        }

        if (gatherInContainerCheckbox.isSelected()) {
            String containerName = containerNameField.getText().trim();
            while (true) {
                if (containerName.isEmpty()) {
                    showMessage("Container name is empty", "Error");
                    return;
                }
                containerDirectory = new File(selectedDirectory, containerName);
                if (containerDirectory.exists()) {
                    showMessage("Container directory already exists. Please define a different name.", "Error");
                    return;
                }
                break;
            }
        }

        int confirm = showConfirmDialog(
                "Are you sure you want to rename the content of this directory?",
                "Confirmation"
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        if (!containerDirectory.mkdirs()) {
            showMessage("Failed to create container directory", "Error");
            return;
        }

        boolean success = renameFilesInDirectory(selectedDirectory);
        if (success) {
            showMessage("Success!", "Information");
        } else {
            showMessage("An error occurred", "Error");
        }
    }

    private boolean renameFilesInDirectory(File directory) {
        try {
            renameFilesRecursively(directory);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void renameFilesRecursively(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (int i = 0; i < files.length; i++) {
            File file = files[i];

            if (file.isDirectory()) {
                renameFilesRecursively(file);
            } else {
                String newFileName = directory.getName() + fileExtensionService.getFileExtension(file);
                Path source = file.toPath();
                Path target;

                if (gatherInContainerCheckbox.isSelected() && containerDirectory != null) {
                    target = containerDirectory.toPath().resolve(newFileName);
                } else {
                    target = source.resolveSibling(newFileName);
                }

                // Gestion des collisions
                int counter = 1;
                String nameWithoutExt = fileExtensionService.getFileNameWithoutExtension(newFileName);
                String ext = fileExtensionService.getFileExtension(newFileName);
                while (Files.exists(target)) {
                    String indexedName = nameWithoutExt + "_" + counter + ext;
                    target = target.getParent().resolve(indexedName);
                    counter++;
                }

                try {
                    Files.move(source, target);
                } catch (IOException e) {
                    System.err.println("Failed to move file: " + source);
                    e.printStackTrace();
                }
            }
        }
    }

    // ===========================
    // Déplacement des fichiers depuis desktop_2.txt
    // ===========================
    @FXML
    private void handleMoveFiles() {
        if (destinationRoot == null) {
            strateStatusLabel.setText("Status: Please select a destination folder first!");
            return;
        }
        moveFilesButton.setDisable(true);

        new Thread(() -> {
            try {
                Path logPath = Paths.get(System.getProperty("user.home"), "OneDrive", "Desktop", "desktop_2.txt");
                if (!Files.exists(logPath)) {
                    Platform.runLater(() -> {
                        strateStatusLabel.setText("Status: desktop_2.txt not found!");
                        moveFilesButton.setDisable(false);
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
                        if (restoredFiles.isEmpty() && line.startsWith("\uFEFF")) {
                            line = line.substring(1);
                        }
                        restoredFiles.add(line);
                    }
                }

                if (restoredFiles.isEmpty()) {
                    Platform.runLater(() -> {
                        strateStatusLabel.setText("Status: No files to move found in desktop_2.txt");
                        moveFilesButton.setDisable(false);
                    });
                    return;
                }

                int movedCount = 0;
                for (String filePathStr : restoredFiles) {
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
                        System.out.println("Moved: " + targetFile);
                    } catch (IOException e) {
                        System.err.println("Failed to move file: " + filePath);
                        e.printStackTrace();
                    }
                }

                int finalMovedCount = movedCount;
                Platform.runLater(() -> {
                    strateStatusLabel.setText("Status: " + finalMovedCount + " file(s) moved successfully!");
                    moveFilesButton.setDisable(false);
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    strateStatusLabel.setText("Status: Error during file moving.");
                    moveFilesButton.setDisable(false);
                });
            }
        }).start();
    }

    // ===========================
    // Nettoyage des noms
    // ===========================
    private String cleanName(String fileName) {
        String name = fileExtensionService.getFileNameWithoutExtension(fileName).trim();
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

        name = name.replaceAll("^[^\\p{L}\\p{N}\\+\\-_'() ]+|[^\\p{L}\\p{N}\\+\\-_'() ]+$", "");
        name = name.replace('\u00A0', ' ')
                .replace('\u2007', ' ')
                .replace('\u202F', ' ');
        name = name.replaceAll("\\s+", " ").trim();
        if (name.isEmpty()) name = "UNKNOWN";
        return name;
    }

    // ===========================
    // Utilitaires
    // ===========================
    private boolean repairImage(File imageFile) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "magick", "convert", imageFile.getAbsolutePath(), imageFile.getAbsolutePath()
            );
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void showMessage(String message, String title) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE));
    }

    private int showConfirmDialog(String message, String title) {
        final int[] result = new int[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                result[0] = JOptionPane.showConfirmDialog(null, message, title, JOptionPane.YES_NO_OPTION);
            });
        } catch (Exception e) {
            e.printStackTrace();
            result[0] = JOptionPane.NO_OPTION;
        }
        return result[0];
    }

    // ===========================
    // Distribution des fichiers dans des sous-dossiers
    // ===========================
    @FXML
    private void handleStartDistribution() {
        if (selectedDirectory == null) {
            showMessage("No directory selected", "Error");
            return;
        }

        int filesPerFolder;
        try {
            filesPerFolder = Integer.parseInt(filesPerFolderField.getText().trim());
            if (filesPerFolder <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showMessage("Invalid number of files per folder", "Error");
            return;
        }

        new Thread(() -> {
            try {
                File[] files = selectedDirectory.listFiles(File::isFile);
                if (files == null || files.length == 0) {
                    Platform.runLater(() -> showMessage("No files to distribute", "Information"));
                    return;
                }

                int folderIndex = 1;
                int fileCount = 0;
                File currentFolder = new File(selectedDirectory, "subfolder_" + folderIndex);
                currentFolder.mkdirs();

                for (File file : files) {
                    if (fileCount >= filesPerFolder) {
                        folderIndex++;
                        currentFolder = new File(selectedDirectory, "subfolder_" + folderIndex);
                        currentFolder.mkdirs();
                        fileCount = 0;
                    }
                    Path target = currentFolder.toPath().resolve(file.getName());
                    Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                    fileCount++;
                }

                Platform.runLater(() -> showMessage("Files distributed into subfolders successfully!", "Information"));
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> showMessage("Error during file distribution", "Error"));
            }
        }).start();
    }

    // ===========================
    // Fusionner les sous-dossiers dans un dossier principal
    // ===========================
    @FXML
    private void handleMergeSubfolders() {
        if (selectedDirectory == null) {
            showMessage("No directory selected", "Error");
            return;
        }

        new Thread(() -> {
            try {
                File[] subfolders = selectedDirectory.listFiles(File::isDirectory);
                if (subfolders == null || subfolders.length == 0) {
                    Platform.runLater(() -> showMessage("No subfolders to merge", "Information"));
                    return;
                }

                for (File subfolder : subfolders) {
                    File[] files = subfolder.listFiles(File::isFile);
                    if (files == null) continue;

                    for (File file : files) {
                        Path target = selectedDirectory.toPath().resolve(file.getName());
                        int counter = 1;
                        String baseName = fileExtensionService.getFileNameWithoutExtension(file.getName());
                        String ext = fileExtensionService.getFileExtension(file.getName());

                        while (Files.exists(target)) {
                            target = selectedDirectory.toPath().resolve(baseName + "_" + counter + ext);
                            counter++;
                        }
                        Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                    }

                    // Supprimer le sous-dossier vide
                    subfolder.delete();
                }

                Platform.runLater(() -> showMessage("Subfolders merged successfully!", "Information"));
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> showMessage("Error during subfolder merging", "Error"));
            }
        }).start();
    }

    // ===========================
    // Nettoyage des noms de dossiers
    // ===========================
    @FXML
    private void handleCleanFoldersName() {
        if (selectedDirectory == null) {
            showMessage("No directory selected", "Error");
            return;
        }

        File[] folders = selectedDirectory.listFiles(File::isDirectory);
        if (folders == null || folders.length == 0) {
            showMessage("No folders found to clean", "Information");
            return;
        }

        for (File folder : folders) {
            String cleanFolderName = cleanName(folder.getName());
            if (!folder.getName().equals(cleanFolderName)) {
                File newFolder = new File(folder.getParent(), cleanFolderName);
                if (!newFolder.exists()) folder.renameTo(newFolder);
            }
        }

        showMessage("Folder names cleaned successfully!", "Information");
    }

    // ===========================
    // Animation de présentation JavaFX
    // ===========================
    private void playPresentationAnimation(Node node) {
        FadeTransition fade = new FadeTransition(Duration.seconds(1), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setCycleCount(1);
        fade.play();

        TranslateTransition translate = new TranslateTransition(Duration.seconds(1), node);
        translate.setFromY(-50);
        translate.setToY(0);
        translate.setCycleCount(1);
        translate.play();
    }


    @FXML
    private TextField filesPerFolderField; // Pour handleStartDistribution
    @FXML
    private File recycleBinDirectory;      // Pour handleExportRecycleBin
    // ===========================
    // Export/Restoration de la corbeille
    // ===========================
    @FXML
    private void handleExportRecycleBin() {
        if (recycleBinDirectory == null || !recycleBinDirectory.exists()) {
            showMessage("Recycle Bin not defined or doesn't exist", "Error");
            return;
        }

        File exportDir = new File(selectedDirectory, "recycle_export");
        if (!exportDir.exists()) exportDir.mkdirs();

        File[] files = recycleBinDirectory.listFiles(File::isFile);
        if (files == null || files.length == 0) {
            showMessage("Recycle Bin is empty", "Information");
            return;
        }

        for (File file : files) {
            Path target = exportDir.toPath().resolve(file.getName());
            int counter = 1;
            String baseName = fileExtensionService.getFileNameWithoutExtension(file.getName());
            String ext = fileExtensionService.getFileExtension(file.getName());

            while (Files.exists(target)) {
                target = exportDir.toPath().resolve(baseName + "_" + counter + ext);
                counter++;
            }

            try {
                Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        showMessage("Recycle Bin exported successfully!", "Information");
    }

    @FXML
    private void handleChooseDestination() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Destination Directory");
        File selected = directoryChooser.showDialog(null);
        if (selected != null) {
            selectedDirectory = selected;
            showMessage("Selected directory: " + selectedDirectory.getAbsolutePath(), "Information");
        }
    }

    @FXML
    private void handleSelectDistributionFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder to Distribute");
        File folder = chooser.showDialog(null);
        if (folder != null && folder.isDirectory()) {
            distributionRootFolder = folder; // variable déjà déclarée dans ton controller
            distributionFolderField.setText(folder.getAbsolutePath()); // met à jour le TextField
        }
    }

}