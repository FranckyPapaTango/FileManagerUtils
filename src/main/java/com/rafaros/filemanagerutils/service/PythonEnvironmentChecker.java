package com.rafaros.filemanagerutils.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class PythonEnvironmentChecker {

    public static void verifyPythonAndPillowOrThrow() {
        try {
            // 1️⃣ Vérifier Python
            Process pythonCheck = new ProcessBuilder("python", "--version")
                    .redirectErrorStream(true)
                    .start();

            if (pythonCheck.waitFor() != 0) {
                throw new IllegalStateException("Python n'est pas installé ou non accessible dans le PATH.");
            }

            // 2️⃣ Vérifier Pillow
            Process pillowCheck = new ProcessBuilder(
                    "python",
                    "-c",
                    "import PIL; print(PIL.__version__)"
            )
                    .redirectErrorStream(true)
                    .start();

            if (pillowCheck.waitFor() != 0) {
                throw new IllegalStateException("Pillow (PIL) n'est pas installé dans l'environnement Python.");
            }

            // 3️⃣ Lecture optionnelle de la version Pillow
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(pillowCheck.getInputStream()))) {

                String version = reader.readLine();
                System.out.println("Python + Pillow OK (Pillow version: " + version + ")");
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Environnement Python invalide. Vérifie que Python et Pillow sont installés.\n"
                            + "Commande recommandée : pip install pillow",
                    e
            );
        }
    }
}