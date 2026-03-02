package com.rafaros.filemanagerutils.service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.List;

public class CorruptedRepairService {

    private final File pythonRepairScript;

    public CorruptedRepairService() {
        this.pythonRepairScript = new File(
                Paths.get("src", "main", "resources", "python", "png_to_jpg_repair.py").toAbsolutePath().toString()
        );
        System.out.println("Python repair script path: " + pythonRepairScript.getAbsolutePath());
        if (!pythonRepairScript.exists()) {
            System.err.println("❌ Python repair script not found: " + pythonRepairScript.getAbsolutePath());
        }
    }
    /**
     * Réécriture de tous les fichiers corrompus avec ImageMagick
     */
    /**
     * Réécriture de tous les fichiers corrompus avec ImageMagick / ImageIO / Python fallback
     */
    public void repairCorruptedFolder(File corruptedDir) {
        PythonEnvironmentChecker.verifyPythonAndPillowOrThrow();

        if (corruptedDir == null || !corruptedDir.exists() || !corruptedDir.isDirectory()) return;

        File[] files = corruptedDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            System.out.println("\n🔧 Processing file: " + file.getName());
            try {
                boolean repaired = repairFileWithFallbacks(file, pythonRepairScript);
                if (!repaired) {
                    System.err.println("❌ Final failure: " + file.getName());
                }
            } catch (Exception e) {
                System.err.println("Failed to process " + file.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Essaye de réparer un fichier avec ImageMagick, puis ImageIO, puis Python
     */
    public boolean repairFileWithFallbacks(File inputFile, File pythonScript) {
        String baseName = inputFile.getName().replaceFirst("\\.[^.]+$", "");
        File outputFile = new File(inputFile.getParentFile(), baseName + "_fixed.jpg");

        // 1️⃣ Tentative ImageMagick
        try {
            boolean magickOk = rewriteWithMagick(inputFile);
            if (magickOk) {
                System.out.println("✅ Rewritten with ImageMagick: " + inputFile.getName());
                return true;
            } else {
                System.err.println("ImageMagick could not repair: " + inputFile.getName());
            }
        } catch (Exception magickError) {
            System.err.println("ImageMagick failed for " + inputFile.getName() + ": " + magickError.getMessage());
        }

        // 2️⃣ Tentative ImageIO (manuel Java)
        try {
            rewriteFile(inputFile); // lance IOException si échec
            System.out.println("✅ Rewritten with ImageIO: " + inputFile.getName());
            return true;
        } catch (Exception imageIoError) {
            System.err.println("ImageIO failed for " + inputFile.getName() + ": " + imageIoError.getMessage());
        }

        // 3️⃣ Fallback Python
        System.out.println("➡ Trying Python fallback for " + inputFile.getName());
        boolean pythonOk = repairWithPython(inputFile, outputFile, pythonScript);
        System.out.println("Python exit: " + pythonOk);
        outputFile = new File(inputFile.getParentFile(), baseName + ".jpg");
        if (pythonOk) {
            inputFile.delete();
            // ne pas renommer en PNG, garder le .jpg
            System.out.println("✅ Rewritten with Python: " + outputFile.getName());
            return true;
        }

        // 4️⃣ Échec total
        System.err.println("❌ All repair methods failed for " + inputFile.getName());
        return false;
    }

    /**
     * Réécrit un fichier avec ImageMagick
     */
    private boolean rewriteWithMagick(File inputFile) {
        try {
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
            Process p = pb.start();
            p.waitFor();

            if (p.exitValue() == 0 && outputFile.exists()) {
                inputFile.delete();
                outputFile.renameTo(inputFile);
                return true;
            }
        } catch (Exception e) {
            System.err.println("rewriteWithMagick exception: " + e.getMessage());
        }
        return false;
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

    /**
     * Réécrit un fichier avec le script Python
     */
    public boolean repairWithPython(File inputFile, File outputFile, File scriptFile) {
        if (!scriptFile.exists()) {
            System.err.println("❌ Python script does not exist: " + scriptFile.getAbsolutePath());
            return false;
        }

       if (scriptFile==null){
          scriptFile=this.pythonRepairScript;
       }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "python",
                    scriptFile.getAbsolutePath(),
                    inputFile.getAbsolutePath(),
                    outputFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[Python] " + line);
                }
            }

            int exit = p.waitFor();
            System.out.println("[Python] Exit code: " + exit);
            return exit == 0 && outputFile.exists();

        } catch (Exception e) {
            System.err.println("Python repair exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}