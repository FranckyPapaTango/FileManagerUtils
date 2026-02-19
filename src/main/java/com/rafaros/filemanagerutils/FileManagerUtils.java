package com.rafaros.filemanagerutils;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class FileManagerUtils extends Application {

    public static void main(String[] args) {
        // 🔹 Shutdown hook global (sécurise la fermeture EXE)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🔹 Shutdown hook : fermeture forcée des threads résiduels...");
            terminateAllThreads();
        }));

        // 🔹 Lancement JavaFX
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("file_manager.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 860, 640);
        scene.getStylesheets().add(getClass().getResource("/styles/tabs.css").toExternalForm());

        stage.setTitle("File Manager Utils");
        stage.setScene(scene);

        // 🔹 Assurer la fermeture complète EXE à la fermeture de la fenêtre
        stage.setOnCloseRequest(event -> {
            System.out.println("🔹 Fermeture appli…");

            // Stop JavaFX proprement
            Platform.exit();

            // Interrompre tous les threads bloquants
            terminateAllThreads();

            // Quitter la JVM → fermeture de l’EXE
            System.exit(0);
        });

        stage.show();
    }

    // 🔹 Méthode utilitaire : interrompt tous les threads non-daemon
    private static void terminateAllThreads() {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (!t.isDaemon() && t != Thread.currentThread()) {
                try {
                    t.interrupt();
                    t.join(50); // attente courte
                } catch (Exception ignored) {}
            }
        }
    }
}
