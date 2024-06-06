package com.rafaros.filemanagerutils;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
