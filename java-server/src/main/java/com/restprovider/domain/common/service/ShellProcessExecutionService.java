package com.restprovider.domain.common.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ShellProcessExecutionService implements ProcessExecutionService {
    @Override
    public String run(String command, String arguments, String stepName, String projectName) {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        for (String part : arguments.split(" ")) {
            if (!part.isBlank()) {
                cmd.add(part);
            }
        }

        StringBuilder output = new StringBuilder();
        try {
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            process.waitFor();
        } catch (Exception e) {
            output.append("Process failed: ").append(e.getMessage());
        }

        return output.toString().trim();
    }
}
