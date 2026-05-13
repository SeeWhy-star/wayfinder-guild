package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.constant.FileConstant;
import com.seewhy.syaiagent.model.DemoArtifactResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SyManusArtifactLinkService {

    private static final Pattern SUCCESS_PATH_PATTERN = Pattern.compile(
            "(?i)(File written successfully to:|PDF generated successfully to:|Image downloaded successfully to:|Resource downloaded successfully to:|The image was downloaded successfully to:|The resource was downloaded successfully to:)\\s*`?(.+?)`?(?=\\s*(?:\\)|\\(|\\r?\\n|$))"
    );

    private final DemoArtifactService artifactService;
    private final Path allowedRoot;

    @Autowired
    public SyManusArtifactLinkService(DemoArtifactService artifactService) {
        this(artifactService, Path.of(FileConstant.FILE_SAVE_DIR));
    }

    public SyManusArtifactLinkService(DemoArtifactService artifactService, Path allowedRoot) {
        this.artifactService = artifactService;
        this.allowedRoot = allowedRoot.toAbsolutePath().normalize();
    }

    public ArtifactLinkResult linkArtifacts(String text) {
        if (text == null || text.isBlank()) {
            return new ArtifactLinkResult(String.valueOf(text), List.of());
        }

        List<RegisteredArtifact> registeredArtifacts = registerArtifactsFromToolResponse(text);
        String sanitizedText = sanitizeRegisteredPaths(text, registeredArtifacts);
        List<DemoArtifactResponse> artifacts = new ArrayList<>();
        registeredArtifacts.forEach(registered -> artifacts.add(registered.artifact()));
        return new ArtifactLinkResult(sanitizedText, List.copyOf(artifacts));
    }

    public List<RegisteredArtifact> registerArtifactsFromToolResponse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Map<Path, RegisteredArtifact> registeredArtifacts = new LinkedHashMap<>();
        Matcher matcher = SUCCESS_PATH_PATTERN.matcher(text);
        while (matcher.find()) {
            String rawPath = matcher.group(2);
            Optional<RegisteredArtifact> response = registerCandidate(rawPath);
            if (response.isEmpty()) {
                continue;
            }
            RegisteredArtifact registered = response.get();
            registeredArtifacts.putIfAbsent(registered.path(), registered);
        }
        return List.copyOf(registeredArtifacts.values());
    }

    public String sanitizeRegisteredPaths(String text, List<RegisteredArtifact> registeredArtifacts) {
        if (text == null || text.isBlank() || registeredArtifacts == null || registeredArtifacts.isEmpty()) {
            return String.valueOf(text);
        }
        String sanitizedText = text;
        for (RegisteredArtifact registered : registeredArtifacts) {
            String replacement = registered.artifact().fileName() + " (secure artifact link registered)";
            for (String variant : pathVariants(registered.path())) {
                sanitizedText = sanitizedText.replace(variant, replacement);
            }
        }
        return sanitizedText;
    }

    public Optional<DemoArtifactResponse> register(String rawPath) {
        return registerCandidate(rawPath).map(RegisteredArtifact::artifact);
    }

    private Optional<RegisteredArtifact> registerCandidate(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return Optional.empty();
        }
        try {
            Path path = normalizeCandidate(rawPath);
            if (!path.startsWith(allowedRoot) || !Files.isRegularFile(path)) {
                return Optional.empty();
            }
            String mimeType = detectMimeType(path).orElse(null);
            if (mimeType == null) {
                return Optional.empty();
            }
            DemoArtifactResponse artifact = artifactService.register(path, path.getFileName().toString(), mimeType);
            return Optional.of(new RegisteredArtifact(path, artifact));
        } catch (RuntimeException e) {
            log.debug("Skipping unsafe or invalid SyManus artifact path '{}': {}", rawPath, e.getMessage());
            return Optional.empty();
        }
    }

    private Path normalizeCandidate(String rawPath) {
        String candidate = rawPath.trim();
        while ((candidate.startsWith("`") && candidate.endsWith("`"))
                || (candidate.startsWith("\"") && candidate.endsWith("\""))
                || (candidate.startsWith("'") && candidate.endsWith("'"))) {
            candidate = candidate.substring(1, candidate.length() - 1).trim();
        }
        Path path = Path.of(candidate).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            String trimmed = candidate.replaceAll("[`\"';,.]+$", "").trim();
            path = Path.of(trimmed).toAbsolutePath().normalize();
        }
        return path;
    }

    private List<String> pathVariants(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString();
        List<String> variants = new ArrayList<>();
        variants.add(normalized);
        variants.add(normalized.replace('\\', '/'));
        variants.add(normalized.replace('/', '\\'));
        variants.add(normalized.replace("\\", "\\\\"));
        variants.add(normalized.replace('\\', '/').replace("/", "\\/"));
        return variants.stream().distinct().toList();
    }

    private Optional<String> detectMimeType(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".txt") || fileName.endsWith(".md")) {
            return Optional.of("text/plain");
        }
        if (fileName.endsWith(".pdf")) {
            return Optional.of("application/pdf");
        }
        if (fileName.endsWith(".png")) {
            return Optional.of("image/png");
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return Optional.of("image/jpeg");
        }
        try {
            return Optional.ofNullable(Files.probeContentType(path))
                    .filter(type -> type.equals("text/plain")
                            || type.equals("application/pdf")
                            || type.equals("image/png")
                            || type.equals("image/jpeg"));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public record ArtifactLinkResult(String text, List<DemoArtifactResponse> artifacts) {
    }

    public record RegisteredArtifact(Path path, DemoArtifactResponse artifact) {
    }
}
