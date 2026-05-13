package com.seewhy.syimagesearchmcp.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImageSearchTool {

    @Value("${pexels.api-key:}")
    private String apiKey;

    private static final String API_URL = "https://api.pexels.com/v1/search";

    @Tool(description = "search image from web")
    public String searchImage(@ToolParam(description = "Search query keyword") String query) {
        try {
            return String.join(",", searchMediumImages(query));
        } catch (Exception e) {
            return "Error search image: " + e.getMessage();
        }
    }

    /**
     * 搜索中等尺寸的图片列表
     *
     * @param query
     * @return
     */
    public List<String> searchMediumImages(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Pexels API key not configured. Set pexels.api-key or PEXELS_API_KEY.");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Image search query cannot be blank.");
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", apiKey);

        // 设置请求参数（仅包含query，可根据文档补充page、per_page等参数）
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);

        String response;
        try (HttpResponse httpResponse = HttpUtil.createGet(API_URL)
                .addHeaders(headers)
                .form(params)
                .setConnectionTimeout(8000)
                .setReadTimeout(15000)
                .execute()) {
            if (httpResponse.getStatus() == 401 || httpResponse.getStatus() == 403) {
                throw new IllegalStateException("Pexels API key was rejected.");
            }
            if (httpResponse.getStatus() == 429) {
                throw new IllegalStateException("Pexels quota or rate limit was reached.");
            }
            if (httpResponse.getStatus() < 200 || httpResponse.getStatus() >= 300) {
                throw new IllegalStateException("Pexels API returned HTTP " + httpResponse.getStatus() + ".");
            }
            response = httpResponse.body();
        }

        // 解析响应JSON（假设响应结构包含"photos"数组，每个元素包含"medium"字段）
        JSONArray photos = JSONUtil.parseObj(response).getJSONArray("photos");
        if (photos == null || photos.isEmpty()) {
            return List.of();
        }
        return photos
                .stream()
                .map(photoObj -> (JSONObject) photoObj)
                .map(photoObj -> photoObj.getJSONObject("src"))
                .filter(src -> src != null)
                .map(photo -> photo.getStr("medium"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}
