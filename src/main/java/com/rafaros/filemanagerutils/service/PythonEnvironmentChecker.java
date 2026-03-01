package com.rafaros.filemanagerutils.service;

public final class PythonEnvironmentChecker {

    public static void verifyPythonAndPillowOrThrow() {
        try {
            // 1️⃣ Python présent ?
            Process p1 = new ProcessBuilder("python", "--version")
                    .redirectErrorStream(true)
                    .start();
            if (p1.waitFor() != 0) {
                throw new RuntimeException("Python not found");
            }

            // 2️⃣ Pillow présent ?
            Process p2 = new ProcessBuilder(
                    "python",
                    "-c",
                    "from PIL import Image"
            ).redirectErrorStream(true).start();

            if (p2.waitFor() != 0) {
                throw new RuntimeException("Python Pillow not installed");
            }

            System.out.println("✔ Python + Pillow detected");

        } catch (Exception e) {
            throw new RuntimeException(
                    "Python 3 + Pillow are REQUIRED for repairing corrupted PNG files.\n" +
                            "Please install Python and run: python -m pip install pillow",
                    e
            );
        }
    }
}