package com.seewhy.syaiagent.tools;

import com.seewhy.syaiagent.guardrail.GuardrailService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.URI;

/**
 * 网页抓取工具
 */

public class WebScrapingTool {

    private static final int TIMEOUT_MILLIS = 15_000;
    private static final int MAX_BODY_BYTES = 512 * 1024;
    private static final int MAX_TEXT_CHARS = 8_000;

    private final GuardrailService guardrailService;

    public WebScrapingTool() {
        this(new GuardrailService());
    }

    public WebScrapingTool(GuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            URI safeUri = guardrailService.validateDownloadUrl(url);
            Document document = Jsoup.connect(safeUri.toString())
                    .timeout(TIMEOUT_MILLIS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .get();
            String text = document.text();
            if (text.length() > MAX_TEXT_CHARS) {
                text = text.substring(0, MAX_TEXT_CHARS) + "...";
            }
            return text;
        } catch (SecurityException e) {
            return "Blocked web scraping request: " + e.getMessage();
        } catch (Exception e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}
