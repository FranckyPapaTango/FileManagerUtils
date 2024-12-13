package com.rafaros.filemanagerutils;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImageReadException;

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

    @FXML
    private void handleProceed() {
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            showMessage("No files selected", "Error");
            return;
        }

        String newExtension = extensionField.getText().trim();
        if (newExtension.isEmpty()) {
            showMessage("Extension field is empty", "Error");
            return;
        }

        boolean success = changeFilesExtension(selectedFiles, newExtension);
        if (success) {
            showMessage("Success!", "Information");
        } else {
            showMessage("An error occurred", "Error");
        }
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
     * Génère la liste des fichiers et enregistre dans filesList.txt.
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
        Path outputFile = outputDir.resolve("filesList.txt");

        try {
            // Crée le dossier fileList s'il n'existe pas.
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            // Ajout du BOM en début de fichier, suivi de l'écriture des chemins de fichiers.
            try (OutputStream outputStream = Files.newOutputStream(outputFile);
                 Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {

                // Écriture du BOM UTF-8.
                outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

                // Récupère tous les fichiers avec les extensions spécifiées.
                List<String> filePaths = Files.walk(selectedDirectory2.toPath())
                        .filter(Files::isRegularFile)
                        .map(Path::toString) // Convertir Path en String avant le filtrage
                        .filter(path -> path.matches(".*\\.(jpg|JPG|jpeg|png|PNG|bmp|BMP|webp|gif)$")) // Filtre sur les extensions d'images
                        .filter(path -> {
                            if (hasRestriction) {
                                // Vérifie si le fichier appartient à un dossier correspondant à la restriction
                                Path parentDir = Paths.get(path).getParent();
                                return parentDir != null && parentDir.getFileName().toString().startsWith(restriction);
                            }
                            return true; // Pas de restriction, tous les fichiers sont pris en compte
                        })
                        .collect(Collectors.toList());

                for (String filePath : filePaths) {
                    writer.write(filePath + System.lineSeparator());
                }

                statusLabel.setText("Status: Files list generated successfully in UTF-8 with BOM!");
            }
        } catch (IOException e) {
            e.printStackTrace();
            statusLabel.setText("Status: An error occurred while generating the files list.");
        }
    }



    @FXML
    private TextField restrictionField;


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

    private boolean changeFilesExtension(List<File> files, String newExtension) {
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
    }

    private String getFileNameWithoutExtension(File file) {
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
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

    private void renameFilesRecursively(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isDirectory()) {
                renameFilesRecursively(file);
            } else {
                String newFileName = directory.getName() + "_" + (i + 1) + getFileExtension(file);
                Path source = file.toPath();
                Path target;
                if (gatherInContainerCheckbox.isSelected() && containerDirectory != null) {
                    target = containerDirectory.toPath().resolve(newFileName);
                } else {
                    target = source.resolveSibling(newFileName);
                }

                // Vérifie si le fichier cible existe déjà
                if (Files.exists(target)) {
                    System.out.println("File already exists: " + target);
                    continue; // Passe au fichier suivant
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

    private String getFileExtension(File file) {
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex);
    }
}
