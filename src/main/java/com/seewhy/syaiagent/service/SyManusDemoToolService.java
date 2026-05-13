package com.seewhy.syaiagent.service;

import com.seewhy.syaiagent.constant.FileConstant;
import com.seewhy.syaiagent.guardrail.GuardrailService;
import com.seewhy.syaiagent.model.DemoArtifactResponse;
import com.seewhy.syaiagent.model.DemoToolSummaryItem;
import com.seewhy.syaiagent.model.DemoToolResponse;
import com.seewhy.syaiagent.tools.FileOperationTool;
import com.seewhy.syaiagent.tools.PDFGenerationTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SyManusDemoToolService {

    private static final String DEMO_TEXT_FILE = "demo-note.txt";
    private static final String DEMO_TEXT_CONTENT =
            "Wayfinder Guild note: SyManus wrote this bounded UTF-8 file through the backend file tool.";
    private static final String DEMO_PDF_FILE = "demo-note.pdf";
    private static final String DEMO_PDF_CONTENT =
            "Wayfinder Guild note: SyManus generated this PDF through a bounded backend tool.";
    private static final String BRIEF_MD_FILE = "wayfinder-guild-brief.md";
    private static final String BRIEF_PDF_FILE = "wayfinder-guild-interview-brief.pdf";
    private static final String TRACE_CARD_IMAGE_FILE = "wayfinder-trace-card.png";
    private static final Pattern DOCTOR_CHECK_PATTERN = Pattern.compile(
            "^\\[(OK|WARN|ERROR)]\\s+([^:]+):\\s+checked\\s+(\\d+),\\s+(\\d+)\\s+warning\\(s\\),\\s+(\\d+)\\s+error\\(s\\)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    private static final Pattern RESOURCE_PATTERN = Pattern.compile("^([A-Za-z ]+):\\s+(\\d+)\\s*$", Pattern.MULTILINE);
    private static final Pattern TEST_RESULT_PATTERN = Pattern.compile(
            "^\\[INFO]\\s+Tests run:\\s+(\\d+),\\s+Failures:\\s+(\\d+),\\s+Errors:\\s+(\\d+),\\s+Skipped:\\s+(\\d+)",
            Pattern.MULTILINE
    );
    private static final Pattern MAVEN_TOTAL_TIME_PATTERN = Pattern.compile("^\\[INFO]\\s+Total time:\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("(?im)^(?:openjdk|java) version \"([^\"]+)\".*$");
    private static final String BRIEF_CONTENT = """
            # Wayfinder Guild Engineering Brief

            ## What this project demonstrates
            - Travel Agent orchestration: requirement collection, itinerary planning, budget sanity checks, risk advice, and report composition.
            - RAG grounding: curated travel documents are loaded and explained so the answer can show which local knowledge was used.
            - Traceability: each demo chat can expose requirement, retrieval, planning, guardrail, and result events for inspection.
            - Eval practice: RPG and travel eval fixtures make behavior visible instead of relying on a single happy-path transcript.
            - Guardrails: user input, terminal commands, file names, downloads, and generated artifacts are constrained before tools run.
            - SyManus boundary: the live agent can call registered tools, but stable demos are fixed local tasks that do not need model quota or API keys.

            ## Stable demo path
            1. Wayfinder Doctor checks skills, RPG data, eval fixtures, prompts, RAG docs, and naming conventions.
            2. Backend Targeted Tests runs a fixed Maven test set around travel demo, RAG, eval, artifacts, and SyManus demo tools.
            3. Java Runtime Check proves the backend can execute an allowlisted local runtime command.
            4. Portfolio Brief Pack generates this Markdown brief plus an interview-ready PDF artifact.
            5. Trace Card Image creates a fixed Wayfinder trace visual without using external image search.

            ## Live tool task boundary
            Live Tool Tasks still use the real SyManus ReAct loop and registered tools. Search, scraping, downloads, and image search may depend on API keys, provider quota, and the external network. Stable Engineering Demos are deterministic local showcase tasks.
            """;

    private final DemoArtifactService artifactService;
    private final WayfinderDoctorRunner doctorRunner;
    private final WayfinderFixedCommandRunner commandRunner;
    private final FileOperationTool fileTool;
    private final PDFGenerationTool pdfTool;

    @Autowired
    public SyManusDemoToolService(DemoArtifactService artifactService, GuardrailService guardrailService) {
        this(artifactService, guardrailService, new WayfinderDoctorRunner(), new WayfinderFixedCommandRunner());
    }

    public SyManusDemoToolService(DemoArtifactService artifactService,
                                  GuardrailService guardrailService,
                                  WayfinderDoctorRunner doctorRunner) {
        this(artifactService, guardrailService, doctorRunner, new WayfinderFixedCommandRunner());
    }

    public SyManusDemoToolService(DemoArtifactService artifactService,
                                  GuardrailService guardrailService,
                                  WayfinderDoctorRunner doctorRunner,
                                  WayfinderFixedCommandRunner commandRunner) {
        this.artifactService = artifactService;
        this.doctorRunner = doctorRunner;
        this.commandRunner = commandRunner;
        this.fileTool = new FileOperationTool(guardrailService);
        this.pdfTool = new PDFGenerationTool(guardrailService);
    }

    public DemoToolResponse runDemo(String rawType) {
        String type = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT).trim();
        return switch (type) {
            case "doctor", "wayfinder-doctor", "terminal" -> runDoctorDemo();
            case "backend-tests", "targeted-tests", "maven-tests" -> runBackendTargetedTestsDemo();
            case "java-runtime", "java-version", "java" -> runJavaRuntimeCheckDemo();
            case "maven-version", "maven" -> runMavenVersionCheckDemo();
            case "portfolio-brief", "portfolio-brief-pack", "brief-pack", "resume", "java-resume", "resume-pack" ->
                    runPortfolioBriefPackDemo();
            case "trace-card-image", "trace-card", "image" -> runTraceCardImageDemo();
            case "file" -> runFileDemo();
            case "pdf" -> runPdfDemo();
            default -> throw new IllegalArgumentException("Unsupported demo tool type.");
        };
    }

    private DemoToolResponse runDoctorDemo() {
        WayfinderDoctorRunner.DoctorResult result = doctorRunner.runDoctor();
        return new DemoToolResponse(
                "doctor",
                result.success() ? "success" : "error",
                result.message(),
                result.output(),
                null,
                List.of(),
                doctorSummary(result)
        );
    }

    private DemoToolResponse runBackendTargetedTestsDemo() {
        WayfinderFixedCommandRunner.CommandResult result = commandRunner.runBackendTargetedTests();
        return new DemoToolResponse(
                "backend-tests",
                result.success() ? "success" : "error",
                result.message(),
                result.output(),
                null,
                List.of(),
                backendTestSummary(result)
        );
    }

    private DemoToolResponse runJavaRuntimeCheckDemo() {
        WayfinderFixedCommandRunner.CommandResult result = commandRunner.runJavaVersion();
        return new DemoToolResponse(
                "java-runtime",
                result.success() ? "success" : "error",
                result.message(),
                result.output(),
                null,
                List.of(),
                javaRuntimeSummary(result)
        );
    }

    private DemoToolResponse runMavenVersionCheckDemo() {
        WayfinderFixedCommandRunner.CommandResult result = commandRunner.runMavenVersion();
        return new DemoToolResponse(
                "maven-version",
                result.success() ? "success" : "error",
                result.message(),
                result.output(),
                null,
                List.of(),
                mavenVersionSummary(result)
        );
    }

    private DemoToolResponse runPortfolioBriefPackDemo() {
        String fileResult = fileTool.writeFile(BRIEF_MD_FILE, BRIEF_CONTENT);
        ensureSuccess(fileResult, "File written successfully to:");
        String pdfResult = pdfTool.generatePDF(BRIEF_PDF_FILE, BRIEF_CONTENT);
        ensureSuccess(pdfResult, "PDF generated successfully to:");

        Path mdPath = Path.of(FileConstant.FILE_SAVE_DIR, "file", BRIEF_MD_FILE);
        Path pdfPath = Path.of(FileConstant.FILE_SAVE_DIR, "pdf", BRIEF_PDF_FILE);
        DemoArtifactResponse mdArtifact = artifactService.register(mdPath, BRIEF_MD_FILE, "text/plain");
        DemoArtifactResponse pdfArtifact = artifactService.register(pdfPath, BRIEF_PDF_FILE, "application/pdf");
        return new DemoToolResponse(
                "portfolio-brief-pack",
                "success",
                "Portfolio Brief Pack generated Wayfinder Guild Markdown and PDF artifacts.",
                null,
                mdArtifact,
                List.of(mdArtifact, pdfArtifact)
        );
    }

    private DemoToolResponse runTraceCardImageDemo() {
        Path imagePath = Path.of(FileConstant.FILE_SAVE_DIR, "download", TRACE_CARD_IMAGE_FILE);
        createTraceCardImage(imagePath);
        DemoArtifactResponse artifact = artifactService.register(imagePath, TRACE_CARD_IMAGE_FILE, "image/png");
        return new DemoToolResponse(
                "trace-card-image",
                "success",
                TRACE_CARD_IMAGE_FILE + " generated as a fixed Wayfinder trace artifact.",
                null,
                artifact
        );
    }

    private DemoToolResponse runFileDemo() {
        String result = fileTool.writeFile(DEMO_TEXT_FILE, DEMO_TEXT_CONTENT);
        ensureSuccess(result, "File written successfully to:");
        Path path = Path.of(FileConstant.FILE_SAVE_DIR, "file", DEMO_TEXT_FILE);
        DemoArtifactResponse artifact = artifactService.register(path, DEMO_TEXT_FILE, "text/plain");
        return new DemoToolResponse(
                "file",
                "success",
                DEMO_TEXT_FILE + " generated by backend file tool.",
                null,
                artifact
        );
    }

    private List<DemoToolSummaryItem> doctorSummary(WayfinderDoctorRunner.DoctorResult result) {
        if (!result.success()) {
            return List.of(errorSummary(result.message()));
        }
        List<DemoToolSummaryItem> items = new ArrayList<>();
        Matcher checkMatcher = DOCTOR_CHECK_PATTERN.matcher(result.output());
        while (checkMatcher.find()) {
            String state = checkMatcher.group(1).equalsIgnoreCase("OK") ? "success" : "warning";
            String label = titleCase(checkMatcher.group(2));
            String checked = checkMatcher.group(3);
            String warnings = checkMatcher.group(4);
            String errors = checkMatcher.group(5);
            items.add(new DemoToolSummaryItem(label, checked + " OK", warnings + " warnings / " + errors + " errors", state));
        }

        List<String> resources = new ArrayList<>();
        Matcher resourceMatcher = RESOURCE_PATTERN.matcher(result.output());
        while (resourceMatcher.find()) {
            String label = resourceMatcher.group(1).trim();
            if (label.equalsIgnoreCase("Wayfinder resource summary")) {
                continue;
            }
            resources.add(label + " " + resourceMatcher.group(2));
        }
        if (!resources.isEmpty()) {
            items.add(new DemoToolSummaryItem(
                    "Resource summary",
                    resources.size() + " groups",
                    String.join(" / ", resources),
                    "info"
            ));
        }
        if (items.isEmpty()) {
            items.add(new DemoToolSummaryItem("Doctor", "Completed", "All fixed project checks passed.", "success"));
        }
        return items;
    }

    private List<DemoToolSummaryItem> backendTestSummary(WayfinderFixedCommandRunner.CommandResult result) {
        if (!result.success()) {
            return List.of(errorSummary(result.message()));
        }
        String output = result.output();
        DemoToolSummaryItem finalResult = new DemoToolSummaryItem(
                "Build",
                output.contains("BUILD SUCCESS") ? "BUILD SUCCESS" : "Completed",
                "Fixed Maven test gate finished.",
                "success"
        );
        DemoToolSummaryItem tests = lastTestSummary(output);
        DemoToolSummaryItem elapsed = new DemoToolSummaryItem(
                "Total time",
                firstMatch(MAVEN_TOTAL_TIME_PATTERN, output, "n/a"),
                "Maven reported elapsed time.",
                "info"
        );
        DemoToolSummaryItem scope = new DemoToolSummaryItem(
                "Test scope",
                "7 classes",
                "Travel demo, RAG, eval, artifacts, SyManus demo, tool registration.",
                "info"
        );
        return List.of(finalResult, tests, elapsed, scope);
    }

    private DemoToolSummaryItem lastTestSummary(String output) {
        Matcher matcher = TEST_RESULT_PATTERN.matcher(output);
        String tests = "n/a";
        String failures = "n/a";
        String errors = "n/a";
        String skipped = "n/a";
        while (matcher.find()) {
            tests = matcher.group(1);
            failures = matcher.group(2);
            errors = matcher.group(3);
            skipped = matcher.group(4);
        }
        String state = "0".equals(failures) && "0".equals(errors) ? "success" : "error";
        return new DemoToolSummaryItem(
                "Tests",
                tests + " passed",
                failures + " failures / " + errors + " errors / " + skipped + " skipped",
                state
        );
    }

    private List<DemoToolSummaryItem> javaRuntimeSummary(WayfinderFixedCommandRunner.CommandResult result) {
        if (!result.success()) {
            return List.of(errorSummary(result.message()));
        }
        String version = firstMatch(JAVA_VERSION_PATTERN, result.output(), "Java runtime available");
        return List.of(
                new DemoToolSummaryItem("Runtime", version, "Allowlisted java -version command.", "success"),
                new DemoToolSummaryItem("Boundary", "Local check", "No model, API key, or external network required.", "info")
        );
    }

    private List<DemoToolSummaryItem> mavenVersionSummary(WayfinderFixedCommandRunner.CommandResult result) {
        if (!result.success()) {
            return List.of(errorSummary(result.message()));
        }
        String firstLine = result.output().lines().filter(line -> !line.isBlank()).findFirst().orElse("Maven available");
        return List.of(
                new DemoToolSummaryItem("Maven", firstLine, "Allowlisted mvn -version command.", "success"),
                new DemoToolSummaryItem("Boundary", "Local check", "No arbitrary command input is accepted.", "info")
        );
    }

    private DemoToolSummaryItem errorSummary(String message) {
        return new DemoToolSummaryItem("Result", "Needs attention", message, "error");
    }

    private String firstMatch(Pattern pattern, String text, String fallback) {
        Matcher matcher = pattern.matcher(String.valueOf(text));
        return matcher.find() ? matcher.group(1).trim() : fallback;
    }

    private String titleCase(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "Check";
        }
        if (normalized.equalsIgnoreCase("rpg")) {
            return "RPG";
        }
        if (normalized.equalsIgnoreCase("rag docs")) {
            return "RAG docs";
        }
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private DemoToolResponse runPdfDemo() {
        String result = pdfTool.generatePDF(DEMO_PDF_FILE, DEMO_PDF_CONTENT);
        ensureSuccess(result, "PDF generated successfully to:");
        Path path = Path.of(FileConstant.FILE_SAVE_DIR, "pdf", DEMO_PDF_FILE);
        DemoArtifactResponse artifact = artifactService.register(path, DEMO_PDF_FILE, "application/pdf");
        return new DemoToolResponse(
                "pdf",
                "success",
                DEMO_PDF_FILE + " generated by backend PDF tool.",
                null,
                artifact
        );
    }

    private void createTraceCardImage(Path imagePath) {
        try {
            Files.createDirectories(imagePath.getParent());
            BufferedImage image = new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0, 0, new Color(15, 23, 42), 960, 540, new Color(20, 83, 84)));
                g.fillRect(0, 0, 960, 540);

                g.setColor(new Color(255, 247, 209));
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
                g.drawString("Wayfinder Guild Trace", 56, 86);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
                g.setColor(new Color(213, 222, 251));
                g.drawString("Stable SyManus artifact: local, fixed, no external image provider", 58, 122);

                String[] nodes = {"Requirement", "RAG", "Tools", "Guardrails", "Artifacts"};
                int startX = 70;
                int y = 256;
                for (int i = 0; i < nodes.length; i++) {
                    int x = startX + i * 170;
                    if (i > 0) {
                        g.setColor(new Color(158, 234, 216, 180));
                        g.setStroke(new BasicStroke(4));
                        g.drawLine(x - 74, y, x - 28, y);
                    }
                    drawTraceNode(g, x, y, nodes[i], i + 1);
                }

                g.setColor(new Color(255, 211, 110));
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
                g.drawString("Interview signal", 58, 410);
                g.setColor(new Color(213, 222, 251));
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
                g.drawString("Engineering demos show quality gates first, then bounded live tool calls.", 58, 444);
            } finally {
                g.dispose();
            }
            ImageIO.write(image, "png", imagePath.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Could not create demo image artifact.", e);
        }
    }

    private void drawTraceNode(Graphics2D g, int centerX, int centerY, String label, int index) {
        g.setColor(new Color(10, 18, 38));
        g.fillRoundRect(centerX - 64, centerY - 52, 128, 104, 18, 18);
        g.setColor(new Color(158, 234, 216));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(centerX - 64, centerY - 52, 128, 104, 18, 18);
        g.setColor(new Color(255, 211, 110));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        g.drawString(String.valueOf(index), centerX - 9, centerY - 10);
        g.setColor(new Color(255, 247, 209));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        int width = g.getFontMetrics().stringWidth(label);
        g.drawString(label, centerX - width / 2, centerY + 28);
    }

    private void ensureSuccess(String result, String marker) {
        if (result == null || !result.contains(marker)) {
            throw new IllegalStateException(result == null ? "Demo tool failed." : result);
        }
    }
}
