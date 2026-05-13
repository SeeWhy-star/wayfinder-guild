package com.seewhy.syaiagent.tools;

import com.seewhy.syaiagent.guardrail.GuardrailService;
import com.seewhy.syaiagent.search.DisabledSearchProvider;
import com.seewhy.syaiagent.search.SearchProvider;
import com.seewhy.syaiagent.search.TavilySearchProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集中的工具注册类
 */
@Configuration
public class ToolRegistration {

    @Value("${search.provider:tavily}")
    private String searchProviderName;

    @Value("${tavily.api-key:}")
    private String tavilyApiKey;

    @Autowired
    private ImageSearchTool imageSearchTool;

    @Autowired
    private GuardrailService guardrailService;

    @Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool(guardrailService);
        WebSearchTool webSearchTool = new WebSearchTool(searchProvider());
        WebScrapingTool webScrapingTool = new WebScrapingTool(guardrailService);
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool(guardrailService);
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool(guardrailService);
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool(guardrailService);
        TerminateTool terminateTool = new TerminateTool();
        return ToolCallbacks.from(
                imageSearchTool,
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                terminateTool
        );
    }

    private SearchProvider searchProvider() {
        if ("disabled".equalsIgnoreCase(searchProviderName)) {
            return new DisabledSearchProvider();
        }
        if ("tavily".equalsIgnoreCase(searchProviderName)) {
            return new TavilySearchProvider(tavilyApiKey);
        }
        return new DisabledSearchProvider();
    }
}
