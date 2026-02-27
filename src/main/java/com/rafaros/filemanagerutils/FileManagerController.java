package com.rafaros.filemanagerutils;

import com.rafaros.filemanagerutils.service.FileExtensionService;
import com.rafaros.filemanagerutils.service.StrateMovingService;
import com.sun.javafx.charts.Legend;
import com.sun.javafx.menu.MenuItemBase;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
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

import java.io.IOException;


public class FileManagerController {


    private StrateMovingService strateMovingService = new StrateMovingService();

    @FXML
    private TextField extensionField;

    // @FXML private TextField selectedDirectoryField;

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

    // @FXML private Label totalFilesLabel;

    // @FXML private Label filesPerFolderLabel;

    // @FXML private Label remainingFilesLabel;

    @FXML
    private Label distributionStatusLabel;

   // @FXML private Button startDistributionButton;


    private File selectedDirectory2; // pour la fonctionnalité de génération .txt de liste de fullpathname de fichier d'images
    private final FileExtensionService fileExtensionService = new FileExtensionService();

    private List<File> selectedFiles = new ArrayList<>();

    // private List<File> selectedFiles;
    private File selectedDirectory;
    private File containerDirectory;

    @FXML
    private TextArea selectedFilesInfo;

    @FXML
    private void handleSelectFiles() {
        // RESET total
        selectedFiles.clear();
        selectedDirectory = null;
        selectedFilesInfo.clear();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files");

        List<File> files = fileChooser.showOpenMultipleDialog(
                selectedFilesInfo.getScene().getWindow()
        );

        if (files != null && !files.isEmpty()) {
            selectedFiles.addAll(files);
            // dossier du dernier fichier sélectionné
            selectedDirectory = files.get(files.size() - 1).getParentFile();
        }

        updateSelectedFilesInfo();
    }


    @FXML
    private void handleProceed() {

        if (selectedFiles == null || selectedFiles.isEmpty()) {
            showMessage(Alert.AlertType.WARNING,
                    "No files selected",
                    "Please select files or a folder first.");
            return;
        }

        String newExtension = extensionField.getText();
        if (newExtension == null || newExtension.trim().isEmpty()) {
            showMessage(Alert.AlertType.WARNING,
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
                        ok = fileExtensionService.changeSingleFileExtension(file, normalizedExtension);
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
                    Platform.runLater(() -> showMessage(
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

            showMessage(Alert.AlertType.INFORMATION,
                    "File Extension",
                    "Processing completed.");
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressBar.setVisible(false);

            showMessage(Alert.AlertType.ERROR,
                    "File Extension",
                    "An error occurred during processing.");
        });

        new Thread(task, "file-extension-task").start();
    }



    @FXML
    private ProgressBar progressBar;

    @FXML
    private void handleSelectDirectory() {
        // RESET total
        selectedFiles.clear();
        selectedDirectory = null;
        selectedFilesInfo.clear();

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Directory");

        File dir = directoryChooser.showDialog(
                selectedFilesInfo.getScene().getWindow()
        );

        if (dir != null && dir.isDirectory()) {
            selectedDirectory = dir;

            File[] filesArray = dir.listFiles(File::isFile);
            if (filesArray != null) {
                selectedFiles.addAll(List.of(filesArray));
            }
        }

        updateSelectedFilesInfo();
    }


    private void updateSelectedFilesInfo() {
        StringBuilder info = new StringBuilder();

        if (selectedDirectory != null) {
            info.append("Selected folder:\n")
                    .append(selectedDirectory.getAbsolutePath())
                    .append("\n\n");
        }

        info.append("Number of files selected: ")
                .append(selectedFiles.size());

        selectedFilesInfo.setText(info.toString());
    }


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

                // Vérifie si le répertoire de conteneur existe déjà
                if (containerDirectory.exists()) {
                    showMessage("Container directory already exists. Please define a different name.", "Error");
                    return;

                }

                break;

            }
        }
        int confirm = showConfirmDialog("Are you sure you want to rename the content of this directory?", "Confirmation");
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        // Crée le répertoire de conteneur s'il n'existe pas
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
            // Try to read the image using ImageIO
            if (ImageIO.read(imageFile) == null) {
                return false;
            }

            // Further validation with Apache Commons Imaging
            try {
                if (Imaging.getImageInfo(imageFile) == null) {
                    return false;
                }
            } catch (ImageReadException e) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Ouvre un sélecteur de dossier pour choisir l'emplacement.
     */
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

    /**
     * Génère la liste des fichiers et enregistre dans plusieurs fichiers .txt
     * de 12 000 éléments maximum chacun (filesList_1.txt, filesList_2.txt, ...)
     * dans un ordre aléatoire.
     */
    @FXML
    private void handleGenerateFilesList() {
        if (selectedDirectory2 == null) {
            statusLabel.setText("Status: Please select a folder first!");
            return;
        }

        // Récupération et nettoyage de la restriction
        String restriction = restrictionField.getText() != null ? restrictionField.getText().trim() : "";
        boolean hasRestriction = !restriction.isEmpty();

        Path outputDir = Paths.get(selectedDirectory2.getAbsolutePath(), "fileList");

        try {
            // Crée le dossier fileList s'il n'existe pas.
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            // Récupère tous les fichiers avec les extensions spécifiées.
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

            // Mélange aléatoire de la liste complète
            Collections.shuffle(filePaths, new Random(System.currentTimeMillis()));

            // Taille max d’un fichier
            final int MAX_FILES_PER_TXT = 12_000;

            // Nombre total de fichiers à générer
            int totalFiles = (int) Math.ceil((double) filePaths.size() / MAX_FILES_PER_TXT);

            for (int i = 0; i < totalFiles; i++) {
                int start = i * MAX_FILES_PER_TXT;
                int end = Math.min(start + MAX_FILES_PER_TXT, filePaths.size());

                List<String> subList = filePaths.subList(start, end);

                String fileName = String.format("filesList_%d.txt", i + 1);
                Path outputFile = outputDir.resolve(fileName);

                try (OutputStream outputStream = Files.newOutputStream(outputFile);
                     Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {

                    // Écriture du BOM UTF-8
                    outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

                    // Écriture des chemins
                    for (String filePath : subList) {
                        writer.write(filePath + System.lineSeparator());
                    }
                }
            }

            statusLabel.setText(String.format(
                    "Status: %d randomized file(s) list generated successfully in UTF-8 with BOM!",
                    totalFiles
            ));
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Status: An error occurred while generating the files list.");
        }
    }
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


    private File distributionRootFolder;



    @FXML
    private void initialize() {

        moveFilesButton.setDisable(true);

        // état initial invisible
        presentationCard.setOpacity(0);
        presentationCard.setTranslateY(20);

        // Animation lors des changements d’onglet
        presentationTab.setOnSelectionChanged(event -> {
            if (presentationTab.isSelected()) {
                playPresentationAnimation();
            }
        });

        // 🔥 CAS SPÉCIAL : premier affichage (tab déjà sélectionné)
        Platform.runLater(() -> {
            if (presentationTab.isSelected()) {
                playPresentationAnimation();
            }
        });

        // Ajuste le texte du toggle au départ
        recycleBinToggle.setText("RecycleBin Off");

            // Désactive le bouton au départ
            exportRecycleBinButton.setDisable(true);

            // Assure que le toggle commence sur OFF
            recycleBinToggle.setSelected(false);
            recycleBinToggle.setText("RecycleBin Off");

            // Binding du texte et activation/désactivation du bouton
            recycleBinToggle.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                exportRecycleBinButton.setDisable(!isNowSelected);
                recycleBinToggle.setText(isNowSelected ? "RecycleBin On" : "RecycleBin Off");
                // style inline si pas CSS
                recycleBinToggle.setStyle(isNowSelected ?
                        "-fx-background-color: green; -fx-text-fill: white;" :
                        "-fx-background-color: red; -fx-text-fill: white;");
            });

            // Applique le style initial
            recycleBinToggle.setStyle("-fx-background-color: red; -fx-text-fill: white;");
    }






/**
 * Renvoie le nom du fichier sans extension.
 *//*

    private String getFileNameWithoutExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    */
    /**
     * Renvoie l'extension avec le point, ex: ".png"
     *//*

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex);
    }
*/






    private boolean repairImage(File imageFile) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "magick", "convert", imageFile.getAbsolutePath(), imageFile.getAbsolutePath());
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            // Exit code 0 indicates success
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    /*private boolean changeFilesExtension(List<File> files, String newExtension) {
        try {
            for (File file : files) {
                Path source = file.toPath();
                String newFileName = getFileNameWithoutExtension(file) + "." + newExtension;
                Files.move(source, source.resolveSibling(newFileName));
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }*/

    // --- surcharge pour String ---
    /*private String getFileNameWithoutExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }*/

    // --- garde les versions pour File ---
    /*private String getFileNameWithoutExtension(File file) {
        return fileExtensionService.getFileNameWithoutExtension(file.getName());
    }*/

    private void showMessage(String message, String title) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE));
    }

    private void showMessage(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
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

    private boolean renameFilesInDirectory(File directory) {
        try {
            renameFilesRecursively(directory);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

//    private void renameFilesRecursively(File directory) throws IOException {
//        File[] files = directory.listFiles();
//        if (files == null) return;
//
//        for (int i = 0; i < files.length; i++) {
//            File file = files[i];
//            if (file.isDirectory()) {
//                renameFilesRecursively(file);
//            } else {
//                String newFileName = directory.getName() + "_" + (i + 1) + getFileExtension(file);
//                Path source = file.toPath();
//                Path target;
//                if (gatherInContainerCheckbox.isSelected() && containerDirectory != null) {
//                    target = containerDirectory.toPath().resolve(newFileName);
//                } else {
//                    target = source.resolveSibling(newFileName);
//                }
//
//                // Vérifie si le fichier cible existe déjà
//                if (Files.exists(target)) {
//                    System.out.println("File already exists: " + target);
//                    continue; // Passe au fichier suivant
//                }
//
//                // Déplacer le fichier
//                try {
//                    Files.move(source, target);
//                } catch (IOException e) {
//                    System.err.println("Failed to move file: " + source);
//                    e.printStackTrace();
//                }
//            }
//        }
//    }

    /*private String getFileExtension(File file) {
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex);
    }*/
    // --- surcharge pour String ---
    /*private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex);
    }*/


// ---------------------------
// Déplacement des fichiers depuis une liste .txt
// ---------------------------
//        public void moveFilesFromList(Path destinationRoot) throws IOException {
//
//            Path recycleRoot = Paths.get("C:\\$Recycle.Bin");
//
//            Files.walkFileTree(recycleRoot, new SimpleFileVisitor<Path>() {
//                @Override
//                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
//                    if (file.getFileName().toString().startsWith("$I")) {
//                        try {
//                            // ton code pour lire et restaurer le fichier
//                            String originalPath = readOriginalPathFromIFile(file);
//                            System.out.println("Found: " + originalPath);
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                    return FileVisitResult.CONTINUE;
//                }
//
//                @Override
//                public FileVisitResult visitFileFailed(Path file, IOException exc) {
//                    // Ignore les fichiers/dossiers inaccessibles
//                    System.err.println("Skipped inaccessible file/folder: " + file);
//                    return FileVisitResult.CONTINUE;
//                }
//            });
//        }



    // ---------------------------
    // Renommage des fichiers dans un dossier
    // ---------------------------
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

                // Gestion des collisions de fichiers
                int counter = 1;
                String nameWithoutExt = fileExtensionService.getFileNameWithoutExtension(newFileName);
                String ext = fileExtensionService.getFileExtension(newFileName);

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

    /*============================================================================
    ====================  LIST FILE STRATE MOVING =================================
    ==============================================================================*/

    private Path destinationRoot;

    @FXML
    private void handleChooseDestination() {
        DirectoryChooser chooser = new DirectoryChooser();
        File dir = chooser.showDialog(null);
        if (dir != null) {
            destinationRoot = dir.toPath();
            destinationField.setText(dir.getAbsolutePath());

            // 🔥 ACTIVER LE BOUTON
            moveFilesButton.setDisable(false);
        }
    }

    @FXML
    private void handleMoveFiles() {
        this.strateMovingService.handleMoveFiles( strateStatusLabel, moveFilesButton, destinationRoot, exportProgressBar);
    }

    @FXML
    private Button cleanFoldersButton;

    @FXML
    private void handleCleanFoldersName() {
      this.strateMovingService.handleCleanFoldersName(strateStatusLabel);
    }


    @FXML
    private Button exportRecycleBinButton;

    @FXML
    private void handleExportRecycleBin() {
     this.strateMovingService.handleExportRecycleBin(  exportRecycleBinButton,  strateStatusLabel);
    }

    @FXML
    private ProgressBar exportProgressBar;


    @FXML
    private void handleExportFolderList() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Folder to Export List");

        File selectedDir = directoryChooser.showDialog(selectedFilesInfo.getScene().getWindow());
        if (selectedDir == null || !selectedDir.isDirectory()) {
            showMessage(Alert.AlertType.WARNING, "No folder selected", "Please select a valid folder.");
            return;
        }

        // 🔹 Chemin EXACT comme dans StrateMovingService
        Path outputPath = Paths.get(System.getProperty("user.home"), "OneDrive", "Desktop", "desktop_2.txt");

        try {
            // Assure que le dossier Desktop existe
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

            showMessage(Alert.AlertType.INFORMATION,
                    "Export Completed",
                    "File list exported to: " + outputPath.toAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
            showMessage(Alert.AlertType.ERROR,
                    "Error",
                    "Failed to export file list: " + e.getMessage());
        }
    }

    @FXML
    private ToggleButton recycleBinToggle;

    @FXML
    private void handleRecycleBinToggle() {
        boolean active = recycleBinToggle.isSelected();
        exportRecycleBinButton.setDisable(!active); // active/désactive le bouton
        recycleBinToggle.setText(active ? "RecycleBin On" : "RecycleBin Off");
    }
    /*============================================================================
    ==============================================================================
    ==============================================================================*/


    @FXML
    private void handleSelectDistributionFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder to Distribute");

        File folder = chooser.showDialog(null);
        if (folder != null && folder.isDirectory()) {
            distributionRootFolder = folder;
            distributionFolderField.setText(folder.getAbsolutePath());
        }
    }

    @FXML
    private void handleStartDistribution() {
        if (distributionRootFolder == null) {
            distributionStatusLabel.setText("Status: No folder selected");
            return;
        }

        int n;
        try {
            n = Integer.parseInt(subdivisionCountField.getText());
            if (n <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            distributionStatusLabel.setText("Status: Invalid subdivision number");
            return;
        }

        File[] files = distributionRootFolder.listFiles(File::isFile);
        if (files == null || files.length == 0) {
            distributionStatusLabel.setText("Status: No files to distribute");
            return;
        }

        int totalFiles = files.length;
        distributionProgressBar.setProgress(0);
        distributionStatusLabel.setText("Status: Processing...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<Path> movedFiles = new ArrayList<>();
                List<Path> createdDirs = new ArrayList<>();

                try {
                    int index = 0;
                    while (index < totalFiles) {
                        for (int i = 1; i <= n && index < totalFiles; i++, index++) {
                            Path subDir = distributionRootFolder.toPath().resolve(String.valueOf(i));
                            if (!Files.exists(subDir)) {
                                Files.createDirectories(subDir);
                                createdDirs.add(subDir);
                            }

                            Path source = files[index].toPath();
                            Path target = subDir.resolve(source.getFileName());
                            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                            movedFiles.add(target);

                            updateProgress(index + 1, totalFiles);
                        }
                    }
                } catch (Exception ex) {
                    // 🔄 ROLLBACK
                    for (Path p : movedFiles) {
                        try {
                            Files.move(p, distributionRootFolder.toPath().resolve(p.getFileName()),
                                    StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException ignored) {}
                    }
                    for (Path dir : createdDirs) {
                        try {
                            if (Files.isDirectory(dir) && Objects.requireNonNull(dir.toFile().list()).length == 0) {
                                Files.deleteIfExists(dir);
                            }
                        } catch (IOException ignored) {}
                    }
                    throw ex;
                }
                return null;
            }
        };

        distributionProgressBar.progressProperty().unbind();
        distributionProgressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            distributionProgressBar.progressProperty().unbind();
            distributionProgressBar.setProgress(1);
            distributionStatusLabel.setText("Status: Done – " + totalFiles + " files distributed");
        });

        task.setOnFailed(e -> {
            distributionProgressBar.progressProperty().unbind();
            distributionProgressBar.setProgress(0);
            distributionStatusLabel.setText("Status: Error – rollback completed");
        });

        new Thread(task).start();
    }

    @FXML
    private void handleMergeSubfolders() {
        if (distributionRootFolder == null) {
            distributionStatusLabel.setText("Status: No folder selected");
            return;
        }

        File[] subDirs = distributionRootFolder.listFiles(File::isDirectory);
        if (subDirs == null || subDirs.length == 0) {
            distributionStatusLabel.setText("Status: No subfolders to merge");
            return;
        }

        distributionProgressBar.setProgress(0);
        distributionStatusLabel.setText("Status: Merging...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<Path> movedFiles = new ArrayList<>();
                int totalFiles = 0;

                for (File dir : subDirs) {
                    totalFiles += Objects.requireNonNull(dir.listFiles(File::isFile)).length;
                }
                int processed = 0;

                for (File dir : subDirs) {
                    File[] filesInDir = dir.listFiles(File::isFile);
                    if (filesInDir == null) continue;

                    for (File file : filesInDir) {
                        Path target = distributionRootFolder.toPath().resolve(file.getName());
                        Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                        movedFiles.add(target);
                        processed++;
                        updateProgress(processed, totalFiles);
                    }

                    // Supprimer le sous-dossier s'il est vide et ne contient pas de sous-dossiers
                    File[] remaining = dir.listFiles();
                    if (remaining == null || remaining.length == 0) {
                        Files.deleteIfExists(dir.toPath());
                    }
                }
                return null;
            }
        };

        distributionProgressBar.progressProperty().unbind();
        distributionProgressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            distributionProgressBar.progressProperty().unbind();
            distributionProgressBar.setProgress(1);
            distributionStatusLabel.setText("Status: Merge completed");
        });

        task.setOnFailed(e -> {
            distributionProgressBar.progressProperty().unbind();
            distributionProgressBar.setProgress(0);
            distributionStatusLabel.setText("Status: Error during merge");
        });

        new Thread(task).start();
    }

    @FXML
    private VBox presentationCard;

    @FXML
    private Tab presentationTab;

    private void playPresentationAnimation() {

        FadeTransition fade = new FadeTransition(Duration.millis(1200), presentationCard);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(1200), presentationCard);
        slide.setFromY(20);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition animation = new ParallelTransition(fade, slide);
        animation.play();
    }
}