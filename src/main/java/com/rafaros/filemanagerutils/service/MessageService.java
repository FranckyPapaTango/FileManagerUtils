package com.rafaros.filemanagerutils.service;

import javafx.scene.control.Alert;

import javax.swing.*;

 public class MessageService {



     public void showMessage(String message, String title) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE));
    }

    public void showMessage(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

     public int showConfirmDialog(String message, String title) {
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

}
