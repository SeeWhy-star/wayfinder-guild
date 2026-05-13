package com.seewhy.syaiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TerminalOperationToolTest {

    @Test
    void executeEchoInternallyWithoutShell() {
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();

        String result = terminalOperationTool.executeTerminalCommand("echo hello from tool");

        Assertions.assertEquals("hello from tool", result);
    }

    @Test
    void echoDoesNotTreatShellOperatorAsExecutionBoundary() {
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();

        String result = terminalOperationTool.executeTerminalCommand("echo hello & whoami");

        Assertions.assertEquals("hello & whoami", result);
    }

    @Test
    void blocksUnsupportedAllowedExecutableArgs() {
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();

        String result = terminalOperationTool.executeTerminalCommand("mvn test");

        Assertions.assertTrue(result.startsWith("Blocked terminal command:"));
        Assertions.assertTrue(result.contains("Only version checks are allowed"));
    }

    @Test
    void executesJavaVersionThroughArgv() {
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();

        String result = terminalOperationTool.executeTerminalCommand("java -version");

        Assertions.assertFalse(result.contains("cmd.exe"));
        Assertions.assertFalse(result.contains("sh -c"));
        Assertions.assertTrue(result.toLowerCase().contains("version"));
    }
}
