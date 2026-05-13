package com.seewhy.syaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.seewhy.syaiagent.constant.FileConstant;
import com.seewhy.syaiagent.guardrail.GuardrailService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF generation tool with safe file names and basic Markdown-to-text cleanup.
 */
@Component
public class PDFGenerationTool {

    private static final String[] FONT_RESOURCE_CANDIDATES = {
            "/fonts/NotoSansCJKsc-Regular.otf",
            "/fonts/SimSun.ttf",
            "/fonts/msyh.ttf",
            "/fonts/SourceHanSansSC-Regular.otf"
    };

    private final GuardrailService guardrailService;

    public PDFGenerationTool() {
        this(new GuardrailService());
    }

    public PDFGenerationTool(GuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    @Tool(description = "Generate a PDF file with given content", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        try {
            Path filePath = guardrailService.validateWritableFileName(fileName, Path.of(fileDir));
            FileUtil.mkdir(fileDir);
            String plainContent = markdownToPlain(content);
            try (PdfWriter writer = new PdfWriter(filePath.toString());
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                PdfFont font = resolveChineseFont();
                document.setFont(font);
                String[] lines = plainContent.split("\\r?\\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        document.add(new Paragraph(" "));
                        continue;
                    }
                    document.add(new Paragraph(trimmed).setFont(font));
                }
            }
            return "PDF generated successfully to: " + filePath;
        } catch (IOException | SecurityException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

    private PdfFont resolveChineseFont() throws IOException {
        for (String resource : FONT_RESOURCE_CANDIDATES) {
            try (InputStream is = getClass().getResourceAsStream(resource)) {
                if (is != null) {
                    String suffix = resource.substring(resource.lastIndexOf('.'));
                    Path temp = Files.createTempFile("pdf-font-", suffix);
                    Files.copy(is, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return PdfFontFactory.createFont(temp.toAbsolutePath().toString(),
                            PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                }
            } catch (Exception ignore) {
                // Try the next configured font.
            }
        }
        for (String[] nameEncoding : new String[][]{
                {"STSongStd-Light", "UniGB-UCS2-H"},
                {"STSong-Light", "UniGB-UCS2-H"}
        }) {
            try {
                return PdfFontFactory.createFont(nameEncoding[0], nameEncoding[1]);
            } catch (Exception ignore) {
                // Try the next iText Asian font alias.
            }
        }
        String winRoot = System.getenv("SystemRoot");
        if (winRoot != null && !winRoot.isEmpty()) {
            String[] winCandidates = {
                    winRoot + "\\Fonts\\simsun.ttc",
                    winRoot + "\\Fonts\\msyh.ttc",
                    winRoot + "\\Fonts\\simhei.ttf",
                    winRoot + "\\Fonts\\simsun.ttf"
            };
            for (String path : winCandidates) {
                if (Files.isRegularFile(Path.of(path))) {
                    try {
                        String fontPath = path.endsWith(".ttc") ? path + ",0" : path;
                        return PdfFontFactory.createFont(fontPath, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                    } catch (Exception ignore) {
                        // Try the next Windows font.
                    }
                }
            }
        }
        return PdfFontFactory.createFont();
    }

    private String markdownToPlain(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("(?m)^#+\\s*", "")
                .replaceAll("(?m)^[-*]\\s+", "  - ")
                .replaceAll("\\*\\*\\*\\*\\s*", "")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("\\|\\s*\\|", " ")
                .trim();
    }
}
