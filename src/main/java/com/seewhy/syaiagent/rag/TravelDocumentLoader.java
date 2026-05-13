package com.seewhy.syaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wayfinder travel knowledge document loader.
 */
@Component
@Slf4j
public class TravelDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    public TravelDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * Load curated Markdown travel knowledge documents with front matter metadata.
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                String content;
                try (InputStream inputStream = resource.getInputStream()) {
                    content = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
                }
                ParsedMarkdown parsed = parseMarkdown(content);
                Map<String, Object> metadata = new LinkedHashMap<>(parsed.metadata());
                metadata.putIfAbsent("filename", filename);
                metadata.putIfAbsent("source", "classpath:document/" + filename);
                allDocuments.add(new Document(parsed.body(), metadata));
            }
        } catch (IOException e) {
            log.error("Markdown document loading failed", e);
        }
        return allDocuments;
    }

    private ParsedMarkdown parseMarkdown(String content) {
        if (content == null || !content.startsWith("---")) {
            return new ParsedMarkdown(Map.of(), content == null ? "" : content.strip());
        }

        String normalized = content.replace("\r\n", "\n");
        int closing = normalized.indexOf("\n---", 4);
        if (closing < 0) {
            return new ParsedMarkdown(Map.of(), normalized.strip());
        }

        String frontMatter = normalized.substring(4, closing).strip();
        String body = normalized.substring(closing + 4).strip();
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (String line : frontMatter.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains(":")) {
                continue;
            }
            String[] parts = trimmed.split(":", 2);
            String key = parts[0].strip();
            String value = parts[1].strip().replaceAll("^\"|\"$", "");
            if ("tags".equals(key)) {
                metadata.put(key, parseTags(value));
            } else {
                metadata.put(key, value);
            }
        }
        return new ParsedMarkdown(metadata, body);
    }

    private List<String> parseTags(String value) {
        String normalized = value.replace("[", "").replace("]", "");
        return Arrays.stream(normalized.split(","))
                .map(tag -> tag.strip().replaceAll("^\"|\"$", ""))
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    private record ParsedMarkdown(Map<String, Object> metadata, String body) {
    }
}
