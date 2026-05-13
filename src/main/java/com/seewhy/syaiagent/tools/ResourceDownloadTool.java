package com.seewhy.syaiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.seewhy.syaiagent.constant.FileConstant;
import com.seewhy.syaiagent.guardrail.GuardrailService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.URI;
import java.nio.file.Path;

public class ResourceDownloadTool {

    private final GuardrailService guardrailService;

    public ResourceDownloadTool() {
        this(new GuardrailService());
    }

    public ResourceDownloadTool(GuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    @Tool(description = "Download a resource from a given URL")
    public String downloadResource(@ToolParam(description = "URL of the resource to download") String url,
                                   @ToolParam(description = "Name of the file to save the downloaded resource") String fileName) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        try {
            URI safeUri = guardrailService.validateDownloadUrl(url);
            Path filePath = guardrailService.validateWritableFileName(fileName, Path.of(fileDir));
            FileUtil.mkdir(fileDir);
            HttpUtil.createGet(safeUri.toString())
                    .setConnectionTimeout(10000)
                    .setReadTimeout(20000)
                    .execute()
                    .writeBody(filePath.toFile());
            return "Resource downloaded successfully to: " + filePath + " (tell user this path only, do not invent another path)";
        } catch (Exception e) {
            return "Error downloading resource: " + e.getMessage();
        }
    }
}
