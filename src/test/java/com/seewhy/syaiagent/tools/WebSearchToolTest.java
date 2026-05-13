package com.seewhy.syaiagent.tools;

import com.seewhy.syaiagent.search.SearchProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSearchToolTest {

    @Test
    void localResumeGenerationDoesNotCallSearchProvider() {
        WebSearchTool tool = new WebSearchTool(new FailingSearchProvider());

        String result = tool.searchWeb("生成后端Java简历 PDF");

        assertTrue(result.contains("local resume generation request"));
        assertTrue(result.contains("do not search the web"));
    }

    @Test
    void explicitResumeTemplateSearchCallsProvider() {
        WebSearchTool tool = new WebSearchTool(query -> "searched: " + query);

        String result = tool.searchWeb("搜索后端Java简历模板");

        assertEquals("searched: 搜索后端Java简历模板", result);
    }

    private static class FailingSearchProvider implements SearchProvider {
        @Override
        public String search(String query) {
            throw new AssertionError("Search provider should not be called for local resume generation.");
        }
    }
}
