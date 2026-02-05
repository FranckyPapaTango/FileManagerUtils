package com.rafaros.filemanagerutils;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class FileManagerUtils extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("file_manager.fxml")
        );
        Parent root = loader.load();

        Scene scene = new Scene(root, 800, 600);

        // 🔥 Chargement du CSS
        scene.getStylesheets().add(
                getClass().getResource("/styles/tabs.css").toExternalForm()
        );

        stage.setTitle("File Manager Utils");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
