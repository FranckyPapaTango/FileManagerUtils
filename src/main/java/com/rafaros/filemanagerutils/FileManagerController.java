package com.rafaros.filemanagerutils;


import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FileManagerController {
    @FXML
    private TextField extensionField;

    private List<File> selectedFiles;

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
            showMessage("Error occur", "Error");
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
        JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE);
    }
}
