package com.rafaros.filemanagerutils;



import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class FileManagerUtils extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("file_manager.fxml"));
        Parent root = loader.load();

        // Initial dimensions of the scene
        double initialWidth = 600;  // Replace with your initial width
        double initialHeight = 400; // Replace with your initial height

        // New dimensions
        double newWidth = initialWidth * 1;
        double newHeight = initialHeight * 1;

        primaryStage.setTitle("File Manager Utils");
        primaryStage.setScene(new Scene(root, newWidth, newHeight));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

