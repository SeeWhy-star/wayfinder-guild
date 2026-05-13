package com.seewhy.syaiagent.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class WayfinderDoctorRunner {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_OUTPUT_CHARS = 20_000;
    private static final List<String> DOCTOR_ARGS = List.of("run", "--", "doctor", "--workspace", "..\\..");
    private static final Path CLI_DIR = Path.of("tools", "wayfinder-cli");
    private static final Path WINDOWS_CARGO = Path.of("C:\\Users\\cycle\\.cargo\\bin\\cargo.exe");

    public DoctorResult runDoctor() {
        Path cliDir = CLI_DIR.toAbsolutePath().normalize();
        if (!Files.isDirectory(cliDir)) {
            return DoctorResult.failure("Wayfinder CLI directory was not found: " + cliDir);
        }

        List<List<String>> candidates = cargoCandidates();
        IOException lastIo = null;
        for (List<String> command : candidates) {
            try {
                return run(command, cliDir);
            } catch (IOException e) {
                lastIo = e;
            }
        }
        String detail = lastIo == null ? "cargo is not available." : lastIo.getMessage();
        return DoctorResult.failure("Wayfinder Doctor could not start because Rust/Cargo is unavailable: " + detail);
    }

    protected List<List<String>> cargoCandidates() {
        List<List<String>> candidates = new ArrayList<>();
        candidates.add(commandWith("cargo"));
        if (Files.isRegularFile(WINDOWS_CARGO)) {
            candidates.add(commandWith(WINDOWS_CARGO.toString()));
        }
        return candidates;
    }

    private List<String> commandWith(String cargoExecutable) {
        List<String> command = new ArrayList<>();
        command.add(cargoExecutable);
        command.addAll(DOCTOR_ARGS);
        return command;
    }

    private DoctorResult run(List<String> command, Path cliDir) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cliDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
        Future<String> outputFuture = readerExecutor.submit(() -> readOutput(process));
        try {
            boolean completed = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return DoctorResult.failure("Wayfinder Doctor timed out after " + TIMEOUT.toSeconds() + " seconds.");
            }
            int exitCode = process.exitValue();
            String text = sanitizeOutput(outputFuture.get(1, TimeUnit.SECONDS).trim(), cliDir);
            if (exitCode != 0) {
                return new DoctorResult(false, "Wayfinder Doctor failed with exit code " + exitCode + ".", text);
            }
            return new DoctorResult(true, "Wayfinder Doctor completed.", text);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DoctorResult.failure("Wayfinder Doctor was interrupted.");
        } catch (ExecutionException | TimeoutException e) {
            return new DoctorResult(false, "Wayfinder Doctor output could not be read.", e.getMessage());
        } finally {
            readerExecutor.shutdownNow();
        }
    }

    private String sanitizeOutput(String output, Path workingDirectory) {
        String sanitized = output == null ? "" : output;
        Path workspaceRoot = Path.of("").toAbsolutePath().normalize();
        sanitized = replacePath(sanitized, workspaceRoot, "<workspace>");
        sanitized = replacePath(sanitized, workingDirectory.toAbsolutePath().normalize(), "<wayfinder-cli>");
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            sanitized = replacePath(sanitized, Path.of(userHome).toAbsolutePath().normalize(), "<user-home>");
        }
        return sanitized;
    }

    private String replacePath(String text, Path path, String replacement) {
        String normalized = path.toString();
        String forward = normalized.replace('\\', '/');
        String escaped = normalized.replace("\\", "\\\\");
        return text
                .replaceAll(Pattern.quote(escaped), replacement)
                .replaceAll(Pattern.quote(normalized), replacement)
                .replaceAll(Pattern.quote(forward), replacement);
    }

    private String readOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && output.length() < MAX_OUTPUT_CHARS) {
                output.append(line).append(System.lineSeparator());
            }
        }
        if (output.length() > MAX_OUTPUT_CHARS) {
            return output.substring(0, MAX_OUTPUT_CHARS) + System.lineSeparator() + "...";
        }
        return output.toString();
    }

    public record DoctorResult(boolean success, String message, String output) {
        static DoctorResult failure(String message) {
            return new DoctorResult(false, message, "");
        }
    }
}
