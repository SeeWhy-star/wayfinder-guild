package com.seewhy.syaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardedToolTest {

    @Test
    void fileToolBlocksPathTraversal() {
        FileOperationTool tool = new FileOperationTool();

        String result = tool.writeFile("../secret.txt", "content");

        assertTrue(result.startsWith("Error writing to file:"));
    }

    @Test
    void terminalToolBlocksDestructiveCommand() {
        TerminalOperationTool tool = new TerminalOperationTool();

        String result = tool.executeTerminalCommand("del important.txt");

        assertTrue(result.startsWith("Blocked terminal command:"));
    }

    @Test
    void downloadToolBlocksLocalNetworkUrl() {
        ResourceDownloadTool tool = new ResourceDownloadTool();

        String result = tool.downloadResource("http://localhost:8123/private", "private.txt");

        assertTrue(result.startsWith("Error downloading resource:"));
    }
}
