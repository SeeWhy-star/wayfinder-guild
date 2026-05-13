package com.seewhy.syaiagent.search;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavilySearchProviderTest {

    @Test
    void missingApiKeyReturnsConfiguredMessageWithoutHttpCall() {
        TavilySearchProvider provider = new TavilySearchProvider("", "https://example.test/search",
                (endpoint, apiKey, body) -> {
                    throw new AssertionError("HTTP should not be called when API key is missing.");
                });

        String result = provider.search("java resume template");

        assertTrue(result.contains("Live search is not configured"));
        assertTrue(result.contains("TAVILY_API_KEY"));
    }

    @Test
    void quotaStatusReturnsSafeFailure() {
        TavilySearchProvider provider = new TavilySearchProvider("test-key", "https://example.test/search",
                (endpoint, apiKey, body) -> new TavilySearchProvider.TavilyHttpResult(429, "{\"detail\":\"quota\"}"));

        String result = provider.search("java resume template");

        assertTrue(result.contains("quota or rate limits"));
        assertTrue(result.contains("Do not fabricate web results"));
    }

    @Test
    void rejectedApiKeyReturnsSafeFailure() {
        TavilySearchProvider provider = new TavilySearchProvider("rejected-key", "https://example.test/search",
                (endpoint, apiKey, body) -> new TavilySearchProvider.TavilyHttpResult(401, "{\"detail\":\"bad key\"}"));

        String result = provider.search("java resume template");

        assertTrue(result.contains("Tavily API key was rejected"));
        assertTrue(result.contains("Do not fabricate web results"));
    }

    @Test
    void invalidChineseQueryRetriesWithSimplerQuery() {
        AtomicReference<String> retryBody = new AtomicReference<>();
        TavilySearchProvider provider = new TavilySearchProvider("test-key", "https://example.test/search",
                (endpoint, apiKey, body) -> {
                    if (body.contains("！！！")) {
                        return new TavilySearchProvider.TavilyHttpResult(400, "{\"detail\":\"invalid query\"}");
                    }
                    retryBody.set(body);
                    return new TavilySearchProvider.TavilyHttpResult(200, """
                            {
                              "results": [
                                {
                                  "title": "罗翔公开视频",
                                  "url": "https://example.com/video",
                                  "content": "公开视频信息。"
                                }
                              ]
                            }
                            """);
                });

        String result = provider.search("罗翔 最新 公开视频 标题 简介！！！");

        assertTrue(retryBody.get().contains("罗翔 最新 公开视频 标题 简介"));
        assertFalse(retryBody.get().contains("！！！"));
        assertTrue(result.contains("Tavily search results"));
    }

    @Test
    void invalidQueryReturnsClearFailure() {
        TavilySearchProvider provider = new TavilySearchProvider("test-key", "https://example.test/search",
                (endpoint, apiKey, body) -> new TavilySearchProvider.TavilyHttpResult(400, "{\"detail\":\"invalid query\"}"));

        String result = provider.search("!!!");

        assertTrue(result.contains("Tavily rejected the query as invalid"));
        assertTrue(result.contains("Do not fabricate web results"));
    }

    @Test
    void successfulResponseFormatsLimitedResults() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        TavilySearchProvider provider = new TavilySearchProvider("test-key", "https://example.test/search",
                (endpoint, apiKey, body) -> {
                    requestBody.set(body);
                    return new TavilySearchProvider.TavilyHttpResult(200, """
                            {
                              "results": [
                                {
                                  "title": "Java Backend Resume Template",
                                  "url": "https://example.com/resume",
                                  "content": "A focused backend Java resume example with Spring Boot and database projects."
                                }
                              ]
                            }
                            """);
                });

        String result = provider.search("java backend resume template");

        assertTrue(requestBody.get().contains("\"query\":\"java backend resume template\""));
        assertTrue(result.contains("Tavily search results"));
        assertTrue(result.contains("Java Backend Resume Template"));
        assertTrue(result.contains("https://example.com/resume"));
        assertFalse(result.contains("raw_content"));
    }
}
