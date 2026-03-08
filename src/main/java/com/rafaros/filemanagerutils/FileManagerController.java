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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private void handleSelectFolder() {

        progressBar.progressProperty().unbind();
        selectedFiles.clear();
        selectedDirectory = null;
        selectedFilesInfo.clear();
        progressBar.setProgress(0);
        progressBar.setVisible(false);

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Folder");

        File folder = directoryChooser.showDialog(selectedFilesInfo.getScene().getWindow());

        if (folder != null && folder.isDirectory()) {

            selectedDirectory = folder;

            File[] files = folder.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {   // évite les sous-dossiers
                        selectedFiles.add(file);
                    }
                }
            }
        }

        updateSelectedFilesInfo();
    }

    @FXML
    private void handleSelectFiles() {
        progressBar.progressProperty().unbind();
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
    /*         RENAME BY FOLDERNAME       */
    /* ================================== */

    @FXML private ProgressBar progressBar2;
    @FXML public Label renameStatusLabel;


    /* ---------- EXTENSION RAPIDE ---------- */

    private String getExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot);
    }


    /* ---------- SELECT DIRECTORY ---------- */

    @FXML
    private void handleSelectDirectory() {

        progressBar2.progressProperty().unbind();
        renameStatusLabel.textProperty().unbind();

        progressBar2.setProgress(0);
        progressBar2.setVisible(false);

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Folder");

        File dir = directoryChooser.showDialog(selectedFilesInfo.getScene().getWindow());

        if (dir == null) {
            return;
        }

        selectedFiles.clear();
        selectedDirectory = dir;

        List<File> images = new ArrayList<>();

        File[] subDirs = dir.listFiles(File::isDirectory);

        if (subDirs != null) {
            for (File sub : subDirs) {

                File[] files = sub.listFiles((d, name) -> {
                    String lower = name.toLowerCase();
                    return lower.endsWith(".png")
                            || lower.endsWith(".jpg")
                            || lower.endsWith(".jpeg");
                });

                if (files != null) {
                    images.addAll(Arrays.asList(files));
                }
            }
        }

        if (images.isEmpty()) {

            messageService.showMessage(
                    Alert.AlertType.INFORMATION,
                    "No images",
                    "No image files found in subfolders."
            );

            return;
        }

        selectedFiles.addAll(images);

        progressBar2.progressProperty().unbind();
        progressBar2.setProgress(0);
        progressBar2.setVisible(false);

        updateSelectedFilesInfo();
    }


    /* ---------- RENAME CONTENT ---------- */

    @FXML
    private void handleRenameContent() {

        if (selectedDirectory == null) {

            messageService.showMessage(
                    Alert.AlertType.WARNING,
                    "No folder",
                    "Please select a directory first."
            );

            return;
        }

        /* IMPORTANT : enlever les anciens bindings */
        progressBar2.progressProperty().unbind();
        renameStatusLabel.textProperty().unbind();

        progressBar2.setProgress(0);
        progressBar2.setVisible(true);

        Task<Void> task = new Task<>() {

            @Override
            protected Void call() throws Exception {

                List<File> allImages = new ArrayList<>();

                /* ---------- SCAN ---------- */

                try (Stream<Path> stream = Files.walk(selectedDirectory.toPath())) {

                    stream.filter(Files::isRegularFile)
                            .map(Path::toFile)
                            .filter(f -> {

                                String n = f.getName().toLowerCase();

                                return n.endsWith(".jpg")
                                        || n.endsWith(".jpeg")
                                        || n.endsWith(".png");
                            })
                            .forEach(allImages::add);
                }

                int total = allImages.size();

                if (total == 0) {
                    updateMessage("No images found");
                    return null;
                }

                updateMessage("Found " + total + " images");

                /* ---------- GROUPE PAR DOSSIER ---------- */

                Map<Path, List<File>> filesByFolder = new HashMap<>();

                for (File file : allImages) {

                    Path parent = file.getParentFile().toPath();

                    filesByFolder
                            .computeIfAbsent(parent, k -> new ArrayList<>())
                            .add(file);
                }

                AtomicInteger processed = new AtomicInteger(0);

                /* ---------- CONTAINER ---------- */

                File containerDirectory = null;

                if (gatherInContainerCheckbox.isSelected()) {

                    containerDirectory = new File(
                            selectedDirectory,
                            containerNameField.getText()
                    );

                    containerDirectory.mkdirs();
                }

                File finalContainerDirectory = containerDirectory;

                /* ---------- TRAITEMENT PAR DOSSIER ---------- */

                filesByFolder.entrySet().parallelStream().forEach(entry -> {

                    Path folder = entry.getKey();
                    List<File> files = entry.getValue();

                    String baseName = folder.getFileName().toString();

                    List<Path> tempFiles = new ArrayList<>();
                    Map<Path, String> extensions = new HashMap<>();

                    /* ---------- PASS 1 : TEMP RENAME ---------- */

                    for (File file : files) {

                        try {

                            String ext = getExtension(file);

                            Path temp = file.toPath().resolveSibling(
                                    "temp_" + UUID.randomUUID()
                            );

                            Files.move(file.toPath(), temp);

                            tempFiles.add(temp);
                            extensions.put(temp, ext);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    int index = 1;

                    /* ---------- PASS 2 : FINAL RENAME ---------- */

                    for (Path temp : tempFiles) {

                        try {

                            String ext = extensions.get(temp);

                            Path targetFolder = gatherInContainerCheckbox.isSelected()
                                    ? finalContainerDirectory.toPath()
                                    : folder;

                            Path target = targetFolder.resolve(
                                    baseName + "_" + index + ext
                            );

                            Files.move(temp, target);

                            index++;

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        int done = processed.incrementAndGet();

                        updateProgress(done, total);

                        updateMessage("Processed " + done + " / " + total);
                    }

                });

                updateMessage("Completed ✔");

                return null;
            }
        };

        progressBar2.progressProperty().bind(task.progressProperty());
        renameStatusLabel.textProperty().bind(task.messageProperty());

        new Thread(task, "rename-task").start();
    }


    /* ---------- CHECKBOX ---------- */

    @FXML
    private void handleGatherInContainer() {
        containerNameField.setVisible(gatherInContainerCheckbox.isSelected());
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

        CheckBox reductionCheckBox = new CheckBox("Merge With Reduction");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Merge Options");
        alert.setHeaderText("Choose merge behaviour");

        VBox content = new VBox(10);
        content.getChildren().add(reductionCheckBox);

        alert.getDialogPane().setContent(content);

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        boolean mergeWithReduction = reductionCheckBox.isSelected();

        if (mergeWithReduction) {
            startMergeTask(true);
        } else {
            startMergeTask(false);
        }
    }

/*
    private void startMergeTask(boolean withReduction) {

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

                int totalFiles = Arrays.stream(subDirs)
                        .mapToInt(d -> Objects.requireNonNull(d.listFiles(File::isFile)).length)
                        .sum();

                int processed = 0;

                for (File dir : subDirs) {

                    File[] filesInDir = dir.listFiles(File::isFile);
                    if (filesInDir == null) continue;

                    for (File file : filesInDir) {

                        Path target = distributionRootFolder.toPath().resolve(file.getName());

                        Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

                        processed++;
                        updateProgress(processed, totalFiles);
                    }

                    File[] remaining = dir.listFiles();

                    if (remaining == null || remaining.length == 0) {
                        Files.deleteIfExists(dir.toPath());
                    }
                }

                if (withReduction) {
                    reduceFolders();
                }

                return null;
            }
        };

        distributionProgressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            distributionProgressBar.progressProperty().unbind();
            distributionProgressBar.setProgress(1);

            if (withReduction) {
                distributionStatusLabel.setText("Status: Merge + Reduction completed");
            } else {
                distributionStatusLabel.setText("Status: Merge completed");
            }
        });

        task.setOnFailed(e -> {
            distributionProgressBar.progressProperty().unbind();
            distributionProgressBar.setProgress(0);
            distributionStatusLabel.setText("Status: Error during merge");
        });

        new Thread(task).start();
    }
*/

    private void startMergeTask(boolean withReduction) {

        File[] subDirs = distributionRootFolder.listFiles(File::isDirectory);
        if (subDirs == null || subDirs.length == 0) {
            distributionStatusLabel.setText("Status: No subfolders to merge");
            return;
        }

        distributionProgressBar.setProgress(0);
        distributionStatusLabel.setText("Status: Merging (multi-thread)...");

        Task<Void> task = new Task<>() {

            @Override
            protected Void call() throws Exception {

                List<Path> filesToMove = new ArrayList<>();

                for (File dir : subDirs) {
                    File[] files = dir.listFiles(File::isFile);
                    if (files != null) {
                        for (File f : files) {
                            filesToMove.add(f.toPath());
                        }
                    }
                }

                int totalFiles = filesToMove.size();
                AtomicInteger processed = new AtomicInteger();

                int threads = Runtime.getRuntime().availableProcessors();
                ExecutorService executor = Executors.newFixedThreadPool(threads);

                for (Path source : filesToMove) {

                    executor.submit(() -> {

                        try {

                            Path target = distributionRootFolder.toPath().resolve(source.getFileName());

                            synchronized (distributionRootFolder) {
                                if (Files.exists(target)) {

                                    String base = source.getFileName().toString();
                                    int counter = 1;
                                    Path newTarget;

                                    do {
                                        newTarget = distributionRootFolder.toPath()
                                                .resolve(counter + "_" + base);
                                        counter++;
                                    } while (Files.exists(newTarget));

                                    target = newTarget;
                                }
                            }

                            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

                        } catch (Exception ignored) {
                        }

                        int done = processed.incrementAndGet();
                        updateProgress(done, totalFiles);

                    });
                }

                executor.shutdown();
                executor.awaitTermination(1, TimeUnit.HOURS);

                for (File dir : subDirs) {
                    File[] remaining = dir.listFiles();
                    if (remaining == null || remaining.length == 0) {
                        Files.deleteIfExists(dir.toPath());
                    }
                }

                if (withReduction) {
                    reduceFolders();
                }

                return null;
            }
        };

        distributionProgressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            distributionProgressBar.progressProperty().unbind();
            distributionProgressBar.setProgress(1);
            distributionStatusLabel.setText("Status: Merge completed (ultra fast)");
        });

        task.setOnFailed(e -> {
            distributionProgressBar.progressProperty().unbind();
            distributionProgressBar.setProgress(0);
            distributionStatusLabel.setText("Status: Error during merge");
        });

        new Thread(task).start();
    }


    private void reduceFolders() throws Exception {

        File[] parentDirs = distributionRootFolder.listFiles(File::isDirectory);
        if (parentDirs == null) return;

        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<Path> foldersToProcess = new ArrayList<>();

        for (File dir : parentDirs) {

            File[] children = dir.listFiles(File::isDirectory);

            if (children != null) {
                for (File child : children) {
                    foldersToProcess.add(child.toPath());
                }
            }
        }

        for (Path sourceFolder : foldersToProcess) {

            executor.submit(() -> {

                try {

                    Path targetFolder =
                            distributionRootFolder.toPath()
                                    .resolve(sourceFolder.getFileName());

                    Files.createDirectories(targetFolder);

                    try (DirectoryStream<Path> stream =
                                 Files.newDirectoryStream(sourceFolder)) {

                        for (Path file : stream) {

                            Path target = targetFolder.resolve(file.getFileName());

                            if (Files.exists(target)) {

                                String base = file.getFileName().toString();
                                int counter = 1;
                                Path newTarget;

                                do {
                                    newTarget = targetFolder.resolve(counter + "_" + base);
                                    counter++;
                                } while (Files.exists(newTarget));

                                target = newTarget;
                            }

                            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }

                    Files.deleteIfExists(sourceFolder);

                } catch (Exception ignored) {
                }

            });

        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);

        // suppression des dossiers parents devenus vides
        for (File dir : parentDirs) {

            File[] remaining = dir.listFiles();

            if (remaining == null || remaining.length == 0) {
                Files.deleteIfExists(dir.toPath());
            }
        }
    }
}