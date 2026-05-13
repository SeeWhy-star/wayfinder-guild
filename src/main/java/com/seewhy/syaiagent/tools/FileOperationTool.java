package com.seewhy.syaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.seewhy.syaiagent.constant.FileConstant;
import com.seewhy.syaiagent.guardrail.GuardrailService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Path;

public class FileOperationTool {

    private final String fileDir = FileConstant.FILE_SAVE_DIR + "/file";
    private final GuardrailService guardrailService;

    public FileOperationTool() {
        this(new GuardrailService());
    }

    public FileOperationTool(GuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    @Tool(description = "Read content from a file")
    public String readFile(@ToolParam(description = "Name of a file to read") String fileName) {
        try {
            Path filePath = guardrailService.validateWritableFileName(fileName, Path.of(fileDir));
            return FileUtil.readUtf8String(filePath.toFile());
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file")
    public String writeFile(@ToolParam(description = "Name of the file to write") String fileName,
                            @ToolParam(description = "Content to write to the file") String content) {
        try {
            Path filePath = guardrailService.validateWritableFileName(fileName, Path.of(fileDir));
            FileUtil.mkdir(fileDir);
            FileUtil.writeUtf8String(content, filePath.toFile());
            return "File written successfully to: " + filePath;
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }
}
