package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.model.DemoArtifactResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyManusArtifactLinkServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void pdfSuccessPathRegistersArtifactAndSanitizesText() throws Exception {
        Path file = write("pdf/resume.pdf", "pdf");
        SyManusArtifactLinkService service = service();

        SyManusArtifactLinkService.ArtifactLinkResult result =
                service.linkArtifacts("PDF generated successfully to: `" + file + "`");

        assertEquals(1, result.artifacts().size());
        DemoArtifactResponse artifact = result.artifacts().getFirst();
        assertEquals("resume.pdf", artifact.fileName());
        assertEquals("application/pdf", artifact.mimeType());
        assertFalse(result.text().contains(file.toString()));
    }

    @Test
    void txtSuccessPathRegistersArtifact() throws Exception {
        Path file = write("file/demo-note.txt", "hello");

        SyManusArtifactLinkService.ArtifactLinkResult result =
                service().linkArtifacts("File written successfully to: " + file);

        assertEquals(1, result.artifacts().size());
        assertEquals("text/plain", result.artifacts().getFirst().mimeType());
    }

    @Test
    void mdSuccessPathRegistersTextArtifact() throws Exception {
        Path file = write("file/backend-java-resume.md", "# Resume");

        SyManusArtifactLinkService.ArtifactLinkResult result =
                service().linkArtifacts("File written successfully to: " + file);

        assertEquals(1, result.artifacts().size());
        assertEquals("backend-java-resume.md", result.artifacts().getFirst().fileName());
        assertEquals("text/plain", result.artifacts().getFirst().mimeType());
        assertFalse(result.text().contains(file.toString()));
    }

    @Test
    void sameRunMdAndPdfRegisterTwoArtifacts() throws Exception {
        Path md = write("file/backend-java-resume.md", "# Resume");
        Path pdf = write("pdf/backend-java-resume.pdf", "pdf");

        SyManusArtifactLinkService.ArtifactLinkResult result = service().linkArtifacts("""
                File written successfully to: %s
                PDF generated successfully to: %s
                """.formatted(md, pdf));

        assertEquals(2, result.artifacts().size());
        assertEquals("backend-java-resume.md", result.artifacts().get(0).fileName());
        assertEquals("backend-java-resume.pdf", result.artifacts().get(1).fileName());
        assertFalse(result.text().contains(md.toString()));
        assertFalse(result.text().contains(pdf.toString()));
    }

    @Test
    void duplicateSuccessPathRegistersOneArtifact() throws Exception {
        Path md = write("file/backend-java-resume.md", "# Resume");

        SyManusArtifactLinkService.ArtifactLinkResult result = service().linkArtifacts("""
                File written successfully to: %s
                File written successfully to: %s
                """.formatted(md, md));

        assertEquals(1, result.artifacts().size());
        assertEquals("backend-java-resume.md", result.artifacts().getFirst().fileName());
    }

    @Test
    void pngSuccessPathRegistersArtifact() throws Exception {
        Path file = write("download/demo-ocean.png", "png");

        SyManusArtifactLinkService.ArtifactLinkResult result =
                service().linkArtifacts("Image downloaded successfully to: `" + file + "`");

        assertEquals(1, result.artifacts().size());
        assertEquals("image/png", result.artifacts().getFirst().mimeType());
    }

    @Test
    void theImageWasDownloadedSuccessfullyToRegistersChineseJpgArtifact() throws Exception {
        Path file = write("download/\u5b87\u5b99.jpg", "jpg");

        SyManusArtifactLinkService.ArtifactLinkResult result =
                service().linkArtifacts("The image was downloaded successfully to: " + file);

        assertEquals(1, result.artifacts().size());
        assertEquals("\u5b87\u5b99.jpg", result.artifacts().getFirst().fileName());
        assertEquals("image/jpeg", result.artifacts().getFirst().mimeType());
        assertFalse(result.text().contains(file.toString()));
    }

    @Test
    void chinesePngJpgAndJpegFileNamesRegisterArtifacts() throws Exception {
        assertImageArtifact("download/\u661f\u7a7a.png", "image/png");
        assertImageArtifact("download/\u661f\u7a7a.jpg", "image/jpeg");
        assertImageArtifact("download/\u661f\u7a7a.jpeg", "image/jpeg");
    }

    @Test
    void outsideTmpPathReturnsEmpty() throws Exception {
        Path outside = Files.createTempFile("outside-artifact", ".txt");
        Files.writeString(outside, "hello");
        try {
            SyManusArtifactLinkService.ArtifactLinkResult result =
                    service().linkArtifacts("File written successfully to: " + outside);

            assertTrue(result.artifacts().isEmpty());
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void unknownExtensionReturnsEmpty() throws Exception {
        Path file = write("file/demo.bin", "hello");

        SyManusArtifactLinkService.ArtifactLinkResult result =
                service().linkArtifacts("File written successfully to: " + file);

        assertTrue(result.artifacts().isEmpty());
    }

    @Test
    void missingFileReturnsEmpty() {
        Path missing = tempDir.resolve("pdf/missing.pdf");

        SyManusArtifactLinkService.ArtifactLinkResult result =
                service().linkArtifacts("PDF generated successfully to: " + missing);

        assertTrue(result.artifacts().isEmpty());
    }

    @Test
    void textWithoutSuccessMarkerReturnsEmpty() throws Exception {
        Path file = write("pdf/resume.pdf", "pdf");

        SyManusArtifactLinkService.ArtifactLinkResult result =
                service().linkArtifacts("Saved a file somewhere: " + file);

        assertTrue(result.artifacts().isEmpty());
    }

    @Test
    void plainPathWithTrailingNoteRegistersArtifact() throws Exception {
        Path file = write("download/demo.jpg", "jpg");

        SyManusArtifactLinkService.ArtifactLinkResult result =
                service().linkArtifacts("Resource downloaded successfully to: " + file + " (tell user this path only)");

        assertEquals(1, result.artifacts().size());
        assertEquals("image/jpeg", result.artifacts().getFirst().mimeType());
    }

    @Test
    void jpegSuccessPathRegistersArtifact() throws Exception {
        Path file = write("download/demo-photo.jpeg", "jpeg");

        SyManusArtifactLinkService.ArtifactLinkResult result =
                service().linkArtifacts("Resource downloaded successfully to: " + file);

        assertEquals(1, result.artifacts().size());
        assertEquals("image/jpeg", result.artifacts().getFirst().mimeType());
    }

    @Test
    void chineseFileNameRegistersArtifact() throws Exception {
        Path file = write("pdf/后端Java简历.pdf", "pdf");

        SyManusArtifactLinkService.ArtifactLinkResult result =
                service().linkArtifacts("PDF generated successfully to: `" + file + "`");

        assertEquals(1, result.artifacts().size());
        assertEquals("后端Java简历.pdf", result.artifacts().getFirst().fileName());
        assertEquals("application/pdf", result.artifacts().getFirst().mimeType());
        assertFalse(result.text().contains(file.toString()));
    }

    @Test
    void successPathWithBackticksDoesNotRegisterArtifact() throws Exception {
        Path file = write("pdf/backend-java-resume.pdf", "pdf");

        SyManusArtifactLinkService.ArtifactLinkResult result =
                service().linkArtifacts("The PDF was generated successfully.\nSuccess path: `" + file + "`");

        assertTrue(result.artifacts().isEmpty());
    }

    @Test
    void repeatedRegisteredPathIsSanitizedEverywhereInCurrentText() throws Exception {
        Path file = write("pdf/backend-java-resume.pdf", "pdf");
        String text = """
                PDF generated successfully to: %s
                The PDF was generated successfully.
                Success path: `%s`
                Download source: %s
                """.formatted(file, file, file);

        SyManusArtifactLinkService.ArtifactLinkResult result = service().linkArtifacts(text);

        assertEquals(1, result.artifacts().size());
        assertFalse(result.text().contains(file.toString()));
        assertEquals(3, count(result.text(), "secure artifact link registered"));
    }

    @Test
    void repeatedImagePathIsSanitizedEverywhereInCurrentText() throws Exception {
        Path file = write("download/\u5b87\u5b99.jpg", "jpg");
        String text = """
                The image was downloaded successfully to: %s
                Image downloaded successfully to: `%s`
                Source path: %s
                """.formatted(file, file, file);

        SyManusArtifactLinkService.ArtifactLinkResult result = service().linkArtifacts(text);

        assertEquals(1, result.artifacts().size());
        assertFalse(result.text().contains(file.toString()));
        assertEquals(3, count(result.text(), "secure artifact link registered"));
    }

    private SyManusArtifactLinkService service() {
        DemoArtifactService artifactService = new DemoArtifactService(
                tempDir,
                Duration.ofMinutes(30),
                DemoArtifactService.DEFAULT_MAX_BYTES,
                Clock.systemUTC()
        );
        return new SyManusArtifactLinkService(artifactService, tempDir);
    }

    private void assertImageArtifact(String relativePath, String mimeType) throws Exception {
        Path file = write(relativePath, "image");

        SyManusArtifactLinkService.ArtifactLinkResult result =
                service().linkArtifacts("The resource was downloaded successfully to: `" + file + "`");

        assertEquals(1, result.artifacts().size());
        assertEquals(mimeType, result.artifacts().getFirst().mimeType());
        assertFalse(result.text().contains(file.toString()));
    }

    private Path write(String relativePath, String content) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private int count(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
