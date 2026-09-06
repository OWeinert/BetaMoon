/**
 * Non-interactive entry point for RetroMCP.
 *
 * RetroMCP's regular main method returns immediately when System.console() is
 * null. Gradle JavaExec intentionally uses redirected streams, so invoke the
 * public CLI constructor directly instead.
 */
public final class RetroMcpCliRunner {
    private RetroMcpCliRunner() {
    }

    public static void main(String[] args) throws Exception {
        new org.mcphackers.mcp.main.MainCLI(args);
    }
}
