package com.seewhy.syaiagent.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PDFGenerationToolTest {

    @Test
    @Tag("integration")
    void generatePDF() {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "demo1.pdf";
        String content = "demo1 https://www.baidu.cn";
        String result = tool.generatePDF(fileName, content);
        assertNotNull(result);
    }
}
