package com.seewhy.syaiagent.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
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

public class WayfinderFixedCommandRunner {

    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(180);
    private static final int MAX_OUTPUT_CHARS = 20_000;
    private static final String TARGETED_TESTS = String.join(",", List.of(
            "WayfinderTravelControllerDemoToolTest",
            "TravelRagServiceTest",
            "RpgEvalServiceTest",
            "DemoArtifactServiceTest",
            "SyManusArtifactLinkServiceTest",
            "SyManusDemoToolServiceTest",
            "ToolRegistrationTest"
    ));

    public CommandResult runJavaVersion() {
        return runFirst(
                "java -version",
                List.of(List.of("java", "-version")),
                workspaceRoot(),
                SHORT_TIMEOUT
        );
    }

    public CommandResult runMavenVersion() {
        return runFirst(
                "mvn -version",
                mavenCandidates(List.of("-version")),
                workspaceRoot(),
                SHORT_TIMEOUT
        );
    }

    public CommandResult runBackendTargetedTests() {
        return runFirst(
                "mvn -Dtest=" + TARGETED_TESTS + " test",
                mavenCandidates(List.of("-Dtest=" + TARGETED_TESTS, "test")),
                workspaceRoot(),
                TEST_TIMEOUT
        );
    }

    protected Path workspaceRoot() {
        return Path.of("").toAbsolutePath().normalize();
    }

    protected List<List<String>> mavenCandidates(List<String> args) {
        List<List<String>> candidates = new ArrayList<>();
        candidates.add(commandWith("mvn", args));
        if (isWindows()) {
            candidates.add(commandWith("mvn.cmd", args));
        }

        Path wrapper = workspaceRoot().resolve(isWindows() ? "mvnw.cmd" : "mvnw").normalize();
        if (Files.isRegularFile(wrapper)) {
            candidates.add(commandWith(wrapper.toString(), args));
        }
        return candidates;
    }

    private List<String> commandWith(String executable, List<String> args) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(args);
        return command;
    }

    private CommandResult runFirst(String label, List<List<String>> candidates, Path workingDirectory, Duration timeout) {
        IOException lastIo = null;
        for (List<String> command : candidates) {
            try {
                return run(label, command, workingDirectory, timeout);
            } catch (IOException e) {
                lastIo = e;
            }
        }
        String detail = lastIo == null ? "command is unavailable." : lastIo.getMessage();
        return CommandResult.failure(label + " could not start: " + detail);
    }

    private CommandResult run(String label, List<String> command, Path workingDirectory, Duration timeout) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
        Future<String> outputFuture = readerExecutor.submit(() -> readOutput(process));
        try {
            boolean completed = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return CommandResult.failure(label + " timed out after " + timeout.toSeconds() + " seconds.");
            }

            String output = sanitizeOutput(outputFuture.get(1, TimeUnit.SECONDS).trim(), workingDirectory);
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return new CommandResult(false, label + " failed with exit code " + exitCode + ".", output);
            }
            return new CommandResult(true, label + " completed.", output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CommandResult.failure(label + " was interrupted.");
        } catch (ExecutionException | TimeoutException e) {
            return new CommandResult(false, label + " output could not be read.", e.getMessage());
        } finally {
            readerExecutor.shutdownNow();
        }
    }

    private String sanitizeOutput(String output, Path workingDirectory) {
        String sanitized = output == null ? "" : output;
        sanitized = replacePath(sanitized, workingDirectory.toAbsolutePath().normalize(), "<workspace>");
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
                new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
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

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public record CommandResult(boolean success, String message, String output) {
        static CommandResult failure(String message) {
            return new CommandResult(false, message, "");
        }
    }
}
