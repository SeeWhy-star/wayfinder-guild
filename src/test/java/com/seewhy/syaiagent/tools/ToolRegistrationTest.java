package com.seewhy.syaiagent.tools;

import com.seewhy.syaiagent.search.SearchProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistrationTest {

    @Test
    void tavilyProviderUsesOnlyTavilyApiKeyConfiguration() {
        ToolRegistration registration = new ToolRegistration();
        ReflectionTestUtils.setField(registration, "searchProviderName", "tavily");
        ReflectionTestUtils.setField(registration, "tavilyApiKey", "");

        SearchProvider provider = (SearchProvider) ReflectionTestUtils.invokeMethod(registration, "searchProvider");
        String result = provider.search("java backend resume template");

        assertTrue(result.contains("Live search is not configured"));
        assertTrue(result.contains("TAVILY_API_KEY"));
    }
}
