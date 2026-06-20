package com.restprovider.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ProcessUtil {
    private ProcessUtil() {
    }

    public static String run(String command, String arguments) {
        String joined = (command == null ? "" : command) + " " + (arguments == null ? "" : arguments);
        List<String> shellCommand = isWindows()
                ? List.of("cmd", "/c", joined)
                : List.of("sh", "-c", joined);
        try {
            Process process = new ProcessBuilder(shellCommand)
                    .redirectErrorStream(true)
                    .start();
            byte[] out = process.getInputStream().readAllBytes();
            process.waitFor();
            return new String(out, StandardCharsets.UTF_8).trim();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Process failed: " + e.getMessage();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
