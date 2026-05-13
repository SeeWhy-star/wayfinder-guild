package com.seewhy.syaiagent.search;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TavilySearchProvider implements SearchProvider {

    static final String DEFAULT_ENDPOINT = "https://api.tavily.com/search";
    private static final int MAX_RESULTS = 5;
    private static final int MAX_SNIPPET_CHARS = 320;
    private static final Pattern CJK = Pattern.compile("\\p{IsHan}");

    private final String apiKey;
    private final String endpoint;
    private final TavilyHttpClient httpClient;

    public TavilySearchProvider(String apiKey) {
        this(apiKey, DEFAULT_ENDPOINT, new HutoolTavilyHttpClient());
    }

    TavilySearchProvider(String apiKey, String endpoint, TavilyHttpClient httpClient) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.endpoint = endpoint;
        this.httpClient = httpClient;
    }

    @Override
    public String search(String query) {
        if (StrUtil.isBlank(query)) {
            return "Live search was not run because the search query is empty. Do not fabricate web results.";
        }
        if (StrUtil.isBlank(apiKey)) {
            return "Live search is not configured. Set TAVILY_API_KEY or tavily.api-key to enable Tavily search. Do not fabricate web results.";
        }

        try {
            TavilyHttpResult response = postSearch(query);
            if (isInvalidQuery(response) && containsChinese(query)) {
                String simplifiedQuery = simplifyChineseQuery(query);
                if (!simplifiedQuery.equals(query)) {
                    TavilyHttpResult retryResponse = postSearch(simplifiedQuery);
                    if (!isInvalidQuery(retryResponse)) {
                        response = retryResponse;
                    }
                }
            }
            if (isInvalidQuery(response)) {
                return "Live search could not run because Tavily rejected the query as invalid. Try a shorter, clearer query. Do not fabricate web results.";
            }
            if (response.status() == 401 || response.status() == 403) {
                return "Live search is unavailable because the Tavily API key was rejected. Do not fabricate web results.";
            }
            if (response.status() == 429 || response.status() == 432 || response.status() == 433) {
                return "Live search is unavailable due to Tavily quota or rate limits. Do not fabricate web results.";
            }
            if (response.status() < 200 || response.status() >= 300) {
                return "Live search is unavailable. Tavily API returned HTTP " + response.status()
                        + ". Do not fabricate web results.";
            }

            JSONObject json = JSONUtil.parseObj(response.body());
            if (json.containsKey("error")) {
                return "Live search is unavailable. Tavily API error: " + json.get("error")
                        + ". Do not fabricate web results.";
            }
            JSONArray results = json.getJSONArray("results");
            if (results == null || results.isEmpty()) {
                return "Tavily search returned no results. Try a more specific search query.";
            }
            return formatResults(results);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return "Live search is unavailable due to a Tavily network/API error: " + message
                    + ". Do not fabricate web results.";
        }
    }

    private TavilyHttpResult postSearch(String query) {
        JSONObject request = JSONUtil.createObj()
                .set("query", query)
                .set("search_depth", "basic")
                .set("max_results", MAX_RESULTS)
                .set("include_answer", false)
                .set("include_raw_content", false)
                .set("include_images", false);
        return httpClient.postJson(endpoint, apiKey, request.toString());
    }

    private boolean isInvalidQuery(TavilyHttpResult response) {
        String body = String.valueOf(response.body()).toLowerCase();
        return response.status() == 400
                && (body.contains("invalid query") || body.contains("query is invalid") || body.contains("invalid_query"));
    }

    private boolean containsChinese(String query) {
        return CJK.matcher(String.valueOf(query)).find();
    }

    private String simplifyChineseQuery(String query) {
        String simplified = String.valueOf(query)
                .replaceAll("[，。！？、；：,.!?;:()（）【】\\[\\]\"'“”‘’]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (simplified.length() <= 40) {
            return simplified;
        }
        return simplified.substring(0, 40).trim();
    }

    private String formatResults(JSONArray results) {
        List<String> formatted = new ArrayList<>();
        int take = Math.min(MAX_RESULTS, results.size());
        for (int i = 0; i < take; i++) {
            JSONObject result = results.getJSONObject(i);
            String title = clean(result.getStr("title", "Untitled"));
            String url = clean(result.getStr("url", ""));
            String content = clean(result.getStr("content", result.getStr("snippet", "")));
            formatted.add((i + 1) + ". " + title
                    + "\nURL: " + url
                    + "\nSnippet: " + truncate(content));
        }
        return "Tavily search results:\n" + String.join("\n\n", formatted);
    }

    private String clean(String value) {
        return String.valueOf(value == null ? "" : value).replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value) {
        String cleaned = clean(value);
        if (cleaned.length() <= MAX_SNIPPET_CHARS) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_SNIPPET_CHARS) + "...";
    }

    interface TavilyHttpClient {
        TavilyHttpResult postJson(String endpoint, String apiKey, String body);
    }

    record TavilyHttpResult(int status, String body) {
    }

    private static class HutoolTavilyHttpClient implements TavilyHttpClient {
        @Override
        public TavilyHttpResult postJson(String endpoint, String apiKey, String body) {
            try (HttpResponse response = HttpUtil.createPost(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .setConnectionTimeout(8000)
                    .setReadTimeout(15000)
                    .execute()) {
                return new TavilyHttpResult(response.getStatus(), response.body());
            }
        }
    }
}
