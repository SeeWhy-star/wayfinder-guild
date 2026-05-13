package com.seewhy.syaiagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.seewhy.syaiagent.constant.FileConstant;
import com.seewhy.syaiagent.guardrail.GuardrailService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 图片搜索工具（Pexels API），供 SyManus 在用户要求「搜索/下载 XX 图片」时使用。
 */
@Component
public class ImageSearchTool {

    private static final String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    @Value("${pexels.api-key:}")
    private String apiKey;

    private final GuardrailService guardrailService;
    private final PexelsImageProvider imageProvider;
    private final ImageDownloader imageDownloader;

    public ImageSearchTool() {
        this(new GuardrailService(), new PexelsImageProvider(), new HutoolImageDownloader());
    }

    ImageSearchTool(GuardrailService guardrailService,
                    PexelsImageProvider imageProvider,
                    ImageDownloader imageDownloader) {
        this.guardrailService = guardrailService;
        this.imageProvider = imageProvider;
        this.imageDownloader = imageDownloader;
    }

    void setApiKeyForTest(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for one image by keyword (e.g. ocean, panda, 大海, grassland), download only a URL returned by the image provider into the safe tmp directory, and return the local success path for artifact registration. Use this when user asks for a picture/image/photo.")
    public String searchImage(@ToolParam(description = "Search query keyword") String query) {
        if (apiKey == null || apiKey.isBlank()) {
            return "Pexels image search is not configured. Set pexels.api-key or PEXELS_API_KEY. Do not fabricate image results.";
        }
        try {
            List<String> urls = imageProvider.searchMediumImages(apiKey, query);
            if (urls.isEmpty()) {
                return "Pexels image search returned no images for: " + query + ". Do not fabricate image results.";
            }
            String selectedUrl = urls.getFirst();
            URI safeUri = guardrailService.validateDownloadUrl(selectedUrl);
            Path filePath = safeImagePath(query, selectedUrl);
            FileUtil.mkdir(filePath.getParent().toString());
            imageDownloader.download(safeUri, filePath);
            return "Image downloaded successfully to: " + filePath;
        } catch (Exception e) {
            return "Pexels image search/download failed: " + e.getMessage()
                    + ". Do not fabricate image results.";
        }
    }

    private Path safeImagePath(String query, String selectedUrl) {
        String safeBase = String.valueOf(query == null ? "image" : query)
                .trim()
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}._ -]+", "-")
                .replaceAll("-+", "-");
        if (safeBase.isBlank()) {
            safeBase = "image";
        }
        if (safeBase.length() > 60) {
            safeBase = safeBase.substring(0, 60).trim();
        }
        String extension = selectedUrl.toLowerCase(Locale.ROOT).contains(".png") ? ".png" : ".jpg";
        String fileName = safeBase + "-" + System.currentTimeMillis() + extension;
        return guardrailService.validateWritableFileName(fileName, Path.of(FileConstant.FILE_SAVE_DIR, "image"));
    }

    interface ImageDownloader {
        void download(URI uri, Path filePath);
    }

    static class HutoolImageDownloader implements ImageDownloader {
        @Override
        public void download(URI uri, Path filePath) {
            HttpUtil.createGet(uri.toString())
                    .setConnectionTimeout(10000)
                    .setReadTimeout(20000)
                    .execute()
                    .writeBody(filePath.toFile());
        }
    }

    static class PexelsImageProvider {
        List<String> searchMediumImages(String apiKey, String query) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", apiKey);
            Map<String, Object> params = new HashMap<>();
            params.put("query", query);
            params.put("per_page", 5);

            try (HttpResponse response = HttpUtil.createGet(PEXELS_API_URL)
                    .addHeaders(headers)
                    .form(params)
                    .setConnectionTimeout(8000)
                    .setReadTimeout(15000)
                    .execute()) {
                if (response.getStatus() == 401 || response.getStatus() == 403) {
                    throw new IllegalStateException("Pexels API key was rejected");
                }
                if (response.getStatus() == 429) {
                    throw new IllegalStateException("Pexels quota or rate limit was reached");
                }
                if (response.getStatus() < 200 || response.getStatus() >= 300) {
                    throw new IllegalStateException("Pexels API returned HTTP " + response.getStatus());
                }
                return parseMediumImages(response.body());
            }
        }

        private List<String> parseMediumImages(String body) {
            var photos = JSONUtil.parseObj(body).getJSONArray("photos");
            if (photos == null || photos.isEmpty()) {
                return new ArrayList<>();
            }
            return photos.stream()
                    .map(obj -> (JSONObject) obj)
                    .map(photo -> photo.getJSONObject("src"))
                    .filter(src -> src != null)
                    .map(src -> src.getStr("medium"))
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
        }
    }
}
