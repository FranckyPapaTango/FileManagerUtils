package com.rafaros.filemanagerutils.service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class CorruptedRepairService {

    /**
     * Réécriture de tous les fichiers corrompus avec ImageMagick
     */
    public void repairCorruptedFolder(File corruptedDir) {
        if (corruptedDir == null || !corruptedDir.exists() || !corruptedDir.isDirectory()) return;

        File[] files = corruptedDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            try {
                rewriteWithMagick(file);
            } catch (Exception e) {
                System.err.println("Failed to rewrite " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private void rewriteWithMagick(File inputFile) throws IOException, InterruptedException {
        String baseName = inputFile.getName().replaceFirst("\\.[^.]+$", "");
        File outputFile = new File(inputFile.getParentFile(), baseName + "_fixed.jpg");

        ProcessBuilder pb = new ProcessBuilder(
                "magick",
                inputFile.getAbsolutePath(),
                "-define", "png:ignore-crc=TRUE",
                "-background", "white",
                "-alpha", "remove",
                outputFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor();

        if (process.exitValue() == 0 && outputFile.exists()) {
            inputFile.delete();
            outputFile.renameTo(inputFile);
            System.out.println("Rewritten with Magick: " + inputFile.getName());
        } else {
            throw new IOException("Magick rewrite failed for " + inputFile.getName());
        }
    }

    private void rewriteFile(File inputFile) throws IOException {
        // Crée un fichier de sortie temporaire
        File outputFile = new File(inputFile.getParentFile(),
                inputFile.getName().replaceFirst("\\.[^.]+$", "") + "_fixed.jpg");

        BufferedImage inputImage = ImageIO.read(inputFile);
        if (inputImage == null) throw new IOException("ImageIO returned null");

        BufferedImage convertedImage = new BufferedImage(
                inputImage.getWidth(),
                inputImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g2d = convertedImage.createGraphics();
        g2d.setColor(Color.WHITE); // fond blanc pour gérer la transparence
        g2d.fillRect(0, 0, convertedImage.getWidth(), convertedImage.getHeight());
        g2d.drawImage(inputImage, 0, 0, null);
        g2d.dispose();

        if (!ImageIO.write(convertedImage, "jpg", outputFile)) {
            throw new IOException("ImageIO write failed");
        }

        // Remplace l’ancien fichier par le nouveau
        inputFile.delete();
        outputFile.renameTo(inputFile);
        System.out.println("Rewritten: " + inputFile.getName());
    }

    public void rewriteCorruptedWithMagick(File inputFile, String baseName) {
        try {
            File outputFile = new File(
                    inputFile.getParentFile(),
                    baseName + ".jpg"
            );

            ProcessBuilder pb = new ProcessBuilder(
                    "magick",
                    inputFile.getAbsolutePath(),
                    "-define", "png:ignore-crc=TRUE",
                    "-background", "white",
                    "-alpha", "remove",
                    outputFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor();

            if (process.exitValue() == 0 && outputFile.exists()) {
                inputFile.delete();
                outputFile.renameTo(inputFile);
                System.out.println("Rewritten: " + inputFile.getName());
            }

        } catch (Exception e) {
            System.err.println("Failed to rewrite " + inputFile.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}