package com.rafaros.filemanagerutils.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class FileExtensionService {

    /**
     * Change l'extension de tous les fichiers dans la liste.
     * @param files liste de fichiers
     * @param newExtension nouvelle extension sans le point
     * @return true si tout s'est bien passé, false sinon
     */
    public boolean changeFilesExtension(List<File> files, String newExtension) {
        if (files == null || files.isEmpty() || newExtension == null || newExtension.isEmpty()) {
            return false;
        }

        // Nettoyage : enlever le point si l’utilisateur l’a mis
        if (newExtension.startsWith(".")) {
            newExtension = newExtension.substring(1);
        }

        try {
            for (File file : files) {
                Path source = file.toPath();
                String newFileName = getFileNameWithoutExtension(file) + "." + newExtension;
                Path target = source.resolveSibling(newFileName);
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Retourne le nom du fichier sans l'extension
     */
    public String getFileNameWithoutExtension(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return (dotIndex == -1) ? name : name.substring(0, dotIndex);
    }


    // --- surcharge pour String ---
    public String getFileNameWithoutExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    // --- garde les versions pour File ---
    /* private String getFileNameWithoutExtension(File file) {
        return getFileNameWithoutExtension(file.getName());
    }*/

    /**
     * Retourne l'extension du fichier, avec le point.
     */
    public String getFileExtension(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return (dotIndex == -1) ? "" : name.substring(dotIndex);
    }

    // --- surcharge pour String ---
    public String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex);
    }

}
