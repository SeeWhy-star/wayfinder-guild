package com.seewhy.syaiagent.tools;

import com.seewhy.syaiagent.search.SearchProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Locale;

/**
 * Web search tool for SyManus. Image search should use {@link ImageSearchTool}.
 */
public class WebSearchTool {

    private final SearchProvider searchProvider;

    public WebSearchTool(SearchProvider searchProvider) {
        this.searchProvider = searchProvider;
    }

    @Tool(description = "Search the web with Tavily. Use only when the user explicitly asks to search, find, look up, research, or query current external web information. Do not use for local resume generation, file generation, PDF generation, or image generation; for image search prefer searchImage tool.")
    public String searchWeb(@ToolParam(description = "Search query keyword") String query) {
        if (looksLikeLocalResumeGeneration(query)) {
            return "This looks like a local resume generation request, not a web search request. Generate the resume locally with the file or PDF tool and do not search the web.";
        }
        return searchProvider.search(query);
    }

    private boolean looksLikeLocalResumeGeneration(String query) {
        String value = String.valueOf(query == null ? "" : query).toLowerCase(Locale.ROOT);
        boolean resume = value.contains("resume") || value.contains("cv") || value.contains("简历");
        boolean javaBackend = value.contains("java") || value.contains("backend") || value.contains("后端");
        boolean explicitSearch = value.contains("搜索") || value.contains("查找") || value.contains("查询")
                || value.contains("search") || value.contains("find") || value.contains("look up")
                || value.contains("template") || value.contains("模板") || value.contains("范例") || value.contains("example");
        return resume && javaBackend && !explicitSearch;
    }
}
