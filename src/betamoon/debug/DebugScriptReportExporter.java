package betamoon.debug;

import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
import betamoon.luamodloader.ScriptIssueReports;
import java.util.List;

/** Adds the same full diagnostic report to isolated debug export runs. */
final class DebugScriptReportExporter implements DebugExporter {
    @Override
    public void export(DebugExportSession session) throws Exception {
        List<ScriptIssue> issues = LuaScriptErrors.getEntries();
        session.writeTextFile("script_report.txt", writer -> {
            writer.write(ScriptIssueReports.generate(issues, "All recorded errors and warnings; debug export"));
            return issues.size();
        });
    }
}
