package com.rafaros.filemanagerutils.service;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

public class MessageService {

    /** Affiche un message simple (info, warning, error…) */
    public void showMessage(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    /** Affiche un message d’information par défaut */
    public void showInfo(String title, String content) {
        showMessage(Alert.AlertType.INFORMATION, title, content);
    }

    /** Affiche un message d’avertissement */
    public void showWarning(String title, String content) {
        showMessage(Alert.AlertType.WARNING, title, content);
    }

    /** Affiche un message d’erreur */
    public void showError(String title, String content) {
        showMessage(Alert.AlertType.ERROR, title, content);
    }

    /**
     * Affiche une boîte de dialogue Oui / Non et renvoie true si l’utilisateur a cliqué sur Oui.
     */
    public boolean showConfirmDialog(String title, String message) {
        final boolean[] result = new boolean[1];
        try {
            // On utilise Platform.runLater et wait pour bloquer le thread appelant
            Runnable dialogRunnable = () -> {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);

                // Personnaliser les boutons
                ButtonType yesButton = new ButtonType("Oui");
                ButtonType noButton = new ButtonType("Non");
                alert.getButtonTypes().setAll(yesButton, noButton);

                Optional<ButtonType> response = alert.showAndWait();
                result[0] = response.isPresent() && response.get() == yesButton;
            };

            if (Platform.isFxApplicationThread()) {
                dialogRunnable.run();
            } else {
                Platform.runLater(dialogRunnable);
                // Simple pause pour attendre que le dialogue soit fermé
                while (!result[0] && !Platform.isFxApplicationThread()) {
                    Thread.sleep(50);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            result[0] = false;
        }

        return result[0];
    }
}