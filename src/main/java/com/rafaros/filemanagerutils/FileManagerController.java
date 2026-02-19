package com.rafaros.filemanagerutils;

import com.rafaros.filemanagerutils.service.FileExtensionService;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
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

    @FXML private Button startDistributionButton;


    private File selectedDirectory2; // pour la fonctionnalité de génération .txt de liste de fullpathname de fichier d'images


    private List<File> selectedFiles;
    private File selectedDirectory;
    private File containerDirectory;

    @FXML
    private void handleSelectFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files");
        selectedFiles = fileChooser.showOpenMultipleDialog(new Stage());
    }



private final FileExtensionService fileExtensionService = new FileExtensionService();


    @FXML
    private void handleProceed() {

        if (extensionField == null) {
            showFxAlert(Alert.AlertType.ERROR, "Configuration error",
                    "extensionField not injected", "Check fx:id=\"extensionField\" in FXML.");
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
        // S'assurer que le code est exécuté sur le thread JavaFX
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title != null ? title : "Alert");
            alert.setHeaderText(headerText); // peut être null
            alert.setContentText(contentText != null ? contentText : "");
            alert.showAndWait();
        });
    }





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
    }




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
        if (destinationRoot == null) {
            strateStatusLabel.setText("Status: Please select a destination folder first!");
            return;
        }

        moveFilesButton.setDisable(true);

        new Thread(() -> {
            try {
                // 🔥 Chemin du fichier desktop_2.txt
                Path logPath = Paths.get(System.getProperty("user.home"), "OneDrive", "Desktop", "desktop_2.txt");

                if (!Files.exists(logPath)) {
                    Platform.runLater(() -> {
                        strateStatusLabel.setText("Status: desktop_2.txt not found!");
                        moveFilesButton.setDisable(false);
                    });
                    return;
                }

                // 🔥 Lecture UTF-8 avec BOM
                List<String> restoredFiles = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(logPath.toFile()), StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;

                        // Enlever BOM si présent
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

                    // 🔹 Chaque fichier a son propre dossier parent basé sur son nom nettoyé
                    String cleanFolderName = cleanName(fileName);
                    if (cleanFolderName.isEmpty()) cleanFolderName = "UNKNOWN";

                    Path targetDir = destinationRoot.resolve(cleanFolderName);
                    Files.createDirectories(targetDir);

                    Path targetFile = targetDir.resolve(fileName);

                    // 🔹 Gestion des collisions de fichiers dans le même dossier
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



    @FXML
    private Button cleanFoldersButton;

    @FXML
    private void handleCleanFoldersName() {
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


    @FXML
    private Button exportRecycleBinButton;

    @FXML
    private void handleExportRecycleBin() {

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