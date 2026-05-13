package com.seewhy.syaiagent.tools;

import com.seewhy.syaiagent.guardrail.GuardrailService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TerminalOperationTool {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_OUTPUT_CHARS = 12_000;

    private final GuardrailService guardrailService;

    public TerminalOperationTool() {
        this(new GuardrailService());
    }

    public TerminalOperationTool(GuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    @Tool(description = "Execute a command in the terminal")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute in the terminal") String command) {
        StringBuilder output = new StringBuilder();
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
        try {
            String safeCommand = guardrailService.validateTerminalCommand(command);
            List<String> tokens = tokenize(safeCommand);
            InternalCommandResult internalResult = runInternalCommand(tokens);
            if (internalResult.handled()) {
                return internalResult.output();
            }

            ProcessBuilder builder = new ProcessBuilder(commandArguments(tokens));
            builder.redirectErrorStream(true);
            Process process = builder.start();
            Future<String> outputFuture = readerExecutor.submit(() -> readOutput(process.getInputStream()));
            boolean completed = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "Command execution timed out after " + COMMAND_TIMEOUT.toSeconds() + " seconds.";
            }
            output.append(outputFuture.get(1, TimeUnit.SECONDS));
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                output.append("Command execution failed with exit code: ").append(exitCode);
            }
        } catch (SecurityException e) {
            output.append("Blocked terminal command: ").append(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            output.append("Error executing command: ").append(e.getMessage());
        } catch (ExecutionException | TimeoutException e) {
            output.append("Error reading command output: ").append(e.getMessage());
        } catch (IOException e) {
            output.append("Error executing command: ").append(e.getMessage());
        } finally {
            readerExecutor.shutdownNow();
        }
        return output.toString();
    }

    private List<String> commandArguments(List<String> tokens) {
        if (tokens.isEmpty()) {
            throw new SecurityException("Terminal command cannot be blank.");
        }
        String executable = tokens.get(0).toLowerCase(Locale.ROOT);
        return switch (executable) {
            case "java" -> versionCommand(tokens, "java");
            case "mvn" -> versionCommand(tokens, isWindows() ? "mvn.cmd" : "mvn");
            case "where" -> whereCommand(tokens);
            default -> throw new SecurityException("Terminal command is not supported by the argv executor.");
        };
    }

    private List<String> versionCommand(List<String> tokens, String executable) {
        if (tokens.size() != 2 || !isVersionArg(tokens.get(1))) {
            throw new SecurityException("Only version checks are allowed for " + tokens.get(0) + ".");
        }
        return List.of(executable, tokens.get(1));
    }

    private List<String> whereCommand(List<String> tokens) {
        if (tokens.size() != 2 || !isSimpleExecutableName(tokens.get(1))) {
            throw new SecurityException("where accepts exactly one simple executable name.");
        }
        return isWindows() ? List.of("where.exe", tokens.get(1)) : List.of("which", tokens.get(1));
    }

    private InternalCommandResult runInternalCommand(List<String> tokens) throws IOException {
        if (tokens.isEmpty()) {
            return new InternalCommandResult(false, "");
        }
        String executable = tokens.get(0).toLowerCase(Locale.ROOT);
        return switch (executable) {
            case "echo" -> new InternalCommandResult(true, String.join(" ", tokens.subList(1, tokens.size())));
            case "dir" -> new InternalCommandResult(true, listCurrentDirectory());
            case "type" -> new InternalCommandResult(true, readSimpleFile(tokens));
            default -> new InternalCommandResult(false, "");
        };
    }

    private String listCurrentDirectory() throws IOException {
        StringBuilder output = new StringBuilder();
        Path current = Path.of("").toAbsolutePath().normalize();
        try (var stream = Files.list(current)) {
            stream
                    .limit(200)
                    .map(path -> Files.isDirectory(path) ? path.getFileName() + "/" : path.getFileName().toString())
                    .forEach(name -> appendLine(output, name));
        }
        return truncate(output.toString());
    }

    private String readSimpleFile(List<String> tokens) throws IOException {
        if (tokens.size() != 2 || !isSimpleFileName(tokens.get(1))) {
            throw new SecurityException("type accepts exactly one simple file name.");
        }
        Path file = Path.of("").toAbsolutePath().normalize().resolve(tokens.get(1)).normalize();
        Path current = Path.of("").toAbsolutePath().normalize();
        if (!file.startsWith(current) || !Files.isRegularFile(file)) {
            throw new SecurityException("type can only read a regular file in the working directory.");
        }
        return truncate(Files.readString(file, Charset.defaultCharset()));
    }

    private List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quoteChar = 0;
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if ((ch == '"' || ch == '\'') && (!quoted || quoteChar == ch)) {
                quoted = !quoted;
                quoteChar = quoted ? ch : 0;
                continue;
            }
            if (Character.isWhitespace(ch) && !quoted) {
                addToken(tokens, current);
                continue;
            }
            current.append(ch);
        }
        if (quoted) {
            throw new SecurityException("Terminal command has an unterminated quote.");
        }
        addToken(tokens, current);
        return tokens;
    }

    private void addToken(List<String> tokens, StringBuilder current) {
        if (!current.isEmpty()) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    private void appendLine(StringBuilder output, String line) {
        if (output.length() < MAX_OUTPUT_CHARS) {
            output.append(line).append(System.lineSeparator());
        }
    }

    private boolean isVersionArg(String value) {
        return "-version".equalsIgnoreCase(value) || "--version".equalsIgnoreCase(value);
    }

    private boolean isSimpleExecutableName(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,80}");
    }

    private boolean isSimpleFileName(String value) {
        return value != null && value.matches("[\\p{IsAlphabetic}\\p{IsDigit}._ -]{1,120}")
                && !value.contains("..")
                && !value.contains("/")
                && !value.contains("\\");
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String readOutput(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null && output.length() < MAX_OUTPUT_CHARS) {
                output.append(line).append(System.lineSeparator());
            }
        }
        if (output.length() > MAX_OUTPUT_CHARS) {
            return truncate(output.toString());
        }
        return output.toString();
    }

    private String truncate(String output) {
        if (output.length() > MAX_OUTPUT_CHARS) {
            return output.substring(0, MAX_OUTPUT_CHARS) + System.lineSeparator() + "...";
        }
        return output;
    }

    private record InternalCommandResult(boolean handled, String output) {
    }
}
