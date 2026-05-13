package com.seewhy.syaiagent.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Tag("integration")
class SyManusTest {

    @Resource
    private SyManus SyManus;

    @Test
    public void run() {
        String userPrompt = """
                我的旅游同伴居住在上海静安区，请帮我找到 5 公里内合适的旅游地点，
                并结合一些网络图片，制定一份详细的旅游计划，
                并以 PDF 格式输出""";
        String answer = SyManus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }
}
