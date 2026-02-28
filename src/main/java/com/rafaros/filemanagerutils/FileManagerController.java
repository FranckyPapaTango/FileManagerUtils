package com.rafaros.filemanagerutils;

import com.rafaros.filemanagerutils.service.FileExtensionService;
import com.rafaros.filemanagerutils.service.MessageService;
import com.rafaros.filemanagerutils.service.StrateMovingService;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImageReadException;

public class FileManagerController {

    private final StrateMovingService strateMovingService = new StrateMovingService();
    private final MessageService messageService = new MessageService();
    private final FileExtensionService fileExtensionService = new FileExtensionService();

    private List<File> selectedFiles = new ArrayList<>();
    private File selectedDirectory;
    private File containerDirectory; // 🔒 mis à jour correctement
    private File selectedDirectory2; // pour génération .txt
    private File distributionRootFolder;
    private Path destinationRoot;

    /* ================================== */
    /*          FILE EXTENSION UI         */
    /* ================================== */
    @FXML private TextField extensionField;
    @FXML private TextArea selectedFilesInfo;
    @FXML private ProgressBar progressBar;
    @FXML private CheckBox gatherInContainerCheckbox;
    @FXML private TextField containerNameField;

    @FXML
    private void handleSelectFiles() {
        selectedFiles.clear();
        selectedDirectory = null;
        selectedFilesInfo.clear();
        progressBar.setProgress(0);
        progressBar.setVisible(false);

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files");
        List<File> files = fileChooser.showOpenMultipleDialog(selectedFilesInfo.getScene().getWindow());

        if (files != null && !files.isEmpty()) {
            selectedFiles.addAll(files);
            selectedDirectory = files.get(files.size() - 1).getParentFile();
        }
        updateSelectedFilesInfo();
    }


    @FXML
    private void handleSelectDirectory() {

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Folder");
        File dir = directoryChooser.showDialog(selectedFilesInfo.getScene().getWindow());

        if (dir == null) {
            return;
        }

        selectedFiles.clear();
        selectedDirectory = dir;

        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".png")
                    || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg");
        });

        if (files == null || files.length == 0) {
            messageService.showMessage(
                    Alert.AlertType.INFORMATION,
                    "No images",
                    "No image files found in selected folder."
            );
            return;
        }

        selectedFiles.addAll(Arrays.asList(files));

        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);
        progressBar.setVisible(false);

        updateSelectedFilesInfo();
    }

    /**
     * Méthode robuste de conversion PNG -> JPG
     */
        private void convertToJpgRobuste(File inputFile, File outputFile) throws IOException {
            BufferedImage inputImage = ImageIO.read(inputFile);
            if (inputImage == null) {
                System.err.println("Impossible de lire l'image : " + inputFile.getName());
                return;
            }

            int width = inputImage.getWidth();
            int height = inputImage.getHeight();

            // Crée directement un BufferedImage TYPE_INT_RGB pour JPG
            BufferedImage convertedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            // Graphics2D avec rendu rapide et fond blanc
            Graphics2D g2d = convertedImage.createGraphics();
            g2d.setBackground(Color.WHITE); // définit le fond
            g2d.clearRect(0, 0, width, height); // remplit le fond en blanc

            // Dessine l'image PNG sur le fond blanc
            g2d.drawImage(inputImage, 0, 0, null);
            g2d.dispose();

            // Écriture optimisée en JPG
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                ImageIO.write(convertedImage, "jpg", os);
            }
        }

    private void updateSelectedFilesInfo() {
        StringBuilder info = new StringBuilder();
        if (selectedDirectory != null) {
            info.append("Selected folder:\n").append(selectedDirectory.getAbsolutePath()).append("\n\n");
        }
        info.append("Number of files selected: ").append(selectedFiles.size());
        selectedFilesInfo.setText(info.toString());
    }

    @FXML
    private void handleProceed() {
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            messageService.showMessage(Alert.AlertType.WARNING, "No files selected", "Please select files or a folder first.");
            return;
        }
        fileExtensionService.handleProceed(selectedFiles, extensionField, progressBar);
    }

    @FXML
    private void handleRenameContent() {
        containerDirectory = null; // reset volontaire
        if (gatherInContainerCheckbox.isSelected()) {
            containerDirectory = fileExtensionService.handleRenameContent(selectedDirectory, true, containerNameField.getText());
        } else {
            fileExtensionService.handleRenameContent(selectedDirectory, false, null);
        }
    }

    @FXML
    private void handleGatherInContainer() {
        containerNameField.setVisible(gatherInContainerCheckbox.isSelected());
    }

    /* ================================== */
    /*         REPAIR CORRUPTED IMAGES   */
    /* ================================== */
    @FXML private TextField selectedImagesField;

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
            for (File file : selectedFiles) fileNames.append(file.getName()).append("; ");
            selectedImagesField.setText(fileNames.toString());
        }
    }

    @FXML
    private void handleRepairImages() {
        if (selectedFiles == null) return;

        for (File file : selectedFiles) {
            if (!isImageValid(file)) {
                System.out.println("Attempting to repair: " + file.getName());
                if (!repairImage(file)) System.out.println("Failed to repair: " + file.getName());
            } else System.out.println("File valid: " + file.getName());
        }
    }

    private boolean isImageValid(File imageFile) {
        try {
            if (ImageIO.read(imageFile) == null) return false;
            try { return Imaging.getImageInfo(imageFile) != null; } catch (ImageReadException e) { return false; }
        } catch (Exception e) { return false; }
    }

    private boolean repairImage(File imageFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder("magick", "convert", imageFile.getAbsolutePath(), imageFile.getAbsolutePath());
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    /* ================================== */
    /*         FILE LIST GENERATION       */
    /* ================================== */
    @FXML private TextField restrictionField;
    @FXML private Label locationLabel;
    @FXML private Label statusLabel;

    @FXML
    private void handleChooseLocation() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder");
        File dir = chooser.showDialog(null);
        if (dir != null) {
            selectedDirectory2 = dir;
            locationLabel.setText("Selected: " + dir.getAbsolutePath());
        } else locationLabel.setText("No folder selected");
    }

    @FXML
    private void handleGenerateFilesList() {
        if (selectedDirectory2 == null) { statusLabel.setText("Status: Please select a folder first!"); return; }
        String restriction = restrictionField.getText() != null ? restrictionField.getText().trim() : "";
        boolean hasRestriction = !restriction.isEmpty();
        Path outputDir = Paths.get(selectedDirectory2.getAbsolutePath(), "fileList");

        try {
            if (!Files.exists(outputDir)) Files.createDirectories(outputDir);

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

            if (filePaths.isEmpty()) { statusLabel.setText("Status: No matching files found."); return; }

            Collections.shuffle(filePaths, new Random(System.currentTimeMillis()));

            final int MAX_FILES_PER_TXT = 12_000;
            int totalFiles = (int) Math.ceil((double) filePaths.size() / MAX_FILES_PER_TXT);

            for (int i = 0; i < totalFiles; i++) {
                int start = i * MAX_FILES_PER_TXT;
                int end = Math.min(start + MAX_FILES_PER_TXT, filePaths.size());
                List<String> subList = filePaths.subList(start, end);

                String fileName = String.format("filesList_%d.txt", i + 1);
                Path outputFile = outputDir.resolve(fileName);

                try (OutputStream outputStream = Files.newOutputStream(outputFile);
                     Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                    for (String filePath : subList) writer.write(filePath + System.lineSeparator());
                }
            }

            statusLabel.setText(String.format("Status: %d randomized file(s) list generated successfully!", totalFiles));
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Status: An error occurred while generating the files list.");
        }
    }

    /* ================================== */
    /*       STRATE MOVING & DISTRIBUTION */
    /* ================================== */
    @FXML private TextField destinationField;
    @FXML private Label strateStatusLabel;
    @FXML private Button moveFilesButton;
    @FXML private ProgressBar distributionProgressBar;
    @FXML private TextField distributionFolderField;
    @FXML private TextField subdivisionCountField;
    @FXML private Label distributionStatusLabel;
    @FXML private Button cleanFoldersButton;
    @FXML private Button exportRecycleBinButton;
    @FXML private ProgressBar exportProgressBar;
    @FXML private ToggleButton recycleBinToggle;

    @FXML private VBox presentationCard;
    @FXML private Tab presentationTab;

    @FXML
    private void initialize() {
        moveFilesButton.setDisable(true);

        // Presentation animation
        presentationCard.setOpacity(0);
        presentationCard.setTranslateY(20);
        presentationTab.setOnSelectionChanged(event -> { if (presentationTab.isSelected()) playPresentationAnimation(); });
        Platform.runLater(() -> { if (presentationTab.isSelected()) playPresentationAnimation(); });

        // RecycleBin toggle
        recycleBinToggle.setSelected(false);
        recycleBinToggle.setText("RecycleBin Off");
        recycleBinToggle.setStyle("-fx-background-color: red; -fx-text-fill: white;");
        exportRecycleBinButton.setDisable(true);

        recycleBinToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            exportRecycleBinButton.setDisable(!newVal);
            recycleBinToggle.setText(newVal ? "RecycleBin On" : "RecycleBin Off");
            recycleBinToggle.setStyle(newVal ?
                    "-fx-background-color: green; -fx-text-fill: white;" :
                    "-fx-background-color: red; -fx-text-fill: white;");
        });
    }

    private void playPresentationAnimation() {
        FadeTransition fade = new FadeTransition(Duration.millis(1200), presentationCard);
        fade.setFromValue(0); fade.setToValue(1); fade.setInterpolator(Interpolator.EASE_OUT);
        TranslateTransition slide = new TranslateTransition(Duration.millis(1200), presentationCard);
        slide.setFromY(20); slide.setToY(0); slide.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fade, slide).play();
    }

    @FXML
    private void handleChooseDestination() {
        DirectoryChooser chooser = new DirectoryChooser();
        File dir = chooser.showDialog(null);
        if (dir != null) {
            destinationRoot = dir.toPath();
            destinationField.setText(dir.getAbsolutePath());
            moveFilesButton.setDisable(false);
        }
    }

    @FXML
    private void handleMoveFiles() {
        strateMovingService.handleMoveFiles(strateStatusLabel, moveFilesButton, destinationRoot, exportProgressBar);
    }

    @FXML
    private void handleCleanFoldersName() { strateMovingService.handleCleanFoldersName(strateStatusLabel); }

    @FXML
    private void handleExportRecycleBin() { strateMovingService.handleExportRecycleBin(exportRecycleBinButton, strateStatusLabel); }

    @FXML
    private void handleExportFolderList() { strateMovingService.handleExportFolderList(selectedFilesInfo); }

    @FXML
    private void handleRecycleBinToggle() {
        boolean active = recycleBinToggle.isSelected();
        exportRecycleBinButton.setDisable(!active);
        recycleBinToggle.setText(active ? "RecycleBin On" : "RecycleBin Off");
    }

    /* ================================== */
    /*     FOLDER CONTENT DISTRIBUTOR     */
    /* ================================== */
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

    // Distribution / Merge / Rollback Tasks sont inchangés depuis ton code
    // ✅ Ils sont sûrs et déjà optimisés
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
                    // rollback en cas d'erreur
                    for (Path p : movedFiles)
                        Files.move(p, distributionRootFolder.toPath().resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    for (Path dir : createdDirs) {
                        if (Files.isDirectory(dir) && Objects.requireNonNull(dir.toFile().list()).length == 0)
                            Files.deleteIfExists(dir);
                    }
                    throw ex;
                }
                return null;
            }
        };

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
                int totalFiles = Arrays.stream(subDirs)
                        .mapToInt(d -> Objects.requireNonNull(d.listFiles(File::isFile)).length)
                        .sum();
                int processed = 0;

                for (File dir : subDirs) {
                    File[] filesInDir = dir.listFiles(File::isFile);
                    if (filesInDir == null) continue;
                    for (File file : filesInDir) {
                        Files.move(file.toPath(), distributionRootFolder.toPath().resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
                        movedFiles.add(file.toPath());
                        processed++;
                        updateProgress(processed, totalFiles);
                    }
                    File[] remaining = dir.listFiles();
                    if (remaining == null || remaining.length == 0) Files.deleteIfExists(dir.toPath());
                }
                return null;
            }
        };

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
}