package com.seewhy.syaiagent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SyManusPromptTest {

    @Test
    void promptDocumentsPortfolioRedirectAndSafeResumeFileNameNormalization() {
        SyManus agent = new SyManus(new ToolCallback[0], mock(ChatModel.class), null);

        String combinedPrompt = agent.getSystemPrompt() + "\n" + agent.getNextStepPrompt();

        assertTrue(combinedPrompt.contains("Portfolio Brief Pack"));
        assertTrue(combinedPrompt.contains("do not create another toy resume"));
        assertTrue(combinedPrompt.contains("C++ -> Cpp"));
        assertTrue(combinedPrompt.contains("C# -> CSharp"));
        assertTrue(combinedPrompt.contains(".NET -> DotNet"));
        assertTrue(combinedPrompt.contains("Node.js -> NodeJs"));
        assertTrue(combinedPrompt.contains("CppBackendResume.pdf"));
    }
}
